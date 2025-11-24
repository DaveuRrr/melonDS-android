package me.magnum.melonds.ui.irmanager

import android.content.Context
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.MelonTheme

data class UsbPortItem(
    val device: UsbDevice,
    val driver: UsbSerialDriver?,
    val portIndex: Int,
    val deviceKey: String // Unique key for this device+port combination
) {
    fun getDisplayName(): String {
        val productName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            device.productName ?: "Unknown Device"
        } else {
            "USB Device"
        }

        return if (driver != null) {
            "$productName - Port ${portIndex + 1}"
        } else {
            "$productName (No driver)"
        }
    }

    fun getDeviceInfo(): String {
        val vid = String.format("0x%04X", device.vendorId)
        val pid = String.format("0x%04X", device.productId)
        val driverName = driver?.javaClass?.simpleName ?: "None"
        return "VID: $vid, PID: $pid, Driver: $driverName"
    }
}

@AndroidEntryPoint
class UsbManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                UsbManagerScreen(
                    onBackClick = { onSupportNavigateUp() }
                )
            }
        }
    }
}

@Composable
fun UsbManagerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("usb_settings", Context.MODE_PRIVATE) }

    var usbPorts by remember { mutableStateOf<List<UsbPortItem>>(emptyList()) }
    var selectedDeviceKey by remember {
        mutableStateOf(prefs.getString("selected_usb_device_key", null))
    }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshDevices() {
        isRefreshing = true
        usbPorts = detectUsbDevices(context)
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usb_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshDevices() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp
            )
        }
    ) { paddingValues ->
        if (usbPorts.isEmpty() && !isRefreshing) {
            // No devices found
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_usb_devices_found),
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_usb_devices_hint),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.usb_port_selection),
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.usb_device_list_description),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                items(usbPorts) { portItem ->
                    UsbPortListItem(
                        portItem = portItem,
                        isSelected = selectedDeviceKey == portItem.deviceKey,
                        onClick = {
                            selectedDeviceKey = portItem.deviceKey
                            // Save both the device key and the port index
                            prefs.edit()
                                .putString("selected_usb_device_key", portItem.deviceKey)
                                .putInt("selected_usb_port", portItem.portIndex)
                                .apply()
                        }
                    )
                    Divider()
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.usb_devices_count, usbPorts.size),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UsbPortListItem(
    portItem: UsbPortItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = portItem.getDisplayName(),
                style = MaterialTheme.typography.body1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = portItem.getDeviceInfo(),
                style = MaterialTheme.typography.caption,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            if (portItem.driver == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠ No serial driver available",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun detectUsbDevices(context: Context): List<UsbPortItem> {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val defaultProber = UsbSerialProber.getDefaultProber()
    val customProber = createCustomProber()

    val portItems = mutableListOf<UsbPortItem>()

    for (device in usbManager.deviceList.values) {
        var driver = defaultProber.probeDevice(device)

        if (driver == null) {
            driver = customProber.probeDevice(device)
        }

        if (driver != null) {
            for (portIndex in 0 until driver.ports.size) {
                val deviceKey = "${device.deviceName}:$portIndex"
                portItems.add(
                    UsbPortItem(
                        device = device,
                        driver = driver,
                        portIndex = portIndex,
                        deviceKey = deviceKey
                    )
                )
            }
        } else {
            val deviceKey = "${device.deviceName}:0"
            portItems.add(
                UsbPortItem(
                    device = device,
                    driver = null,
                    portIndex = 0,
                    deviceKey = deviceKey
                )
            )
        }
    }

    return portItems
}

private fun createCustomProber(): UsbSerialProber {
    val customTable = ProbeTable()

    // Raspberry Pi Foundation VID: 0x2E8A
    // Add Raspberry Pi Pico (RP2040)
    customTable.addProduct(0x2E8A, 0x0005, CdcAcmSerialDriver::class.java)
    customTable.addProduct(0x2E8A, 0x000A, CdcAcmSerialDriver::class.java)

    // Add Raspberry Pi Pico 2 (RP2350)
    customTable.addProduct(0x2E8A, 0x000F, CdcAcmSerialDriver::class.java)
    customTable.addProduct(0x2E8A, 0x0003, CdcAcmSerialDriver::class.java)

    return UsbSerialProber(customTable)
}
