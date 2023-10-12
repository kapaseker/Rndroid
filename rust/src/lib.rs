extern crate jni;
extern crate log;
extern crate android_logger;

use jni::JNIEnv;
use jni::objects::JClass;
use log::debug;
use log::LevelFilter;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_init(
    _env: JNIEnv,
    _class: JClass
) {
    android_logger::init_once(android_logger::Config::default().with_max_level(LevelFilter::Trace));
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_invokeViaJNI(
    _env: JNIEnv,
    _class: JClass
) {
    debug!("Hello Rust");
}
