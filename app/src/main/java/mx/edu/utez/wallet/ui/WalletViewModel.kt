package mx.edu.utez.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.edu.utez.wallet.core.*
import mx.edu.utez.wallet.data.LocalStateStore
import mx.edu.utez.wallet.data.WalletRepository
import mx.edu.utez.wallet.security.BiometricBinding
import java.util.UUID

data class WalletUiState(
    val user: UserProfile? = null, val busy: Boolean = false, val message: String? = null,
    val demo: Boolean = true, val movements: List<Movement> = emptyList(),
    val students: List<UserProfile> = emptyList(), val products: List<Product> = emptyList(),
    val qr: TokenResponse? = null, val order: Order? = null,
    val baseUrl: String = "http://10.0.2.2:8080", val terminalCode: String = "", val review: QrReview? = null,
    val receipt: PurchaseReceipt? = null, val detailStudent: UserProfile? = null,
    val detailMovements: List<Movement> = emptyList(), val detailAudit: List<AuditEntry> = emptyList(),
    val biometricInvalid: Boolean = false,
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val engine by lazy { WalletEngine(LocalStateStore(application)) }
    private var repository: WalletRepository? = null
    private val mutable = MutableStateFlow(WalletUiState())
    val state = mutable.asStateFlow()
    // Retained after network failure, so retrying a top-up uses the same idempotency key.
    private var topupAttempt: Pair<Long, String>? = null
    private var paymentAttempt: Pair<String, String>? = null
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = when (error) {
                    is WalletException -> error.message
                    is java.io.IOException -> "No se pudo conectar. Revisa el servidor y la dirección; tus datos no se cambiaron a modo demo."
                    else -> error.message ?: "No se pudo completar la operación"
                }
                mutable.update { it.copy(message = message) }
                if (error is WalletException && error.code == "STUDENT_INACTIVE") {
                    mutable.update { it.copy(user = it.user?.takeIf { profile -> profile.role == Role.ALUMNO }
                        ?.copy(status = StudentStatus.INACTIVO) ?: it.user) }
                }
                if (error is WalletException && error.statusCode == 401) {
                    repository = null
                    mutable.update { WalletUiState(message = message, demo = it.demo, baseUrl = it.baseUrl, busy = true,
                        biometricInvalid = error.code.startsWith("BIOMETRIC_")) }
                }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun repo() = requireNotNull(repository)
    private suspend fun refreshData() {
        val user = repo().me()
        mutable.update { it.copy(user = user) }
        if (user.mustChangePassword) return
        val products = repo().catalog()
        if (user.role == Role.ALUMNO) {
            val movements = repo().movements()
            mutable.update { it.copy(products = products, movements = movements) }
        } else {
            val students = repo().students()
            mutable.update { it.copy(products = products, students = students) }
        }
    }
    fun login(username: String, password: String, demo: Boolean, baseUrl: String) = action {
        val next = withContext(Dispatchers.IO) { WalletRepository(engine, if (demo) null else baseUrl.trim()) }
        val user = next.login(LoginRequest(username, password))
        repository = next
        mutable.update { WalletUiState(user = user, demo = demo, baseUrl = baseUrl.trim().trimEnd('/'), busy = true) }
        refreshData()
    }
    fun biometricLogin(binding: BiometricBinding) = action {
        val next = withContext(Dispatchers.IO) { WalletRepository(engine, if (binding.demo) null else binding.serverUrl) }
        val user = next.biometricLogin(binding.token)
        if (user.id != binding.userId) throw WalletException(401, "BIOMETRIC_REVOKED", "Inicia sesión con tu contraseña")
        repository = next
        mutable.update { WalletUiState(user = user, demo = binding.demo, baseUrl = binding.serverUrl, busy = true) }
        refreshData()
    }
    fun prepareBiometric(ready: (BiometricBinding) -> Unit) = action {
        val grant = repo().biometricGrant()
        val value = mutable.value; val user = requireNotNull(value.user)
        ready(BiometricBinding(grant.id, grant.token, user.id, user.username, value.demo, value.baseUrl, grant.expiresAt))
    }
    fun revokeBiometric(id: String) = action { repo().revokeBiometric(id) }
    fun clearBiometricInvalid() { mutable.update { it.copy(biometricInvalid = false) } }
    fun logout(forgetGrantId: String? = null, done: () -> Unit = {}) = action {
        try {
            if (forgetGrantId != null) repository?.revokeBiometric(forgetGrantId)
            repository?.logout()
        } catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error }
        repository = null; topupAttempt = null; paymentAttempt = null
        mutable.update { WalletUiState(demo = it.demo, baseUrl = it.baseUrl, busy = true) }
        done()
    }
    fun refresh() = action { refreshData() }
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun notify(message: String) { mutable.update { it.copy(message = message) } }
    fun dismissQr() { mutable.update { it.copy(qr = null) } }
    fun password(current: String, new: String, done: () -> Unit) = action {
        repo().changePassword(PasswordRequest(current, new)); refreshData()
        mutable.update { it.copy(message = "Contraseña actualizada") }; done()
    }
    fun photo(value: String) = action { repo().photo(value); refreshData(); mutable.update { it.copy(message = "Fotografía actualizada") } }
    fun topup(amount: Long, done: () -> Unit) = action {
        val attempt = topupAttempt?.takeIf { it.first == amount } ?: (amount to UUID.randomUUID().toString()).also { topupAttempt = it }
        val result = repo().topup(amount, attempt.second)
        topupAttempt = null
        mutable.update { it.copy(user = it.user?.copy(balanceCents = result.balanceCents), message = "Recarga simulada aprobada") }
        done(); refreshData()
    }
    fun simulateAccess() = action { repo().simulateAccess(); refreshData(); mutable.update { it.copy(message = "Acceso de prueba permitido · sin lectura NFC") } }
    fun accessQr() = action { val qr = repo().accessToken(); mutable.update { it.copy(qr = qr) } }
    fun order(items: List<OrderItem>, studentId: String? = null) = action {
        val order = repo().createOrder(items, studentId)
        mutable.update { it.copy(order = order, message = "Orden creada. Revisa el importe antes de autorizar") }
    }
    fun approve(orderId: String) = action {
        val qr = repo().approve(orderId.trim()); mutable.update { it.copy(qr = qr) }
    }
    fun pay(value: String, done: () -> Unit = {}) = action {
        val attempt = paymentAttempt?.takeIf { it.first == value } ?: (value to UUID.randomUUID().toString()).also { paymentAttempt = it }
        val result = repo().pay(value, attempt.second)
        paymentAttempt = null
        mutable.update { it.copy(qr = null, order = null, review = null, terminalCode = "", message = "Compra simulada aprobada",
            user = if(it.user?.role == Role.ALUMNO) it.user.copy(balanceCents = result.balanceCents) else it.user) }
        done()
        try {
            val receipt = repo().receipt(result.movement.id)
            mutable.update { it.copy(receipt = receipt) }
            refreshData()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            mutable.update { it.copy(message = "Compra aprobada. Consulta el comprobante en el historial al recuperar la conexión.") }
        }
    }
    fun redeemAccess(value: String) = action {
        repo().redeemAccess(value.trim()); mutable.update { it.copy(review = null, terminalCode = "", message = "Acceso por QR aprobado") }
    }
    fun saveStudent(existing: UserProfile?, request: StudentRequest, done: () -> Unit) = action {
        if (existing == null) repo().createStudent(request) else repo().editStudent(existing.id, request)
        refreshData(); done(); mutable.update { it.copy(message = "Alumno guardado") }
    }
    fun status(user: UserProfile) = action {
        repo().status(user.id, if (user.status == StudentStatus.ACTIVO) StudentStatus.INACTIVO else StudentStatus.ACTIVO)
        refreshData(); mutable.update { it.copy(message = "Estatus actualizado") }
    }
    fun delete(user: UserProfile, done: () -> Unit) = action {
        repo().delete(user.id); refreshData(); done(); mutable.update { it.copy(message = "Alumno dado de baja; historial conservado") }
    }
    fun setTerminalCode(value: String) { mutable.update { it.copy(terminalCode = value, review = null) } }
    fun inspect(value: String) = action {
        val code = value.trim()
        mutable.update { it.copy(terminalCode = code, review = null) }
        val review = repo().inspect(code)
        mutable.update { it.copy(review = review) }
    }
    fun receipt(id: String) = action { val receipt = repo().receipt(id); mutable.update { it.copy(receipt = receipt) } }
    fun dismissReceipt() { mutable.update { it.copy(receipt = null) } }
    fun studentDetail(user: UserProfile) = action {
        val history = repo().studentMovements(user.id)
        val audit = repo().audit(user.id)
        mutable.update { it.copy(detailStudent = user, detailMovements = history, detailAudit = audit) }
    }
    fun dismissStudentDetail() { mutable.update { it.copy(detailStudent = null, detailMovements = emptyList(), detailAudit = emptyList()) } }
    fun resetPassword(user: UserProfile, temporary: String, done: () -> Unit) = action {
        repo().resetPassword(user.id, temporary); refreshData(); done()
        mutable.update { it.copy(message = "Contraseña temporal asignada. Las sesiones y accesos biométricos anteriores quedaron revocados.") }
    }
    fun saveProduct(product: Product?, request: ProductRequest, done: () -> Unit) = action {
        repo().saveProduct(product?.id, request); refreshData(); done()
        mutable.update { it.copy(message = "Producto guardado") }
    }
    fun productAvailability(product: Product) = action {
        repo().saveProduct(product.id, ProductRequest(product.name, product.description, product.priceCents, !product.available))
        refreshData()
    }
    fun archiveProduct(product: Product, done: () -> Unit) = action {
        repo().archiveProduct(product.id); refreshData(); done(); mutable.update { it.copy(message = "Producto retirado del catálogo; comprobantes conservados") }
    }
}
