package mx.edu.utez.wallet.core

import kotlinx.serialization.Serializable

@Serializable enum class Role { ALUMNO, ADMINISTRADOR }
@Serializable enum class StudentStatus { ACTIVO, INACTIVO }
@Serializable enum class MovementKind { RECARGA, COMPRA, ACCESO }
@Serializable data class UserProfile(
    val id: String, val username: String, val name: String, val role: Role,
    val career: String = "", val email: String = "",
    val status: StudentStatus = StudentStatus.ACTIVO, val balanceCents: Long = 0,
    val mustChangePassword: Boolean = false, val photoBase64: String? = null,
)
@Serializable data class SessionResponse(val token: String, val user: UserProfile)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class PasswordRequest(val currentPassword: String, val newPassword: String)
@Serializable data class PhotoRequest(val photoBase64: String)
@Serializable data class StudentRequest(
    val name: String, val username: String, val career: String, val email: String,
    val initialPassword: String = "", val status: StudentStatus = StudentStatus.ACTIVO,
)
@Serializable data class StatusRequest(val status: StudentStatus)
@Serializable data class TopupRequest(val amountCents: Long, val testCard: String = "4242424242424242")
@Serializable data class Product(val id: String, val name: String, val description: String, val priceCents: Long,
    val available: Boolean = true, val archived: Boolean = false)
@Serializable data class ProductRequest(val name: String, val description: String, val priceCents: Long, val available: Boolean = true)
@Serializable data class OrderItem(val productId: String, val quantity: Int)
@Serializable data class OrderRequest(val items: List<OrderItem>, val studentId: String? = null)
@Serializable data class Order(
    val id: String, val studentId: String, val items: List<OrderItem>, val amountCents: Long,
    val createdAt: Long, val paid: Boolean = false,
    val lines: List<OrderLine> = emptyList(),
)
@Serializable data class OrderLine(val productId: String, val name: String, val quantity: Int, val unitPriceCents: Long)
@Serializable data class Movement(
    val id: String, val studentId: String, val kind: MovementKind,
    val title: String, val amountCents: Long, val balanceAfterCents: Long,
    val timestamp: Long, val method: String,
    val orderId: String? = null,
)
@Serializable data class TransactionResult(val movement: Movement, val balanceCents: Long)
@Serializable data class TokenResponse(val token: String, val expiresAt: Long, val purpose: String)
@Serializable data class RedeemRequest(val token: String)
@Serializable data class ResetPasswordRequest(val temporaryPassword: String)
@Serializable data class BiometricGrantResponse(val id: String, val token: String, val expiresAt: Long)
@Serializable data class AuditEntry(val id: String, val actorId: String, val actorName: String,
    val targetId: String, val targetType: String, val action: String, val details: String, val timestamp: Long)
@Serializable data class QrReview(val purpose: String, val studentName: String, val username: String,
    val expiresAt: Long, val amountCents: Long = 0, val lines: List<OrderLine> = emptyList())
@Serializable data class PurchaseReceipt(val movement: Movement, val studentName: String, val username: String,
    val lines: List<OrderLine> = emptyList(), val currency: String = "MXN", val simulated: Boolean = true)
@Serializable data class ApiError(val code: String, val message: String)
class WalletException(val statusCode: Int, val code: String, override val message: String) : RuntimeException(message)

object DemoAccounts {
    const val STUDENT = "20240001"
    const val STUDENT_PASSWORD = "Alumno123!"
    const val ADMIN = "admin"
    const val ADMIN_PASSWORD = "Admin123!"
}
