package mx.edu.utez.wallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class StoredAccount(val profile: UserProfile, val passwordHash: String, val deleted: Boolean = false)
@Serializable data class TokenClaims(val id: String, val purpose: String, val studentId: String, val orderId: String?, val expiresAt: Long)
@Serializable data class StoredToken(val claims: TokenClaims, val consumed: Boolean = false)
@Serializable data class Receipt(val key: String, val fingerprint: String, val result: TransactionResult)
@Serializable data class StoredBiometricGrant(val id: String, val userId: String, val tokenHash: String, val expiresAt: Long)
private fun initialProducts() = listOf(
    Product("cafe", "Café americano", "Recién preparado · 250 ml", 2500),
    Product("sandwich", "Sándwich integral", "Jamón, queso y vegetales", 4500),
    Product("agua", "Agua natural", "Botella de 600 ml", 1500),
    Product("ensalada", "Ensalada del día", "Vegetales frescos y pollo", 6000),
    Product("fruta", "Vaso de fruta", "Fruta fresca de temporada", 3000),
)
@Serializable data class WalletState(
    val accounts: List<StoredAccount> = emptyList(), val movements: List<Movement> = emptyList(),
    val orders: List<Order> = emptyList(), val tokens: List<StoredToken> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
    val products: List<Product> = initialProducts(),
    val audit: List<AuditEntry> = emptyList(),
    val biometricGrants: List<StoredBiometricGrant> = emptyList(),
)

/** Save must complete atomically or throw. Engine publishes new state only after save succeeds. */
interface StateStore { fun load(): WalletState?; fun save(state: WalletState) }
class MemoryStateStore : StateStore {
    private var value: WalletState? = null
    override fun load() = value
    override fun save(state: WalletState) { value = state }
}

/** Single-process transaction boundary; all public entry points share this monitor. */
class WalletEngine(private val store: StateStore = MemoryStateStore(), private val clock: Clock = Clock.systemUTC()) {
    private val json = Json { encodeDefaults = true }
    private val signingKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private data class Session(val userId: String, val expiresAt: Long)
    private val sessions = mutableMapOf<String, Session>()
    private var state: WalletState = store.load() ?: seed().also(store::save)
    val catalog: List<Product> @Synchronized get() = state.products.filter { !it.archived }
    private fun now() = clock.millis()
    private fun id() = UUID.randomUUID().toString()
    private fun fail(status: Int, code: String, message: String): Nothing = throw WalletException(status, code, message)
    private fun commit(next: WalletState) { store.save(next); state = next }
    private fun WalletState.withProfile(profile: UserProfile) = copy(accounts = accounts.map {
        if (it.profile.id == profile.id) it.copy(profile = profile) else it
    })
    private fun account(userId: String) = state.accounts.find { it.profile.id == userId && !it.deleted }
        ?: fail(404, "NOT_FOUND", "Alumno no encontrado")
    private fun current(token: String): UserProfile {
        val session = sessions[token] ?: fail(401, "UNAUTHORIZED", "Inicia sesión para continuar")
        if (now() >= session.expiresAt) { sessions.remove(token); fail(401, "SESSION_EXPIRED", "Tu sesión venció") }
        return state.accounts.find { it.profile.id == session.userId && !it.deleted }?.profile
            ?: fail(401, "UNAUTHORIZED", "La cuenta ya no está disponible")
    }
    private fun ready(profile: UserProfile) {
        if (profile.mustChangePassword) fail(403, "PASSWORD_CHANGE_REQUIRED", "Primero cambia tu contraseña temporal")
    }
    private fun active(profile: UserProfile) {
        ready(profile)
        if (profile.status != StudentStatus.ACTIVO) fail(403, "STUDENT_INACTIVE", "Tu cuenta está inactiva. Consulta a Control Escolar")
    }
    private fun student(token: String, requireActive: Boolean = true): UserProfile = current(token).also {
        if (it.role != Role.ALUMNO) fail(403, "FORBIDDEN", "Esta operación es para alumnos")
        if (requireActive) active(it)
    }
    private fun admin(token: String): UserProfile = current(token).also {
        ready(it)
        if (it.role != Role.ADMINISTRADOR) fail(403, "FORBIDDEN", "Se requieren permisos de administrador")
    }

    @Synchronized fun login(request: LoginRequest): SessionResponse {
        val found = state.accounts.find { !it.deleted && it.profile.username.equals(request.username.trim(), true) }
        if (found == null || !Passwords.verify(request.password, found.passwordHash))
            fail(401, "INVALID_CREDENTIALS", "Matrícula o contraseña incorrecta")
        return newSession(found.profile)
    }
    private fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private fun newSession(user: UserProfile): SessionResponse {
        sessions.entries.removeAll { now() >= it.value.expiresAt }
        val token = randomToken()
        sessions[token] = Session(user.id, now() + 8 * 60 * 60 * 1000)
        return SessionResponse(token, user)
    }
    private fun tokenHash(value: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
    @Synchronized fun createBiometricGrant(token: String): BiometricGrantResponse {
        val user = current(token); ready(user)
        val secret = randomToken()
        val grant = StoredBiometricGrant(id(), user.id, tokenHash(secret), now() + 30L * 24 * 60 * 60 * 1000)
        commit(state.copy(biometricGrants = state.biometricGrants.filter { it.expiresAt > now() } + grant))
        return BiometricGrantResponse(grant.id, secret, grant.expiresAt)
    }
    @Synchronized fun biometricLogin(request: RedeemRequest): SessionResponse {
        if (request.token.length !in 32..100) fail(401, "BIOMETRIC_REVOKED", "Este acceso ya no está disponible. Usa tu contraseña")
        val hash = tokenHash(request.token)
        val grant = state.biometricGrants.find { MessageDigest.isEqual(it.tokenHash.toByteArray(), hash.toByteArray()) }
            ?: fail(401, "BIOMETRIC_REVOKED", "Este acceso fue revocado. Usa tu contraseña")
        if (now() >= grant.expiresAt) fail(401, "BIOMETRIC_EXPIRED", "El acceso biométrico venció. Inicia sesión con contraseña")
        val user = state.accounts.find { !it.deleted && it.profile.id == grant.userId }?.profile
            ?: fail(401, "BIOMETRIC_REVOKED", "La cuenta ya no está disponible")
        if (user.mustChangePassword) fail(401, "BIOMETRIC_REVOKED", "Primero cambia tu contraseña temporal")
        return newSession(user)
    }
    @Synchronized fun revokeBiometricGrant(token: String, grantId: String) {
        val user = current(token)
        val grant = state.biometricGrants.find { it.id == grantId } ?: return
        if (grant.userId != user.id) fail(403, "FORBIDDEN", "El acceso pertenece a otra cuenta")
        commit(state.copy(biometricGrants = state.biometricGrants.filter { it.id != grantId }))
    }
    @Synchronized fun logout(token: String) { sessions.remove(token) }
    @Synchronized fun me(token: String) = current(token)
    @Synchronized fun changePassword(token: String, request: PasswordRequest): UserProfile {
        val profile = current(token)
        val found = account(profile.id)
        if (!Passwords.verify(request.currentPassword, found.passwordHash)) fail(422, "WRONG_PASSWORD", "La contraseña actual no es correcta")
        validatePassword(request.newPassword)
        if (request.currentPassword == request.newPassword) fail(422, "SAME_PASSWORD", "La nueva contraseña debe ser diferente")
        val updated = profile.copy(mustChangePassword = false)
        commit(state.copy(accounts = state.accounts.map {
            if (it.profile.id == profile.id) it.copy(profile = updated, passwordHash = Passwords.hash(request.newPassword)) else it
        }, biometricGrants = state.biometricGrants.filter { it.userId != profile.id },
            tokens = state.tokens.filter { it.claims.studentId != profile.id }))
        sessions.entries.removeAll { it.value.userId == profile.id && it.key != token }
        return updated
    }
    @Synchronized fun updatePhoto(token: String, request: PhotoRequest): UserProfile {
        val profile = student(token)
        if (request.photoBase64.length > 280_000) fail(422, "PHOTO_TOO_LARGE", "La foto debe pesar menos de 200 KB")
        val bytes = try { Base64.getDecoder().decode(request.photoBase64) } catch (_: IllegalArgumentException) {
            fail(422, "INVALID_PHOTO", "Fotografía inválida")
        }
        if (bytes.size > 204_800 || bytes.size < 3 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte() || bytes[2] != 0xFF.toByte())
            fail(422, "INVALID_PHOTO", "Usa una fotografía JPEG de hasta 200 KB")
        val updated = profile.copy(photoBase64 = request.photoBase64)
        commit(state.withProfile(updated))
        return updated
    }
    @Synchronized fun movements(token: String): List<Movement> {
        val user = student(token, false)
        return state.movements.filter { it.studentId == user.id }.sortedByDescending { it.timestamp }.take(100)
    }
    @Synchronized fun students(token: String, query: String = "", career: String = "", status: String = ""): List<UserProfile> {
        admin(token)
        return state.accounts.filter { !it.deleted && it.profile.role == Role.ALUMNO }.map { it.profile }
            .filter { (it.name.contains(query, true) || it.username.contains(query, true)) &&
                (career.isBlank() || it.career == career) && (status.isBlank() || it.status.name == status) }
            .sortedBy { it.name }
    }
    @Synchronized fun createStudent(token: String, request: StudentRequest): UserProfile {
        val actor = admin(token); validateStudent(request); validatePassword(request.initialPassword)
        uniqueUsername(request.username)
        val profile = UserProfile(id(), request.username.trim(), request.name.trim(), Role.ALUMNO,
            request.career.trim(), request.email.trim(), request.status, mustChangePassword = true)
        commit(state.copy(accounts = state.accounts + StoredAccount(profile, Passwords.hash(request.initialPassword)),
            audit = state.audit + auditEntry(actor, profile.id, "ALUMNO", "ALTA", "Alta de ${profile.username} · ${profile.status}")))
        return profile
    }
    @Synchronized fun editStudent(token: String, studentId: String, request: StudentRequest): UserProfile {
        val actor = admin(token); validateStudent(request); uniqueUsername(request.username, studentId)
        val old = targetStudent(studentId)
        val updated = old.copy(name = request.name.trim(), username = request.username.trim(), career = request.career.trim(), email = request.email.trim())
        commit(state.withProfile(updated).copy(audit = state.audit + auditEntry(actor, studentId, "ALUMNO", "EDICION", "Datos actualizados · matrícula ${old.username} → ${updated.username}"))); return updated
    }
    @Synchronized fun setStatus(token: String, studentId: String, status: StudentStatus): UserProfile {
        val actor = admin(token)
        val old = targetStudent(studentId)
        if (old.status == status) return old
        val updated = old.copy(status = status)
        commit(state.withProfile(updated).copy(audit = state.audit + auditEntry(actor, studentId, "ALUMNO", "ESTATUS", "${old.status} → $status"),
            tokens = if (status == StudentStatus.INACTIVO) state.tokens.filter { it.claims.studentId != studentId } else state.tokens))
        return updated
    }
    @Synchronized fun deleteStudent(token: String, studentId: String) {
        val actor = admin(token); targetStudent(studentId)
        commit(state.copy(accounts = state.accounts.map { if (it.profile.id == studentId) it.copy(deleted = true) else it },
            biometricGrants = state.biometricGrants.filter { it.userId != studentId },
            tokens = state.tokens.filter { it.claims.studentId != studentId },
            audit = state.audit + auditEntry(actor, studentId, "ALUMNO", "BAJA", "Baja lógica; historial conservado")))
        sessions.entries.removeAll { it.value.userId == studentId }
    }
    private fun auditEntry(actor: UserProfile, targetId: String, type: String, action: String, details: String) =
        AuditEntry(id(), actor.id, actor.name, targetId, type, action, details, now())
    @Synchronized fun resetStudentPassword(token: String, studentId: String, request: ResetPasswordRequest): UserProfile {
        val actor = admin(token)
        val user = targetStudent(studentId); validatePassword(request.temporaryPassword)
        val updated = user.copy(mustChangePassword = true)
        commit(state.copy(accounts = state.accounts.map {
            if (it.profile.id == studentId) it.copy(profile = updated, passwordHash = Passwords.hash(request.temporaryPassword)) else it
        }, biometricGrants = state.biometricGrants.filter { it.userId != studentId },
            tokens = state.tokens.filter { it.claims.studentId != studentId },
            audit = state.audit + auditEntry(actor, studentId, "ALUMNO", "CONTRASENA", "Contraseña temporal restablecida; sesiones y accesos biométricos revocados")))
        sessions.entries.removeAll { it.value.userId == studentId }
        return updated
    }
    @Synchronized fun studentMovements(token: String, studentId: String): List<Movement> {
        admin(token); targetStudent(studentId)
        return state.movements.filter { it.studentId == studentId }.sortedByDescending { it.timestamp }.take(100)
    }
    @Synchronized fun audit(token: String, studentId: String? = null): List<AuditEntry> {
        admin(token)
        return state.audit.asReversed().filter { studentId == null || (it.targetType == "ALUMNO" && it.targetId == studentId) }.take(200)
    }
    @Synchronized fun adminProducts(token: String): List<Product> { admin(token); return catalog }
    private fun validateProduct(request: ProductRequest) {
        if (request.name.trim().length !in 2..80 || request.description.trim().length > 160 || request.priceCents !in 1L..100000L)
            fail(422, "INVALID_PRODUCT", "Revisa nombre, descripción y precio (hasta $1,000 MXN)")
    }
    @Synchronized fun createProduct(token: String, request: ProductRequest): Product {
        val actor = admin(token); validateProduct(request)
        val product = Product(id(), request.name.trim(), request.description.trim(), request.priceCents, request.available)
        commit(state.copy(products = state.products + product,
            audit = state.audit + auditEntry(actor, product.id, "PRODUCTO", "ALTA", "${product.name} · ${product.priceCents} centavos")))
        return product
    }
    @Synchronized fun editProduct(token: String, productId: String, request: ProductRequest): Product {
        val actor = admin(token); validateProduct(request)
        val old = state.products.find { it.id == productId && !it.archived } ?: fail(404, "NOT_FOUND", "Producto no encontrado")
        val updated = old.copy(name = request.name.trim(), description = request.description.trim(), priceCents = request.priceCents, available = request.available)
        commit(state.copy(products = state.products.map { if (it.id == productId) updated else it },
            audit = state.audit + auditEntry(actor, productId, "PRODUCTO", "EDICION", "${updated.name} · ${old.priceCents} → ${updated.priceCents} centavos · ${if (updated.available) "Disponible" else "Agotado"}")))
        return updated
    }
    @Synchronized fun archiveProduct(token: String, productId: String) {
        val actor = admin(token)
        val old = state.products.find { it.id == productId && !it.archived } ?: fail(404, "NOT_FOUND", "Producto no encontrado")
        commit(state.copy(products = state.products.map { if (it.id == productId) it.copy(archived = true, available = false) else it },
            audit = state.audit + auditEntry(actor, productId, "PRODUCTO", "BAJA", "${old.name} retirado del catálogo")))
    }
    private fun targetStudent(studentId: String) = account(studentId).profile.also {
        if (it.role != Role.ALUMNO) fail(403, "FORBIDDEN", "No se puede modificar una cuenta administrativa")
    }
    private fun uniqueUsername(username: String, exceptId: String? = null) {
        if (state.accounts.any { it.profile.id != exceptId && it.profile.username.equals(username.trim(), true) })
            fail(409, "DUPLICATE_USERNAME", "La matrícula ya está registrada")
    }
    private fun validateStudent(request: StudentRequest) {
        if (request.name.trim().length !in 3..100 || !request.username.trim().matches(Regex("[0-9]{6,15}")) ||
            request.career.trim().length !in 3..100 || request.email.length > 150 || !request.email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            fail(422, "INVALID_STUDENT", "Revisa nombre, matrícula numérica (6–15 dígitos), carrera y correo")
    }
    private fun validatePassword(password: String) {
        if (password.length !in 8..128 || password.none(Char::isLetter) || password.none(Char::isDigit))
            fail(422, "WEAK_PASSWORD", "Usa entre 8 y 128 caracteres, con letras y números")
    }
    private fun receiptKey(userId: String, key: String): String {
        if (key.isBlank() || key.length > 100) fail(422, "IDEMPOTENCY_REQUIRED", "Falta un identificador válido de operación")
        return "$userId:$key"
    }
    private fun replay(key: String, fingerprint: String): TransactionResult? = state.receipts.find { it.key == key }?.let {
        if (it.fingerprint != fingerprint) fail(409, "IDEMPOTENCY_CONFLICT", "El identificador ya se usó para otra operación")
        it.result
    }
    @Synchronized fun topup(token: String, request: TopupRequest, key: String): TransactionResult {
        val user = student(token)
        val receipt = receiptKey(user.id, key)
        val fingerprint = "topup:${request.amountCents}:${request.testCard}"
        replay(receipt, fingerprint)?.let { return it }
        if (request.amountCents !in 1000L..500000L) fail(422, "INVALID_AMOUNT", "Recarga entre $10 y $5,000 MXN")
        if (request.testCard != "4242424242424242") fail(422, "TEST_CARD_ONLY", "Usa únicamente la tarjeta ficticia 4242 4242 4242 4242")
        if (user.balanceCents > 10_000_000 - request.amountCents) fail(422, "BALANCE_LIMIT", "Se alcanzó el límite del monedero de demostración")
        val balance = user.balanceCents + request.amountCents
        val movement = Movement(id(), user.id, MovementKind.RECARGA, "Recarga simulada · •••• 4242", request.amountCents, balance, now(), "TARJETA_PRUEBA")
        val result = TransactionResult(movement, balance)
        commit(state.withProfile(user.copy(balanceCents = balance)).copy(movements = state.movements + movement,
            receipts = state.receipts + Receipt(receipt, fingerprint, result)))
        return result
    }
    @Synchronized fun simulateAccess(token: String): Movement {
        val user = student(token)
        val movement = accessMovement(user, "SIMULACION", "Acceso de prueba · entrada principal")
        commit(state.copy(movements = state.movements + movement)); return movement
    }
    private fun accessMovement(user: UserProfile, method: String, title: String) = Movement(
        id(), user.id, MovementKind.ACCESO, title, 0, user.balanceCents, now(), method)

    @Synchronized fun accessToken(token: String): TokenResponse = issue("ACCESS", student(token).id, null)
    private fun issue(purpose: String, studentId: String, orderId: String?): TokenResponse {
        val claims = TokenClaims(id(), purpose, studentId, orderId, now() + 45_000)
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(claims).toByteArray(Charsets.UTF_8))
        val signed = "$body.${sign(body)}"
        commit(state.copy(tokens = state.tokens.filter { it.claims.expiresAt > now() } + StoredToken(claims)))
        return TokenResponse(signed, claims.expiresAt, purpose)
    }
    private fun sign(body: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(signingKey, "HmacSHA256")); doFinal(body.toByteArray(Charsets.UTF_8)) })
    private fun claims(value: String, purpose: String? = null): TokenClaims {
        if (value.length > 2000) fail(422, "INVALID_TOKEN", "Código QR inválido")
        val parts = value.split('.')
        if (parts.size != 2 || !MessageDigest.isEqual(sign(parts[0]).toByteArray(), parts[1].toByteArray()))
            fail(422, "INVALID_TOKEN", "El código QR no es válido")
        val claims = try { json.decodeFromString<TokenClaims>(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)) }
            catch (_: Exception) { fail(422, "INVALID_TOKEN", "El código QR no es válido") }
        if (purpose != null && claims.purpose != purpose) fail(422, "WRONG_TOKEN_PURPOSE", "Este QR no corresponde a la operación")
        if (now() >= claims.expiresAt) fail(409, "TOKEN_EXPIRED", "El código QR venció. Genera uno nuevo")
        active(targetStudent(claims.studentId))
        val saved = state.tokens.find { it.claims == claims } ?: fail(422, "INVALID_TOKEN", "El código QR no está registrado")
        if (saved.consumed) fail(409, "TOKEN_USED", "Este código QR ya fue utilizado")
        return claims
    }
    @Synchronized fun inspectToken(token: String, request: RedeemRequest): QrReview {
        admin(token)
        val claims = claims(request.token)
        val user = targetStudent(claims.studentId)
        if (claims.purpose == "ACCESS") return QrReview("ACCESS", user.name, user.username, claims.expiresAt)
        if (claims.purpose != "PAYMENT") fail(422, "WRONG_TOKEN_PURPOSE", "Finalidad desconocida")
        val order = state.orders.find { it.id == claims.orderId && it.studentId == user.id } ?: fail(404, "NOT_FOUND", "Orden no encontrada")
        validateOrder(order)
        return QrReview("PAYMENT", user.name, user.username, claims.expiresAt, order.amountCents, order.lines)
    }
    private fun validateOrder(order: Order) {
        if (order.paid) fail(409, "ORDER_PAID", "La orden ya está pagada")
        if (now() >= order.createdAt + 300_000) fail(409, "ORDER_EXPIRED", "La orden venció")
        if (order.items.any { item -> state.products.none { it.id == item.productId && it.available && !it.archived } })
            fail(409, "PRODUCT_UNAVAILABLE", "Un producto del pedido ya no está disponible. Prepara un pedido nuevo")
    }
    @Synchronized fun redeemAccess(token: String, request: RedeemRequest): Movement {
        admin(token)
        val claims = claims(request.token, "ACCESS")
        val user = targetStudent(claims.studentId); active(user)
        val movement = accessMovement(user, "QR", "Acceso aprobado · QR de prueba")
        commit(state.copy(movements = state.movements + movement,
            tokens = state.tokens.map { if (it.claims.id == claims.id) it.copy(consumed = true) else it }))
        return movement
    }
    @Synchronized fun createOrder(token: String, request: OrderRequest): Order {
        val actor = current(token); ready(actor)
        val user = if (actor.role == Role.ADMINISTRADOR) targetStudent(request.studentId ?: fail(422, "STUDENT_REQUIRED", "Selecciona un alumno"))
            else student(token).also { if (request.studentId != null && request.studentId != it.id) fail(403, "FORBIDDEN", "Solo puedes comprar para tu propia cuenta") }
        active(user)
        if (request.items.isEmpty() || request.items.size > 20 || request.items.any { it.quantity !in 1..20 } || request.items.map { it.productId }.distinct().size != request.items.size)
            fail(422, "INVALID_ITEMS", "Selecciona productos y cantidades válidos")
        val lines = request.items.map { item ->
            val product = state.products.find { it.id == item.productId && !it.archived } ?: fail(422, "PRODUCT_NOT_FOUND", "Producto no disponible")
            if (!product.available) fail(409, "PRODUCT_UNAVAILABLE", "${product.name} está agotado")
            OrderLine(product.id, product.name, item.quantity, product.priceCents)
        }
        val amount = lines.sumOf { it.unitPriceCents * it.quantity }
        val order = Order(id(), user.id, request.items, amount, now(), lines = lines)
        commit(state.copy(orders = state.orders + order)); return order
    }
    @Synchronized fun approveOrder(token: String, orderId: String): TokenResponse {
        val user = student(token)
        val order = state.orders.find { it.id == orderId } ?: fail(404, "NOT_FOUND", "Orden no encontrada")
        if (order.studentId != user.id) fail(403, "FORBIDDEN", "La orden pertenece a otro alumno")
        validateOrder(order)
        if (user.balanceCents < order.amountCents) fail(409, "INSUFFICIENT_FUNDS", "Saldo insuficiente")
        return issue("PAYMENT", user.id, order.id)
    }
    @Synchronized fun pay(token: String, request: RedeemRequest, key: String): TransactionResult {
        val actor = current(token); active(actor)
        val receipt = receiptKey(actor.id, key)
        val fingerprint = "payment:${request.token}"
        replay(receipt, fingerprint)?.let { return it }
        val claims = claims(request.token, "PAYMENT")
        if (actor.role == Role.ALUMNO && actor.id != claims.studentId) fail(403, "FORBIDDEN", "La compra pertenece a otro alumno")
        val user = targetStudent(claims.studentId); active(user)
        val order = state.orders.find { it.id == claims.orderId && it.studentId == user.id } ?: fail(404, "NOT_FOUND", "Orden no encontrada")
        validateOrder(order)
        if (user.balanceCents < order.amountCents) fail(409, "INSUFFICIENT_FUNDS", "Saldo insuficiente")
        val balance = user.balanceCents - order.amountCents
        val title = if (order.lines.isNotEmpty()) order.lines.joinToString(" + ") { "${it.quantity}× ${it.name}" }
            else order.items.joinToString(" + ") { "${it.quantity}× ${state.products.find { product -> product.id == it.productId }?.name ?: it.productId}" }
        val movement = Movement(id(), user.id, MovementKind.COMPRA, title, -order.amountCents, balance, now(), if (actor.role == Role.ADMINISTRADOR) "QR_TERMINAL" else "AUTOTERMINAL_DEMO", order.id)
        val result = TransactionResult(movement, balance)
        commit(state.withProfile(user.copy(balanceCents = balance)).copy(
            movements = state.movements + movement,
            orders = state.orders.map { if (it.id == order.id) it.copy(paid = true) else it },
            tokens = state.tokens.map { if (it.claims.id == claims.id) it.copy(consumed = true) else it },
            receipts = state.receipts + Receipt(receipt, fingerprint, result),
        ))
        return result
    }
    @Synchronized fun purchaseReceipt(token: String, movementId: String): PurchaseReceipt {
        val actor = current(token); ready(actor)
        val movement = state.movements.find { it.id == movementId && it.kind != MovementKind.ACCESO }
            ?: fail(404, "NOT_FOUND", "Comprobante no encontrado")
        if (actor.role != Role.ADMINISTRADOR && actor.id != movement.studentId) fail(403, "FORBIDDEN", "Este comprobante pertenece a otro alumno")
        val owner = state.accounts.first { it.profile.id == movement.studentId }.profile
        val order = state.orders.find { it.id == movement.orderId }
        return PurchaseReceipt(movement, owner.name, owner.username, order?.lines.orEmpty())
    }
    private fun seed(): WalletState {
        val student = UserProfile("student-demo", DemoAccounts.STUDENT, "Alexandra Martínez López", Role.ALUMNO,
            "Desarrollo y Gestión de Software", "20240001@utez.edu.mx", balanceCents = 18500)
        val other = UserProfile("student-second", "20240002", "Diego Hernández Ruiz", Role.ALUMNO,
            "Mecatrónica", "20240002@utez.edu.mx", StudentStatus.INACTIVO)
        val administrator = UserProfile("admin-demo", DemoAccounts.ADMIN, "Control Escolar", Role.ADMINISTRADOR)
        return WalletState(accounts = listOf(
            StoredAccount(student, Passwords.hash(DemoAccounts.STUDENT_PASSWORD)),
            StoredAccount(other, Passwords.hash(DemoAccounts.STUDENT_PASSWORD)),
            StoredAccount(administrator, Passwords.hash(DemoAccounts.ADMIN_PASSWORD))), movements = listOf(
            Movement(id(), student.id, MovementKind.RECARGA, "Saldo inicial de demostración", 20000, 20000, now() - 86400000, "DEMO"),
            Movement(id(), student.id, MovementKind.COMPRA, "Agua natural", -1500, 18500, now() - 3600000, "DEMO")))
    }
}

private object Passwords {
    private const val ITERATIONS = 120_000
    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "$ITERATIONS:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS))}"
    }
    fun verify(password: String, encoded: String): Boolean {
        if (password.length > 128) return false
        val parts = encoded.split(':')
        return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[1]), parts[0].toInt()), Base64.getDecoder().decode(parts[2]))
    }
    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
