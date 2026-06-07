pub mod audio;

use jni::objects::{JClass, JIntArray};
use jni::sys::{jboolean, jdouble, jint, jlong};
use jni::JNIEnv;
use keyer_lib::{Keyer, KeyerMode};
use log::debug;

fn set_up_logger() {
    use android_logger::Config;
    use log::LevelFilter;

    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Debug)
            .with_tag("Morse IME"),
    );
}

struct KeyerState {
    keyer: Keyer,
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

fn event_to_int(event: keyer_lib::KeyEvent) -> i32 {
    match event {
        keyer_lib::KeyEvent::KeyOn => 0,
        keyer_lib::KeyEvent::KeyOff => 1,
        keyer_lib::KeyEvent::Dit => 2,
        keyer_lib::KeyEvent::Dah => 3,
        keyer_lib::KeyEvent::CharSpace => 4,
        keyer_lib::KeyEvent::WordSpace => 5,
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
    Box::into_raw(Box::new(KeyerState { keyer })) as jlong
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
            drop(Box::from_raw(ptr as *mut KeyerState));
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
    now: jdouble,
) -> jboolean {
    let state = unsafe { &mut *(ptr as *mut KeyerState) };
    state.keyer.set_dit(pressed != 0, now as f64) as jboolean
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_setDah(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    pressed: jboolean,
    now: jdouble,
) -> jboolean {
    let state = unsafe { &mut *(ptr as *mut KeyerState) };
    state.keyer.set_dah(pressed != 0, now as f64) as jboolean
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_isKeyDown(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let state = unsafe { &mut *(ptr as *mut KeyerState) };
    state.keyer.is_key_down() as jboolean
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_tick<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    now: jdouble,
) -> JIntArray<'local> {
    let state = unsafe { &mut *(ptr as *mut KeyerState) };
    let events = state.keyer.tick(now as f64);
    let len = events.len();
    let result = env
        .new_int_array(len as i32)
        .expect("Failed to create int array");
    if len <= 8 {
        let mut buf = [0i32; 8];
        for (i, &event) in events.iter().enumerate() {
            buf[i] = event_to_int(event);
        }
        env.set_int_array_region(&result, 0, &buf[..len])
            .expect("Failed to set int array region");
    } else {
        let buf: Vec<jint> = events.iter().map(|&e| event_to_int(e) as _).collect();
        env.set_int_array_region(&result, 0, &buf)
            .expect("Failed to set int array region");
    }
    result
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_initAudio(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let player = audio::AudioPlayer::new();
    Box::into_raw(Box::new(player)) as jlong
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_startTone(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    let player = unsafe { &*(ptr as *const audio::AudioPlayer) };
    player.start_tone();
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_stopTone(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    let player = unsafe { &*(ptr as *const audio::AudioPlayer) };
    player.stop_tone();
}

#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_KeyerJNI_destroyAudio(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        unsafe {
            drop(Box::from_raw(ptr as *mut audio::AudioPlayer));
        }
    }
}
