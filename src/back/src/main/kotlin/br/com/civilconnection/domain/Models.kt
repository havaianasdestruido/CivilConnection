package br.com.civilconnection.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Actor(
    val userId: UUID,
)

data class Medicao(
    val id: UUID,
    val obraId: UUID,
    val periodo: LocalDate,
    val percentualFisicoAcumulado: BigDecimal,
    val valorPeriodo: BigDecimal,
    val valorAcumulado: BigDecimal,
    val status: String,
)

data class CurvaSPoint(
    val periodo: LocalDate,
    val planejadoPercentual: BigDecimal,
    val realizadoPercentual: BigDecimal,
    val realizadoValor: BigDecimal,
)

data class CustosResumo(
    val obraId: UUID,
    val custoOrcado: BigDecimal,
    val custoRealizado: BigDecimal,
    val saldoOrcamento: BigDecimal,
    val comprometidoEmAberto: BigDecimal,
)

data class DiarioObra(
    val id: UUID,
    val obraId: UUID,
    val obraNome: String,
    val data: LocalDate,
    val climaManha: String?,
    val climaTarde: String?,
    val efetivo: Int,
    val atividades: String?,
    val ocorrencias: String?,
    val fotos: List<DiarioFoto>,
)

data class DiarioFoto(
    val storagePath: String,
    val legenda: String?,
)

data class OrcamentoItemImportado(
    val codigo: String?,
    val descricao: String,
    val unidade: String,
    val quantidade: BigDecimal,
    val custoUnitario: BigDecimal,
)

data class ImportacaoOrcamento(
    val obraId: UUID,
    val itensImportados: Int,
    val valorTotal: BigDecimal,
)

data class CompraAprovada(
    val id: UUID,
    val status: String,
    val aprovadoEm: OffsetDateTime,
)
