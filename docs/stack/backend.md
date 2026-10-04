# Backend — Sistema de Construção Civil

## 1. Visão geral e divisão de responsabilidades

Como o front roda em **GitHub Pages (estático)**, não existe servidor próprio nele. O backend é dividido em três camadas:

| Camada | Tecnologia | Responsabilidade |
|---|---|---|
| **BaaS** | Supabase (PostgREST, Auth, Storage, Realtime) | CRUD simples direto do front, protegido por RLS |
| **API de domínio** | **Kotlin + Ktor** | Regras de negócio complexas: medições, cálculo de custos, curva S, orçamento, relatórios |
| **Serviços auxiliares** | **TypeScript + Node.js (Fastify)** | Webhooks, integrações externas, geração de PDF/RDO, jobs leves |

```
[React @ GH Pages]
   │  ├── supabase-js ──────────► Supabase (CRUD + Auth + Storage + Realtime, RLS)
   │  └── fetch (JWT) ──► API Kotlin (Ktor) ──► Postgres (Supabase)
   │                    └► Serviço Node/TS ───► Postgres / Storage / APIs externas
```

**Critério de escolha:** se a operação é só ler/gravar uma tabela respeitando permissões → direto no Supabase. Se envolve regra de negócio, múltiplas tabelas ou cálculo → API Kotlin. Se é integração/glue/IO → Node/TS.

## 2. API de domínio (Kotlin)

| Item | Escolha |
|---|---|
| Linguagem / JVM | Kotlin 2.x, JDK 21 |
| Framework | **Ktor** (server-netty) |
| Build | Gradle (Kotlin DSL) |
| Serialização | `kotlinx.serialization` |
| Acesso a dados | **Exposed** (ou jOOQ) + HikariCP |
| Injeção de dependência | Koin |
| Validação | Konform ou validação manual em camada de domínio |
| Auth | Plugin `ktor-server-auth-jwt` validando o JWT do Supabase (segredo/JWKS do projeto) |
| Testes | JUnit 5, Kotest, Testcontainers (Postgres), MockK |
| Docs da API | OpenAPI (ktor-openapi) |
| Lint / formato | ktlint + detekt |

### Estrutura de pacotes (hexagonal simplificada)

```
src/main/kotlin/br/com/obras/
├── Application.kt
├── config/            # env, DI, plugins Ktor
├── auth/              # verificação JWT, extração de organizacao_id e papel
├── domain/
│   ├── obras/
│   ├── medicoes/      # regras de medição e avanço físico-financeiro
│   ├── orcamento/     # composição de custos, BDI
│   └── estoque/
├── application/       # casos de uso (services)
├── infrastructure/
│   ├── db/            # repositórios Exposed
│   └── storage/       # cliente Supabase Storage
└── api/               # rotas Ktor + DTOs
```

### Endpoints exemplo

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/v1/obras/{id}/medicoes` | Registra medição (transacional) |
| `GET` | `/v1/obras/{id}/curva-s` | Curva S planejado × realizado |
| `GET` | `/v1/obras/{id}/custos` | Custo orçado × realizado |
| `POST` | `/v1/orcamentos/importar` | Importa planilha (SINAPI/Excel) |
| `POST` | `/v1/compras/{id}/aprovar` | Fluxo de aprovação de compra |
| `GET` | `/health` | Health check |

### Segurança

- Valida JWT em toda rota; extrai `sub` (user) e resolve `organizacao_id`/`papel` consultando `membros`.
- **Conexão ao banco com o usuário/role restrito**, e `set local request.jwt.claims` por transação para manter a RLS efetiva (evitar usar `service_role` para operações de usuário).
- CORS restrito ao domínio do GitHub Pages (`https://<org>.github.io`) e domínio customizado.
- Rate limiting (plugin Ktor) e validação de payload.
- Segredos apenas por variáveis de ambiente (nunca no repositório).

## 3. Serviços auxiliares (TypeScript / Node.js)

| Item | Escolha |
|---|---|
| Runtime | Node.js 22 LTS |
| Framework | **Fastify** + `@fastify/cors`, `@fastify/helmet` |
| Linguagem | TypeScript 5 (`strict: true`) |
| Validação | Zod |
| Supabase | `@supabase/supabase-js` (service role **somente** no servidor) |
| PDF | Playwright (HTML → PDF) ou `pdf-lib` |
| Jobs | `node-cron` / pg_cron + `pg-boss` (fila em Postgres) |
| Testes | Vitest + Supertest |
| Gerenciador de pacotes | pnpm |

Casos de uso: geração de PDF do **RDO** e relatórios de medição, envio de e-mails (Resend/SES), webhooks de Supabase (Database Webhooks), integrações (NF-e, ERP, WhatsApp API), processamento de imagens (miniaturas).

> **Alternativa:** funções leves podem rodar como **Supabase Edge Functions** (Deno/TS) para evitar hospedar mais um serviço.

## 4. Hospedagem

GitHub Pages não executa backend, então:

| Componente | Opção sugerida |
|---|---|
| API Kotlin | Container Docker em **Fly.io**, **Render** ou **Google Cloud Run** (scale-to-zero) |
| Serviço Node/TS | Mesmo provedor (container) ou Edge Functions |
| Banco / Auth / Storage | Supabase Cloud |

Dockerfile Kotlin: build multi-stage (`gradle` → `eclipse-temurin:21-jre`), imagem final < 250 MB.

## 5. CI/CD (GitHub Actions)

- **PR:** `ktlint`, `detekt`, testes Kotlin (Testcontainers); `eslint`, `tsc --noEmit`, `vitest` no Node.
- **Merge na `main`:** build da imagem → push (GHCR) → deploy no provedor → `supabase db push` para migrations.
- Ambientes `staging` e `production` com secrets separados (`environments` do GitHub).

## 6. Observabilidade

- Logs estruturados em JSON (Logback + logstash encoder / pino).
- Métricas Micrometer (Ktor) → Prometheus/Grafana Cloud.
- Tracing OpenTelemetry (opcional) e Sentry para erros.
- Correlation ID propagado do front (`X-Request-Id`).

## 7. Variáveis de ambiente (exemplo)

```
SUPABASE_URL=
SUPABASE_JWKS_URL=
DATABASE_URL=            # role restrita
SUPABASE_SERVICE_ROLE_KEY=   # só no serviço Node, nunca no front
ALLOWED_ORIGINS=https://<org>.github.io
SENTRY_DSN=
```
