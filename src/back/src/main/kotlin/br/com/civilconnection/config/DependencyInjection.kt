package br.com.civilconnection.config

import br.com.civilconnection.application.CompraRepository
import br.com.civilconnection.application.CompraService
import br.com.civilconnection.application.DiarioRepository
import br.com.civilconnection.application.DiarioService
import br.com.civilconnection.application.ObraRepository
import br.com.civilconnection.application.ObraService
import br.com.civilconnection.application.OrcamentoRepository
import br.com.civilconnection.application.OrcamentoService
import br.com.civilconnection.application.PdfRenderer
import br.com.civilconnection.application.PlanilhaOrcamentoReader
import br.com.civilconnection.infrastructure.db.DatabaseFactory
import br.com.civilconnection.infrastructure.db.JdbcCompraRepository
import br.com.civilconnection.infrastructure.db.JdbcDiarioRepository
import br.com.civilconnection.infrastructure.db.JdbcObraRepository
import br.com.civilconnection.infrastructure.db.JdbcOrcamentoRepository
import br.com.civilconnection.infrastructure.db.XlsxOrcamentoReader
import br.com.civilconnection.infrastructure.pdf.OpenPdfRenderer
import br.com.civilconnection.infrastructure.webhook.WebhookVerifier
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencyInjection(appConfig: AppConfig) {
    install(Koin) {
        slf4jLogger()
        modules(
            module {
                single { appConfig }
                single { DatabaseFactory(appConfig.database) }
                single<ObraRepository> { JdbcObraRepository(get()) }
                single<DiarioRepository> { JdbcDiarioRepository(get()) }
                single<OrcamentoRepository> { JdbcOrcamentoRepository(get()) }
                single<CompraRepository> { JdbcCompraRepository(get()) }
                single<PdfRenderer> { OpenPdfRenderer() }
                single<PlanilhaOrcamentoReader> { XlsxOrcamentoReader() }
                single { ObraService(get()) }
                single { DiarioService(get(), get()) }
                single {
                    OrcamentoService(
                        repository = get(),
                        reader = get(),
                        maxBytes = appConfig.importMaxBytes,
                        maxRows = appConfig.importMaxRows,
                    )
                }
                single { CompraService(get()) }
                single { WebhookVerifier(appConfig.webhookSecret) }
            },
        )
    }

    monitor.subscribe(ApplicationStopped) {
        runCatching { getKoin().get<DatabaseFactory>().close() }
    }
}
