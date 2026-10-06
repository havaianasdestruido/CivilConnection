package br.com.civilconnection.domain

sealed class DomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ValidationException(
    message: String,
    val details: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(message, cause)

class NotFoundException(
    message: String,
) : DomainException(message)

class ConflictException(
    message: String,
) : DomainException(message)

class ForbiddenException(
    message: String = "Você não tem permissão para executar esta ação.",
) : DomainException(message)
