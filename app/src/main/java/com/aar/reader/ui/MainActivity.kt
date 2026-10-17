package com.aar.reader.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aar.reader.R
import com.aar.reader.data.UserPreferencesRepository
import com.aar.reader.service.AutoReaderAccessibilityService
import com.aar.reader.service.model.ReaderState
import com.aar.reader.ui.components.ProminentDisclosureDialog
import com.aar.reader.ui.components.rememberProminentDisclosureState
import com.aar.reader.ui.theme.AarTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = UserPreferencesRepository(applicationContext)
        val viewModel = ViewModelProvider(
            this,
            SettingsViewModel.Factory(repo)
        )[SettingsViewModel::class.java]

        setContent {
            AarTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsState()
    val readerState by viewModel.readerState.collectAsState()

    var isServiceEnabled by remember { mutableStateOf(checkAccessibilityServiceEnabled(context)) }
    val disclosureState = rememberProminentDisclosureState(
        initialVisible = !isServiceEnabled,
        initialAccepted = isServiceEnabled
    )

    // Recheck accessibility service status on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = checkAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (disclosureState.isDialogVisible && !disclosureState.isDisclosureAccepted) {
        ProminentDisclosureDialog(
            state = disclosureState,
            onAgreeAdditional = {
                if (disclosureState.consumeRedirectRequest()) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.zero_network_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Accessibility Service Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isServiceEnabled)
                                stringResource(R.string.service_status_active)
                            else
                                stringResource(R.string.service_status_disabled),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isServiceEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.accessibility_service_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }

                    if (!isServiceEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!disclosureState.isDisclosureAccepted) {
                                    disclosureState.show()
                                } else {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.service_enable_button))
                        }
                    }
                }
            }

            // 2. Playback Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.status_label_format, readerState.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.toggleReading() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            val isPlaying = readerState == ReaderState.SPEAKING || readerState == ReaderState.SCANNING
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (readerState) {
                                    ReaderState.SPEAKING, ReaderState.SCANNING -> stringResource(R.string.btn_pause_reading)
                                    ReaderState.PAUSED -> stringResource(R.string.btn_resume_reading)
                                    else -> stringResource(R.string.btn_start_reading)
                                }
                            )
                        }

                        if (readerState != ReaderState.IDLE) {
                            Button(
                                onClick = { viewModel.stopReading() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_stop_reading))
                            }
                        }
                    }
                }
            }

            // 3. Speech Rate, Pitch & Pause Between Sentences
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${stringResource(R.string.speech_rate_label)}: ${String.format(Locale.US, "%.1fx", preferences.speechRate)}",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = preferences.speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${stringResource(R.string.speech_pitch_label)}: ${String.format(Locale.US, "%.1fx", preferences.speechPitch)}",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = preferences.speechPitch,
                        onValueChange = { viewModel.setSpeechPitch(it) },
                        valueRange = 0.5f..1.5f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${stringResource(R.string.pause_duration_label)}: ${preferences.pauseBetweenSentencesMs} ms",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = preferences.pauseBetweenSentencesMs.toFloat(),
                        onValueChange = { viewModel.setPauseDuration(it.toLong()) },
                        valueRange = 0f..500f,
                        steps = 9
                    )
                }
            }

            // 4. Language Selector & Offline TTS Voice Pack Check
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.language_selector_label),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferences.language == "AUTO",
                            onClick = { viewModel.setLanguage("AUTO") },
                            label = { Text(stringResource(R.string.language_auto)) }
                        )
                        FilterChip(
                            selected = preferences.language == "ZH-TW",
                            onClick = { viewModel.setLanguage("ZH-TW") },
                            label = { Text(stringResource(R.string.language_zh_tw)) }
                        )
                        FilterChip(
                            selected = preferences.language == "EN-US",
                            onClick = { viewModel.setLanguage("EN-US") },
                            label = { Text(stringResource(R.string.language_en_us)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                            } catch (e: Exception) {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_install_tts_data))
                    }
                }
            }

            // 5. Auto-Scroll Mode & Distance
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.scroll_mode_label),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferences.scrollMode == "NODE_PRIORITY",
                            onClick = { viewModel.setScrollMode("NODE_PRIORITY") },
                            label = { Text(stringResource(R.string.scroll_mode_node)) }
                        )
                        FilterChip(
                            selected = preferences.scrollMode == "GESTURE_ONLY",
                            onClick = { viewModel.setScrollMode("GESTURE_ONLY") },
                            label = { Text(stringResource(R.string.scroll_mode_gesture)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${stringResource(R.string.scroll_percent_label)}: ${(preferences.scrollPercent * 100).toInt()}%",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = preferences.scrollPercent,
                        onValueChange = { viewModel.setScrollPercent(it) },
                        valueRange = 0.4f..0.9f,
                        steps = 4
                    )
                }
            }

            // 6. Highlight Frame Switch
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.highlight_toggle_label),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.highlight_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.highlightEnabled,
                        onCheckedChange = { viewModel.setHighlightEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    if (AutoReaderAccessibilityService.isRunning) return true

    val expectedComponentName = android.content.ComponentName(context, AutoReaderAccessibilityService::class.java).flattenToString()
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        if (componentNameString.equals(expectedComponentName, ignoreCase = true) ||
            componentNameString.contains(AutoReaderAccessibilityService::class.java.simpleName)
        ) {
            return true
        }
    }
    return false
}
