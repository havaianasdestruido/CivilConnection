package br.com.civilconnection.config

import io.ktor.server.config.ApplicationConfig

/** Configuration is read from application.yaml and can be overridden with environment variables. */
data class AppConfig(
    val environment: String,
    val database: DatabaseConfig,
    val supabase: SupabaseConfig,
    val allowedOrigins: Set<String>,
    val webhookSecret: String,
    val importMaxBytes: Long,
    val importMaxRows: Int,
) {
    val isProduction: Boolean = environment.equals("production", ignoreCase = true)

    init {
        require(allowedOrigins.isNotEmpty()) { "ALLOWED_ORIGINS must contain at least one origin" }
        if (isProduction) {
            require(webhookSecret.length >= 32) { "WEBHOOK_SECRET must have at least 32 characters in production" }
            require(allowedOrigins.none { it.contains("localhost") }) {
                "localhost is not a valid production CORS origin"
            }
        }
    }

    companion object {
        fun from(config: ApplicationConfig): AppConfig =
            AppConfig(
                environment = config.property("app.environment").getString(),
                database =
                    DatabaseConfig(
                        url = config.property("app.database.url").getString(),
                        user = config.property("app.database.user").getString(),
                        password = config.property("app.database.password").getString(),
                        maximumPoolSize = config.property("app.database.maximumPoolSize").getString().toInt(),
                    ),
                supabase =
                    SupabaseConfig(
                        url = config.property("app.supabase.url").getString().trimEnd('/'),
                        jwksUrl = config.property("app.supabase.jwksUrl").getString(),
                        jwtIssuer = config.property("app.supabase.jwtIssuer").getString().trimEnd('/'),
                        jwtAudience = config.property("app.supabase.jwtAudience").getString(),
                    ),
                allowedOrigins =
                    config
                        .property("app.http.allowedOrigins")
                        .getString()
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .toSet(),
                webhookSecret = config.property("app.webhook.secret").getString(),
                importMaxBytes = config.property("app.import.maxBytes").getString().toLong(),
                importMaxRows = config.property("app.import.maxRows").getString().toInt(),
            )
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int,
)

data class SupabaseConfig(
    val url: String,
    val jwksUrl: String,
    val jwtIssuer: String,
    val jwtAudience: String,
)
