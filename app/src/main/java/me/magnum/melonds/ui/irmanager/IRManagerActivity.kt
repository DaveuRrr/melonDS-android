package me.magnum.melonds.ui.irmanager

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.melonds.ui.irmanager.UsbManagerActivity
import me.magnum.melonds.common.ir.IRTransportType

@AndroidEntryPoint
class IRManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                IRManagerScreen(
                    onBackClick = { onSupportNavigateUp() }
                )
            }
        }
    }
}

@Composable
fun IRManagerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ir_settings", Context.MODE_PRIVATE) }

    var selectedTransport by remember {
        mutableStateOf(
            IRTransportType.valueOf(
                prefs.getString("ir_transport_type", IRTransportType.NONE.name)
                    ?: IRTransportType.NONE.name
            )
        )
    }

    fun saveSelection(transport: IRTransportType) {
        selectedTransport = transport
        prefs.edit().putString("ir_transport_type", transport.name).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ir_manager)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ir_transport_selection),
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.ir_transport_description),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
            item {
                IRTransportItem(
                    title = stringResource(R.string.ir_none),
                    description = stringResource(R.string.ir_none_description),
                    isSelected = selectedTransport == IRTransportType.NONE,
                    isEnabled = true,
                    hasSubmenu = false,
                    onClick = { saveSelection(IRTransportType.NONE) },
                    onSubmenuClick = { }
                )
                Divider()
            }
            item {
                IRTransportItem(
                    title = stringResource(R.string.ir_usb_serial),
                    description = stringResource(R.string.ir_usb_serial_description),
                    isSelected = selectedTransport == IRTransportType.USB_SERIAL,
                    isEnabled = true,
                    hasSubmenu = true,
                    onClick = { saveSelection(IRTransportType.USB_SERIAL) },
                    onSubmenuClick = {
                        val intent = Intent(context, UsbManagerActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                Divider()
            }
            item {
                IRTransportItem(
                    title = stringResource(R.string.ir_tcp),
                    description = stringResource(R.string.ir_tcp_description),
                    isSelected = selectedTransport == IRTransportType.TCP,
                    isEnabled = true,
                    hasSubmenu = true,
                    onClick = { saveSelection(IRTransportType.TCP) },
                    onSubmenuClick = {
                        val intent = Intent(context, TCPManagerActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                Divider()
            }
            item {
                IRTransportItem(
                    title = stringResource(R.string.ir_direct_storage),
                    description = stringResource(R.string.ir_direct_storage_description),
                    isSelected = selectedTransport == IRTransportType.DIRECT_STORAGE,
                    isEnabled = false, // TODO: Disabled until implemented
                    hasSubmenu = false,
                    onClick = { saveSelection(IRTransportType.DIRECT_STORAGE) },
                    onSubmenuClick = { }
                )
                Divider()
            }
        }
    }
}

@Composable
fun IRTransportItem(
    title: String,
    description: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    hasSubmenu: Boolean,
    onClick: () -> Unit,
    onSubmenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            enabled = isEnabled
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = if (isEnabled) {
                    MaterialTheme.colors.onSurface
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.caption,
                color = if (isEnabled) {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
                }
            )
        }
        if (hasSubmenu && isEnabled) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSubmenuClick) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Configure",
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
