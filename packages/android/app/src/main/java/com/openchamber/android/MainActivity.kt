package com.openchamber.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openchamber.android.core.design.OpenChamberTheme
import com.openchamber.android.i18n.Catalog
import com.openchamber.android.i18n.LocalCatalog
import com.openchamber.android.ui.AppShell

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenChamberApplication).container
        setContent {
            val viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(container))
            val state = viewModel.state.collectAsStateWithLifecycle().value
            var catalog by remember { mutableStateOf(Catalog(emptyMap())) }
            LaunchedEffect(Unit) {
                catalog = container.catalogStore.load(Locale.getDefault().toLanguageTag())
            }
            CompositionLocalProvider(LocalCatalog provides catalog) {
                OpenChamberTheme(themeMode = state.themeMode) {
                    AppShell(state = state, viewModel = viewModel)
                }
            }
        }
    }
}
