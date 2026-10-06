package br.com.civilconnection.infrastructure.db

import br.com.civilconnection.application.DiarioRepository
import br.com.civilconnection.domain.Actor
import br.com.civilconnection.domain.DiarioFoto
import br.com.civilconnection.domain.DiarioObra
import java.time.LocalDate
import java.util.UUID

class JdbcDiarioRepository(
    private val database: DatabaseFactory,
) : DiarioRepository {
    override suspend fun buscar(
        actor: Actor,
        diarioId: UUID,
    ): DiarioObra? =
        database.asUser(actor) {
            val diario =
                exec(
                    """
                    select d.id, d.obra_id, o.nome as obra_nome, d.data, d.clima_manha,
                           d.clima_tarde, d.efetivo, d.atividades, d.ocorrencias
                      from public.diario_obra d
                      join public.obras o on o.id = d.obra_id
                     where d.id = '$diarioId'::uuid
                    """.trimIndent(),
                ) { result ->
                    if (!result.next()) return@exec null
                    DiarioObra(
                        id = result.getObject("id", UUID::class.java),
                        obraId = result.getObject("obra_id", UUID::class.java),
                        obraNome = result.getString("obra_nome"),
                        data = result.getObject("data", LocalDate::class.java),
                        climaManha = result.getString("clima_manha"),
                        climaTarde = result.getString("clima_tarde"),
                        efetivo = result.getInt("efetivo"),
                        atividades = result.getString("atividades"),
                        ocorrencias = result.getString("ocorrencias"),
                        fotos = emptyList(),
                    )
                } ?: return@asUser null

            val fotos =
                exec(
                    """
                    select storage_path, legenda
                      from public.diario_fotos
                     where diario_id = '$diarioId'::uuid
                     order by criado_em
                    """.trimIndent(),
                ) { result ->
                    buildList {
                        while (result.next()) {
                            add(DiarioFoto(result.getString("storage_path"), result.getString("legenda")))
                        }
                    }
                }.orEmpty()
            diario.copy(fotos = fotos)
        }
}
