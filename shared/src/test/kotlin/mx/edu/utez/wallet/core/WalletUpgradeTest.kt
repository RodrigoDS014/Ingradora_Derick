package mx.edu.utez.wallet.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class WalletUpgradeTest {
    private fun WalletEngine.student() = login(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD))
    private fun WalletEngine.admin() = login(LoginRequest(DemoAccounts.ADMIN, DemoAccounts.ADMIN_PASSWORD))
    private fun expect(code: String, action: () -> Unit) =
        assertEquals(code, assertFailsWith<WalletException>(block = action).code)

    @Test fun biometricPermissionPersistsWithoutStoringItsSecret() {
        val store = MemoryStateStore()
        val first = WalletEngine(store)
        val student = first.student()
        val grant = first.createBiometricGrant(student.token)
        assertFalse(Json.encodeToString(store.load()).contains(grant.token))
        val reopened = WalletEngine(store)
        assertEquals(student.user.id, reopened.biometricLogin(RedeemRequest(grant.token)).user.id)
        expect("FORBIDDEN") { reopened.revokeBiometricGrant(reopened.admin().token, grant.id) }
        reopened.revokeBiometricGrant(reopened.student().token, grant.id)
        expect("BIOMETRIC_REVOKED") { reopened.biometricLogin(RedeemRequest(grant.token)) }
    }

    @Test fun resetRevokesSessionsAndBiometricsAndAuditsTheActor() {
        val engine = WalletEngine()
        val student = engine.student()
        val admin = engine.admin()
        val grant = engine.createBiometricGrant(student.token)
        val temporary = "NuevaTemporal2026!"
        assertTrue(engine.resetStudentPassword(admin.token, student.user.id, ResetPasswordRequest(temporary)).mustChangePassword)
        expect("UNAUTHORIZED") { engine.me(student.token) }
        expect("BIOMETRIC_REVOKED") { engine.biometricLogin(RedeemRequest(grant.token)) }
        val signedIn = engine.login(LoginRequest(student.user.username, temporary))
        expect("PASSWORD_CHANGE_REQUIRED") { engine.createBiometricGrant(signedIn.token) }
        val audit = engine.audit(admin.token, student.user.id).single()
        assertEquals(admin.user.id, audit.actorId)
        assertEquals("CONTRASENA", audit.action)
        assertFalse(audit.details.contains(temporary))
    }

    @Test fun reviewIsReadOnlyAndTheReceiptKeepsThePriceAgreed() {
        val engine = WalletEngine()
        val student = engine.student()
        val admin = engine.admin()
        val product = engine.catalog.first()
        val order = engine.createOrder(student.token, OrderRequest(listOf(OrderItem(product.id, 1))))
        val code = RedeemRequest(engine.approveOrder(student.token, order.id).token)
        engine.editProduct(admin.token, product.id, ProductRequest("Nuevo nombre", "", 8900))
        expect("FORBIDDEN") { engine.inspectToken(student.token, code) }
        assertEquals(product.priceCents, engine.inspectToken(admin.token, code).amountCents)
        assertEquals(product.name, engine.inspectToken(admin.token, code).lines.single().name)
        val payment = engine.pay(admin.token, code, "sample-purchase")
        engine.archiveProduct(admin.token, product.id)
        assertEquals(-product.priceCents, payment.movement.amountCents)
        assertEquals(product.name, engine.purchaseReceipt(student.token, payment.movement.id).lines.single().name)
        expect("TOKEN_USED") { engine.inspectToken(admin.token, code) }
    }

    @Test fun unavailableItemsAreRejectedBeforeChangingBalances() {
        val engine = WalletEngine()
        val student = engine.student()
        val admin = engine.admin()
        val product = engine.catalog.first()
        val request = OrderRequest(listOf(OrderItem(product.id, 1)))
        val order = engine.createOrder(student.token, request)
        val code = RedeemRequest(engine.approveOrder(student.token, order.id).token)
        engine.editProduct(admin.token, product.id, ProductRequest(product.name, "", product.priceCents, false))
        expect("PRODUCT_UNAVAILABLE") { engine.createOrder(student.token, request) }
        expect("PRODUCT_UNAVAILABLE") { engine.pay(admin.token, code, "unavailable") }
        assertEquals(student.user.balanceCents, engine.me(student.token).balanceCents)
        assertEquals(engine.movements(student.token), engine.studentMovements(admin.token, student.user.id))
        expect("FORBIDDEN") { engine.studentMovements(student.token, student.user.id) }
    }

    @Test fun anInactiveStudentsQrCannotBeReusedAfterReactivation() {
        val engine = WalletEngine()
        val student = engine.student()
        val admin = engine.admin()
        val code = RedeemRequest(engine.accessToken(student.token).token)
        engine.setStatus(admin.token, student.user.id, StudentStatus.INACTIVO)
        expect("STUDENT_INACTIVE") { engine.inspectToken(admin.token, code) }
        engine.setStatus(admin.token, student.user.id, StudentStatus.ACTIVO)
        expect("INVALID_TOKEN") { engine.redeemAccess(admin.token, code) }
        assertEquals(listOf("INACTIVO → ACTIVO", "ACTIVO → INACTIVO"), engine.audit(admin.token, student.user.id).map { it.details })
    }
}
