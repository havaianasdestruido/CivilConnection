package br.com.civilconnection.application

import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.ImportacaoOrcamento
import br.com.civilconnection.domain.OrcamentoItemImportado
import br.com.civilconnection.domain.ValidationException
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrcamentoServiceTest {
    private val actor = Actor(UUID.randomUUID())
    private val obraId = UUID.randomUUID()
    private val item = OrcamentoItemImportado("001", "Concreto", "m3", BigDecimal.TEN, BigDecimal("25.50"))

    @Test
    fun `normaliza a base e persiste itens validos`() = runBlocking {
        val repository = FakeOrcamentoRepository()
        val service = OrcamentoService(repository, PlanilhaOrcamentoReader { listOf(item) }, 1024, 10)

        val result = service.importar(actor, obraId, "sinapi", byteArrayOf(1))

        assertEquals("SINAPI", repository.base)
        assertEquals(1, result.itensImportados)
    }

    @Test
    fun `rejeita arquivo maior que o limite antes de ler`() = runBlocking {
        val service =
            OrcamentoService(
                FakeOrcamentoRepository(),
                PlanilhaOrcamentoReader { error("não deve ler") },
                1,
                10,
            )

        assertFailsWith<ValidationException> {
            service.importar(actor, obraId, "SINAPI", byteArrayOf(1, 2))
        }
    }

    private class FakeOrcamentoRepository : OrcamentoRepository {
        var base: String? = null

        override suspend fun importar(
            actor: Actor,
            obraId: UUID,
            base: String,
            itens: List<OrcamentoItemImportado>,
        ): ImportacaoOrcamento {
            this.base = base
            return ImportacaoOrcamento(obraId, itens.size, BigDecimal("255.00"))
        }
    }
}
