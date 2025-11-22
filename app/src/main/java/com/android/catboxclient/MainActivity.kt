package com.android.catboxclient

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

// --- 備案顏色 (Fallback) ---
private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF6650a4),
    secondary = Color(0xFF625b71),
    tertiary = Color(0xFF7d5260),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                darkTheme -> FallbackDarkColorScheme
                else -> FallbackLightColorScheme
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CatboxScreen()
                }
            }
        }
    }
}

// 開啟 Custom Tabs
private fun openUrl(context: Context, url: String) {
    try {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatboxScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val state = viewModel.uploadState

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadFile(context, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Catbox Uploader",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = viewModel.selectedService == ServiceType.CATBOX,
                onClick = { viewModel.selectedService = ServiceType.CATBOX },
                label = { Text("Catbox (Perm)") } // Permanent
            )
            FilterChip(
                selected = viewModel.selectedService == ServiceType.LITTERBOX,
                onClick = { viewModel.selectedService = ServiceType.LITTERBOX },
                label = { Text("Litterbox (Temp)") } // Temporary
            )
        }

        if (viewModel.selectedService == ServiceType.LITTERBOX) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Expiration", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1h", "12h", "24h", "72h").forEach { time ->
                    FilterChip(
                        selected = viewModel.litterboxTime == time,
                        onClick = { viewModel.litterboxTime = time },
                        label = { Text(time) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            enabled = state !is UploadState.Loading
        ) {
            Text(if (state is UploadState.Loading) "Uploading..." else "Select File & Upload")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            is UploadState.Loading -> CircularProgressIndicator()
            is UploadState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UploadState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Upload Successful!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            SelectionContainer {
                                Text(
                                    text = state.url,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        openUrl(context, state.url)
                                    }
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Catbox URL", state.url)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
            else -> {}
        }
    }
}