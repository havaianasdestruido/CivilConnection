package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.application.PlanilhaOrcamentoReader
import br.com.civilconnection.domain.OrcamentoItemImportado
import br.com.civilconnection.domain.ValidationException
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigDecimal
import java.text.Normalizer

class XlsxOrcamentoReader : PlanilhaOrcamentoReader {
    override fun ler(conteudo: ByteArray): List<OrcamentoItemImportado> =
        try {
            XSSFWorkbook(ByteArrayInputStream(conteudo)).use { workbook ->
                val formatter = DataFormatter()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                readSheet(workbook.getSheetAt(0), formatter, evaluator)
            }
        } catch (exception: IOException) {
            throw ValidationException("Não foi possível ler a planilha XLSX.", cause = exception)
        } catch (exception: IllegalArgumentException) {
            throw ValidationException("O arquivo enviado não é uma planilha XLSX válida.", cause = exception)
        }

    private fun readSheet(
        sheet: Sheet,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): List<OrcamentoItemImportado> {
        val header = findHeader(sheet, formatter, evaluator)
        val columns =
            header.associate { cell ->
                normalize(formatter.formatCellValue(cell, evaluator)) to cell.columnIndex
            }
        validateColumns(columns)
        return sheet
            .asSequence()
            .dropWhile { it.rowNum <= header.rowNum }
            .filterNot { it.isBlank(formatter, evaluator) }
            .map { it.toBudgetItem(columns, formatter, evaluator) }
            .toList()
    }

    private fun findHeader(
        sheet: Sheet,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): Row =
        sheet.firstOrNull { row -> row.any { formatter.formatCellValue(it, evaluator).isNotBlank() } }
            ?: throw ValidationException("A planilha não possui cabeçalho.")

    private fun validateColumns(columns: Map<String, Int>) {
        val missing = REQUIRED_COLUMNS.firstOrNull { it !in columns } ?: return
        throw ValidationException(
            "Coluna obrigatória ausente na planilha.",
            mapOf("coluna" to missing),
        )
    }

    private fun Row.toBudgetItem(
        columns: Map<String, Int>,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ) = OrcamentoItemImportado(
        codigo = optionalValue(columns, "codigo", formatter, evaluator).ifBlank { null },
        descricao = requiredValue(columns, "descricao", formatter, evaluator),
        unidade = requiredValue(columns, "unidade", formatter, evaluator),
        quantidade = decimal(columns, "quantidade", formatter, evaluator),
        custoUnitario = decimal(columns, "custo_unitario", formatter, evaluator),
    )

    private fun Row.requiredValue(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): String =
        value(columns, name, formatter, evaluator).takeIf(String::isNotBlank)
            ?: throw ValidationException("Valor obrigatório ausente na linha ${rowNum + 1}.", mapOf("coluna" to name))

    private fun Row.value(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): String = formatter.formatCellValue(getCell(columns.getValue(name)), evaluator).trim()

    private fun Row.optionalValue(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): String {
        val columnIndex = columns[name] ?: return ""
        return formatter.formatCellValue(getCell(columnIndex), evaluator).trim()
    }

    private fun Row.decimal(
        columns: Map<String, Int>,
        name: String,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): BigDecimal {
        val raw = requiredValue(columns, name, formatter, evaluator)
        val normalized = if (raw.contains(',')) raw.replace(".", "").replace(',', '.') else raw
        val number =
            normalized.toBigDecimalOrNull()
                ?: throw ValidationException("Número inválido na linha ${rowNum + 1}.", mapOf("coluna" to name))
        if (number < BigDecimal.ZERO) {
            throw ValidationException("Número negativo na linha ${rowNum + 1}.", mapOf("coluna" to name))
        }
        return number
    }

    private fun Row.isBlank(
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
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
