# CLAUDE.md — Civil Connection

Sistema de gestão de obras de construção civil (obras, cronograma, medições, orçamento, estoque, compras e diário de obra/RDO).

## Fonte da verdade (leia antes de codar)

As specs da stack estão em `docs/stack/`. Em caso de conflito entre este arquivo e as specs, **as specs vencem**; atualize o que estiver desatualizado.

| Área | Spec |
|---|---|
| Banco de dados | `docs/stack/db.md` |
| Backend | `docs/stack/backend.md` |
| Frontend | `docs/stack/frontend.md` |

Leia só a spec da área em que está trabalhando. Mudanças que alterem decisões de arquitetura devem atualizar a spec no mesmo PR.

## Stack em uma linha

- **Frontend:** React 18 + TypeScript + Vite, **100% estático no GitHub Pages** (Node.js só como ferramenta de build)
- **DB/BaaS:** Supabase (Postgres, Auth, Storage, Realtime) com RLS
- **Backend:** **somente Kotlin + Ktor** (regras de negócio, cálculos, PDF, webhooks, jobs). Não há backend em Node.
- **Hospedagem:** front no GitHub Pages; a API Kotlin roda em container (Fly.io/Render/Cloud Run), pois o GH Pages não executa JVM

## Estrutura do repositório (esperada)

```
.
├── CLAUDE.md
├── docs/stack/            # specs de banco, backend e frontend
├── src/
│   ├── db/                # migrations, seed e testes SQL
│   ├── web/               # React + Vite
│   └── back/              # Kotlin/Ktor (Gradle)
└── .github/workflows/
```

> O código usa a estrutura histórica em `src/`; não mova as áreas sem uma decisão de arquitetura.

## Regras inegociáveis

1. **Toda tabela em `public` tem RLS habilitada** e `organizacao_id` (multi-tenant). Sem exceção.
2. **Nunca** usar `service_role` ou qualquer segredo no frontend. Variáveis `VITE_*` são públicas.
3. **Schema só muda por migration** em `supabase/migrations/` (nunca editar o banco manualmente). Migrations são imutáveis depois do merge.
4. **Decisão de camada:** CRUD simples sob RLS → direto do front via `supabase-js`; regra de negócio, multi-tabela, cálculo, PDF, integração ou job → API Kotlin. **Não criar serviços em Node/TS.**
4a. **O front permanece estático e hospedado no GitHub Pages**: sem SSR, sem servidor, sem segredos em runtime.
5. **Autorização real está no banco.** Checagem de papel na UI é só experiência de usuário.
6. TypeScript em `strict: true`; proibido `any` sem comentário justificando.
7. Tipos do banco são **gerados** (`supabase gen types`), nunca escritos à mão.
8. CORS restrito ao domínio do GitHub Pages; JWT do Supabase validado em toda rota da API.
9. Strings de UI e mensagens ao usuário em **pt-BR**; código, commits e nomes de branches em inglês ou português de forma consistente com o que já existe.
10. Não commitar `.env`, chaves ou dumps de produção.

## Convenções

- **Banco:** `snake_case`, nomes de tabela no plural em português (`obras`, `medicoes`), `uuid` como PK, `timestamptz` para datas/hora, dinheiro em `numeric(14,2)`.
- **Kotlin:** ktlint + detekt; camadas `api → application → domain → infrastructure`; DTOs separados de entidades; testes com Testcontainers.
- **TypeScript:** ESLint + Prettier; estrutura por feature (`features/<nome>/{api,components,pages,schemas}`); validação com Zod; dados remotos com TanStack Query.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`), escopo opcional (`feat(medicoes): …`).
- **PRs:** pequenos, com descrição do que mudou e como testar; CI verde antes do merge.

## Comandos

> Ajuste se os scripts reais forem diferentes.

```bash
# Supabase (raiz)
supabase start                       # sobe stack local
supabase migration new <nome>        # nova migration
supabase db reset                    # recria local com migrations + seed
supabase test db                     # testes pgTAP (RLS)
supabase gen types typescript --local > frontend/src/types/database.ts

# Frontend
cd frontend && pnpm install
pnpm dev | pnpm lint | pnpm typecheck | pnpm test | pnpm build

# Backend Kotlin
cd src/back
./gradlew run
./gradlew ktlintCheck detekt test build
```

## Definição de pronto (DoD)

- [ ] Lint, typecheck e testes passando nas áreas tocadas
- [ ] Nova tabela → RLS + políticas + teste pgTAP + tipos regenerados
- [ ] Novo endpoint → teste de integração + OpenAPI atualizado + cliente TS regenerado
- [ ] Nova tela → responsiva (mobile-first), pt-BR, estados de loading/erro/vazio
- [ ] Spec em `docs/stack/` atualizada se a decisão de arquitetura mudou
- [ ] Sem segredos no diff

## Skills do projeto

Skills ficam em `.claude/skills/<nome>/SKILL.md`. Use a skill correspondente sempre que a tarefa se encaixar na descrição dela.

### Banco de dados (Supabase / Postgres)

| # | Skill | Quando usar |
|---|---|---|
| 1 | `supabase-migration` | Criar/alterar tabelas, índices, enums, triggers e views seguindo as convenções de `01-database.md`; gera o arquivo em `supabase/migrations/` já com `organizacao_id` e RLS habilitada. |
| 2 | `rls-policy-audit` | Escrever e auditar políticas RLS por papel (`admin`, `engenheiro`, `mestre_obras`, `financeiro`, `suprimentos`, `leitor`); cria testes pgTAP e lista tabelas sem RLS. |
| 3 | `supabase-types-sync` | Regenerar `database.ts` após migrations e corrigir os pontos do front/serviços que quebrarem por mudança de schema. |

### Backend (Kotlin)

| # | Skill | Quando usar |
|---|---|---|
| 4 | `ktor-endpoint` | Criar rota Ktor completa (DTO, caso de uso, repositório Exposed, validação JWT/papel, OpenAPI) respeitando a arquitetura em camadas de `02-backend.md`. |
| 5 | `kotlin-integration-tests` | Escrever testes com JUnit5 + Kotest + Testcontainers (Postgres com RLS ativa) para casos de uso e rotas. |
| 6 | `kotlin-jobs-reports` | Implementar em Kotlin geração de PDF (RDO/medição), webhooks do Supabase com validação de assinatura, envio de e-mail e jobs agendados (`db-scheduler`). |

### Frontend (React + TypeScript)

| # | Skill | Quando usar |
|---|---|---|
| 7 | `react-feature-scaffold` | Gerar uma feature nova em `frontend/src/features/<nome>/` (api com TanStack Query, schemas Zod, páginas, componentes shadcn/ui, rotas). |
| 8 | `frontend-design` | Direção visual e qualidade de UI (tipografia, hierarquia, evitar aparência genérica); aplicar em dashboards, Gantt e formulários. |
| 9 | `pwa-offline-rdo` | Implementar o fluxo offline do Diário de Obra: rascunho em IndexedDB (Dexie), fila de mutações, upload de fotos com compressão e sincronização ao reconectar. |

### Transversal

| # | Skill | Quando usar |
|---|---|---|
| 10 | `gh-pages-ci-deploy` | Criar/ajustar workflows do GitHub Actions: deploy do front no GH Pages (base path, fallback `404.html`), build/test do Kotlin, `supabase db push` e deploy do container da API. |

> Skill de contrato de API: ao alterar a API Kotlin, regenerar o cliente TS com `openapi-typescript` faz parte da skill `ktor-endpoint` (passo final).

### Como criar uma skill nova

Pasta `.claude/skills/<nome>/` com um `SKILL.md` contendo frontmatter (`name`, `description` — dizendo **quando** usar) seguido de instruções objetivas, exemplos e checklist. Mantenha cada skill focada em uma única tarefa.

## Fluxo de trabalho recomendado para o Claude

1. Identifique a(s) área(s) afetada(s) (db / back / front) e leia a spec correspondente em `docs/stack/`.
2. Planeje em poucos passos antes de editar quando a mudança tocar mais de uma camada.
3. Ordem para features full-stack: **migration + RLS → tipos → API (se necessário) → front**.
4. Rode lint/typecheck/testes da área e corrija antes de concluir.
5. Resuma ao final: o que mudou, como testar e o que ficou pendente.

## Segurança e dados

- Dados pessoais de funcionários (LGPD): coletar o mínimo necessário; nunca logar CPF/telefone/endereço.
- Arquivos no Storage sempre privados, com path `{organizacao_id}/{obra_id}/…` e URLs assinadas de curta duração.
- Ações críticas (obras, medições, compras) devem gerar registro em `audit_log`.
- Ao dúvida sobre permissão, escolha o comportamento **mais restritivo** e pergunte.

## O que perguntar antes de assumir

- Nova entidade de negócio sem papel/permissão definidos.
- Mudança que afete dados existentes em produção (migrations destrutivas).
- Introdução de dependência nova relevante (framework, serviço pago, provedor de hospedagem).
