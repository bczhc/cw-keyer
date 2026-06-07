use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;

#[unsafe(no_mangle)]
pub extern "system" fn Java_pers_zhc_android_morseime_MainActivity_helloFromRust(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env
        .new_string("Hello from Rust!")
        .expect("Failed to create Java string");
    output.into_raw()
}
