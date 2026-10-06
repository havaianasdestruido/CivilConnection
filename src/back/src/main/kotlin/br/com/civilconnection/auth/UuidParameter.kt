package br.com.civilconnection.auth

import br.com.civilconnection.domain.ValidationException
import io.ktor.server.application.ApplicationCall
import java.util.UUID

fun ApplicationCall.uuidParameter(name: String): UUID {
    val raw =
        parameters[name]
            ?: throw ValidationException("Parâmetro obrigatório ausente.", mapOf(name to "Informe um UUID válido."))
    return runCatching { UUID.fromString(raw) }
        .getOrElse {
            throw ValidationException("Parâmetro inválido.", mapOf(name to "Informe um UUID válido."))
        }
}
