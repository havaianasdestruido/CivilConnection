package br.com.civilconnection.application

import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.DiarioObra
import br.com.civilconnection.domain.NotFoundException
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class DiarioServiceTest {
    private val actor = Actor(UUID.randomUUID())

    @Test
    fun `gera pdf para diario visivel`() = runBlocking {
        val diario = DiarioObra(UUID.randomUUID(), UUID.randomUUID(), "Obra", LocalDate.now(), null, null, 2, null, null, emptyList())
        val expected = byteArrayOf(1, 2, 3)
        val service = DiarioService(DiarioRepository { _, _ -> diario }, PdfRenderer { expected })

        assertContentEquals(expected, service.gerarPdf(actor, diario.id))
    }

    @Test
    fun `nao revela diario ausente pela rls`() = runBlocking {
        val service = DiarioService(DiarioRepository { _, _ -> null }, PdfRenderer { byteArrayOf() })
        assertFailsWith<NotFoundException> { service.gerarPdf(actor, UUID.randomUUID()) }
    }
}
