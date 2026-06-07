use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use keyer_lib::KeyerMode;

#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_MainActivity_helloFromRust(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let keyer = keyer_lib::Keyer::new(25.0, KeyerMode::Straight);

    let output = env
        .new_string(format!("Hello keyer: {:p}", &keyer))
        .expect("Failed to create Java string");
    output.into_raw()
}
