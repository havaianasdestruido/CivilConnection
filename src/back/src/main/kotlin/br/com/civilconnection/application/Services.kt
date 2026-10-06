package br.com.civilconnection.application

import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.CompraAprovada
import br.com.civilconnection.domain.CustosResumo
import br.com.civilconnection.domain.CurvaSPoint
import br.com.civilconnection.domain.ImportacaoOrcamento
import br.com.civilconnection.domain.Medicao
import br.com.civilconnection.domain.NotFoundException
import br.com.civilconnection.domain.OrcamentoItemImportado
import br.com.civilconnection.domain.ValidationException
import java.time.LocalDate
import java.util.UUID

class ObraService(
    private val repository: ObraRepository,
) {
    suspend fun registrarMedicao(
        actor: Actor,
        obraId: UUID,
        periodo: LocalDate,
    ): Medicao = repository.registrarMedicao(actor, obraId, periodo.withDayOfMonth(1))

    suspend fun curvaS(
        actor: Actor,
        obraId: UUID,
    ): List<CurvaSPoint> = repository.curvaS(actor, obraId)

    suspend fun custos(
        actor: Actor,
        obraId: UUID,
    ): CustosResumo = repository.custos(actor, obraId)
}

class DiarioService(
    private val repository: DiarioRepository,
    private val pdfRenderer: PdfRenderer,
) {
    suspend fun gerarPdf(
        actor: Actor,
        diarioId: UUID,
    ): ByteArray {
        val diario = repository.buscar(actor, diarioId)
            ?: throw NotFoundException("Diário de obra não encontrado.")
        return pdfRenderer.renderizarRdo(diario)
    }
}

class OrcamentoService(
    private val repository: OrcamentoRepository,
    private val reader: PlanilhaOrcamentoReader,
    private val maxBytes: Long,
    private val maxRows: Int,
) {
    suspend fun importar(
        actor: Actor,
        obraId: UUID,
        base: String,
        conteudo: ByteArray,
    ): ImportacaoOrcamento {
        validarArquivo(conteudo)
        val baseNormalizada = validarBase(base)
        val itens = reader.ler(conteudo)
        validarItens(itens)
        return repository.importar(actor, obraId, baseNormalizada, itens)
    }

    private fun validarArquivo(conteudo: ByteArray) {
        if (conteudo.isEmpty()) throw ValidationException("A planilha está vazia.")
        if (conteudo.size > maxBytes) throw ValidationException("A planilha excede o limite de tamanho.")
    }

    private fun validarBase(base: String): String {
        val normalizada = base.uppercase()
        if (normalizada !in BASES_SUPORTADAS) {
            throw ValidationException("Base de orçamento inválida.", mapOf("base" to "Use SINAPI, TCPO ou PROPRIA."))
        }
        return normalizada
    }

    private fun validarItens(itens: List<OrcamentoItemImportado>) {
        if (itens.isEmpty()) throw ValidationException("Nenhum item válido foi encontrado na planilha.")
        if (itens.size > maxRows) throw ValidationException("A planilha excede o limite de $maxRows itens.")
    }

    private companion object {
        val BASES_SUPORTADAS = setOf("SINAPI", "TCPO", "PROPRIA")
    }
}

class CompraService(
    private val repository: CompraRepository,
) {
    suspend fun aprovar(
        actor: Actor,
        compraId: UUID,
    ): CompraAprovada = repository.aprovar(actor, compraId)
}
