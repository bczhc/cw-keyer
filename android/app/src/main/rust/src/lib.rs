pub mod audio;

use jni::objects::{GlobalRef, JClass, JObject, JValue};
use jni::sys::{jboolean, jdouble, jint, jlong};
use jni::JNIEnv;
use keyer_lib::{KeyEvent, Keyer, KeyerMode};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant};

fn mono_now() -> f64 {
    static START: OnceLock<Instant> = OnceLock::new();
    let start = START.get_or_init(Instant::now);
    start.elapsed().as_secs_f64()
}

fn set_up_logger() {
    use android_logger::Config;
    use log::LevelFilter;

    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Debug)
            .with_tag("Morse IME"),
    );
}

fn mode_from_int(mode: i32) -> KeyerMode {
    match mode {
        0 => KeyerMode::IambicA,
        1 => KeyerMode::IambicB,
        2 => KeyerMode::Ultimatic,
        3 => KeyerMode::Straight,
        _ => KeyerMode::IambicB,
    }
}

struct KeyerState {
    keyer: Keyer,
    audio: audio::AudioPlayer,
}

struct KeyerHandle {
    state: Arc<Mutex<KeyerState>>,
    running: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
    callback: Option<GlobalRef>,
}

impl Drop for KeyerHandle {
    fn drop(&mut self) {
        self.running.store(false, Ordering::Relaxed);
        if let Some(handle) = self.handle.take() {
            handle.join().ok();
        }
    }
}

/// It's safe to call this multiple times.
#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_initLogger(
    _env: JNIEnv,
    _class: JClass,
) {
    set_up_logger();
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_createKeyer(
    _env: JNIEnv,
    _class: JClass,
    wpm: jdouble,
    mode: jint,
) -> jlong {
    let keyer = Keyer::new(wpm as f64, mode_from_int(mode));
    let state = KeyerState {
        keyer,
        audio: audio::AudioPlayer::new(),
    };
    let handle = KeyerHandle {
        state: Arc::new(Mutex::new(state)),
        running: Arc::new(AtomicBool::new(false)),
        handle: None,
        callback: None,
    };
    Box::into_raw(Box::new(handle)) as jlong
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_setEventCallback(
    mut env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    callback: JObject,
) {
    let handle = unsafe { &mut *(ptr as *mut KeyerHandle) };
    handle.callback = Some(env.new_global_ref(callback).unwrap());
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_startKeyer(
    mut env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    let handle = unsafe { &mut *(ptr as *mut KeyerHandle) };
    if handle.handle.is_some() {
        return;
    }
    let state = handle.state.clone();
    let running = handle.running.clone();
    let callback = handle.callback.as_ref().map(|cb| cb.clone());
    let jvm = env.get_java_vm().unwrap();
    running.store(true, Ordering::Relaxed);
    handle.handle = Some(thread::spawn(move || {
        let mut env = jvm.attach_current_thread().unwrap();
        while running.load(Ordering::Relaxed) {
            let now = mono_now();
            let events = {
                let mut guard = state.lock().unwrap();
                guard.keyer.tick(now)
            };
            for event in &events {
                match event {
                    KeyEvent::KeyOn => {
                        let guard = state.lock().unwrap();
                        guard.audio.start_tone();
                    }
                    KeyEvent::KeyOff => {
                        let guard = state.lock().unwrap();
                        guard.audio.stop_tone();
                    }
                    _ => {}
                }
                if let Some(ref cb) = callback {
                    let event_int: i32 = match event {
                        KeyEvent::KeyOn => 0,
                        KeyEvent::KeyOff => 1,
                        KeyEvent::Dit => 2,
                        KeyEvent::Dah => 3,
                        KeyEvent::CharSpace => 4,
                        KeyEvent::WordSpace => 5,
                    };
                    let obj = (*cb).clone();
                    let _ = env.call_method(obj, "onEvent", "(I)V", &[JValue::Int(event_int)]);
                }
            }
            thread::sleep(Duration::from_millis(1));
        }
    }));
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_stopKeyer(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    let handle = unsafe { &mut *(ptr as *mut KeyerHandle) };
    handle.running.store(false, Ordering::Relaxed);
    if let Some(h) = handle.handle.take() {
        h.join().ok();
    }
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_destroyKeyer(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        unsafe {
            drop(Box::from_raw(ptr as *mut KeyerHandle));
        }
    }
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_setDit(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    pressed: jboolean,
) -> jboolean {
    let handle = unsafe { &*(ptr as *const KeyerHandle) };
    let mut guard = handle.state.lock().unwrap();
    guard.keyer.set_dit(pressed != 0, mono_now()) as jboolean
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_setDah(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    pressed: jboolean,
) -> jboolean {
    let handle = unsafe { &*(ptr as *const KeyerHandle) };
    let mut guard = handle.state.lock().unwrap();
    guard.keyer.set_dah(pressed != 0, mono_now()) as jboolean
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_isKeyOn(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let handle = unsafe { &*(ptr as *const KeyerHandle) };
    let guard = handle.state.lock().unwrap();
    guard.keyer.is_key_on() as jboolean
}
