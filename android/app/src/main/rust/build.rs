fn main() {
    // https://github.com/RustAudio/cpal/issues/563#issuecomment-2665150053
    println!("cargo:rustc-link-lib=static=c++");
}