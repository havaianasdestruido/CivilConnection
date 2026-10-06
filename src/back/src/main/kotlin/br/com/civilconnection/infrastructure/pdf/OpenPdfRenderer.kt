package br.com.civilconnection.infrastructure.pdf

import br.com.civilconnection.application.PdfRenderer
import br.com.civilconnection.domain.DiarioObra
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

class OpenPdfRenderer : PdfRenderer {
    override fun renderizarRdo(diario: DiarioObra): ByteArray {
        val output = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, output)
        document.open()

        document.add(
            Paragraph("RELATÓRIO DIÁRIO DE OBRA", Font(Font.HELVETICA, 16f, Font.BOLD)).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 18f
            },
        )
        document.add(label("Obra", diario.obraNome))
        document.add(label("Data", diario.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
        document.add(label("Clima (manhã)", diario.climaManha ?: "Não informado"))
        document.add(label("Clima (tarde)", diario.climaTarde ?: "Não informado"))
        document.add(label("Efetivo", diario.efetivo.toString()))
        document.add(section("Atividades executadas", diario.atividades))
        document.add(section("Ocorrências", diario.ocorrencias))
        document.add(section("Registros fotográficos", "${diario.fotos.size} foto(s) vinculada(s) ao RDO."))
        diario.fotos.forEachIndexed { index, foto ->
            document.add(Paragraph("${index + 1}. ${foto.legenda ?: "Sem legenda"} — ${foto.storagePath}"))
        }

        document.close()
        return output.toByteArray()
    }

    private fun label(
        title: String,
        value: String,
    ) = Paragraph().apply {
        add(Paragraph("$title: ", Font(Font.HELVETICA, 10f, Font.BOLD)))
        add(Paragraph(value, Font(Font.HELVETICA, 10f)))
        spacingAfter = 6f
    }

    private fun section(
        title: String,
        content: String?,
    ) = Paragraph().apply {
        spacingBefore = 12f
        spacingAfter = 8f
        add(Paragraph(title, Font(Font.HELVETICA, 11f, Font.BOLD)))
        add(Paragraph(content?.takeIf { it.isNotBlank() } ?: "Nada registrado.", Font(Font.HELVETICA, 10f)))
    }
}
