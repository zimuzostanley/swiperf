package com.swiperf.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swiperf.app.ui.theme.SwiPerfTheme
import com.swiperf.app.ui.viewmodel.SwiPerfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: SwiPerfViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()
            SwiPerfTheme(themeMode = themeMode) {
                SwiPerfApp(vm = vm)
            }
        }
    }
}
