package mx.edu.utez.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import mx.edu.utez.wallet.ui.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.edu.utez.wallet.core.*

@Composable fun LoginScreen(state: WalletUiState, vm: WalletViewModel, biometricUser: String? = null,
    biometricAvailable: Boolean = false, biometricLogin: () -> Unit = {}) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var demo by rememberSaveable { mutableStateOf(state.demo) }
    var address by rememberSaveable { mutableStateOf(state.baseUrl) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(24.dp))
        Surface(color = Mint.copy(alpha = .13f), shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.padding(18.dp).size(34.dp), tint = Mint)
        }
        Eyebrow("UNIVERSIDAD TECNOLÓGICA EMILIANO ZAPATA")
        Text("Tu campus.\nEn tu bolsillo.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
        Text("Tu identidad, tus accesos y tu monedero en un solo lugar.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        PanelCard {
            Text("Bienvenido a UTEZ Wallet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (biometricUser != null) {
                ActionButton("Entrar con biometría · $biometricUser", Icons.Outlined.Lock, !state.busy && biometricAvailable, biometricLogin)
                Text("Accede a la cuenta que activaste en este teléfono o usa otra contraseña abajo.", color = Muted, fontSize = 12.sp)
            }
            Field(username, { username = it }, "Matrícula o usuario", enabled = !state.busy)
            Field(password, { password = it }, "Contraseña", password = true, enabled = !state.busy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demo en este dispositivo", fontWeight = FontWeight.SemiBold)
                    Text(if (demo) "Funciona sin servidor ni internet" else "Conectar al servidor del equipo", color = Muted, fontSize = 12.sp)
                }
                Switch(demo, { demo = it }, enabled = !state.busy)
            }
            if (!demo) Field(address, { address = it }, "Dirección del servidor", supporting = "Emulador: 10.0.2.2 · Celular: IP de tu computadora", enabled = !state.busy)
            ActionButton(if (state.busy) "Iniciando sesión…" else "Iniciar sesión", Icons.Outlined.ArrowForward,
                enabled = !state.busy && username.isNotBlank() && password.isNotEmpty() && (demo || address.isNotBlank())) {
                vm.login(username, password, demo, address)
            }
        }
        Text("CUENTAS DE DEMOSTRACIÓN", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ username = DemoAccounts.STUDENT; password = DemoAccounts.STUDENT_PASSWORD }, Modifier.weight(1f), enabled = !state.busy) { Text("Alumno") }
            OutlinedButton({ username = DemoAccounts.ADMIN; password = DemoAccounts.ADMIN_PASSWORD }, Modifier.weight(1f), enabled = !state.busy) { Text("Administrador", fontSize = 12.sp) }
        }
        InfoBox("Prototipo escolar · Las recargas y compras son simuladas. Los datos de la demo y del servidor se guardan por separado.")
    }
}

@Composable fun HomeScreen(state: WalletUiState, vm: WalletViewModel, onShop: () -> Unit, onHistory: () -> Unit, onTopup: () -> Unit) {
    val user = requireNotNull(state.user)
    var selected by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hola, ${user.name.substringBefore(' ')}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Todo listo para tu día en el campus", color = Muted, fontSize = 12.sp)
                }
                Avatar(user, 48)
            }
        }
        if (user.status == StudentStatus.INACTIVO) item { InfoBox("Tu cuenta está inactiva. Puedes consultar tus datos; accesos y operaciones están bloqueados.", true) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("MIS TARJETAS")
                Text("${selected + 1} / 2", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(300.dp).height(207.dp).background(Brush.linearGradient(listOf(Color(0xFF006847), Color(0xFF064B40))), RoundedCornerShape(25.dp)).clickable { selected = 0 }.padding(22.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("UTEZ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Icon(Icons.Outlined.Badge, null, tint = Color(0xFFA7F3D0))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(user, 48)
                            Column {
                                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                                Text(user.username, color = Color(0xFFA7F3D0), fontSize = 12.sp, letterSpacing = 2.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CREDENCIAL ESTUDIANTIL", color = Color(0xFFA7F3D0), fontSize = 9.sp, letterSpacing = 1.sp)
                            Text(if (selected == 0) "● Seleccionada" else "Seleccionar", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                Box(Modifier.width(300.dp).height(207.dp).background(Brush.linearGradient(listOf(Color(0xFF273951), Color(0xFF172337))), RoundedCornerShape(25.dp)).clickable { selected = 1 }.padding(22.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("UTEZ / WALLET", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Mint)
                        }
                        Column {
                            Text("SALDO DISPONIBLE", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                            Text(money(user.balanceCents), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MONEDERO CAFETERÍA", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                            Text(if (selected == 1) "● Seleccionada" else "Seleccionar", color = Mint, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item {
            PanelCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(if(selected == 0) "Acceso al campus" else "Tu monedero", fontWeight = FontWeight.Bold); Text(if (selected == 0) user.career else "Saldo: ${money(user.balanceCents)}", color = Muted, fontSize = 12.sp) }
                    StatusPill(user.status == StudentStatus.ACTIVO)
                }
                if (selected == 0) {
                    ActionButton("Mostrar QR de acceso", Icons.Outlined.QrCode2, !state.busy && user.status == StudentStatus.ACTIVO) { vm.accessQr() }
                    OutlinedButton({ vm.simulateAccess() }, Modifier.fillMaxWidth(), enabled = !state.busy && user.status == StudentStatus.ACTIVO) { Text("Simular entrada sin sticker") }
                    InfoBox("NFC físico pendiente de integrar cuando esté disponible el sticker. Esta prueba registra un acceso simulado.")
                } else {
                    ActionButton("Recargar saldo", Icons.Outlined.Add, !state.busy && user.status == StudentStatus.ACTIVO, onTopup)
                    OutlinedButton(onShop, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Ir a cafetería de demostración") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Actividad reciente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onHistory) { Text("Ver todo") }
            }
        }
        if (state.movements.isEmpty()) item { InfoBox("Tus accesos, compras y recargas aparecerán aquí.") }
        items(state.movements.take(3), key = { it.id }) { MovementRow(it) }
    }
}

@Composable fun ShopScreen(state: WalletUiState, vm: WalletViewModel) {
    var cart by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val total = state.products.sumOf { it.priceCents * (cart[it.id] ?: 0) }
    val active = state.user?.status == StudentStatus.ACTIVO
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle("Un descanso sabe bien", "Cafetería de demostración · precios en MXN") }
        item { InfoBox("Compra simulada. Saldo disponible: ${money(state.user?.balanceCents ?: 0)}") }
        items(state.products, key = { it.id }) { product ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Mint.copy(alpha = .1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(if (product.id == "cafe") Icons.Outlined.Coffee else Icons.Outlined.Restaurant,
                            null, Modifier.padding(13.dp).size(26.dp), tint = Mint)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold)
                        Text(product.description, color = Muted, fontSize = 11.sp)
                        Text(money(product.priceCents), color = Mint, fontWeight = FontWeight.SemiBold)
                        if(!product.available) Text("Agotado", color = Warning, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    val quantity = cart[product.id] ?: 0
                    IconButton({ cart = cart + (product.id to (quantity - 1)) }, enabled = !state.busy && quantity > 0) { Icon(Icons.Outlined.Remove, "Quitar ${product.name}") }
                    Text(quantity.toString(), Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.Bold)
                    IconButton({ cart = cart + (product.id to (quantity + 1)) }, enabled = !state.busy && active && product.available && quantity < 20) { Icon(Icons.Outlined.Add, "Agregar ${product.name}") }
                }
            }
        }
        item {
            PanelCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total del pedido", fontWeight = FontWeight.Bold)
                    Text(money(total), color = Mint, fontWeight = FontWeight.Bold)
                }
                ActionButton("Preparar compra de prueba", Icons.Outlined.ShoppingBag, !state.busy && active && total > 0) {
                    vm.order(cart.filterValues { it > 0 }.map { OrderItem(it.key, it.value) })
                }
                state.order?.takeIf { it.studentId == state.user?.id }?.let { order ->
                    Text("Pedido ${order.id.take(8)} · ${money(order.amountCents)}", fontWeight = FontWeight.Bold)
                    order.lines.forEach { line -> Text("${line.quantity}× ${line.name} · ${money(line.unitPriceCents * line.quantity)}", color = Muted, fontSize = 12.sp) }
                    Text("Revisa el importe. El saldo se descontará al confirmar el pago.", color = Muted, fontSize = 12.sp)
                    ActionButton("Autorizar y generar QR", Icons.Outlined.QrCode2, !state.busy && active) { vm.approve(order.id) }
                }
            }
        }
    }
}

@Composable fun HistoryScreen(state: WalletUiState, vm: WalletViewModel) {
    var filter by rememberSaveable { mutableStateOf("Todos") }
    val filtered = state.movements.filter { filter == "Todos" || when (it.kind) { MovementKind.COMPRA -> filter == "Compras"; MovementKind.RECARGA -> filter == "Recargas"; MovementKind.ACCESO -> filter == "Accesos" } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Tus movimientos", "Últimos 100 registros · compras, recargas y accesos") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Compras", "Recargas", "Accesos").forEach { label -> FilterChip(filter == label, { filter = label }, { Text(label) }) }
            }
        }
        if (filtered.isEmpty()) item { InfoBox("Todavía no hay movimientos en esta categoría.") }
        items(filtered, key = { it.id }) { movement ->
            Column {
                MovementRow(movement)
                if (movement.kind != MovementKind.ACCESO) TextButton({ vm.receipt(movement.id) }, enabled = !state.busy) { Text("Ver comprobante") }
            }
        }
    }
}

@Composable fun StudentsScreen(state: WalletUiState, vm: WalletViewModel, edit: (UserProfile?) -> Unit,
    delete: (UserProfile) -> Unit, resetPassword: (UserProfile) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var career by rememberSaveable { mutableStateOf("Todas") }
    val filtered = state.students.filter { (it.name.contains(query, true) || it.username.contains(query)) &&
        (filter == "Todos" || it.status.name == filter) && (career == "Todas" || it.career == career) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionTitle("Comunidad UTEZ", "${state.students.size} alumnos registrados · Control Escolar")
            Spacer(Modifier.height(20.dp))
            ActionButton("Registrar alumno", Icons.Outlined.PersonAdd, !state.busy) { edit(null) }
        }
        item { Field(query, { query = it }, "Buscar nombre o matrícula") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "ACTIVO", "INACTIVO").forEach { label -> FilterChip(filter == label, { filter = label }, { Text(label) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("Todas") + state.students.map { it.career }.distinct().sorted()).forEach { label -> FilterChip(career == label, { career = label }, { Text(label) }) }
            }
        }
        if (filtered.isEmpty()) item { InfoBox("No se encontraron alumnos con estos filtros.") }
        items(filtered, key = { it.id }) { user ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(user, 44)
                    Column(Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Bold)
                        Text(user.username, color = Mint, fontSize = 12.sp)
                    }
                }
                Text(user.career, color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(user.status == StudentStatus.ACTIVO)
                    Switch(user.status == StudentStatus.ACTIVO, { vm.status(user) }, enabled = !state.busy)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ edit(user) }, enabled = !state.busy) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Editar") }
                    TextButton({ delete(user) }, enabled = !state.busy) { Text("Dar de baja", color = Danger) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ vm.studentDetail(user) }, enabled = !state.busy) { Text("Historial y cambios") }
                    TextButton({ resetPassword(user) }, enabled = !state.busy) { Text("Restablecer clave") }
                }
            }
        }
    }
}

@Composable fun TerminalScreen(state: WalletUiState, vm: WalletViewModel, scan: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.review) {
        while(state.review != null) { now = System.currentTimeMillis(); if(now >= state.review.expiresAt) break; kotlinx.coroutines.delay(500) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SectionTitle("Terminal de pruebas", "Valida códigos del mismo servidor o de la misma demo local")
        ActionButton("Escanear QR con cámara", Icons.Outlined.QrCodeScanner, !state.busy, scan)
        InfoBox("Escanea el QR del alumno o pega su contenido. Revisa los datos antes de confirmar.")
        PanelCard {
            OutlinedTextField(state.terminalCode, vm::setTerminalCode, Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text("Contenido del QR temporal") }, maxLines = 4, shape = RoundedCornerShape(13.dp), enabled = !state.busy)
            OutlinedButton({ vm.inspect(state.terminalCode) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.terminalCode.isNotBlank()) { Text("Revisar código") }
            state.review?.let { review ->
                Text(if(review.purpose == "ACCESS") "Acceso al campus" else "Cobro en cafetería", fontWeight = FontWeight.Bold)
                Text(review.studentName)
                Text(review.username, color = Mint)
                review.lines.forEach { Text("${it.quantity}× ${it.name} · ${money(it.quantity * it.unitPriceCents)}", color = Muted) }
                if(review.purpose == "PAYMENT") Text("Total: ${money(review.amountCents)}", style = MaterialTheme.typography.titleLarge, color = Mint)
                val remaining = ((review.expiresAt - now + 999) / 1000).coerceAtLeast(0)
                Text(if(remaining > 0) "Vigente por ${remaining}s" else "QR vencido; solicita uno nuevo", color = if(remaining > 0) Mint else Warning)
                ActionButton(if(review.purpose == "ACCESS") "Confirmar acceso" else "Confirmar cobro simulado", Icons.Outlined.Verified,
                    !state.busy && remaining > 0) { if(review.purpose == "ACCESS") vm.redeemAccess(state.terminalCode) else vm.pay(state.terminalCode) }
            }
        }
        InfoBox("Para probar entre dos celulares, ambos deben usar el modo Servidor y la misma dirección. Dos demos locales tienen datos independientes.", true)
        PanelCard {
            Text("Validaciones del servidor", fontWeight = FontWeight.Bold)
            Text("Alumno activo · QR firmado y vigente · un solo uso · saldo suficiente · cobro sin duplicados", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun ProfileScreen(state: WalletUiState, vm: WalletViewModel, photo: () -> Unit, password: () -> Unit,
    biometricEnabled: Boolean, biometricAvailable: Boolean, biometricMessage: String, enableBiometric: () -> Unit,
    disableBiometric: () -> Unit, configureBiometric: () -> Unit, lock: () -> Unit, logout: () -> Unit) {
    val user = requireNotNull(state.user)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SectionTitle("Tu perfil", if(user.role == Role.ALUMNO) "Identidad universitaria" else "Administración de la comunidad")
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(user, 74)
                Column(Modifier.weight(1f)) { Text(user.name, fontWeight = FontWeight.Bold); Text(user.username, color = Mint); Text(user.role.name, color = Muted, fontSize = 11.sp) }
            }
            if (user.role == Role.ALUMNO) {
                Text(user.career, color = Muted)
                Text(user.email, color = Muted, fontSize = 12.sp)
                StatusPill(user.status == StudentStatus.ACTIVO)
                OutlinedButton(photo, Modifier.fillMaxWidth(), enabled = !state.busy && user.status == StudentStatus.ACTIVO) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Tomar fotografía") }
            }
        }
        ActionButton("Cambiar contraseña", Icons.Outlined.Lock, !state.busy, password)
        PanelCard {
            Text("Acceso biométrico", fontWeight = FontWeight.Bold)
            Text(if(biometricEnabled) "Activado para esta cuenta y este teléfono." else biometricMessage, color = Muted)
            if(biometricEnabled) {
                ActionButton("Bloquear y probar biometría", Icons.Outlined.Lock, !state.busy, lock)
                OutlinedButton(disableBiometric, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Desactivar acceso biométrico") }
            } else {
                ActionButton("Activar biometría", Icons.Outlined.Lock, !state.busy && biometricAvailable, enableBiometric)
                if(!biometricAvailable) OutlinedButton(configureBiometric, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Abrir ajustes de seguridad") }
            }
        }
        InfoBox(if(state.demo) "Demo local: los cambios se conservan en este dispositivo. No necesitas Oracle ni servidor." else "Conectado al servidor del equipo. Las operaciones financieras requieren conexión.")
        OutlinedButton(logout, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !state.busy) { Text("Cerrar sesión y olvidar acceso", color = Danger) }
        Text("UTEZ Student Wallet & Access Control\nPrototipo académico · v0.2.0", color = Muted, fontSize = 11.sp)
    }
}
