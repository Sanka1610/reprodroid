package com.sanka1610.reprodroid

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.sanka1610.reprodroid.ui.JobViewModel
import com.sanka1610.reprodroid.ui.ManagedAppsViewModel
import com.sanka1610.reprodroid.ui.ReproDroidApp

class MainActivity : ComponentActivity() {
    private val jobViewModel: JobViewModel by viewModels()
    private val managedAppsViewModel: ManagedAppsViewModel by viewModels()
    private val requestedRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        setContent {
            ReproDroidApp(
                managedViewModel = managedAppsViewModel,
                jobViewModel = jobViewModel,
                initialRoute = requestedRoute.value,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "com.sanka1610.reprodroid.extra.ROUTE"
    }
}
