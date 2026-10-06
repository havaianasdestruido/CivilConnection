package br.com.civilconnection.infrastructure.db

import org.jetbrains.exposed.sql.Table

internal object OrcamentoItens : Table("orcamento_itens") {
    val id = uuid("id").autoGenerate()
    val organizacaoId = uuid("organizacao_id")
    val obraId = uuid("obra_id")
    val base = varchar("base", 16)
    val codigo = text("codigo").nullable()
    val descricao = text("descricao")
    val unidade = text("unidade")
    val quantidade = decimal("quantidade", 14, 4)
    val custoUnitario = decimal("custo_unitario", 14, 4)

    override val primaryKey = PrimaryKey(id)
}
