# Backend — Civil Connection

## 1. Visão geral

O backend é **100% Kotlin (Ktor)**. Não há serviços em Node no backend: o Node.js é usado **apenas como ferramenta de build do frontend** (Vite/TypeScript).

| Camada | Tecnologia | Responsabilidade |
|---|---|---|
| **Frontend** | React + TypeScript, **estático no GitHub Pages** | Interface; sem servidor |
| **BaaS** | Supabase (PostgREST, Auth, Storage, Realtime) | CRUD simples direto do front, protegido por RLS |
| **API de domínio** | **Kotlin + Ktor** | Regras de negócio, cálculos, relatórios/PDF, webhooks, integrações, jobs |

```
[React estático @ GitHub Pages]
   │  ├── supabase-js ──────────► Supabase (CRUD + Auth + Storage + Realtime, RLS)
   │  └── fetch (JWT) ──► API Kotlin (Ktor) ──► Postgres (Supabase) / Storage / APIs externas
```

**Critério de escolha:** CRUD simples respeitando permissões → direto no Supabase. Regra de negócio, múltiplas tabelas, cálculo, PDF, integração ou job → API Kotlin.

> **Importante sobre hospedagem:** o GitHub Pages só serve arquivos estáticos e **não executa Kotlin/JVM**. O front fica 100% no GH Pages; a API Kotlin precisa rodar em um host de containers (seção 5). Isso é inerente ao GH Pages, não uma escolha de arquitetura.

## 2. API de domínio (Kotlin)

A implementação fica em `src/back/`; o contrato versionado está em
`src/back/src/main/resources/openapi.yaml` e as instruções de execução em `src/back/README.md`.

| Item | Escolha |
|---|---|
| Linguagem / JVM | Kotlin 2.x, JDK 21 |
| Framework | **Ktor** (server-netty) |
| Build | Gradle (Kotlin DSL) |
| Serialização | `kotlinx.serialization` |
| Acesso a dados | **Exposed** (ou jOOQ) + HikariCP |
| Injeção de dependência | Koin |
| HTTP client | Ktor Client (Supabase Storage, APIs externas) |
| Auth | `ktor-server-auth-jwt` validando o JWT do Supabase (JWKS do projeto) |
| Jobs agendados | `db-scheduler` ou Quartz (+ coroutines) |
| PDF (RDO, medições) | OpenPDF ou Flying Saucer (HTML → PDF) |
| E-mail | Resend/SES via HTTP (Ktor Client) |
| Imagens (miniaturas) | Thumbnailator |
| Testes | JUnit 5, Kotest, Testcontainers (Postgres), MockK |
| Docs da API | OpenAPI (ktor-openapi) |
| Lint / formato | ktlint + detekt |

### Estrutura de pacotes (hexagonal simplificada)

```
src/main/kotlin/br/com/civilconnection/
├── Application.kt
├── config/            # env, DI, plugins Ktor
├── auth/              # verificação JWT, extração de organizacao_id e papel
├── domain/
│   ├── obras/
│   ├── medicoes/      # avanço físico-financeiro
│   ├── orcamento/     # composição de custos, BDI
│   └── estoque/
├── application/       # casos de uso (services)
├── infrastructure/
│   ├── db/            # repositórios Exposed
│   ├── storage/       # cliente Supabase Storage
│   ├── pdf/           # geração de RDO e relatórios
│   ├── mail/
│   └── jobs/          # tarefas agendadas
└── api/               # rotas Ktor + DTOs (inclui /webhooks)
```

### Endpoints exemplo

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/v1/obras/{id}/medicoes` | Registra medição (transacional) |
| `GET` | `/v1/obras/{id}/curva-s` | Curva S planejado × realizado |
| `GET` | `/v1/obras/{id}/custos` | Custo orçado × realizado |
| `GET` | `/v1/diario/{id}/pdf` | Gera PDF do RDO |
| `POST` | `/v1/orcamentos/importar` | Importa planilha (SINAPI/Excel) |
| `POST` | `/v1/compras/{id}/aprovar` | Fluxo de aprovação de compra |
| `POST` | `/webhooks/supabase` | Recebe Database Webhooks (assinatura validada) |
| `GET` | `/health` | Health check |

### Segurança

- Valida JWT em toda rota; extrai `sub` e resolve `organizacao_id`/`papel` consultando `membros`.
- Conexão ao banco com **role restrita**, aplicando os claims do JWT por transação para manter a RLS efetiva; `service_role` só em tarefas de sistema (jobs, webhooks).
- CORS restrito a `https://<org>.github.io` (e domínio customizado, se houver).
- Rate limiting e validação de payload; webhooks com assinatura/segredo compartilhado.
- Segredos somente por variáveis de ambiente.

## 3. Hospedagem da API

| Componente | Opção sugerida |
|---|---|
| API Kotlin | Container Docker em **Fly.io**, **Render** ou **Google Cloud Run** (scale-to-zero) |
| Banco / Auth / Storage | Supabase Cloud |
| Frontend | **GitHub Pages** |

Dockerfile: build multi-stage (`gradle` → `eclipse-temurin:21-jre`). Para reduzir cold start em scale-to-zero, considerar CDS/AppCDS ou GraalVM native image.

## 4. CI/CD (GitHub Actions)

- **PR:** `ktlintCheck`, `detekt`, testes (Testcontainers).
- **Merge na `main`:** build da imagem → push (GHCR) → deploy no provedor → `supabase db push`.
- Ambientes `staging` e `production` com secrets separados.

## 5. Observabilidade

- Logs JSON (Logback + logstash encoder).
- Métricas Micrometer → Prometheus/Grafana Cloud.
- Sentry para erros; OpenTelemetry opcional.
- `X-Request-Id` propagado do front.

## 6. Variáveis de ambiente (exemplo)

```
SUPABASE_URL=
SUPABASE_JWKS_URL=
DATABASE_URL=                # role restrita
SUPABASE_SERVICE_ROLE_KEY=   # só na API, jamais no front
WEBHOOK_SECRET=
ALLOWED_ORIGINS=https://<org>.github.io
SENTRY_DSN=
```
