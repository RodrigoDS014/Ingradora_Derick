package mx.edu.utez.wallet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.edu.utez.wallet.core.*
import mx.edu.utez.wallet.security.BiometricAccess
import mx.edu.utez.wallet.ui.*
import mx.edu.utez.wallet.ui.icons.Icons
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
    val user = state.user
    var tab by rememberSaveable(user?.id) { mutableIntStateOf(0) }
    var topup by rememberSaveable(user?.id) { mutableStateOf(false) }
    var password by rememberSaveable(user?.id) { mutableStateOf(false) }
    var editOpen by remember(user?.id) { mutableStateOf(false) }
    var editing by remember(user?.id) { mutableStateOf<UserProfile?>(null) }
    var deleting by remember(user?.id) { mutableStateOf<UserProfile?>(null) }
    var resetting by remember(user?.id) { mutableStateOf<UserProfile?>(null) }
    var productOpen by remember(user?.id) { mutableStateOf(false) }
    var productEdit by remember(user?.id) { mutableStateOf<Product?>(null) }
    var productDelete by remember(user?.id) { mutableStateOf<Product?>(null) }
    var scanning by remember(user?.id) { mutableStateOf(false) }
    var pendingCamera by rememberSaveable { mutableStateOf("") }
    val biometric = remember(activity) { BiometricAccess(activity.applicationContext) }
    var biometricRevision by remember { mutableIntStateOf(0) }
    var capability by remember { mutableStateOf(biometric.availability()) }
    var biometricPromptOpen by remember { mutableStateOf(false) }
    val biometricUser = remember(biometricRevision) { biometric.username().takeIf { biometric.hasEntry() } }
    val biometricEnabled = remember(biometricRevision, user?.id, state.demo, state.baseUrl) {
        user != null && biometric.isFor(user.id, state.demo, state.baseUrl)
    }
    val snackbar = remember { SnackbarHostState() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this) }.toByteArray()
            vm.photo(Base64.getEncoder().encodeToString(bytes))
        }
    }
    fun openCamera(kind: String) {
        when (kind) {
            "SCAN" -> if (user?.role == Role.ADMINISTRADOR) scanning = true
            "PHOTO" -> if (user?.role == Role.ALUMNO) try {
                camera.launch(null)
            } catch (_: android.content.ActivityNotFoundException) {
                vm.notify("No hay una aplicación de cámara disponible en este dispositivo")
            } catch (_: SecurityException) {
                vm.notify("No se pudo abrir la cámara. Revisa el permiso en los ajustes.")
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingCamera
        pendingCamera = ""
        if (granted) openCamera(kind)
        else vm.notify("Permite el acceso a la cámara en los ajustes de la aplicación. En la terminal también puedes pegar el QR.")
    }
    fun requestCamera(kind: String) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera(kind)
        else { pendingCamera = kind; permission.launch(Manifest.permission.CAMERA) }
    }
    fun clearBiometric() { biometric.clear(); biometricRevision++ }
    fun unlock() {
        if (biometricPromptOpen) return
        biometricPromptOpen = true
        biometric.unlock(activity, {
            biometricPromptOpen = false
            vm.biometricLogin(it)
        }, {
            biometricPromptOpen = false; biometricRevision++; vm.notify(it)
        })
    }
    fun enroll() {
        if (biometricPromptOpen) return
        biometricPromptOpen = true
        vm.prepareBiometric { binding ->
            biometric.enroll(activity, binding, {
                biometricPromptOpen = false; biometricRevision++; vm.notify("Acceso biométrico activado")
            }, { message ->
                biometricPromptOpen = false; vm.notify(message); vm.revokeBiometric(binding.grantId)
            })
        }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) capability = biometric.availability() }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.biometricInvalid) {
        if (state.biometricInvalid) { clearBiometric(); vm.clearBiometricInvalid() }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            biometricPromptOpen = false
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    val profile: @Composable () -> Unit = {
        ProfileScreen(state, vm, { requestCamera("PHOTO") }, { password = true },
            biometricEnabled, capability.first, capability.second, ::enroll,
            {
                val id = biometric.grantId()
                clearBiometric()
                if (id != null) vm.revokeBiometric(id)
            },
            { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
            { vm.logout() },
            {
                val id = biometric.grantId().takeIf { biometricEnabled }
                if (biometricEnabled) clearBiometric()
                vm.logout(id)
            })
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
                val labels = if(user.role == Role.ALUMNO) listOf("Inicio", "Cafetería", "Historial", "Perfil")
                    else listOf("Alumnos", "Terminal", "Catálogo", "Perfil")
                val icons = if(user.role == Role.ALUMNO) listOf(Icons.Outlined.Home, Icons.Outlined.Restaurant, Icons.Outlined.ReceiptLong, Icons.Outlined.Person)
                    else listOf(Icons.Outlined.People, Icons.Outlined.QrCodeScanner, Icons.Outlined.Restaurant, Icons.Outlined.Person)
                NavigationBar(containerColor = Slate, tonalElevation = 0.dp) {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(tab == index, { tab = index }, icon = { Icon(icons[index], null) }, label = { Text(label, fontSize = 10.sp) }, enabled = !state.busy)
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                user == null -> LoginScreen(state, vm, biometricUser, capability.first && !biometricPromptOpen, ::unlock)
                user.mustChangePassword -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionTitle("Haz tuya tu cuenta", "Cambia la contraseña temporal para continuar.")
                    PasswordContent(state, vm) { if (biometricEnabled) clearBiometric() }
                    TextButton({ vm.logout() }, enabled = !state.busy) { Text("Cerrar sesión") }
                }
                user.role == Role.ALUMNO -> when(tab) {
                    0 -> HomeScreen(state, vm, { tab = 1 }, { tab = 2 }, { topup = true })
                    1 -> ShopScreen(state, vm)
                    2 -> HistoryScreen(state, vm)
                    else -> profile()
                }
                else -> when(tab) {
                    0 -> StudentsScreen(state, vm, { editing = it; editOpen = true }, { deleting = it }, { resetting = it })
                    1 -> TerminalScreen(state, vm) { requestCamera("SCAN") }
                    2 -> AdminCatalogScreen(state, vm, { productEdit = it; productOpen = true }, { productDelete = it })
                    else -> profile()
                }
            }
        }
    }
    if(user != null && !user.mustChangePassword) {
        if(topup) TopupDialog(state, vm) { topup = false }
        if(password) AlertDialog(onDismissRequest = { if(!state.busy) password = false }, title = { Text("Cambiar contraseña") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { PasswordContent(state, vm) {
                password = false; if (biometricEnabled) clearBiometric()
            } } },
            confirmButton = {}, dismissButton = { TextButton({ password = false }, enabled = !state.busy) { Text("Cancelar") } })
        if(editOpen) StudentEditor(editing, state, vm) { editOpen = false }
        deleting?.let { target ->
            AlertDialog(onDismissRequest = { if(!state.busy) deleting = null }, title = { Text("Dar de baja al alumno") },
                text = { Text("${target.name} ya no podrá iniciar sesión ni operar. Su historial se conservará.") },
                confirmButton = { TextButton({ vm.delete(target) { deleting = null } }, enabled = !state.busy) { Text("Dar de baja", color = Danger) } },
                dismissButton = { TextButton({ deleting = null }, enabled = !state.busy) { Text("Cancelar") } })
        }
        resetting?.let { ResetStudentDialog(it, state, vm) { resetting = null } }
        state.detailStudent?.let { StudentDetailDialog(it, state, vm) }
        if(productOpen) ProductDialog(productEdit, state, vm) { productOpen = false }
        productDelete?.let { target ->
            AlertDialog({ if(!state.busy) productDelete = null }, title = { Text("Retirar producto") },
                text = { Text("${target.name} dejará de ofrecerse. Los comprobantes anteriores se conservarán.") },
                confirmButton = { TextButton({ vm.archiveProduct(target) { productDelete = null } }, enabled = !state.busy) { Text("Retirar", color = Danger) } },
                dismissButton = { TextButton({ productDelete = null }, enabled = !state.busy) { Text("Cancelar") } })
        }
        if(scanning && user.role == Role.ADMINISTRADOR) QrScanner({ code -> scanning = false; vm.inspect(code) }, { scanning = false })
        state.qr?.let { QrDialog(it, state, vm) }
        state.receipt?.let { ReceiptDialog(it, vm::dismissReceipt) }
    }
}
