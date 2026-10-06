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
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
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
    val config = dependencies.get<AppConfig>()
    val database = dependencies.get<DatabaseFactory>()
    val obraService = dependencies.get<ObraService>()
    val diarioService = dependencies.get<DiarioService>()
    val orcamentoService = dependencies.get<OrcamentoService>()
    val compraService = dependencies.get<CompraService>()
    val webhookVerifier = dependencies.get<WebhookVerifier>()

    routing {
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
                this@configureRoutes::class.java.classLoader
                    .getResource("openapi.yaml")
                    ?.readText()
                    ?: error("Contrato OpenAPI não encontrado")
            call.respondText(contract, ContentType.parse("application/yaml"))
        }

        authenticate("supabase") {
            route("/v1") {
                post("/obras/{id}/medicoes") {
                    val request = call.receive<RegistrarMedicaoRequest>()
                    val result =
                        obraService.registrarMedicao(
                            actor = call.actor(),
                            obraId = call.uuidParameter("id"),
                            periodo = request.periodoOrDefault(),
                        )
                    call.respond(HttpStatusCode.Created, result.toResponse())
                }

                get("/obras/{id}/curva-s") {
                    val obraId = call.uuidParameter("id")
                    val result = obraService.curvaS(call.actor(), obraId)
                    call.respond(CurvaSResponse(obraId.toString(), result.map { it.toResponse() }))
                }

                get("/obras/{id}/custos") {
                    call.respond(obraService.custos(call.actor(), call.uuidParameter("id")).toResponse())
                }

                get("/diario/{id}/pdf") {
                    val diarioId = call.uuidParameter("id")
                    val pdf = diarioService.gerarPdf(call.actor(), diarioId)
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            "rdo-$diarioId.pdf",
                        ).toString(),
                    )
                    call.respondBytes(pdf, ContentType.Application.Pdf)
                }

                post("/orcamentos/importar") {
                    val obraId = call.requiredUuidQuery("obraId")
                    val base = call.request.queryParameters["base"] ?: "SINAPI"
                    val bytes = call.receiveLimitedBody(config.importMaxBytes, "A planilha excede o limite de tamanho.")
                    val result = orcamentoService.importar(call.actor(), obraId, base, bytes)
                    call.respond(HttpStatusCode.Created, result.toResponse())
                }

                post("/compras/{id}/aprovar") {
                    val result = compraService.aprovar(call.actor(), call.uuidParameter("id"))
                    call.respond(result.toResponse())
                }
            }
        }

        post("/webhooks/supabase") {
            val payload = call.receiveLimitedBody(1_048_576, "O webhook excede o limite de tamanho.")
            if (!webhookVerifier.isValid(payload, call.request.header("X-Webhook-Signature"))) {
                call.respond(HttpStatusCode.Unauthorized, ApiError("ASSINATURA_INVALIDA", "Assinatura inválida."))
                return@post
            }
            Json.parseToJsonElement(payload.decodeToString())
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "recebido"))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requiredUuidQuery(name: String): UUID {
    val value = request.queryParameters[name]
        ?: throw ValidationException("Parâmetro obrigatório ausente.", mapOf(name to "Informe um UUID válido."))
    return runCatching { UUID.fromString(value) }
        .getOrElse { throw ValidationException("Parâmetro inválido.", mapOf(name to "Informe um UUID válido.")) }
}

private suspend fun io.ktor.server.application.ApplicationCall.receiveLimitedBody(
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
