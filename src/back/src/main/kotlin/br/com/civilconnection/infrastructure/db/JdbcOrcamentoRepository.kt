package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.application.OrcamentoRepository
import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.ImportacaoOrcamento
import br.com.civilconnection.domain.NotFoundException
import br.com.civilconnection.domain.OrcamentoItemImportado
import org.jetbrains.exposed.sql.batchInsert
import java.math.BigDecimal
import java.util.UUID

class JdbcOrcamentoRepository(
    private val database: DatabaseFactory,
) : OrcamentoRepository {
    override suspend fun importar(
        actor: Actor,
        obraId: UUID,
        base: String,
        itens: List<OrcamentoItemImportado>,
    ): ImportacaoOrcamento =
        database.asUser(actor) {
            val organizacaoId =
                exec("select organizacao_id from public.obras where id = '$obraId'::uuid") { result ->
                    if (result.next()) result.getObject(1, UUID::class.java) else null
                } ?: throw NotFoundException("Obra não encontrada.")

            OrcamentoItens.batchInsert(itens) { item ->
                this[OrcamentoItens.organizacaoId] = organizacaoId
                this[OrcamentoItens.obraId] = obraId
                this[OrcamentoItens.base] = base
                this[OrcamentoItens.codigo] = item.codigo
                this[OrcamentoItens.descricao] = item.descricao
                this[OrcamentoItens.unidade] = item.unidade
                this[OrcamentoItens.quantidade] = item.quantidade
                this[OrcamentoItens.custoUnitario] = item.custoUnitario
            }

            ImportacaoOrcamento(
                obraId = obraId,
                itensImportados = itens.size,
                valorTotal =
                    itens.fold(BigDecimal.ZERO) { total, item ->
                        total + item.quantidade.multiply(item.custoUnitario)
                    }.setScale(2, java.math.RoundingMode.HALF_UP),
            )
        }
}
