package br.com.civilconnection

import br.com.civilconnection.api.configureRoutes
import br.com.civilconnection.auth.configureAuthentication
import br.com.civilconnection.config.AppConfig
import br.com.civilconnection.config.configureDependencyInjection
import br.com.civilconnection.config.configureHttpPlugins
import io.ktor.server.application.Application

fun Application.module() {
    val config = AppConfig.from(environment.config)
    configureDependencyInjection(config)
    configureHttpPlugins(config)
    configureAuthentication(
        jwksUrl = config.supabase.jwksUrl,
        issuer = config.supabase.jwtIssuer,
        audience = config.supabase.jwtAudience,
    )
    configureRoutes()
}
