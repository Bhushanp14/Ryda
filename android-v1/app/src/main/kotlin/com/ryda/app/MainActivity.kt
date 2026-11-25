package com.ryda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ryda.app.core.sync.SyncManager
import com.ryda.app.ui.navigation.AppNavigation
import com.ryda.app.ui.theme.RydaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncManager.schedulePeriodicSync(this)
        enableEdgeToEdge()
        setContent {
            RydaTheme {
                AppNavigation()
            }
        }
    }
}
