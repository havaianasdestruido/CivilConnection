# Civil Connection — API Kotlin

Esqueleto executável do backend de domínio. O frontend continua estático e usa o Supabase diretamente para CRUD simples; esta API concentra operações transacionais, cálculos, documentos e integrações.

## Mapa da arquitetura

```text
React/GitHub Pages
  ├─ supabase-js ───────────────► Auth + PostgREST + Storage + Realtime
  └─ HTTP + Bearer JWT
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Ktor                                                               │
│  api (rotas/DTOs) → application (casos de uso/ports) → domain      │
│                                     │                               │
│                                     ▼                               │
│  infrastructure: Exposed/JDBC · OpenPDF · Apache POI · webhooks     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ SET LOCAL ROLE authenticated
                               │ request.jwt.claim.sub = JWT sub
                               ▼
                     PostgreSQL/Supabase + RLS
```

A API não confia em `organizacao_id` enviado pelo cliente. Cada transação propaga o `sub` validado do JWT para `auth.uid()` e executa sob a role `authenticated`; as políticas RLS do banco são a fronteira final de autorização.

## Superfície inicial

| Método | Rota | Responsabilidade |
|---|---|---|
| `GET` | `/health` | Liveness, sem consultar dependências |
| `GET` | `/health/ready` | Readiness do PostgreSQL |
| `POST` | `/v1/obras/{id}/medicoes` | Recalcula/registra medição mensal via RPC transacional |
| `GET` | `/v1/obras/{id}/curva-s` | Planejado × realizado por mês |
| `GET` | `/v1/obras/{id}/custos` | Orçado, realizado, saldo e compras em aberto |
| `GET` | `/v1/diario/{id}/pdf` | Gera o RDO em PDF |
| `POST` | `/v1/orcamentos/importar` | Importa uma planilha XLSX |
| `POST` | `/v1/compras/{id}/aprovar` | Transiciona compra de `rascunho` para `pedido` |
| `POST` | `/webhooks/supabase` | Valida HMAC-SHA256 e aceita evento do Supabase |
| `GET` | `/openapi.yaml` | Contrato OpenAPI versionado |

Todas as rotas `/v1` exigem `Authorization: Bearer <JWT do Supabase>`.

### Formato da planilha

`POST /v1/orcamentos/importar?obraId=<uuid>&base=SINAPI` recebe o arquivo XLSX como corpo binário (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`). A primeira aba deve conter:

```text
codigo | descricao | unidade | quantidade | custo_unitario
```

`codigo` é opcional. O arquivo padrão aceita até 5 MiB e 10.000 linhas.

## Executar

Requisitos: JDK 21 e PostgreSQL/Supabase local com o schema em `../db/`.

```bash
cp .env.example .env
# Exporte as variáveis de .env no shell, então:
./gradlew run

./gradlew ktlintCheck detekt test
./gradlew build

docker build -t civil-connection-api .
docker run --env-file .env -p 8080:8080 civil-connection-api
```

A aplicação escuta em `0.0.0.0:$PORT` (8080 por padrão). Para o preview remoto ou produção, inclua a origem exata em `ALLOWED_ORIGINS`; várias origens são separadas por vírgula.

## Usuário do banco

`DATABASE_USER` não deve ser superusuário nem `service_role`. Ele precisa conectar ao schema e executar apenas `SET ROLE authenticated`. A API aplica os claims localmente em cada transação. Jobs de sistema que venham a exigir privilégios elevados devem usar um pool e credenciais separados — nunca o fluxo HTTP comum.

## O que este esqueleto deixa como próximo incremento

- persistência/idempotência e processamento assíncrono dos eventos de webhook;
- imagens reais no PDF por URL assinada do Storage;
- peso físico dos itens/tarefas para uma Curva S de linha de base (o cálculo atual é temporal linear);
- e-mail, jobs agendados e métricas exportadas;
- testes de integração com Supabase/Postgres e RLS ativa no CI.

Esses limites são explícitos para não sugerir que integrações ainda não configuradas estejam prontas para produção.
