package br.com.civilconnection.api

import br.com.civilconnection.application.CompraService
import br.com.civilconnection.application.DiarioService
import br.com.civilconnection.application.ObraService
import br.com.civilconnection.application.OrcamentoService
import br.com.civilconnection.auth.actor
import br.com.civilconnection.auth.uuidParameter
import br.com.civilconnection.config.AppConfig
import br.com.civilconnection.domain.ValidationException
import br.com.civilconnection.infrastructure.db.DatabaseFactory
import br.com.civilconnection.infrastructure.webhook.WebhookVerifier
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.getKoin
import java.io.ByteArrayOutputStream
import java.util.UUID

fun Application.configureRoutes() {
    val dependencies = getKoin()
    routing {
        operationalRoutes(dependencies.get())
        authenticate("supabase") {
            domainRoutes(
                config = dependencies.get(),
                obraService = dependencies.get(),
                diarioService = dependencies.get(),
                orcamentoService = dependencies.get(),
                compraService = dependencies.get(),
            )
        }
        webhookRoute(dependencies.get())
    }
}

private fun Route.operationalRoutes(database: DatabaseFactory) {
    get("/health") {
        call.respond(HealthResponse("ok"))
    }
    get("/health/ready") {
        if (database.ping()) {
            call.respond(HealthResponse("pronto"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("indisponivel"))
        }
    }
    get("/openapi.yaml") {
        val contract =
            requireNotNull(Thread.currentThread().contextClassLoader.getResource("openapi.yaml")) {
                "Contrato OpenAPI não encontrado"
            }.readText()
        call.respondText(contract, ContentType.parse("application/yaml"))
    }
}

private fun Route.domainRoutes(
    config: AppConfig,
    obraService: ObraService,
    diarioService: DiarioService,
    orcamentoService: OrcamentoService,
    compraService: CompraService,
) {
    route("/v1") {
        obraRoutes(obraService)
        diarioRoutes(diarioService)
        orcamentoRoutes(config, orcamentoService)
        compraRoutes(compraService)
    }
}

private fun Route.obraRoutes(service: ObraService) {
    post("/obras/{id}/medicoes") {
        val request = call.receive<RegistrarMedicaoRequest>()
        val result =
            service.registrarMedicao(
                actor = call.actor(),
                obraId = call.uuidParameter("id"),
                periodo = request.periodoOrDefault(),
            )
        call.respond(HttpStatusCode.Created, result.toResponse())
    }
    get("/obras/{id}/curva-s") {
        val obraId = call.uuidParameter("id")
        val result = service.curvaS(call.actor(), obraId)
        call.respond(CurvaSResponse(obraId.toString(), result.map { it.toResponse() }))
    }
    get("/obras/{id}/custos") {
        call.respond(service.custos(call.actor(), call.uuidParameter("id")).toResponse())
    }
}

private fun Route.diarioRoutes(service: DiarioService) {
    get("/diario/{id}/pdf") {
        val diarioId = call.uuidParameter("id")
        val pdf = service.gerarPdf(call.actor(), diarioId)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                "rdo-$diarioId.pdf",
            ).toString(),
        )
        call.respondBytes(pdf, ContentType.Application.Pdf)
    }
}

private fun Route.orcamentoRoutes(
    config: AppConfig,
    service: OrcamentoService,
) {
    post("/orcamentos/importar") {
        val obraId = call.requiredUuidQuery("obraId")
        val base = call.request.queryParameters["base"] ?: "SINAPI"
        val bytes = call.receiveLimitedBody(config.importMaxBytes, "A planilha excede o limite de tamanho.")
        val result = service.importar(call.actor(), obraId, base, bytes)
        call.respond(HttpStatusCode.Created, result.toResponse())
    }
}

private fun Route.compraRoutes(service: CompraService) {
    post("/compras/{id}/aprovar") {
        val result = service.aprovar(call.actor(), call.uuidParameter("id"))
        call.respond(result.toResponse())
    }
}

private fun Route.webhookRoute(verifier: WebhookVerifier) {
    post("/webhooks/supabase") {
        val payload = call.receiveLimitedBody(WEBHOOK_MAX_BYTES, "O webhook excede o limite de tamanho.")
        if (!verifier.isValid(payload, call.request.header("X-Webhook-Signature"))) {
            call.respond(HttpStatusCode.Unauthorized, ApiError("ASSINATURA_INVALIDA", "Assinatura inválida."))
            return@post
        }
        Json.parseToJsonElement(payload.decodeToString())
        call.respond(HttpStatusCode.Accepted, mapOf("status" to "recebido"))
    }
}

private fun ApplicationCall.requiredUuidQuery(name: String): UUID {
    val value = request.queryParameters[name]
        ?: throw ValidationException("Parâmetro obrigatório ausente.", mapOf(name to "Informe um UUID válido."))
    return runCatching { UUID.fromString(value) }
        .getOrElse { throw ValidationException("Parâmetro inválido.", mapOf(name to "Informe um UUID válido.")) }
}

private suspend fun ApplicationCall.receiveLimitedBody(
    maxBytes: Long,
    errorMessage: String,
): ByteArray {
    val channel = receiveChannel()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        if (read == 0) continue
        total += read
        if (total > maxBytes) throw ValidationException(errorMessage)
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private const val WEBHOOK_MAX_BYTES = 1_048_576L
