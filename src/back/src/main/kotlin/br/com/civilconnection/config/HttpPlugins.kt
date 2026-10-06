package br.com.civilconnection.config

import br.com.civilconnection.api.ApiError
import br.com.civilconnection.domain.ConflictException
import br.com.civilconnection.domain.ForbiddenException
import br.com.civilconnection.domain.NotFoundException
import br.com.civilconnection.domain.ValidationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.mdc
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.net.URI
import java.time.format.DateTimeParseException
import java.util.UUID

fun Application.configureHttpPlugins(config: AppConfig) {
    configureSerialization()
    configureRequestTracing()
    configureCors(config)
    configureErrorHandling()
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = false
                explicitNulls = false
                encodeDefaults = true
            },
        )
    }
}

private fun Application.configureRequestTracing() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        verify {
            it.length in MIN_REQUEST_ID_LENGTH..MAX_REQUEST_ID_LENGTH &&
                it.all { char -> char.isLetterOrDigit() || char in "-_." }
        }
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        level = Level.INFO
        mdc("requestId") { call -> call.callId }
        filter { call -> call.request.path() != "/health" }
    }
}

private fun Application.configureCors(config: AppConfig) {
    install(CORS) {
        config.allowedOrigins.forEach { origin ->
            val uri = URI(origin)
            require(uri.scheme in setOf("http", "https") && uri.host != null) {
                "Origem CORS inválida: $origin"
            }
            val authority = if (uri.port == -1) uri.host else "${uri.host}:${uri.port}"
            allowHost(authority, schemes = listOf(uri.scheme))
        }
        listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)
            .forEach { method -> allowMethod(method) }
        listOf(HttpHeaders.Authorization, HttpHeaders.ContentType, HttpHeaders.XRequestId)
            .forEach { header -> allowHeader(header) }
        exposeHeader(HttpHeaders.XRequestId)
        maxAgeInSeconds = CORS_MAX_AGE_SECONDS
    }
}

private fun Application.configureErrorHandling() {
    val applicationLog = log
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "VALIDACAO", cause.message.orEmpty(), cause.details)
        }
        exception<DateTimeParseException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, "DATA_INVALIDA", "Use uma data no formato AAAA-MM-DD.")
        }
        exception<NotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, "NAO_ENCONTRADO", cause.message.orEmpty())
        }
        exception<ForbiddenException> { call, cause ->
            call.respondError(HttpStatusCode.Forbidden, "ACESSO_NEGADO", cause.message.orEmpty())
        }
        exception<ConflictException> { call, cause ->
            call.respondError(HttpStatusCode.Conflict, "CONFLITO", cause.message.orEmpty())
        }
        exception<Throwable> { call, cause ->
            applicationLog.error("Erro não tratado em ${call.request.path()}", cause)
            call.respondError(
                HttpStatusCode.InternalServerError,
                "ERRO_INTERNO",
                "Não foi possível concluir a solicitação.",
            )
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    details: Map<String, String> = emptyMap(),
) {
    respond(status, ApiError(code, message, details, callId))
}

private const val MIN_REQUEST_ID_LENGTH = 8
private const val MAX_REQUEST_ID_LENGTH = 128
private const val CORS_MAX_AGE_SECONDS = 3600L
