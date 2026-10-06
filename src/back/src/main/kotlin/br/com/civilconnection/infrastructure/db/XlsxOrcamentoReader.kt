package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.application.PlanilhaOrcamentoReader
import br.com.civilconnection.domain.OrcamentoItemImportado
import br.com.civilconnection.domain.ValidationException
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.text.Normalizer

class XlsxOrcamentoReader : PlanilhaOrcamentoReader {
    override fun ler(conteudo: ByteArray): List<OrcamentoItemImportado> =
        try {
            XSSFWorkbook(ByteArrayInputStream(conteudo)).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                val header = sheet.firstOrNull { row -> row.any { formatter.formatCellValue(it, evaluator).isNotBlank() } }
                    ?: throw ValidationException("A planilha não possui cabeçalho.")
                val columns =
                    header.associate { cell ->
                        normalize(formatter.formatCellValue(cell, evaluator)) to cell.columnIndex
                    }
                REQUIRED_COLUMNS.forEach { column ->
                    if (column !in columns) {
                        throw ValidationException(
                            "Coluna obrigatória ausente na planilha.",
                            mapOf("coluna" to column),
                        )
                    }
                }

                sheet
                    .asSequence()
                    .dropWhile { it.rowNum <= header.rowNum }
                    .filterNot { it.isBlank(formatter, evaluator) }
                    .map { row ->
                        OrcamentoItemImportado(
                            codigo = row.value(columns, "codigo", formatter, evaluator).ifBlank { null },
                            descricao = row.requiredValue(columns, "descricao", formatter, evaluator),
                            unidade = row.requiredValue(columns, "unidade", formatter, evaluator),
                            quantidade = row.decimal(columns, "quantidade", formatter, evaluator),
                            custoUnitario = row.decimal(columns, "custo_unitario", formatter, evaluator),
                        )
                    }.toList()
            }
        } catch (exception: ValidationException) {
            throw exception
        } catch (exception: Exception) {
            throw ValidationException("Não foi possível ler a planilha XLSX.")
        }

    private fun Row.requiredValue(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
    ): String =
        value(columns, name, formatter, evaluator).takeIf(String::isNotBlank)
            ?: throw ValidationException("Valor obrigatório ausente na linha ${rowNum + 1}.", mapOf("coluna" to name))

    private fun Row.value(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
    ): String = formatter.formatCellValue(getCell(columns.getValue(name)), evaluator).trim()

    private fun Row.decimal(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
    ): BigDecimal {
        val raw = requiredValue(columns, name, formatter, evaluator)
        val normalized = if (raw.contains(',')) raw.replace(".", "").replace(',', '.') else raw
        val value = normalized.toBigDecimalOrNull()
            ?: throw ValidationException("Número inválido na linha ${rowNum + 1}.", mapOf("coluna" to name))
        if (value < BigDecimal.ZERO) {
            throw ValidationException("Número negativo na linha ${rowNum + 1}.", mapOf("coluna" to name))
        }
        return value
    }

    private fun Row.isBlank(
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
    ): Boolean = none { formatter.formatCellValue(it, evaluator).isNotBlank() }

    private fun normalize(value: String): String =
        Normalizer
            .normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')

    private companion object {
        val REQUIRED_COLUMNS = setOf("descricao", "unidade", "quantidade", "custo_unitario")
    }
}
