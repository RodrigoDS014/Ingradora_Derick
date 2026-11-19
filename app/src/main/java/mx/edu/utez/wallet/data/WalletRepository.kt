package mx.edu.utez.wallet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.edu.utez.wallet.core.*
import java.net.HttpURLConnection
import java.net.URI

/** Explicit adapters: a network failure never creates a local financial transaction. */
class WalletRepository(private val demoEngine: WalletEngine, private val baseUrl: String? = null) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var token = ""
    val isDemo get() = baseUrl == null
    private suspend inline fun <reified T> operation(method: String, path: String, body: String? = null,
        key: String? = null, crossinline local: () -> T): T = withContext(Dispatchers.IO) {
        if (isDemo) return@withContext local()
        val endpoint = requireNotNull(baseUrl).trimEnd('/')
        val uri = URI(endpoint)
        require(uri.scheme in listOf("http", "https") && uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Usa una dirección como http://10.0.2.2:8080"
        }
        val connection = URI("$endpoint/api/v1$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 7000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
            key?.let { connection.setRequestProperty("Idempotency-Key", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val error = runCatching { json.decodeFromString<ApiError>(errorText) }.getOrNull()
                throw WalletException(status, error?.code ?: "HTTP_ERROR", error?.message ?: "El servidor no pudo procesar la operación ($status)")
            }
            if (T::class == Unit::class) @Suppress("UNCHECKED_CAST") (Unit as T)
            else json.decodeFromString<T>(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }
    suspend fun login(request: LoginRequest): UserProfile {
        val result = operation("POST", "/auth/login", json.encodeToString(request)) { demoEngine.login(request) }
        token = result.token; return result.user
    }
    suspend fun biometricLogin(value: String): UserProfile {
        val request = RedeemRequest(value)
        val result = operation("POST", "/auth/biometric/login", json.encodeToString(request)) { demoEngine.biometricLogin(request) }
        token = result.token; return result.user
    }
    suspend fun biometricGrant(): BiometricGrantResponse = operation("POST", "/auth/biometric") { demoEngine.createBiometricGrant(token) }
    suspend fun revokeBiometric(id: String): Unit = operation("DELETE", "/auth/biometric/$id") { demoEngine.revokeBiometricGrant(token, id) }
    suspend fun logout() {
        try { operation("POST", "/auth/logout") { demoEngine.logout(token) } } finally { token = "" }
    }
    suspend fun me(): UserProfile = operation("GET", "/me") { demoEngine.me(token) }
    suspend fun movements(): List<Movement> = operation("GET", "/me/movements") { demoEngine.movements(token) }
    suspend fun catalog(): List<Product> = operation("GET", "/catalog") { demoEngine.catalog }
    suspend fun students(): List<UserProfile> = operation("GET", "/admin/students") { demoEngine.students(token) }
    suspend fun changePassword(request: PasswordRequest): UserProfile = operation("POST", "/auth/password", json.encodeToString(request)) { demoEngine.changePassword(token, request) }
    suspend fun photo(value: String): UserProfile = operation("PUT", "/me/photo", json.encodeToString(PhotoRequest(value))) { demoEngine.updatePhoto(token, PhotoRequest(value)) }
    suspend fun createStudent(request: StudentRequest): UserProfile = operation("POST", "/admin/students", json.encodeToString(request)) { demoEngine.createStudent(token, request) }
    suspend fun editStudent(id: String, request: StudentRequest): UserProfile = operation("PUT", "/admin/students/$id", json.encodeToString(request)) { demoEngine.editStudent(token, id, request) }
    suspend fun status(id: String, status: StudentStatus): UserProfile = operation("PATCH", "/admin/students/$id/status", json.encodeToString(StatusRequest(status))) { demoEngine.setStatus(token, id, status) }
    suspend fun delete(id: String): Unit = operation("DELETE", "/admin/students/$id") { demoEngine.deleteStudent(token, id) }
    suspend fun topup(amountCents: Long, key: String): TransactionResult = operation("POST", "/wallet/topups", json.encodeToString(TopupRequest(amountCents)), key) { demoEngine.topup(token, TopupRequest(amountCents), key) }
    suspend fun simulateAccess(): Movement = operation("POST", "/access/simulate") { demoEngine.simulateAccess(token) }
    suspend fun accessToken(): TokenResponse = operation("POST", "/tokens/access") { demoEngine.accessToken(token) }
    suspend fun approve(orderId: String): TokenResponse = operation("POST", "/orders/$orderId/approve") { demoEngine.approveOrder(token, orderId) }
    suspend fun createOrder(items: List<OrderItem>, studentId: String? = null): Order {
        val request = OrderRequest(items, studentId)
        return operation("POST", "/orders", json.encodeToString(request)) { demoEngine.createOrder(token, request) }
    }
    suspend fun pay(value: String, key: String): TransactionResult = operation("POST", "/payments", json.encodeToString(RedeemRequest(value)), key) { demoEngine.pay(token, RedeemRequest(value), key) }
    suspend fun redeemAccess(value: String): Movement = operation("POST", "/tokens/access/redeem", json.encodeToString(RedeemRequest(value))) { demoEngine.redeemAccess(token, RedeemRequest(value)) }
    suspend fun inspect(value: String): QrReview = operation("POST", "/tokens/inspect", json.encodeToString(RedeemRequest(value))) { demoEngine.inspectToken(token, RedeemRequest(value)) }
    suspend fun receipt(id: String): PurchaseReceipt = operation("GET", "/receipts/$id") { demoEngine.purchaseReceipt(token, id) }
    suspend fun studentMovements(id: String): List<Movement> = operation("GET", "/admin/students/$id/movements") { demoEngine.studentMovements(token, id) }
    suspend fun audit(id: String? = null): List<AuditEntry> = operation("GET", "/admin/audit" + if(id == null) "" else "?studentId=$id") { demoEngine.audit(token, id) }
    suspend fun resetPassword(id: String, password: String): UserProfile = operation("POST", "/admin/students/$id/password", json.encodeToString(ResetPasswordRequest(password))) { demoEngine.resetStudentPassword(token, id, ResetPasswordRequest(password)) }
    suspend fun saveProduct(id: String?, request: ProductRequest): Product = if (id == null)
        operation("POST", "/admin/products", json.encodeToString(request)) { demoEngine.createProduct(token, request) }
        else operation("PUT", "/admin/products/$id", json.encodeToString(request)) { demoEngine.editProduct(token, id, request) }
    suspend fun archiveProduct(id: String): Unit = operation("DELETE", "/admin/products/$id") { demoEngine.archiveProduct(token, id) }
}
