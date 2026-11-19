package mx.edu.utez.wallet.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.edu.utez.wallet.core.*
import java.nio.file.Files
import kotlin.test.*

class ApiTest {
    @Test fun `HTTP requires authentication and protects administrative routes`() = testApplication {
        application { walletModule() }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD)))
        }
        val session = Json.decodeFromString<SessionResponse>(login.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/students") { bearerAuth(session.token) }.status)
    }
    @Test fun `HTTP topup requires idempotency and rejects malformed JSON`() = testApplication {
        application { walletModule() }
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD)))
        }
        val token = Json.decodeFromString<SessionResponse>(login.bodyAsText()).token
        val missingKey = client.post("/api/v1/wallet/topups") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(TopupRequest(5000)))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, missingKey.status)
        assertEquals("IDEMPOTENCY_REQUIRED", Json.decodeFromString<ApiError>(missingKey.bodyAsText()).code)
        val malformed = client.post("/api/v1/auth/login") { contentType(ContentType.Application.Json); setBody("{") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }
    @Test fun `HTTP purchase retry returns same receipt and rejects reused QR`() = testApplication {
        application { walletModule() }
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json); setBody(Json.encodeToString(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD)))
        }
        val token = Json.decodeFromString<SessionResponse>(login.bodyAsText()).token
        val orderResponse = client.post("/api/v1/orders") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OrderRequest(listOf(OrderItem("cafe", 1)))))
        }
        assertEquals(HttpStatusCode.Created, orderResponse.status)
        val order = Json.decodeFromString<Order>(orderResponse.bodyAsText())
        val qr = Json.decodeFromString<TokenResponse>(client.post("/api/v1/orders/${order.id}/approve") { bearerAuth(token) }.bodyAsText())
        suspend fun pay(key: String) = client.post("/api/v1/payments") {
            bearerAuth(token); contentType(ContentType.Application.Json); header("Idempotency-Key", key)
            setBody(Json.encodeToString(RedeemRequest(qr.token)))
        }
        val first = pay("http-pay")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(first.bodyAsText(), pay("http-pay").bodyAsText())
        assertEquals(HttpStatusCode.Conflict, pay("http-other-pay").status)
    }
    @Test fun `file storage survives reopening and refuses a second writer`() {
        val directory = Files.createTempDirectory("utez-wallet-test")
        val path = directory.resolve("wallet.json")
        try {
            FileStateStore(path).use { store ->
                val engine = WalletEngine(store)
                val token = engine.login(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD)).token
                engine.topup(token, TopupRequest(5000), "file-test")
                assertFails { FileStateStore(path) }
            }
            FileStateStore(path).use { store ->
                val engine = WalletEngine(store)
                val token = engine.login(LoginRequest(DemoAccounts.STUDENT, DemoAccounts.STUDENT_PASSWORD)).token
                assertEquals(23500, engine.me(token).balanceCents)
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory.resolve("wallet.json.lock"))
            Files.deleteIfExists(directory)
        }
    }
}
