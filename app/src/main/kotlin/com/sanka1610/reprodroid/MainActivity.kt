package com.sanka1610.reprodroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sanka1610.reprodroid.ui.JobViewModel
import com.sanka1610.reprodroid.ui.ManagedAppsViewModel
import com.sanka1610.reprodroid.ui.ReproDroidApp

class MainActivity : ComponentActivity() {
    private val jobViewModel: JobViewModel by viewModels()
    private val managedAppsViewModel: ManagedAppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReproDroidApp(managedAppsViewModel, jobViewModel)
        }
    }
}
