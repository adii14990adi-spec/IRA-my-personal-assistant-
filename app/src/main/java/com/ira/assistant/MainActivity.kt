package com.ira.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ira.assistant.core.IraViewModel
import com.ira.assistant.core.SpeechInput
import com.ira.assistant.ui.IraScreen
import com.ira.assistant.ui.IraTheme

class MainActivity : ComponentActivity() {
    private lateinit var speechInput: SpeechInput

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechInput = SpeechInput(this)
        setContent {
            IraTheme {
                val model: IraViewModel = viewModel()
                IraScreen(model, speechInput)
            }
        }
    }

    override fun onDestroy() {
        speechInput.close()
        super.onDestroy()
    }
}
