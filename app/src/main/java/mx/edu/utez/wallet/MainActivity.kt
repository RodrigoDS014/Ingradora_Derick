package mx.edu.utez.wallet

import android.graphics.Bitmap
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import mx.edu.utez.wallet.ui.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.edu.utez.wallet.core.*
import mx.edu.utez.wallet.ui.*
import mx.edu.utez.wallet.security.BiometricAccess
import java.io.ByteArrayOutputStream
import java.util.Base64

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WalletTheme { WalletApp(this) } }
    }
}

@Composable private fun WalletApp(activity: FragmentActivity, vm: WalletViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(state.user?.id) { mutableIntStateOf(0) }
    var topup by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserProfile?>(null) }
    var deleting by remember { mutableStateOf<UserProfile?>(null) }
    var resetting by remember { mutableStateOf<UserProfile?>(null) }
    var temporaryPassword by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    val biometric = remember { BiometricAccess(activity) }
    var biometricVersion by remember { mutableIntStateOf(0) }
    val availability = biometricVersion.let { biometric.availability() }
    val scannerPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else vm.notify("Permite el acceso a la cámara para escanear el QR")
    }
    val snackbar = remember { SnackbarHostState() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this) }.toByteArray()
            vm.photo(Base64.getEncoder().encodeToString(bytes))
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(state.user?.id) {
        topup = false; password = false; editOpen = false; deleting = null; resetting = null; scanning = false
    }
    LaunchedEffect(state.biometricInvalid) {
        if (state.biometricInvalid) { biometric.clear(); biometricVersion++; vm.clearBiometricInvalid() }
    }
    val user = state.user
    val profile: @Composable (() -> Unit) = {
        ProfileScreen(state, vm,
            photo = { try { camera.launch(null) } catch (_: android.content.ActivityNotFoundException) { vm.notify("No hay una aplicación de cámara disponible en este dispositivo") } },
            password = { password = true },
            biometricEnabled = user != null && biometricVersion.let { biometric.isFor(user.id, state.demo, state.baseUrl) },
            biometricAvailable = availability.first, biometricMessage = availability.second,
            enableBiometric = { vm.prepareBiometric { binding -> biometric.enroll(activity, binding,
                { biometricVersion++; vm.notify("Acceso biométrico activado") },
                { message -> vm.revokeBiometric(binding.grantId); vm.notify(message) }) } },
            disableBiometric = { biometric.grantId()?.let { vm.revokeBiometric(it) }; biometric.clear(); biometricVersion++ },
            configureBiometric = { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
            lock = { vm.logout() },
            logout = { val grant = biometric.grantId(); biometric.clear(); biometricVersion++; vm.logout(grant) })
    }
    Scaffold(containerColor = Slate, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (user != null) Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("UTEZ", color = Mint, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 21.sp)
                    Text(" / WALLET", fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    Text(if(state.demo) "DEMO LOCAL" else "SERVIDOR", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    IconButton({ vm.refresh() }, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Actualizar datos", tint = Muted) }
                }
                if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mint)
            }
        },
        bottomBar = {
            if (user != null && !user.mustChangePassword) {
                val labels = if(user.role == Role.ALUMNO) listOf("Inicio", "Cafetería", "Historial", "Perfil") else listOf("Alumnos", "Terminal", "Perfil")
                val icons = if(user.role == Role.ALUMNO) listOf(Icons.Outlined.Home, Icons.Outlined.Restaurant, Icons.Outlined.ReceiptLong, Icons.Outlined.Person)
                    else listOf(Icons.Outlined.People, Icons.Outlined.QrCodeScanner, Icons.Outlined.Person)
                NavigationBar(containerColor = Slate, tonalElevation = 0.dp) {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(tab == index, { tab = index }, icon = { Icon(icons[index], null) }, label = { Text(label, fontSize = 10.sp) }, enabled = !state.busy)
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                user == null -> LoginScreen(state, vm, biometricVersion.let { if (biometric.hasEntry()) biometric.username() else null }, availability.first,
                    { biometric.unlock(activity, { vm.biometricLogin(it) }, { vm.notify(it) }) })
                user.mustChangePassword -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionTitle("Haz tuya tu cuenta", "Cambia la contraseña temporal para continuar.")
                    PasswordContent(state, vm) {}
                    TextButton({ vm.logout() }, enabled = !state.busy) { Text("Cerrar sesión") }
                }
                user.role == Role.ALUMNO -> when(tab) {
                    0 -> HomeScreen(state, vm, { tab = 1 }, { tab = 2 }, { topup = true })
                    1 -> ShopScreen(state, vm)
                    2 -> HistoryScreen(state, vm)
                    else -> profile()
                }
                else -> when(tab) {
                    0 -> StudentsScreen(state, vm, { editing = it; editOpen = true }, { deleting = it }, { resetting = it; temporaryPassword = "" })
                    1 -> TerminalScreen(state, vm) {
                        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                        else scannerPermission.launch(Manifest.permission.CAMERA)
                    }
                    else -> profile()
                }
            }
        }
    }
    if(user != null && !user.mustChangePassword) {
        if(topup) TopupDialog(state, vm) { topup = false }
        if(password) AlertDialog(onDismissRequest = { if(!state.busy) password = false }, title = { Text("Cambiar contraseña") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { PasswordContent(state, vm) { password = false } } },
            confirmButton = {}, dismissButton = { TextButton({ password = false }, enabled = !state.busy) { Text("Cancelar") } })
        if(editOpen) StudentEditor(editing, state, vm) { editOpen = false }
        deleting?.let { target ->
            AlertDialog(onDismissRequest = { if(!state.busy) deleting = null }, title = { Text("Dar de baja al alumno") },
                text = { Text("${target.name} ya no podrá iniciar sesión ni operar. Su historial se conservará.") },
                confirmButton = { TextButton({ vm.delete(target) { deleting = null } }, enabled = !state.busy) { Text("Dar de baja", color = Danger) } },
                dismissButton = { TextButton({ deleting = null }, enabled = !state.busy) { Text("Cancelar") } })
        }
        state.qr?.let { QrDialog(it, state, vm) }
        if (scanning) QrScanner({ scanning = false; vm.inspect(it) }, { scanning = false })
        resetting?.let { target ->
            AlertDialog(onDismissRequest = { if (!state.busy) resetting = null }, title = { Text("Restablecer contraseña de ${target.name}") },
                text = { Field(temporaryPassword, { temporaryPassword = it }, "Contraseña temporal", password = true, enabled = !state.busy) },
                confirmButton = { TextButton({ vm.resetPassword(target, temporaryPassword) { resetting = null; temporaryPassword = "" } }, enabled = !state.busy && temporaryPassword.length >= 8) { Text("Guardar") } },
                dismissButton = { TextButton({ resetting = null }, enabled = !state.busy) { Text("Cancelar") } })
        }
        state.receipt?.let { receipt ->
            AlertDialog(onDismissRequest = { vm.dismissReceipt() }, title = { Text("Comprobante") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(receipt.studentName); MovementRow(receipt.movement)
                    receipt.lines.forEach { Text("${it.quantity}× ${it.name} · ${money(it.quantity * it.unitPriceCents)}") }
                } }, confirmButton = { TextButton({ vm.dismissReceipt() }) { Text("Cerrar") } })
        }
        state.detailStudent?.let { student ->
            AlertDialog(onDismissRequest = { vm.dismissStudentDetail() }, title = { Text("Historial de ${student.name}") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Movimientos", fontWeight = FontWeight.Bold)
                    state.detailMovements.forEach { MovementRow(it) }
                    Text("Cambios", fontWeight = FontWeight.Bold)
                    state.detailAudit.forEach { Text("${it.action}: ${it.details}") }
                } }, confirmButton = { TextButton({ vm.dismissStudentDetail() }) { Text("Cerrar") } })
        }
    }
}
