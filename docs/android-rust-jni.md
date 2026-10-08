# 在 Android 项目中使用 Rust：集成、JNI 接口编写与多类型示例

这篇文章以本仓库为例（`app/` 是 Android 工程，`rust/` 是 Rust crate），介绍如何在 Android 工程中集成 Rust，并通过 JNI 提供多种类型的接口（基础数值、字符串、数组/字节、异常、回调）。

## 1. 如何在 Android 工程中集成 Rust

### 1.1 工程结构建议

- **Android 模块**：`app/`
- **Rust crate**：`rust/`

Rust crate 的 `crate-type` 需要能产出 Android 可加载的 so（常见是 `cdylib`）。本仓库的配置（见 `rust/Cargo.toml`）如下：

```toml
[lib]
crate-type = ["staticlib", "dylib"]
```

### 1.2 用 Gradle 在构建阶段触发 Cargo 构建

本工程使用 `org.mozilla.rust-android-gradle.rust-android` 插件，在 `:app` 模块内声明 Rust module 路径、库名与目标 ABI，并让 Java/Kotlin 编译前依赖 `cargoBuild`（见 `app/build.gradle.kts`）：

```kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.mozilla.rust-android-gradle.rust-android")
}

cargo {
    module = "../rust"
    libname = "rndroid"
    targets = listOf("arm64")
}

tasks.whenTaskAdded {
    if (name == "javaPreCompileDebug" || name == "javaPreCompileRelease") {
        dependsOn("cargoBuild")
    }
}
```

要点：

- **module**：指向 Rust crate 目录。
- **libname**：最终 `System.loadLibrary(...)` 里用到的名字（不带 `lib` 前缀和后缀）。
- **targets**：目标 ABI；实际开发可按需加上 `x86_64` 以便跑模拟器。

### 1.3 Android 侧加载 Rust so

在 `MainActivity` 的 `init` 块里加载 native library（见 `app/src/main/java/.../MainActivity.kt`）：

```kotlin
class MainActivity : ComponentActivity() {
    init {
        System.loadLibrary("rndroid")
    }
}
```

### 1.4 构建环境提示（很容易踩坑）

如果本地/CI 缺少 Android SDK，会遇到类似：

- `SDK location not found. Define a valid SDK location with an ANDROID_HOME ...`

解决方式二选一：

- 设置 `ANDROID_HOME`/`ANDROID_SDK_ROOT`
- 或在项目根目录创建 `local.properties`，写入 `sdk.dir=/path/to/Android/Sdk`

## 2. 如何编写 JNI 接口

### 2.1 Kotlin/Java 侧声明 native 方法

在 Kotlin 里用 `external` 声明（本仓库示例集中在 `MainActivity`）：

```kotlin
external fun init()
external fun invokeViaJNI(input: String)
external fun add(a: Int, b: Int): Int
external fun multiply(a: Int, b: Int): Int

external fun greet(name: String): String
external fun reverseBytes(input: ByteArray): ByteArray
external fun sumIntArray(input: IntArray): Int
external fun makeIntArray(n: Int): IntArray
external fun throwIfNegative(v: Int)
```

### 2.2 Rust 侧导出符号：命名规则 + ABI

经典做法是使用 “按约定命名” 的符号名：

- `Java_<包名用下划线>_<类名>_<方法名>`
- 并配合：
  - `#[no_mangle]`：不做符号改名
  - `pub extern "C"`：使用 C ABI

本工程的包名是 `com.example.rndroid`，类名是 `MainActivity`，所以 `greet` 的符号就是 `Java_com_example_rndroid_MainActivity_greet`。

## 3. 几种类型 JNI 接口的代码实例与解释

下面按“最常见类型”给出例子，并解释 Kotlin 与 Rust 侧如何对应。Rust 侧实现位于 `rust/src/lib.rs`。

### 3.1 基础数值：`Int -> Int`

Kotlin：

```kotlin
external fun add(a: Int, b: Int): Int
external fun multiply(a: Int, b: Int): Int
```

Rust（使用 `jint` 对应 JVM `int`）：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_add(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    a: jni::sys::jint,
    b: jni::sys::jint,
) -> jni::sys::jint {
    a + b
}
```

### 3.2 `String` 入参（只读，不返回）

Kotlin：

```kotlin
external fun invokeViaJNI(input: String)
```

Rust（用 `JString` 接收，`env.get_string` 读取内容）：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_invokeViaJNI(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    input: jni::objects::JString,
) {
    if let Ok(s) = env.get_string(&input) {
        log::debug!("Hello {}", s.to_string_lossy());
    }
}
```

### 3.3 返回 `String`：`String -> String`

Kotlin：

```kotlin
external fun greet(name: String): String
```

Rust（用 `env.new_string` 构造 Java 字符串并返回 `jstring`）：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_greet(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    name: jni::objects::JString,
) -> jni::sys::jstring {
    let name = env
        .get_string(&name)
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|_| "Unknown".to_string());

    env.new_string(format!("Hello from Rust, {}!", name))
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
```

### 3.4 `ByteArray <-> byte[]`：传入字节数组并返回

Kotlin：

```kotlin
external fun reverseBytes(input: ByteArray): ByteArray
```

Rust（`convert_byte_array` 读入 `Vec<i8>`，再 `byte_array_from_slice` 返回）：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_reverseBytes(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    input: jni::objects::JByteArray,
) -> jni::sys::jbyteArray {
    let mut bytes = env
        .convert_byte_array(&input)
        .unwrap_or_default();
    bytes.reverse();
    env.byte_array_from_slice(&bytes)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
```

解释：

- JVM 的 `byte` 是有符号 8 位，所以 `jni` crate 通常用 `i8` 表示。
- 对性能敏感的场景要注意避免频繁分配/复制，必要时可考虑 direct buffer 或复用缓存。

### 3.5 `IntArray <-> int[]`：读数组求和、创建数组返回

Kotlin：

```kotlin
external fun sumIntArray(input: IntArray): Int
external fun makeIntArray(n: Int): IntArray
```

Rust（求和）：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_sumIntArray(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    input: jni::objects::JIntArray,
) -> jni::sys::jint {
    let len = env.get_array_length(&input).unwrap_or(0);
    let mut buf = vec![0i32; len as usize];
    let _ = env.get_int_array_region(&input, 0, &mut buf);
    buf.into_iter().sum()
}
```

Rust（创建数组）：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_makeIntArray(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    n: jni::sys::jint,
) -> jni::sys::jintArray {
    let n = n.max(0) as usize;
    let values: Vec<i32> = (0..n).map(|i| i as i32).collect();

    let arr = env.new_int_array(n as i32).ok().unwrap();
    let _ = env.set_int_array_region(&arr, 0, &values);
    arr.into_raw()
}
```

### 3.6 从 Rust 抛 Java 异常：`throwIfNegative`

Kotlin：

```kotlin
external fun throwIfNegative(v: Int)
```

Rust：

```rust
#[no_mangle]
pub extern "C" fn Java_com_example_rndroid_MainActivity_throwIfNegative(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    v: jni::sys::jint,
) {
    if v < 0 {
        let _ = env.throw_new(
            "java/lang/IllegalArgumentException",
            format!("v must be >= 0, got {}", v),
        );
    }
}
```

解释：

- JNI 层抛异常后，**尽快返回到 Java/Kotlin**，让异常沿调用栈传播。
- Kotlin 侧用 `try/catch` 捕获即可。

### 3.7 Rust 调 Kotlin 回调（对象回调）

本仓库也包含 “Rust 保存回调对象 -> 之后调用其 `onResult(String)`” 的例子（见 `rust/src/lib.rs` 的 `registerCallback` / `calculateWithCallback`，以及 `MainActivity.kt` 的 `RustCallback`）。

关键点：

- **回调引用必须是 GlobalRef**，否则超出当前 JNI 调用帧就可能失效。
- 回调可能在非主线程触发：本仓库在 Kotlin 侧用 `Handler(Looper.getMainLooper())` 把 UI 更新切回主线程。

## 4. 小结与实践建议

- **集成层**：用 Gradle 插件或自定义 task 把 `cargo build` 接入 Android 构建链路，确保 `System.loadLibrary` 的名字与产物一致。
- **JNI 编写**：从“基础类型 + String + 数组 + 异常 + 回调”这几类开始覆盖，大多数业务都够用。
- **稳定性**：在 Rust 侧遇到 JNI 错误要么抛 Java 异常，要么返回可控的默认值；避免 silent failure。

