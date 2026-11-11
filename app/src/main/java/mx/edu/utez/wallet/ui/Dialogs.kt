package mx.edu.utez.wallet.ui

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import mx.edu.utez.wallet.ui.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.edu.utez.wallet.core.*

@Composable fun TopupDialog(state: WalletUiState, vm: WalletViewModel, dismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("100") }
    val value = cents(amount)
    AlertDialog(onDismissRequest = { if (!state.busy) dismiss() }, title = { Text("Recargar monedero") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoBox("Pago simulado · no se realizará ningún cargo bancario.")
                Field(amount, { amount = it }, "Monto en MXN", numeric = true, enabled = !state.busy, supporting = "Entre $10 y $5,000 · máximo 2 decimales")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("50", "100", "200").forEach { preset -> FilterChip(amount == preset, { amount = preset }, { Text("$$preset") }, enabled = !state.busy) }
                }
                Text("Tarjeta ficticia · •••• 4242", fontWeight = FontWeight.Bold)
                Text("Esta versión usa únicamente una tarjeta de prueba predefinida.", color = Muted)
            }
        },
        confirmButton = { Button({ vm.topup(requireNotNull(value), dismiss) }, enabled = !state.busy && value != null && value in 1000L..500000L) { Text("Simular recarga") } },
        dismissButton = { TextButton(dismiss, enabled = !state.busy) { Text("Cancelar") } })
}

@Composable fun PasswordContent(state: WalletUiState, vm: WalletViewModel, done: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoBox("Usa al menos 8 caracteres con letras y números.")
        Field(current, { current = it }, "Contraseña actual", password = true, enabled = !state.busy)
        Field(next, { next = it }, "Nueva contraseña", password = true, enabled = !state.busy)
        Field(confirmation, { confirmation = it }, "Confirmar contraseña", password = true, enabled = !state.busy)
        if (confirmation.isNotEmpty() && next != confirmation) Text("Las contraseñas no coinciden", color = Danger)
        ActionButton("Guardar contraseña", Icons.Outlined.Lock, !state.busy && current.isNotEmpty() && next.length >= 8 && next == confirmation) {
            vm.password(current, next, done)
        }
    }
}

@Composable fun StudentEditor(existing: UserProfile?, state: WalletUiState, vm: WalletViewModel, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var career by rememberSaveable { mutableStateOf(existing?.career ?: "Desarrollo y Gestión de Software") }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    AlertDialog(onDismissRequest = { if (!state.busy) dismiss() },
        title = { Text(if (existing == null) "Registrar alumno" else "Editar alumno") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(name, { name = it }, "Nombre completo", enabled = !state.busy)
                Field(username, { username = it }, "Matrícula", enabled = !state.busy)
                Field(career, { career = it }, "Carrera", enabled = !state.busy)
                Field(email, { email = it }, "Correo institucional", enabled = !state.busy)
                if (existing == null) {
                    Field(password, { password = it }, "Contraseña temporal", password = true, enabled = !state.busy)
                    Text("El alumno deberá cambiarla al iniciar sesión.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Estatus inicial activo")
                        Switch(active, { active = it }, enabled = !state.busy)
                    }
                }
            }
        },
        confirmButton = { Button({
            vm.saveStudent(existing, StudentRequest(name, username, career, email, password, if (active) StudentStatus.ACTIVO else StudentStatus.INACTIVO), dismiss)
        }, enabled = !state.busy && name.isNotBlank() && username.isNotBlank() && email.isNotBlank() && (existing != null || password.length >= 8)) { Text("Guardar") } },
        dismissButton = { TextButton(dismiss, enabled = !state.busy) { Text("Cancelar") } })
}

@Composable fun QrDialog(response: TokenResponse, state: WalletUiState, vm: WalletViewModel) {
    var remaining by remember(response.token) { mutableLongStateOf(((response.expiresAt - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(response.token) {
        while (true) { remaining = ((response.expiresAt - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0); if (remaining == 0L) break; delay(250) }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!state.busy) vm.dismissQr() },
        title = { Text(if(response.purpose == "ACCESS") "Tu QR de acceso" else "Autorizar compra") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (remaining > 0) QrImage(response.token) else InfoBox("Este QR venció. Cierra y genera uno nuevo.", true)
                Text(if (remaining > 0) "Válido por ${remaining}s · un solo uso" else "Código vencido", color = if (remaining > 0) Mint else Warning)
                Text(if(state.demo) "Válido solo en esta demo local." else "Válido en la terminal conectada al mismo servidor.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("UTEZ QR", response.token)))
                    vm.notify("Contenido del QR copiado")
                } }, Modifier.fillMaxWidth(), enabled = remaining > 0 && !state.busy) { Text("Copiar para terminal de pruebas") }
                if (response.purpose == "PAYMENT") {
                    state.order?.let { Text("Importe autorizado: ${money(it.amountCents)}", fontWeight = FontWeight.Bold) }
                    InfoBox("La autoterminal permite completar la demostración con un solo celular.")
                    ActionButton("Pagar en autoterminal demo", Icons.Outlined.CheckCircle, remaining > 0 && !state.busy) { vm.pay(response.token) }
                }
            }
        },
        confirmButton = { TextButton({ vm.dismissQr() }, enabled = !state.busy) { Text("Cerrar") } })
}
