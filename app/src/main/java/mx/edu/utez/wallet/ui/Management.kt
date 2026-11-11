package mx.edu.utez.wallet.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.edu.utez.wallet.core.*
import mx.edu.utez.wallet.ui.icons.Icons
import java.math.BigDecimal
import kotlin.math.abs

@Composable fun AdminCatalogScreen(state: WalletUiState, vm: WalletViewModel,
    edit: (Product?) -> Unit, archive: (Product) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val products = state.products.filter { it.name.contains(query.trim(), ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionTitle("Catálogo de cafetería", "Gestiona los precios y lo que está disponible para los alumnos.")
            Spacer(Modifier.height(16.dp))
            ActionButton("Agregar producto", Icons.Outlined.Add, !state.busy) { edit(null) }
        }
        item { Field(query, { query = it }, "Buscar producto") }
        if (products.isEmpty()) item { InfoBox("No hay productos que coincidan.") }
        items(products, key = { it.id }) { product ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold)
                        Text(money(product.priceCents), color = Mint)
                    }
                    Switch(product.available, { vm.productAvailability(product) }, enabled = !state.busy)
                }
                if (product.description.isNotBlank()) Text(product.description, color = Muted)
                Text(if (product.available) "Disponible" else "Agotado", color = if (product.available) Mint else Warning)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ edit(product) }, enabled = !state.busy) { Text("Editar") }
                    TextButton({ archive(product) }, enabled = !state.busy) { Text("Retirar", color = Danger) }
                }
            }
        }
    }
}

@Composable fun ProductDialog(product: Product?, state: WalletUiState, vm: WalletViewModel, dismiss: () -> Unit) {
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var description by rememberSaveable(product?.id) { mutableStateOf(product?.description.orEmpty()) }
    var price by rememberSaveable(product?.id) { mutableStateOf(product?.let { BigDecimal.valueOf(it.priceCents, 2).toPlainString() }.orEmpty()) }
    var available by rememberSaveable(product?.id) { mutableStateOf(product?.available ?: true) }
    val amount = cents(price)
    AlertDialog({ if (!state.busy) dismiss() },
        title = { Text(if (product == null) "Nuevo producto" else "Editar producto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it.take(80) }, "Nombre", enabled = !state.busy)
                Field(description, { description = it.take(160) }, "Descripción", enabled = !state.busy)
                Field(price, { price = it }, "Precio en MXN", numeric = true, enabled = !state.busy)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Disponible", Modifier.weight(1f))
                    Switch(available, { available = it }, enabled = !state.busy)
                }
            }
        },
        confirmButton = {
            Button({
                if (amount != null) vm.saveProduct(product, ProductRequest(name, description, amount, available), dismiss)
            }, enabled = !state.busy && name.trim().length >= 2 && amount != null && amount in 1L..100000L) { Text("Guardar") }
        },
        dismissButton = { TextButton(dismiss, enabled = !state.busy) { Text("Cancelar") } })
}

@Composable fun ResetStudentDialog(student: UserProfile, state: WalletUiState, vm: WalletViewModel, dismiss: () -> Unit) {
    var password by remember(student.id) { mutableStateOf("") }
    var repeated by remember(student.id) { mutableStateOf("") }
    AlertDialog({ if (!state.busy) dismiss() }, title = { Text("Restablecer contraseña") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${student.name} · ${student.username}")
                InfoBox("El alumno deberá cambiar la contraseña temporal. Se revocarán sus sesiones y accesos biométricos anteriores.")
                Field(password, { password = it.take(128) }, "Contraseña temporal", password = true, enabled = !state.busy,
                    supporting = "Mínimo 8 caracteres, con letras y números.")
                Field(repeated, { repeated = it.take(128) }, "Repetir contraseña", password = true, enabled = !state.busy)
            }
        },
        confirmButton = { Button({ vm.resetPassword(student, password, dismiss) },
            enabled = !state.busy && password == repeated && password.length >= 8 && password.any(Char::isLetter) && password.any(Char::isDigit)) { Text("Restablecer") } },
        dismissButton = { TextButton(dismiss, enabled = !state.busy) { Text("Cancelar") } })
}

@Composable fun StudentDetailDialog(student: UserProfile, state: WalletUiState, vm: WalletViewModel) {
    var audit by rememberSaveable(student.id) { mutableStateOf(false) }
    AlertDialog(vm::dismissStudentDetail, title = { Text(student.name) }, text = {
        Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${student.username} · ${money(student.balanceCents)}", color = Mint)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!audit, { audit = false }, { Text("Movimientos") })
                FilterChip(audit, { audit = true }, { Text("Cambios") })
            }
            if (audit) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.detailAudit.isEmpty()) item { Text("Todavía no hay cambios registrados.", color = Muted) }
                    items(state.detailAudit, key = { it.id }) { entry ->
                        Column {
                            Text(entry.details, fontWeight = FontWeight.SemiBold)
                            Text("${entry.actorName} · ${date(entry.timestamp)}", color = Muted)
                        }
                    }
                }
            } else {
                LazyColumn {
                    if (state.detailMovements.isEmpty()) item { Text("Todavía no hay movimientos.", color = Muted) }
                    items(state.detailMovements, key = { it.id }) { movement ->
                        Column {
                            MovementRow(movement)
                            if (movement.kind != MovementKind.ACCESO) TextButton({ vm.receipt(movement.id) }, enabled = !state.busy) { Text("Ver comprobante") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(vm::dismissStudentDetail) { Text("Cerrar") } })
}

@Composable fun ReceiptDialog(receipt: PurchaseReceipt, dismiss: () -> Unit) {
    val context = LocalContext.current
    val movement = receipt.movement
    val total = "${money(abs(movement.amountCents))} ${receipt.currency}"
    val title = if (movement.kind == MovementKind.RECARGA) "Comprobante de recarga" else "Comprobante de compra"
    val rows = receipt.lines.map { "${it.quantity}× ${it.name} · ${money(it.quantity * it.unitPriceCents)}" }
    AlertDialog(dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("UTEZ WALLET · OPERACIÓN SIMULADA", color = Mint)
            Text("${receipt.studentName} · ${receipt.username}")
            Text(date(movement.timestamp), color = Muted)
            if (rows.isEmpty()) Text(movement.title) else rows.forEach { Text(it) }
            HorizontalDivider()
            Text(total, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Saldo posterior: ${money(movement.balanceAfterCents)}")
            Text("Folio: ${movement.id}", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(dismiss) { Text("Cerrar") } },
        dismissButton = {
            TextButton({
                val details = (rows.ifEmpty { listOf(movement.title) }).joinToString("\n")
                val content = "UTEZ Wallet · $title simulado\n${receipt.studentName} · ${receipt.username}\n${date(movement.timestamp)}\n$details\nTotal: $total\nFolio: ${movement.id}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content), "Compartir comprobante"))
            }) { Text("Compartir") }
        })
}
