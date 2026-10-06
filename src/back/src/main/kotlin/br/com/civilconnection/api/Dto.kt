package br.com.civilconnection.api

import br.com.civilconnection.domain.CompraAprovada
import br.com.civilconnection.domain.CurvaSPoint
import br.com.civilconnection.domain.CustosResumo
import br.com.civilconnection.domain.ImportacaoOrcamento
import br.com.civilconnection.domain.Medicao
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class RegistrarMedicaoRequest(
    val periodo: String? = null,
) {
    fun periodoOrDefault(): LocalDate = periodo?.let(LocalDate::parse) ?: LocalDate.now()
}

@Serializable
data class MedicaoResponse(
    val id: String,
    val obraId: String,
    val periodo: String,
    val percentualFisicoAcumulado: String,
    val valorPeriodo: String,
    val valorAcumulado: String,
    val status: String,
)

@Serializable
data class CurvaSResponse(
    val obraId: String,
    val pontos: List<CurvaSPointResponse>,
)

@Serializable
data class CurvaSPointResponse(
    val periodo: String,
    val planejadoPercentual: String,
    val realizadoPercentual: String,
    val realizadoValor: String,
)

@Serializable
data class CustosResponse(
    val obraId: String,
    val custoOrcado: String,
    val custoRealizado: String,
    val saldoOrcamento: String,
    val comprometidoEmAberto: String,
)

@Serializable
data class ImportacaoOrcamentoResponse(
    val obraId: String,
    val itensImportados: Int,
    val valorTotal: String,
)

@Serializable
data class CompraAprovadaResponse(
    val id: String,
    val status: String,
    val aprovadoEm: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val servico: String = "civil-connection-api",
)

@Serializable
data class ApiError(
    val codigo: String,
    val mensagem: String,
    val detalhes: Map<String, String> = emptyMap(),
    val requestId: String? = null,
)

fun Medicao.toResponse() =
    MedicaoResponse(
        id = id.toString(),
        obraId = obraId.toString(),
        periodo = periodo.toString(),
        percentualFisicoAcumulado = percentualFisicoAcumulado.toPlainString(),
        valorPeriodo = valorPeriodo.toPlainString(),
        valorAcumulado = valorAcumulado.toPlainString(),
        status = status,
    )

fun CurvaSPoint.toResponse() =
    CurvaSPointResponse(
        periodo = periodo.toString(),
        planejadoPercentual = planejadoPercentual.toPlainString(),
        realizadoPercentual = realizadoPercentual.toPlainString(),
        realizadoValor = realizadoValor.toPlainString(),
    )

fun CustosResumo.toResponse() =
    CustosResponse(
        obraId = obraId.toString(),
        custoOrcado = custoOrcado.toPlainString(),
        custoRealizado = custoRealizado.toPlainString(),
        saldoOrcamento = saldoOrcamento.toPlainString(),
        comprometidoEmAberto = comprometidoEmAberto.toPlainString(),
    )

fun ImportacaoOrcamento.toResponse() =
    ImportacaoOrcamentoResponse(
        obraId = obraId.toString(),
        itensImportados = itensImportados,
        valorTotal = valorTotal.toPlainString(),
    )

fun CompraAprovada.toResponse() = CompraAprovadaResponse(id.toString(), status, aprovadoEm.toString())
