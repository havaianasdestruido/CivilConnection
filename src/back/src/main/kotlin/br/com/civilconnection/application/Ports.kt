package br.com.civilconnection.application

import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.CompraAprovada
import br.com.civilconnection.domain.CurvaSPoint
import br.com.civilconnection.domain.CustosResumo
import br.com.civilconnection.domain.DiarioObra
import br.com.civilconnection.domain.ImportacaoOrcamento
import br.com.civilconnection.domain.Medicao
import br.com.civilconnection.domain.OrcamentoItemImportado
import java.time.LocalDate
import java.util.UUID

interface ObraRepository {
    suspend fun registrarMedicao(
        actor: Actor,
        obraId: UUID,
        periodo: LocalDate,
    ): Medicao

    suspend fun curvaS(
        actor: Actor,
        obraId: UUID,
    ): List<CurvaSPoint>

    suspend fun custos(
        actor: Actor,
        obraId: UUID,
    ): CustosResumo
}

fun interface DiarioRepository {
    suspend fun buscar(
        actor: Actor,
        diarioId: UUID,
    ): DiarioObra?
}

interface OrcamentoRepository {
    suspend fun importar(
        actor: Actor,
        obraId: UUID,
        base: String,
        itens: List<OrcamentoItemImportado>,
    ): ImportacaoOrcamento
}

interface CompraRepository {
    suspend fun aprovar(
        actor: Actor,
        compraId: UUID,
    ): CompraAprovada
}

fun interface PdfRenderer {
    fun renderizarRdo(diario: DiarioObra): ByteArray
}

fun interface PlanilhaOrcamentoReader {
    fun ler(conteudo: ByteArray): List<OrcamentoItemImportado>
}
