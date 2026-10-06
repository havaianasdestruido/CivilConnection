package br.com.civilconnection.infrastructure.db

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XlsxOrcamentoReaderTest {
    @Test
    fun `le planilha sem a coluna opcional codigo`() {
        val content =
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Orçamento")
                val header = sheet.createRow(0)
                listOf("descrição", "unidade", "quantidade", "custo unitário")
                    .forEachIndexed { index, value -> header.createCell(index).setCellValue(value) }
                val item = sheet.createRow(1)
                item.createCell(0).setCellValue("Concreto usinado")
                item.createCell(1).setCellValue("m3")
                item.createCell(2).setCellValue(10.5)
                item.createCell(3).setCellValue(450.25)
                ByteArrayOutputStream().use { output ->
                    workbook.write(output)
                    output.toByteArray()
                }
            }

        val result = XlsxOrcamentoReader().ler(content).single()

        assertNull(result.codigo)
        assertEquals("Concreto usinado", result.descricao)
        assertEquals(BigDecimal("10.5"), result.quantidade.stripTrailingZeros())
        assertEquals(BigDecimal("450.25"), result.custoUnitario.stripTrailingZeros())
    }
}
