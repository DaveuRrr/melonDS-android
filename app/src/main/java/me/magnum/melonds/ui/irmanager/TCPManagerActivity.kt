package me.magnum.melonds.ui.irmanager

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.MelonTheme

/**
 * TCP/IP Manager Settings Activity
 * Allows users to configure TCP connection mode (Server/Client) and connection parameters
 *
 * Ported from melonDS-IR Qt6 IRSettingsDialog (IRSettingsDialog.cpp)
 * Configuration keys match the Qt implementation:
 * - IR.TCP.IsServer -> tcp_is_server
 * - IR.TCP.SelfPort -> tcp_server_port
 * - IR.TCP.HostIP -> tcp_client_host
 * - IR.TCP.HostPort -> tcp_client_port
 */
@AndroidEntryPoint
class TCPManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                TCPManagerScreen(
                    onBackClick = { onSupportNavigateUp() }
                )
            }
        }
    }
}

@Composable
fun TCPManagerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tcp_settings", Context.MODE_PRIVATE) }

    // TCP/IP settings
    var isServerMode by remember {
        mutableStateOf(prefs.getBoolean("tcp_is_server", true))
    }
    var serverPort by remember {
        mutableStateOf(prefs.getInt("tcp_server_port", 8081).toString())
    }
    var clientHost by remember {
        mutableStateOf(prefs.getString("tcp_client_host", "127.0.0.1") ?: "127.0.0.1")
    }
    var clientPort by remember {
        mutableStateOf(prefs.getInt("tcp_client_port", 8081).toString())
    }

    // Validation states
    var serverPortError by remember { mutableStateOf(false) }
    var clientPortError by remember { mutableStateOf(false) }

    fun saveSettings() {
        val serverPortInt = serverPort.toIntOrNull()
        val clientPortInt = clientPort.toIntOrNull()

        // Validate ports
        serverPortError = serverPortInt == null || serverPortInt !in 1..65535
        clientPortError = clientPortInt == null || clientPortInt !in 1..65535

        if (!serverPortError && !clientPortError) {
            prefs.edit()
                .putBoolean("tcp_is_server", isServerMode)
                .putInt("tcp_server_port", serverPortInt ?: 8081)
                .putString("tcp_client_host", clientHost)
                .putInt("tcp_client_port", clientPortInt ?: 8081)
                .apply()
        }
    }

    LaunchedEffect(isServerMode, serverPort, clientHost, clientPort) {
        saveSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tcp_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // Connection Mode Selection
            Text(
                text = stringResource(R.string.tcp_connection_mode),
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Server mode radio button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isServerMode,
                            onClick = { isServerMode = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.tcp_server_mode),
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = stringResource(R.string.tcp_server_mode_description),
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Client mode radio button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isServerMode,
                            onClick = { isServerMode = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.tcp_client_mode),
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = stringResource(R.string.tcp_client_mode_description),
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Server Settings Section
            if (isServerMode) {
                Text(
                    text = stringResource(R.string.tcp_server_settings),
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.tcp_server_info),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it },
                            label = { Text(stringResource(R.string.tcp_server_port)) },
                            placeholder = { Text("8081") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = serverPortError,
                            singleLine = true
                        )

                        if (serverPortError) {
                            Text(
                                text = stringResource(R.string.tcp_port_error),
                                color = MaterialTheme.colors.error,
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Client Settings Section
            if (!isServerMode) {
                Text(
                    text = stringResource(R.string.tcp_client_settings),
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.tcp_client_info),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = clientHost,
                            onValueChange = { clientHost = it },
                            label = { Text(stringResource(R.string.tcp_client_host)) },
                            placeholder = { Text("127.0.0.1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = clientPort,
                            onValueChange = { clientPort = it },
                            label = { Text(stringResource(R.string.tcp_client_port)) },
                            placeholder = { Text("8081") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = clientPortError,
                            singleLine = true
                        )

                        if (clientPortError) {
                            Text(
                                text = stringResource(R.string.tcp_port_error),
                                color = MaterialTheme.colors.error,
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Additional information section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 2.dp,
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.tcp_usage_tips_title),
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.tcp_usage_tips),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
