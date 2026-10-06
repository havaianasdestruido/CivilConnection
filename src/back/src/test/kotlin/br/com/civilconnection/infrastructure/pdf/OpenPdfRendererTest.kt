package br.com.civilconnection.infrastructure.pdf

import br.com.civilconnection.domain.DiarioObra
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenPdfRendererTest {
    @Test
    fun `gera documento pdf valido`() {
        val diario =
            DiarioObra(
                id = UUID.randomUUID(),
                obraId = UUID.randomUUID(),
                obraNome = "Edifício Aurora",
                data = LocalDate.of(2026, 10, 6),
                climaManha = "sol",
                climaTarde = "nublado",
                efetivo = 12,
                atividades = "Concretagem dos pilares.",
                ocorrencias = null,
                fotos = emptyList(),
            )

        val result = OpenPdfRenderer().renderizarRdo(diario)

        assertTrue(result.size > 100)
        assertTrue(result.decodeToString(0, 5).startsWith("%PDF-"))
    }
}
