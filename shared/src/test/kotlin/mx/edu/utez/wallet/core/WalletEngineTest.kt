package mx.edu.utez.wallet.core

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WalletEngineTest {
    private fun student(engine: WalletEngine) = engine.login(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD)).token
    private fun admin(engine: WalletEngine) = engine.login(LoginRequest(DemoAccounts.ADMIN, DemoAccounts.ADMIN_PASSWORD)).token
    private fun payment(engine: WalletEngine, token: String, quantity: Int = 1): TokenResponse {
        val order = engine.createOrder(token, OrderRequest(listOf(OrderItem("ensalada", quantity))))
        return engine.approveOrder(token, order.id)
    }
    private fun error(code: String, block: () -> Unit) {
        assertEquals(code, assertFailsWith<WalletException>(block = block).code)
    }

    @Test fun `wrong password cannot authenticate`() {
        error("INVALID_CREDENTIALS") { WalletEngine().login(LoginRequest(DemoAccounts.STUDENT, "incorrecta")) }
    }
    @Test fun `student cannot administer accounts`() {
        val engine = WalletEngine()
        error("FORBIDDEN") { engine.students(student(engine)) }
    }
    @Test fun `new accounts must replace their temporary password`() {
        val engine = WalletEngine()
        val account = engine.createStudent(admin(engine), StudentRequest("María Pérez López", "20245555", "Mecatrónica", "maria@utez.edu.mx", "Temporal123!"))
        val token = engine.login(LoginRequest(account.username, "Temporal123!")).token
        error("PASSWORD_CHANGE_REQUIRED") { engine.topup(token, TopupRequest(5000), "before-password") }
        engine.changePassword(token, PasswordRequest("Temporal123!", "NuevaSegura123!"))
        assertEquals(5000, engine.topup(token, TopupRequest(5000), "after-password").balanceCents)
    }
    @Test fun `password change invalidates other sessions`() {
        val engine = WalletEngine()
        val first = student(engine); val second = student(engine)
        engine.changePassword(first, PasswordRequest(DemoAccounts.STUDENT_PASSWORD, "Cambiada123!"))
        error("UNAUTHORIZED") { engine.me(second) }
        assertEquals(DemoAccounts.STUDENT, engine.me(first).username)
    }
    @Test fun `deactivation revokes previously issued payment and access tokens`() {
        val engine = WalletEngine()
        val s = student(engine); val a = admin(engine)
        val payment = payment(engine, s); val access = engine.accessToken(s)
        engine.setStatus(a, engine.me(s).id, StudentStatus.INACTIVO)
        error("STUDENT_INACTIVE") { engine.pay(a, RedeemRequest(payment.token), "inactive-pay") }
        error("STUDENT_INACTIVE") { engine.redeemAccess(a, RedeemRequest(access.token)) }
        error("STUDENT_INACTIVE") { engine.simulateAccess(s) }
        error("STUDENT_INACTIVE") { engine.topup(s, TopupRequest(5000), "inactive-topup") }
        assertTrue(engine.movements(s).isNotEmpty())
        assertEquals(18500, engine.me(s).balanceCents)
    }
    @Test fun `retries credit a topup once and reject changes to its amount`() {
        val engine = WalletEngine(); val token = student(engine)
        val first = engine.topup(token, TopupRequest(5000), "same-operation")
        assertEquals(first, engine.topup(token, TopupRequest(5000), "same-operation"))
        error("IDEMPOTENCY_CONFLICT") { engine.topup(token, TopupRequest(6000), "same-operation") }
        assertEquals(23500, engine.me(token).balanceCents)
        assertEquals(1, engine.movements(token).count { it.id == first.movement.id })
    }
    @Test fun `concurrent retries credit the wallet exactly once`() {
        val engine = WalletEngine(); val token = student(engine)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = executor.invokeAll((1..16).map { Callable { engine.topup(token, TopupRequest(1000), "concurrent-key") } })
            assertEquals(1, futures.map { it.get(10, TimeUnit.SECONDS).movement.id }.distinct().size)
            assertEquals(19500, engine.me(token).balanceCents)
        } finally { executor.shutdownNow() }
    }
    @Test fun `real card numbers and out of range topups are rejected`() {
        val engine = WalletEngine(); val token = student(engine)
        error("TEST_CARD_ONLY") { engine.topup(token, TopupRequest(1000, "1234567890123456"), "real-card") }
        error("INVALID_AMOUNT") { engine.topup(token, TopupRequest(-1), "negative") }
        error("INVALID_AMOUNT") { engine.topup(token, TopupRequest(Long.MAX_VALUE), "overflow") }
    }
    @Test fun `access QR cannot pay and payment QR cannot grant access`() {
        val engine = WalletEngine(); val s = student(engine); val a = admin(engine)
        error("WRONG_TOKEN_PURPOSE") { engine.pay(s, RedeemRequest(engine.accessToken(s).token), "wrong-purpose") }
        error("WRONG_TOKEN_PURPOSE") { engine.redeemAccess(a, RedeemRequest(payment(engine, s).token)) }
    }
    @Test fun `tampered tokens are rejected`() {
        val engine = WalletEngine(); val s = student(engine)
        val code = engine.accessToken(s).token
        error("INVALID_TOKEN") { engine.redeemAccess(admin(engine), RedeemRequest("X" + code.drop(1))) }
    }
    @Test fun `token expires according to the server clock`() {
        val clock = MutableClock(); val engine = WalletEngine(clock = clock)
        val s = student(engine); val a = admin(engine); val code = engine.accessToken(s)
        clock.millisValue += 45_000
        error("TOKEN_EXPIRED") { engine.redeemAccess(a, RedeemRequest(code.token)) }
    }
    @Test fun `access token grants a single access`() {
        val engine = WalletEngine(); val s = student(engine); val a = admin(engine)
        val code = engine.accessToken(s)
        engine.redeemAccess(a, RedeemRequest(code.token))
        error("TOKEN_USED") { engine.redeemAccess(a, RedeemRequest(code.token)) }
    }
    @Test fun `paid order cannot be paid with a second token`() {
        val engine = WalletEngine(); val s = student(engine)
        val order = engine.createOrder(s, OrderRequest(listOf(OrderItem("cafe", 1))))
        val first = engine.approveOrder(s, order.id); val second = engine.approveOrder(s, order.id)
        val result = engine.pay(s, RedeemRequest(first.token), "pay-one")
        assertEquals(result, engine.pay(s, RedeemRequest(first.token), "pay-one"))
        error("ORDER_PAID") { engine.pay(s, RedeemRequest(second.token), "pay-two") }
        assertEquals(16000, engine.me(s).balanceCents)
    }
    @Test fun `competing purchases never make the balance negative`() {
        val engine = WalletEngine(); val s = student(engine)
        val first = payment(engine, s, 2); val second = payment(engine, s, 2)
        val start = CountDownLatch(1); val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).mapIndexed { index, value -> executor.submit(Callable {
                start.await(5, TimeUnit.SECONDS)
                runCatching { engine.pay(s, RedeemRequest(value.token), "purchase-$index") }
            }) }
            start.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals("INSUFFICIENT_FUNDS", (results.first { it.isFailure }.exceptionOrNull() as WalletException).code)
            assertEquals(6500, engine.me(s).balanceCents)
        } finally { executor.shutdownNow() }
    }
    @Test fun `failed persistence does not publish a partial purchase`() {
        val store = FailingStore(); val engine = WalletEngine(store); val s = student(engine)
        val code = payment(engine, s)
        store.failWrites = true
        assertFailsWith<IllegalStateException> { engine.pay(s, RedeemRequest(code.token), "storage-fail") }
        assertEquals(18500, engine.me(s).balanceCents)
        store.failWrites = false
        assertEquals(12500, engine.pay(s, RedeemRequest(code.token), "storage-fail").balanceCents)
    }
    @Test fun `deleted accounts cannot authenticate and movements are retained`() {
        val store = MemoryStateStore(); val engine = WalletEngine(store); val s = student(engine); val a = admin(engine)
        val id = engine.me(s).id
        engine.deleteStudent(a, id)
        error("UNAUTHORIZED") { engine.me(s) }
        error("INVALID_CREDENTIALS") { student(engine) }
        assertTrue(store.load()!!.movements.any { it.studentId == id })
    }
    @Test fun `persisted balances survive engine recreation`() {
        val store = MemoryStateStore(); val first = WalletEngine(store); val s = student(first)
        first.topup(s, TopupRequest(5000), "persist")
        val second = WalletEngine(store)
        assertEquals(23500, second.me(student(second)).balanceCents)
    }
    @Test fun `server uses catalog prices and rejects malformed carts`() {
        val engine = WalletEngine(); val s = student(engine)
        assertEquals(7000, engine.createOrder(s, OrderRequest(listOf(OrderItem("cafe", 1), OrderItem("sandwich", 1)))).amountCents)
        error("INVALID_ITEMS") { engine.createOrder(s, OrderRequest(listOf(OrderItem("cafe", -1)))) }
        error("PRODUCT_NOT_FOUND") { engine.createOrder(s, OrderRequest(listOf(OrderItem("unknown", 1)))) }
        error("INVALID_ITEMS") { engine.createOrder(s, OrderRequest(listOf(OrderItem("cafe", 1), OrderItem("cafe", 1)))) }
    }
    private class MutableClock(var millisValue: Long = Instant.parse("2026-10-22T12:00:00Z").toEpochMilli()) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millisValue)
        override fun millis(): Long = millisValue
    }
    private class FailingStore : StateStore {
        var failWrites = false
        private var value: WalletState? = null
        override fun load() = value
        override fun save(state: WalletState) { check(!failWrites) { "Simulated disk failure" }; value = state }
    }
}
