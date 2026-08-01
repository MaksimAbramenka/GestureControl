package com.gesturecontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GestureControlScaffold()
            }
        }
    }
}

@Composable
private fun GestureControlScaffold() {
    Scaffold { innerPadding ->
        Text(
            text = "GestureControl",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
