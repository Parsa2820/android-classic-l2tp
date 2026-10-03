package dev.l2tpipsec.client

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.parseColor("#0E1412")),
            navigationBarStyle = SystemBarStyle.dark(Color.parseColor("#0E1412")),
        )
        super.onCreate(savedInstanceState)
        setContent {
            ClassicL2tpTheme {
                ConnectScreen()
            }
        }
    }
}

private val Ink = ComposeColor(0xFF0E1412)
private val Panel = ComposeColor(0xFF17211E)
private val Mist = ComposeColor(0xFFE7F2EC)
private val Muted = ComposeColor(0xFF9BB0A6)
private val Teal = ComposeColor(0xFF8EEDC7)
private val Amber = ComposeColor(0xFFE6B15A)
private val Clay = ComposeColor(0xFFE07A5F)

@Composable
private fun ClassicL2tpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Ink,
            background = Ink,
            surface = Panel,
            onBackground = Mist,
            onSurface = Mist,
            error = Clay,
        ),
        content = content,
    )
}

@Composable
private fun ConnectScreen() {
    val context = LocalContext.current
    val session by TunnelSession.state.collectAsStateWithLifecycle()
    val prefs = context.getSharedPreferences("classic_l2tp", Context.MODE_PRIVATE)
    var server by rememberSaveable { mutableStateOf(prefs.getString("server", "") ?: "") }
    var psk by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPsk by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startService() {
        prefs.edit().putString("server", server.trim()).putString("username", username.trim()).apply()
        PendingConnect.request = ConnectRequest(
            server = server.trim(),
            psk = psk,
            username = username.trim(),
            password = password,
        )
        val intent = Intent(context, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_CONNECT)
        context.startForegroundService(intent)
    }

    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService()
        } else {
            formError = "Android needs VPN permission before the tunnel can start."
            PendingConnect.request = null
        }
    }

    fun connect() {
        formError = null
        val check = ProfileValidator.validate(
            ProfileInput(server, psk, username, password),
        )
        if (check is ProfileCheck.Invalid) {
            formError = check.message
            return
        }
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            vpnConsent.launch(prepare)
        } else {
            startService()
        }
    }

    fun disconnect() {
        context.startService(
            Intent(context, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_DISCONNECT),
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Text("Classic L2TP", style = MaterialTheme.typography.headlineMedium, color = Mist)
                Spacer(Modifier.height(6.dp))
                Text(
                    "L2TP/IPsec with a pre-shared key and PPP MS-CHAPv2. Android 12 removed this from Settings. This app speaks it from a normal VpnService, without root.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(22.dp))
                StatusCard(session)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server") },
                    placeholder = { Text("vpn.example.com") },
                    singleLine = true,
                    enabled = !session.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(10.dp))
                SecretField(
                    value = psk,
                    onValueChange = { psk = it },
                    label = "IPsec pre-shared key",
                    visible = showPsk,
                    onToggle = { showPsk = !showPsk },
                    enabled = !session.busy,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PPP username") },
                    singleLine = true,
                    enabled = !session.busy,
                )
                Spacer(Modifier.height(10.dp))
                SecretField(
                    value = password,
                    onValueChange = { password = it },
                    label = "PPP password",
                    visible = showPassword,
                    onToggle = { showPassword = !showPassword },
                    enabled = !session.busy,
                )
                formError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Clay, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { if (session.busy) disconnect() else connect() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (session.busy) "Disconnect" else "Connect")
                }
                if (session.logs.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Engine", style = MaterialTheme.typography.titleSmall, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        session.logs.takeLast(12).joinToString("\n"),
                        modifier = Modifier.fillMaxWidth(),
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(session: TunnelSnapshot) {
    val tone = when (session.phase) {
        TunnelPhase.CONNECTED -> Teal
        TunnelPhase.CONNECTING -> Amber
        TunnelPhase.FAILED -> Clay
        TunnelPhase.IDLE -> Muted
    }
    val title = when (session.phase) {
        TunnelPhase.IDLE -> "Idle"
        TunnelPhase.CONNECTING -> "Connecting"
        TunnelPhase.CONNECTED -> "Connected"
        TunnelPhase.FAILED -> "Couldn't connect"
    }
    Surface(color = Panel, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session.phase == TunnelPhase.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = tone)
            }
            Column {
                Text(title, color = tone, style = MaterialTheme.typography.titleMedium)
                Text(session.detail, color = Mist, style = MaterialTheme.typography.bodyMedium)
                session.clientIpv4?.let {
                    Text("Client $it", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (session.dns.isNotEmpty()) {
                    Text("DNS ${session.dns.joinToString()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide $label" else "Show $label",
                )
            }
        },
    )
}
