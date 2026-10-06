package br.com.civilconnection.auth

import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.ValidationException
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

fun Application.configureAuthentication(
    jwksUrl: String,
    issuer: String,
    audience: String,
) {
    val jwkProvider =
        JwkProviderBuilder(URI(jwksUrl).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    authentication {
        jwt("supabase") {
            realm = "civil-connection"
            verifier(jwkProvider, issuer)
            validate { credential ->
                val hasAudience = credential.payload.audience.contains(audience)
                val subjectIsUuid = runCatching { UUID.fromString(credential.payload.subject) }.isSuccess
                if (hasAudience && subjectIsUuid) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("erro" to "Token de acesso ausente, inválido ou expirado."),
                )
            }
        }
    }
}

fun io.ktor.server.application.ApplicationCall.actor(): Actor {
    val subject = principal<JWTPrincipal>()?.payload?.subject
        ?: throw ValidationException("Identidade do usuário não encontrada no token.")
    return Actor(UUID.fromString(subject))
}
