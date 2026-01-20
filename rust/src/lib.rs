extern crate jni;
extern crate log;
extern crate android_logger;

use jni::JNIEnv;
use jni::objects::{GlobalRef, JByteArray, JClass, JIntArray, JObject, JString};
use jni::sys::{jbyteArray, jint, jintArray, jstring};
use log::debug;
use log::LevelFilter;
use std::sync::Mutex;
use std::ptr;

// Global callback reference storage
static CALLBACK_REF: Mutex<Option<GlobalRef>> = Mutex::new(None);

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
    mut env: JNIEnv,
    _class: JClass,
    input_string: JString
) {
    match env.get_string(&input_string) {
        Ok(java_string) => {
            let rust_string = java_string.to_string_lossy();
            debug!("Hello {}", rust_string);
        }
        Err(e) => {
            debug!("Failed to get string from JNI: {:?}", e);
        }
    }
}

// Addition function
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_add(
    _env: JNIEnv,
    _class: JClass,
    a: jint,
    b: jint
) -> jint {
    debug!("Rust: Adding {} + {}", a, b);
    a + b
}

// Multiplication function
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_multiply(
    _env: JNIEnv,
    _class: JClass,
    a: jint,
    b: jint
) -> jint {
    debug!("Rust: Multiplying {} * {}", a, b);
    a * b
}

// Register callback function
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_registerCallback(
    env: JNIEnv,
    _class: JClass,
    callback: JObject
) {
    debug!("Rust: Registering callback");
    
    // First, unregister any existing callback to prevent memory leak
    let mut callback_guard = CALLBACK_REF.lock().unwrap();
    if let Some(old_ref) = callback_guard.take() {
        drop(old_ref); // Explicitly drop the old reference
        debug!("Rust: Old callback unregistered");
    }
    
    // Register new callback
    match env.new_global_ref(callback) {
        Ok(global_ref) => {
            *callback_guard = Some(global_ref);
            debug!("Rust: Callback registered successfully");
        }
        Err(e) => {
            debug!("Rust: Failed to register callback: {:?}", e);
        }
    }
}

// Unregister callback function
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_unregisterCallback(
    _env: JNIEnv,
    _class: JClass
) {
    debug!("Rust: Unregistering callback");
    let mut callback_guard = CALLBACK_REF.lock().unwrap();
    if let Some(callback_ref) = callback_guard.take() {
        drop(callback_ref); // Explicitly drop to release the global reference
        debug!("Rust: Callback unregistered successfully");
    } else {
        debug!("Rust: No callback to unregister");
    }
}

// Perform calculation with callback
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_calculateWithCallback(
    mut env: JNIEnv,
    _class: JClass,
    a: jint,
    b: jint
) {
    debug!("Rust: Calculating with callback: {} + {}", a, b);
    let result = a + b;
    
    // Generate random UUID string
    use uuid::Uuid;
    let uuid = Uuid::new_v4();
    let random_str = format!("Result: {}, UUID: {}", result, uuid);
    
    debug!("Rust: Calling callback with result: {}", random_str);
    
    // Get callback reference - need to keep lock while using the object
    let callback_guard = CALLBACK_REF.lock().unwrap();
    if let Some(ref callback) = *callback_guard {
        let callback_obj = callback.as_obj();
        
        // Create Java string
        match env.new_string(&random_str) {
            Ok(jstr) => {
                // Convert JString to JObject for JValue
                let jstr_obj: jni::objects::JObject = jstr.into();
                // Call the method using call_method for void return type
                match env.call_method(
                    callback_obj,
                    "onResult",
                    "(Ljava/lang/String;)V",
                    &[jni::objects::JValue::Object(&jstr_obj)]
                ) {
                    Ok(_) => {
                        debug!("Rust: Callback invoked successfully");
                    }
                    Err(e) => {
                        debug!("Rust: Failed to invoke callback method: {:?}", e);
                        // Check and clear any Java exception
                        if let Ok(_) = env.exception_check() {
                            debug!("Rust: Java exception occurred, clearing it");
                            env.exception_clear().ok();
                        }
                    }
                }
            }
            Err(e) => {
                debug!("Rust: Failed to create Java string: {:?}", e);
            }
        }
    } else {
        debug!("Rust: No callback registered");
    }
}

// Return a Java String back to Kotlin/Java
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_greet(
    mut env: JNIEnv,
    _class: JClass,
    name: JString
) -> jstring {
    let name = match env.get_string(&name) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(e) => {
            debug!("Rust: Failed to read name string: {:?}", e);
            return ptr::null_mut();
        }
    };

    let msg = format!("Hello from Rust, {}!", name);
    match env.new_string(msg) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            debug!("Rust: Failed to create return string: {:?}", e);
            ptr::null_mut()
        }
    }
}

// Accept a byte[] and return another byte[] (here we reverse it)
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_reverseBytes(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray
) -> jbyteArray {
    let mut bytes = match env.convert_byte_array(&input) {
        Ok(v) => v,
        Err(e) => {
            debug!("Rust: Failed to convert byte array: {:?}", e);
            return ptr::null_mut();
        }
    };

    bytes.reverse();
    match env.byte_array_from_slice(&bytes) {
        Ok(arr) => arr.into_raw(),
        Err(e) => {
            debug!("Rust: Failed to create return byte array: {:?}", e);
            ptr::null_mut()
        }
    }
}

// Sum an int[] and return the total
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_sumIntArray(
    env: JNIEnv,
    _class: JClass,
    input: JIntArray
) -> jint {
    let len = match env.get_array_length(&input) {
        Ok(n) => n,
        Err(e) => {
            debug!("Rust: Failed to get int array length: {:?}", e);
            return 0;
        }
    };

    let mut buf = vec![0i32; len as usize];
    if let Err(e) = env.get_int_array_region(&input, 0, &mut buf) {
        debug!("Rust: Failed to read int array: {:?}", e);
        return 0;
    }

    buf.into_iter().sum()
}

// Create an int[] [0, 1, 2, ... n-1]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_makeIntArray(
    env: JNIEnv,
    _class: JClass,
    n: jint
) -> jintArray {
    let n = n.max(0) as usize;
    let mut values = Vec::with_capacity(n);
    for i in 0..n {
        values.push(i as i32);
    }

    let arr = match env.new_int_array(n as i32) {
        Ok(a) => a,
        Err(e) => {
            debug!("Rust: Failed to create int array: {:?}", e);
            return ptr::null_mut();
        }
    };

    if let Err(e) = env.set_int_array_region(&arr, 0, &values) {
        debug!("Rust: Failed to fill int array: {:?}", e);
        return ptr::null_mut();
    }

    arr.into_raw()
}

// Throw a Java exception from Rust
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_rndroid_MainActivity_throwIfNegative(
    mut env: JNIEnv,
    _class: JClass,
    v: jint
) {
    if v < 0 {
        // After throwing, return to Java/Kotlin immediately.
        let _ = env.throw_new(
            "java/lang/IllegalArgumentException",
            format!("v must be >= 0, got {}", v)
        );
    }
}
