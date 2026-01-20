package com.example.rndroid

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.rndroid.ui.theme.RndroidTheme
import kotlin.math.min

// Callback interface for Rust to call back to Android
interface RustCallback {
    fun onResult(result: String)
}

class MainActivity : ComponentActivity() {
    init {
        System.loadLibrary("rndroid")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
        setContent {
            MainUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure callback is unregistered when activity is destroyed
        unregisterCallback()
    }

    @Composable
    @Preview
    private fun MainUI() {
        var addResult by remember { mutableStateOf<String?>(null) }
        var multiplyResult by remember { mutableStateOf<String?>(null) }
        var callbackResult by remember { mutableStateOf<String?>(null) }
        var greetResult by remember { mutableStateOf<String?>(null) }
        var bytesResult by remember { mutableStateOf<String?>(null) }
        var sumArrayResult by remember { mutableStateOf<String?>(null) }
        var makeArrayResult by remember { mutableStateOf<String?>(null) }
        var exceptionResult by remember { mutableStateOf<String?>(null) }
        
        // Create callback that updates Compose state
        // Note: This callback will be called from JNI thread, so we need to ensure UI updates happen on main thread
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val callback = remember {
            object : RustCallback {
                override fun onResult(result: String) {
                    // Update state on main thread
                    mainHandler.post {
                        callbackResult = result
                    }
                }
            }
        }
        
        // Register callback when composable is first created
        // Unregister when composable is disposed to prevent memory leak
        DisposableEffect(Unit) {
            registerCallback(callback)
            onDispose {
                unregisterCallback()
            }
        }
        
        RndroidTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Greeting("Android + Rust")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Addition section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Addition (5 + 3)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val result = add(5, 3)
                                addResult = "5 + 3 = $result"
                            }) {
                                Text(text = "Calculate Addition")
                            }
                            if (addResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = addResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    // Multiplication section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Multiplication (7 * 4)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val result = multiply(7, 4)
                                multiplyResult = "7 * 4 = $result"
                            }) {
                                Text(text = "Calculate Multiplication")
                            }
                            if (multiplyResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = multiplyResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    // Callback section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Callback Test, Return random",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                calculateWithCallback(10, 20)
                            }) {
                                Text(text = "Calculate with Callback")
                            }
                            if (callbackResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Callback Result:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = callbackResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Return String section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Return String (greet)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                greetResult = greet("Android")
                            }) {
                                Text(text = "Call greet()")
                            }
                            if (greetResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = greetResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // ByteArray section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ByteArray (reverseBytes)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val input = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
                                val out = reverseBytes(input)
                                bytesResult = "in=${toHex(input)} out=${toHex(out)}"
                            }) {
                                Text(text = "Call reverseBytes()")
                            }
                            if (bytesResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = bytesResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // IntArray section (sum + make)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "IntArray (sumIntArray / makeIntArray)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val arr = intArrayOf(1, 2, 3, 4, 5)
                                val sum = sumIntArray(arr)
                                sumArrayResult = "sum([1,2,3,4,5]) = $sum"
                            }) {
                                Text(text = "Call sumIntArray()")
                            }
                            if (sumArrayResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = sumArrayResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val n = 8
                                val arr = makeIntArray(n)
                                makeArrayResult = "makeIntArray($n) = ${arr.joinToString(prefix = \"[\", postfix = \"]\")}"
                            }) {
                                Text(text = "Call makeIntArray()")
                            }
                            if (makeArrayResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = makeArrayResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Exception section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Exception (throwIfNegative)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                exceptionResult = try {
                                    throwIfNegative(-1)
                                    "No exception"
                                } catch (t: Throwable) {
                                    "Caught: ${t::class.java.simpleName}: ${t.message}"
                                }
                            }) {
                                Text(text = "Call throwIfNegative(-1)")
                            }
                            if (exceptionResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = exceptionResult!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                    
                    // Original test button
                    Button(onClick = { invokeViaJNI("World") }) {
                        Text(text = "Test JNI (Log Only)")
                    }
                }
            }
        }
    }

    external fun init()
    external fun invokeViaJNI(input: String)
    external fun add(a: Int, b: Int): Int
    external fun multiply(a: Int, b: Int): Int
    external fun registerCallback(callback: RustCallback)
    external fun unregisterCallback()
    external fun calculateWithCallback(a: Int, b: Int)
    external fun greet(name: String): String
    external fun reverseBytes(input: ByteArray): ByteArray
    external fun sumIntArray(input: IntArray): Int
    external fun makeIntArray(n: Int): IntArray
    external fun throwIfNegative(v: Int)

    private fun toHex(bytes: ByteArray): String {
        val limit = min(bytes.size, 64)
        return buildString {
            for (i in 0 until limit) {
                if (i > 0) append(' ')
                append(bytes[i].toUByte().toString(16).padStart(2, '0'))
            }
            if (bytes.size > limit) append(" ...")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}