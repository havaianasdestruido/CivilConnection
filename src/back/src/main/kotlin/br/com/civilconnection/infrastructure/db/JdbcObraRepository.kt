package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.application.ObraRepository
import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.CurvaSPoint
import br.com.civilconnection.domain.CustosResumo
import br.com.civilconnection.domain.Medicao
import br.com.civilconnection.domain.NotFoundException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class JdbcObraRepository(
    private val database: DatabaseFactory,
) : ObraRepository {
    override suspend fun registrarMedicao(
        actor: Actor,
        obraId: UUID,
        periodo: LocalDate,
    ): Medicao =
        database.asUser(actor) {
            val sql =
                """
                select id, obra_id, periodo, percentual_fisico_acum, valor_periodo,
                       valor_acumulado, status
                  from public.fn_registrar_medicao('$obraId'::uuid, '$periodo'::date)
                """.trimIndent()
            exec(sql) { result ->
                if (!result.next()) throw NotFoundException("Obra não encontrada.")
                Medicao(
                    id = result.getObject("id", UUID::class.java),
                    obraId = result.getObject("obra_id", UUID::class.java),
                    periodo = result.getObject("periodo", LocalDate::class.java),
                    percentualFisicoAcumulado = result.getBigDecimal("percentual_fisico_acum"),
                    valorPeriodo = result.getBigDecimal("valor_periodo"),
                    valorAcumulado = result.getBigDecimal("valor_acumulado"),
                    status = result.getString("status"),
                )
            } ?: throw NotFoundException("Obra não encontrada.")
        }

    override suspend fun curvaS(
        actor: Actor,
        obraId: UUID,
    ): List<CurvaSPoint> =
        database.asUser(actor) {
            val sql =
                """
                with obra as (
                  select id,
                         coalesce(data_inicio, current_date) as inicio,
                         greatest(coalesce(data_prevista_fim, current_date), coalesce(data_inicio, current_date)) as fim
                    from public.obras
                   where id = '$obraId'::uuid
                ), meses as (
                  select o.inicio, o.fim, gs::date as periodo
                    from obra o
                    cross join lateral generate_series(
                      date_trunc('month', o.inicio)::date,
                      date_trunc('month', o.fim)::date,
                      interval '1 month'
                    ) gs
                )
                select m.periodo,
                       case
                         when m.fim = m.inicio then 100::numeric
                         else least(100, greatest(0,
                           round(100.0 * ((m.periodo + interval '1 month - 1 day')::date - m.inicio)
                             / nullif(m.fim - m.inicio, 0), 2)))
                       end as planejado,
                       coalesce(real.percentual_fisico_acum, 0) as realizado,
                       coalesce(real.valor_acumulado, 0) as valor_realizado
                  from meses m
                  left join lateral (
                    select percentual_fisico_acum, valor_acumulado
                      from public.medicoes md
                     where md.obra_id = '$obraId'::uuid
                       and md.periodo <= m.periodo
                     order by md.periodo desc
                     limit 1
                  ) real on true
                 order by m.periodo
                """.trimIndent()
            val points =
                exec(sql) { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                CurvaSPoint(
                                    periodo = result.getObject("periodo", LocalDate::class.java),
                                    planejadoPercentual = result.getBigDecimal("planejado"),
                                    realizadoPercentual = result.getBigDecimal("realizado"),
                                    realizadoValor = result.getBigDecimal("valor_realizado"),
                                ),
                            )
                        }
                    }
                }.orEmpty()
            if (points.isEmpty()) throw NotFoundException("Obra não encontrada.")
            points
        }

    override suspend fun custos(
        actor: Actor,
        obraId: UUID,
    ): CustosResumo =
        database.asUser(actor) {
            val sql =
                """
                select r.obra_id, r.custo_orcado, r.custo_realizado, r.saldo_orcamento,
                       coalesce((
                         select sum(c.valor_total)
                           from public.compras c
                          where c.obra_id = r.obra_id and c.status in ('pedido', 'parcial')
                       ), 0) as comprometido_em_aberto
                  from public.vw_obra_resumo r
                 where r.obra_id = '$obraId'::uuid
                """.trimIndent()
            exec(sql) { result ->
                if (!result.next()) throw NotFoundException("Obra não encontrada.")
                CustosResumo(
                    obraId = result.getObject("obra_id", UUID::class.java),
                    custoOrcado = result.getBigDecimal("custo_orcado") ?: BigDecimal.ZERO,
                    custoRealizado = result.getBigDecimal("custo_realizado") ?: BigDecimal.ZERO,
                    saldoOrcamento = result.getBigDecimal("saldo_orcamento") ?: BigDecimal.ZERO,
                    comprometidoEmAberto = result.getBigDecimal("comprometido_em_aberto") ?: BigDecimal.ZERO,
                )
            } ?: throw NotFoundException("Obra não encontrada.")
        }
}
