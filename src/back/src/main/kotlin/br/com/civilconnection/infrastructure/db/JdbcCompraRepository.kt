package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.application.CompraRepository
import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.CompraAprovada
import br.com.civilconnection.domain.ConflictException
import br.com.civilconnection.domain.ForbiddenException
import br.com.civilconnection.domain.NotFoundException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class JdbcCompraRepository(
    private val database: DatabaseFactory,
) : CompraRepository {
    override suspend fun aprovar(
        actor: Actor,
        compraId: UUID,
    ): CompraAprovada =
        database.asUser(actor) {
            val statusAtual =
                exec("select status::text from public.compras where id = '$compraId'::uuid") { result ->
                    if (result.next()) result.getString(1) else null
                } ?: throw NotFoundException("Compra não encontrada.")
            if (statusAtual != "rascunho") {
                throw ConflictException("Somente compras em rascunho podem ser aprovadas.")
            }

            val atualizada =
                exec(
                    """
                    update public.compras
                       set status = 'pedido', atualizado_em = now()
                     where id = '$compraId'::uuid and status = 'rascunho'
                    returning id, status::text
                    """.trimIndent(),
                ) { result ->
                    if (result.next()) {
                        CompraAprovada(
                            id = result.getObject("id", UUID::class.java),
                            status = result.getString("status"),
                            aprovadoEm = OffsetDateTime.now(ZoneOffset.UTC),
                        )
                    } else {
                        null
                    }
                }
            atualizada ?: throw ForbiddenException()
        }
}
