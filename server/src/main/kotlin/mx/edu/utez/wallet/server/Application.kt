package mx.edu.utez.wallet.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mx.edu.utez.wallet.core.*
import java.nio.file.Path

fun main() {
    val storage = FileStateStore(Path.of(System.getenv("WALLET_DATA_FILE") ?: "data/wallet.json"))
    Runtime.getRuntime().addShutdownHook(Thread { storage.close() })
    val engine = WalletEngine(storage)
    val host = System.getenv("WALLET_HOST") ?: "127.0.0.1"
    val port = (System.getenv("PORT") ?: "8080").toInt()
    embeddedServer(Netty, host = host, port = port) { walletModule(engine) }.start(wait = true)
}

fun Application.walletModule(engine: WalletEngine = WalletEngine()) {
    install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
    install(StatusPages) {
        exception<WalletException> { call, cause -> call.respond(HttpStatusCode.fromValue(cause.statusCode), ApiError(cause.code, cause.message)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_JSON", "Revisa el formato de la solicitud")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_JSON", "Revisa los campos de la solicitud")) }
        exception<Throwable> { call, cause ->
            call.application.log.error("No se pudo completar la operación", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("INTERNAL_ERROR", "No se pudo completar la operación. Reintenta con el mismo identificador"))
        }
    }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "mode" to "school-prototype", "storage" to "local-json")) }
        route("/api/v1") {
            post("/auth/login") { call.respond(engine.login(call.receive())) }
            post("/auth/logout") { engine.logout(call.bearer()); call.respond(HttpStatusCode.NoContent) }
            post("/auth/password") { call.respond(engine.changePassword(call.bearer(), call.receive())) }
            post("/auth/biometric") { call.respond(HttpStatusCode.Created, engine.createBiometricGrant(call.bearer())) }
            post("/auth/biometric/login") { call.respond(engine.biometricLogin(call.receive())) }
            delete("/auth/biometric/{id}") { engine.revokeBiometricGrant(call.bearer(), call.id()); call.respond(HttpStatusCode.NoContent) }
            get("/me") { call.respond(engine.me(call.bearer())) }
            put("/me/photo") { call.respond(engine.updatePhoto(call.bearer(), call.receive())) }
            get("/me/movements") { call.respond(engine.movements(call.bearer())) }
            get("/catalog") { engine.me(call.bearer()); call.respond(engine.catalog) }
            post("/wallet/topups") { call.respond(engine.topup(call.bearer(), call.receive(), call.operationKey())) }
            post("/access/simulate") { call.respond(engine.simulateAccess(call.bearer())) }
            post("/tokens/access") { call.respond(engine.accessToken(call.bearer())) }
            post("/tokens/inspect") { call.respond(engine.inspectToken(call.bearer(), call.receive())) }
            post("/tokens/access/redeem") { call.respond(engine.redeemAccess(call.bearer(), call.receive())) }
            post("/orders") { call.respond(HttpStatusCode.Created, engine.createOrder(call.bearer(), call.receive())) }
            post("/orders/{id}/approve") { call.respond(engine.approveOrder(call.bearer(), call.id())) }
            post("/payments") { call.respond(engine.pay(call.bearer(), call.receive(), call.operationKey())) }
            get("/receipts/{id}") { call.respond(engine.purchaseReceipt(call.bearer(), call.id())) }
            get("/admin/audit") { call.respond(engine.audit(call.bearer(), call.request.queryParameters["studentId"])) }
            route("/admin/products") {
                get { call.respond(engine.adminProducts(call.bearer())) }
                post { call.respond(HttpStatusCode.Created, engine.createProduct(call.bearer(), call.receive())) }
                put("/{id}") { call.respond(engine.editProduct(call.bearer(), call.id(), call.receive())) }
                delete("/{id}") { engine.archiveProduct(call.bearer(), call.id()); call.respond(HttpStatusCode.NoContent) }
            }
            route("/admin/students") {
                get {
                    call.respond(engine.students(call.bearer(), call.request.queryParameters["query"] ?: "",
                        call.request.queryParameters["career"] ?: "", call.request.queryParameters["status"] ?: ""))
                }
                post { call.respond(HttpStatusCode.Created, engine.createStudent(call.bearer(), call.receive())) }
                get("/{id}/movements") { call.respond(engine.studentMovements(call.bearer(), call.id())) }
                post("/{id}/password") { call.respond(engine.resetStudentPassword(call.bearer(), call.id(), call.receive())) }
                put("/{id}") { call.respond(engine.editStudent(call.bearer(), call.id(), call.receive())) }
                patch("/{id}/status") {
                    call.respond(engine.setStatus(call.bearer(), call.id(), call.receive<StatusRequest>().status))
                }
                delete("/{id}") { engine.deleteStudent(call.bearer(), call.id()); call.respond(HttpStatusCode.NoContent) }
            }
        }
    }
}
private fun ApplicationCall.bearer(): String {
    val value = request.headers[HttpHeaders.Authorization] ?: ""
    if (!value.startsWith("Bearer ", ignoreCase = true) || value.length <= 7)
        throw WalletException(401, "UNAUTHORIZED", "Inicia sesión para continuar")
    return value.substring(7)
}
private fun ApplicationCall.operationKey() = request.headers["Idempotency-Key"] ?: ""
private fun ApplicationCall.id() = parameters["id"] ?: throw WalletException(422, "INVALID_ID", "Falta el identificador")
