# Banco de Dados — Sistema de Construção Civil

## 1. Visão geral

| Item | Escolha |
|---|---|
| SGBD | PostgreSQL 15+ (gerenciado pelo **Supabase**) |
| Autenticação | Supabase Auth (JWT, e-mail/senha + magic link; opcional SSO Google) |
| Autorização | **Row Level Security (RLS)** por organização e papel |
| Arquivos | Supabase Storage (plantas, fotos de obra, NF, contratos) |
| Tempo real | Supabase Realtime (diário de obra, status de tarefas) |
| Migrations | Supabase CLI (`supabase/migrations/*.sql`) versionadas no Git |
| Seeds / testes | `supabase/seed.sql` + `pgTAP` para testar políticas RLS |
| Ambientes | `local` (Docker via CLI) → `staging` → `production` (projetos Supabase separados) |

## 2. Modelo multi-tenant

Toda tabela de negócio possui `organizacao_id`. O acesso é controlado por RLS usando uma tabela de membros.

```
organizacoes ──< membros >── auth.users
      │
      └──< obras ──< etapas ──< tarefas
              │         └──< medicoes
              ├──< diario_obra (RDO) ──< diario_fotos
              ├──< orcamento_itens
              ├──< movimentacoes_estoque >── materiais
              ├──< compras >── fornecedores
              ├──< documentos
              └──< equipe_obra >── funcionarios
```

## 3. Entidades principais

| Tabela | Descrição |
|---|---|
| `organizacoes` | Construtora / empresa (tenant) |
| `membros` | Vínculo usuário ↔ organização + `papel` |
| `obras` | Projeto de construção (endereço, datas, status, orçamento total) |
| `etapas` | Fases da obra (fundação, estrutura, acabamento…) |
| `tarefas` | Atividades por etapa, responsável, % concluído, datas previstas/reais |
| `medicoes` | Medições periódicas de avanço físico-financeiro |
| `orcamento_itens` | Itens de orçamento (código SINAPI/TCPO, unidade, quantidade, custo unitário) |
| `materiais` | Catálogo de insumos |
| `movimentacoes_estoque` | Entradas/saídas por obra |
| `fornecedores` | Cadastro de fornecedores |
| `compras` | Pedidos de compra e itens |
| `funcionarios` / `equipe_obra` | Mão de obra e alocação |
| `diario_obra` | RDO: clima, efetivo, atividades, ocorrências |
| `diario_fotos` | Fotos vinculadas ao RDO (path no Storage) |
| `documentos` | Metadados de arquivos (projetos, ART, contratos) |
| `audit_log` | Trilha de auditoria (quem alterou o quê) |

## 4. Papéis (RBAC)

`papel` enum: `admin`, `engenheiro`, `mestre_obras`, `financeiro`, `suprimentos`, `leitor`.

| Papel | Permissões resumidas |
|---|---|
| admin | Tudo na organização |
| engenheiro | CRUD obras, etapas, tarefas, medições, orçamento |
| mestre_obras | CRUD diário de obra, tarefas (atualização de progresso), estoque |
| financeiro | Leitura geral + CRUD compras/medições financeiras |
| suprimentos | CRUD materiais, fornecedores, compras, estoque |
| leitor | Somente leitura |

## 5. Exemplo de DDL

```sql
create type papel_membro as enum
  ('admin','engenheiro','mestre_obras','financeiro','suprimentos','leitor');

create table organizacoes (
  id uuid primary key default gen_random_uuid(),
  nome text not null,
  cnpj text unique,
  criado_em timestamptz not null default now()
);

create table membros (
  organizacao_id uuid references organizacoes on delete cascade,
  user_id uuid references auth.users on delete cascade,
  papel papel_membro not null default 'leitor',
  primary key (organizacao_id, user_id)
);

create table obras (
  id uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  nome text not null,
  endereco text,
  status text not null default 'planejada'
    check (status in ('planejada','em_andamento','pausada','concluida')),
  data_inicio date,
  data_prevista_fim date,
  orcamento_total numeric(14,2) default 0,
  criado_em timestamptz not null default now()
);

create table tarefas (
  id uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  etapa_id uuid not null references etapas on delete cascade,
  titulo text not null,
  responsavel_id uuid references auth.users,
  percentual smallint not null default 0 check (percentual between 0 and 100),
  inicio_previsto date,
  fim_previsto date,
  fim_real date
);

create index on obras (organizacao_id);
create index on tarefas (organizacao_id, etapa_id);
```

## 6. Row Level Security

```sql
-- Funções auxiliares (security definer evita recursão de RLS)
create function public.minhas_orgs() returns setof uuid
language sql stable security definer set search_path = public as $$
  select organizacao_id from membros where user_id = auth.uid()
$$;

create function public.tem_papel(org uuid, variadic papeis papel_membro[])
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from membros
    where organizacao_id = org and user_id = auth.uid() and papel = any(papeis)
  )
$$;

alter table obras enable row level security;

create policy "obras_select" on obras for select
  using (organizacao_id in (select minhas_orgs()));

create policy "obras_write" on obras for all
  using (tem_papel(organizacao_id, 'admin','engenheiro'))
  with check (tem_papel(organizacao_id, 'admin','engenheiro'));
```

> **Regra:** nenhuma tabela em `public` sem `enable row level security`. Validar no CI com um script que lista tabelas sem RLS.

## 7. Storage

| Bucket | Conteúdo | Acesso |
|---|---|---|
| `diario-fotos` | Fotos do RDO | Privado; path `{organizacao_id}/{obra_id}/{rdo_id}/…` |
| `documentos` | Plantas, ART, contratos | Privado; policy baseada no 1º segmento do path |
| `notas-fiscais` | NFs de compra | Privado |

URLs de acesso via **signed URLs** de curta duração.

## 8. Lógica no banco

- **Triggers:** `updated_at` automático; `audit_log` em tabelas críticas (obras, medições, compras).
- **Views:** `vw_obra_resumo` (avanço físico %, custo realizado × orçado, atraso).
- **Funções (RPC):** `fn_registrar_medicao(obra_id, periodo)` para operações transacionais.
- **Extensões:** `pgcrypto`, `pg_trgm` (busca de materiais), `pg_cron` (relatórios/rotinas), `postgis` (opcional: geolocalização das obras).

## 9. Backup e operação

- Backups diários automáticos do Supabase (PITR em plano Pro).
- Migrations aplicadas via CI (`supabase db push`) apenas em merge na `main`.
- Monitoramento: Supabase Logs + alerta de queries lentas (`pg_stat_statements`).
- LGPD: dados pessoais de funcionários mínimos, criptografia em repouso (padrão Supabase), política de retenção para `audit_log`.
