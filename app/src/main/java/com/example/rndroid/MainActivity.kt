package com.example.rndroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.rndroid.ui.theme.RndroidTheme

class MainActivity : ComponentActivity() {
    init {
        System.loadLibrary("rust")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
        setContent {
            mainUI()
        }
    }

    @Composable
    @Preview
    private fun mainUI() {
        RndroidTheme {
            // A surface container using the 'background' color from the theme
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Greeting("Android")
                    Button(onClick = ::invokeViaJNI) {
                        Text(text = "Hello")
                    }
                }
            }
        }
    }

    external fun init()
    external fun invokeViaJNI()
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}