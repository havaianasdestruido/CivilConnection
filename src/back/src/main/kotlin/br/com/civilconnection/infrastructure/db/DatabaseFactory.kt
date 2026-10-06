package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.config.DatabaseConfig
import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.ConflictException
import br.com.civilconnection.domain.ForbiddenException
import br.com.civilconnection.domain.ValidationException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException

class DatabaseFactory(
    config: DatabaseConfig,
) : AutoCloseable {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.maximumPoolSize
                minimumIdle = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                initializationFailTimeout = -1
                validate()
            },
        )

    private val database = Database.connect(dataSource)

    suspend fun ping(): Boolean =
        runCatching {
            newSuspendedTransaction(Dispatchers.IO, database) {
                exec("select 1") { it.next() && it.getInt(1) == 1 } ?: false
            }
        }.getOrDefault(false)

    /**
     * Opens a transaction as PostgreSQL's `authenticated` role and propagates the
     * Supabase subject to `auth.uid()`. RLS remains the final authorization boundary.
     * The connection user must only be allowed to SET ROLE authenticated.
     */
    suspend fun <T> asUser(
        actor: Actor,
        block: suspend Transaction.() -> T,
    ): T =
        try {
            newSuspendedTransaction(Dispatchers.IO, database) {
                exec("set local role authenticated")
                exec("select set_config('request.jwt.claim.sub', '${actor.userId}', true)")
                exec("select set_config('request.jwt.claim.role', 'authenticated', true)")
                block()
            }
        } catch (exception: ExposedSQLException) {
            val postgresException = exception.cause as? PSQLException ?: throw exception
            throw postgresException.toDomainException()
        } catch (exception: PSQLException) {
            throw exception.toDomainException()
        }

    override fun close() = dataSource.close()
}

private fun PSQLException.toDomainException(): RuntimeException =
    when (sqlState) {
        "42501" -> ForbiddenException()
        "23505" -> ConflictException("Já existe um registro com os mesmos dados.")
        "23503" -> ValidationException("Um registro relacionado não existe ou ainda está em uso.")
        "23514", "22P02" -> ValidationException("Os dados informados são inválidos.")
        else -> RuntimeException("Falha na operação com o banco de dados.", this)
    }
