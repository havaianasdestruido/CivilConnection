-- =====================================================================
-- Sistema de Construção Civil — Schema inicial (PostgreSQL 15+ / Supabase)
-- Multi-tenant por organizacao_id + RLS por papel
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Extensões
-- ---------------------------------------------------------------------
create extension if not exists pgcrypto with schema extensions;
create extension if not exists pg_trgm  with schema extensions;
-- Opcionais (habilite quando necessário):
-- create extension if not exists pg_cron;                      -- rotinas/relatórios
-- create extension if not exists postgis with schema extensions; -- geolocalização

-- ---------------------------------------------------------------------
-- 1. Tipos
-- ---------------------------------------------------------------------
create type papel_membro as enum
  ('admin','engenheiro','mestre_obras','financeiro','suprimentos','leitor');

create type tipo_movimentacao as enum ('entrada','saida');

create type status_compra as enum
  ('rascunho','pedido','parcial','recebido','cancelado');

-- ---------------------------------------------------------------------
-- 2. Tabelas
-- ---------------------------------------------------------------------
create table organizacoes (
  id            uuid primary key default gen_random_uuid(),
  nome          text not null,
  cnpj          text unique,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now()
);

create table membros (
  organizacao_id uuid not null references organizacoes on delete cascade,
  user_id        uuid not null references auth.users on delete cascade,
  papel          papel_membro not null default 'leitor',
  criado_em      timestamptz not null default now(),
  primary key (organizacao_id, user_id)
);

create table obras (
  id                uuid primary key default gen_random_uuid(),
  organizacao_id    uuid not null references organizacoes on delete cascade,
  nome              text not null,
  endereco          text,
  latitude          numeric(9,6),
  longitude         numeric(9,6),
  status            text not null default 'planejada'
                    check (status in ('planejada','em_andamento','pausada','concluida')),
  data_inicio       date,
  data_prevista_fim date,
  orcamento_total   numeric(14,2) default 0,
  criado_em         timestamptz not null default now(),
  atualizado_em     timestamptz not null default now()
);

create table etapas (
  id              uuid primary key default gen_random_uuid(),
  organizacao_id  uuid not null references organizacoes on delete cascade,
  obra_id         uuid not null references obras on delete cascade,
  nome            text not null,              -- fundação, estrutura, acabamento…
  ordem           int  not null default 0,
  status          text not null default 'pendente'
                  check (status in ('pendente','em_andamento','concluida')),
  inicio_previsto date,
  fim_previsto    date,
  criado_em       timestamptz not null default now(),
  atualizado_em   timestamptz not null default now()
);

create table tarefas (
  id              uuid primary key default gen_random_uuid(),
  organizacao_id  uuid not null references organizacoes on delete cascade,
  etapa_id        uuid not null references etapas on delete cascade,
  titulo          text not null,
  descricao       text,
  responsavel_id  uuid references auth.users on delete set null,
  percentual      smallint not null default 0 check (percentual between 0 and 100),
  inicio_previsto date,
  fim_previsto    date,
  fim_real        date,
  criado_em       timestamptz not null default now(),
  atualizado_em   timestamptz not null default now()
);

create table medicoes (
  id                      uuid primary key default gen_random_uuid(),
  organizacao_id          uuid not null references organizacoes on delete cascade,
  obra_id                 uuid not null references obras on delete cascade,
  periodo                 date not null,  -- 1º dia do mês de referência
  percentual_fisico_acum  numeric(5,2) not null default 0 check (percentual_fisico_acum between 0 and 100),
  valor_periodo           numeric(14,2) not null default 0,
  valor_acumulado         numeric(14,2) not null default 0,
  status                  text not null default 'rascunho'
                          check (status in ('rascunho','aprovada')),
  observacoes             text,
  criado_por              uuid references auth.users on delete set null,
  criado_em               timestamptz not null default now(),
  atualizado_em           timestamptz not null default now(),
  unique (obra_id, periodo)
);

create table orcamento_itens (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  obra_id        uuid not null references obras on delete cascade,
  etapa_id       uuid references etapas on delete set null,
  base           text not null default 'SINAPI' check (base in ('SINAPI','TCPO','PROPRIA')),
  codigo         text,
  descricao      text not null,
  unidade        text not null,
  quantidade     numeric(14,4) not null default 0 check (quantidade >= 0),
  custo_unitario numeric(14,4) not null default 0 check (custo_unitario >= 0),
  custo_total    numeric(14,2) generated always as (round(quantidade * custo_unitario, 2)) stored,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now()
);

create table materiais (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  codigo         text,
  nome           text not null,
  categoria      text,
  unidade        text not null,
  estoque_minimo numeric(14,4) not null default 0,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  unique (organizacao_id, codigo)
);

create table fornecedores (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  razao_social   text not null,
  cnpj           text,
  contato        text,
  email          text,
  telefone       text,
  ativo          boolean not null default true,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  unique (organizacao_id, cnpj)
);

create table compras (
  id                    uuid primary key default gen_random_uuid(),
  organizacao_id        uuid not null references organizacoes on delete cascade,
  obra_id               uuid not null references obras on delete restrict,
  fornecedor_id         uuid references fornecedores on delete restrict,
  numero                text,
  status                status_compra not null default 'rascunho',
  data_pedido           date not null default current_date,
  data_entrega_prevista date,
  valor_total           numeric(14,2) not null default 0,  -- mantido por trigger
  nf_path               text,                              -- path no bucket notas-fiscais
  observacoes           text,
  criado_por            uuid references auth.users on delete set null,
  criado_em             timestamptz not null default now(),
  atualizado_em         timestamptz not null default now()
);

create table compra_itens (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  compra_id      uuid not null references compras on delete cascade,
  material_id    uuid not null references materiais on delete restrict,
  quantidade     numeric(14,4) not null check (quantidade > 0),
  preco_unitario numeric(14,4) not null check (preco_unitario >= 0),
  total          numeric(14,2) generated always as (round(quantidade * preco_unitario, 2)) stored
);

create table movimentacoes_estoque (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  obra_id        uuid not null references obras on delete cascade,
  material_id    uuid not null references materiais on delete restrict,
  tipo           tipo_movimentacao not null,
  quantidade     numeric(14,4) not null check (quantidade > 0),
  custo_unitario numeric(14,4),
  compra_id      uuid references compras on delete set null,
  responsavel_id uuid references auth.users on delete set null,
  data           date not null default current_date,
  observacao     text,
  criado_em      timestamptz not null default now()
);

-- LGPD: dados pessoais mínimos
create table funcionarios (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  nome           text not null,
  funcao         text,
  ativo          boolean not null default true,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now()
);

create table equipe_obra (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  obra_id        uuid not null references obras on delete cascade,
  funcionario_id uuid not null references funcionarios on delete cascade,
  funcao_na_obra text,
  data_inicio    date not null default current_date,
  data_fim       date,
  check (data_fim is null or data_fim >= data_inicio)
);

create table diario_obra (   -- RDO
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  obra_id        uuid not null references obras on delete cascade,
  data           date not null default current_date,
  clima_manha    text check (clima_manha in ('sol','nublado','chuva','chuva_forte')),
  clima_tarde    text check (clima_tarde in ('sol','nublado','chuva','chuva_forte')),
  efetivo        int  not null default 0 check (efetivo >= 0),
  atividades     text,
  ocorrencias    text,
  criado_por     uuid references auth.users on delete set null,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  unique (obra_id, data)
);

create table diario_fotos (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  diario_id      uuid not null references diario_obra on delete cascade,
  storage_path   text not null,   -- {organizacao_id}/{obra_id}/{rdo_id}/arquivo
  legenda        text,
  criado_em      timestamptz not null default now()
);

create table documentos (
  id             uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references organizacoes on delete cascade,
  obra_id        uuid references obras on delete cascade,
  tipo           text not null default 'outro'
                 check (tipo in ('projeto','planta','art','contrato','licenca','outro')),
  nome           text not null,
  storage_path   text not null,
  criado_por     uuid references auth.users on delete set null,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now()
);

create table audit_log (
  id             bigint generated always as identity primary key,
  organizacao_id uuid not null,
  tabela         text not null,
  registro_id    text,
  acao           text not null,   -- INSERT | UPDATE | DELETE
  usuario_id     uuid,
  dados_antigos  jsonb,
  dados_novos    jsonb,
  criado_em      timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- 3. Índices
-- ---------------------------------------------------------------------
-- organizacao_id em todas as tabelas de negócio
do $$
declare t text;
begin
  for t in
    select table_name from information_schema.columns
    where table_schema = 'public' and column_name = 'organizacao_id'
      and table_name not in (select table_name from information_schema.views where table_schema = 'public')
  loop
    execute format('create index if not exists idx_%1$s_org on public.%1$I (organizacao_id)', t);
  end loop;
end $$;

create index idx_membros_user          on membros (user_id);
create index idx_etapas_obra           on etapas (obra_id, ordem);
create index idx_tarefas_org_etapa     on tarefas (organizacao_id, etapa_id);
create index idx_tarefas_responsavel   on tarefas (responsavel_id);
create index idx_medicoes_obra         on medicoes (obra_id, periodo desc);
create index idx_orcamento_obra        on orcamento_itens (obra_id);
create index idx_materiais_nome_trgm   on materiais using gin (nome extensions.gin_trgm_ops);
create index idx_mov_obra_material     on movimentacoes_estoque (obra_id, material_id, data desc);
create index idx_compras_obra          on compras (obra_id, status);
create index idx_compras_fornecedor    on compras (fornecedor_id);
create index idx_compra_itens_compra   on compra_itens (compra_id);
create index idx_equipe_obra           on equipe_obra (obra_id, funcionario_id);
create index idx_diario_obra_data      on diario_obra (obra_id, data desc);
create index idx_diario_fotos_diario   on diario_fotos (diario_id);
create index idx_documentos_obra       on documentos (obra_id);
create index idx_audit_org_tabela      on audit_log (organizacao_id, tabela, criado_em desc);

-- ---------------------------------------------------------------------
-- 4. Funções auxiliares de autorização (security definer evita recursão de RLS)
-- ---------------------------------------------------------------------
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

revoke all on function public.minhas_orgs() from public, anon;
revoke all on function public.tem_papel(uuid, papel_membro[]) from public, anon;
grant execute on function public.minhas_orgs() to authenticated;
grant execute on function public.tem_papel(uuid, papel_membro[]) to authenticated;

-- ---------------------------------------------------------------------
-- 5. Row Level Security
-- ---------------------------------------------------------------------
-- Regra: nenhuma tabela em public sem RLS.
do $$
declare t text;
begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

-- 5.1 Tabelas de negócio: SELECT para membros da org; escrita por papel
do $$
declare r record;
begin
  for r in select * from (values
    ('obras',                 array['admin','engenheiro']),
    ('etapas',                array['admin','engenheiro']),
    ('tarefas',               array['admin','engenheiro']),
    ('medicoes',              array['admin','engenheiro','financeiro']),
    ('orcamento_itens',       array['admin','engenheiro']),
    ('materiais',             array['admin','suprimentos']),
    ('fornecedores',          array['admin','suprimentos']),
    ('compras',               array['admin','suprimentos','financeiro']),
    ('compra_itens',          array['admin','suprimentos','financeiro']),
    ('movimentacoes_estoque', array['admin','mestre_obras','suprimentos']),
    ('funcionarios',          array['admin','engenheiro']),
    ('equipe_obra',           array['admin','engenheiro']),
    ('diario_obra',           array['admin','mestre_obras']),
    ('diario_fotos',          array['admin','mestre_obras']),
    ('documentos',            array['admin','engenheiro'])
  ) as v(tabela, papeis)
  loop
    execute format(
      'create policy %I on public.%I for select to authenticated
         using (organizacao_id in (select public.minhas_orgs()))',
      r.tabela || '_select', r.tabela);

    execute format(
      'create policy %I on public.%I for all to authenticated
         using (public.tem_papel(organizacao_id, variadic %L::public.papel_membro[]))
         with check (public.tem_papel(organizacao_id, variadic %L::public.papel_membro[]))',
      r.tabela || '_write', r.tabela, r.papeis, r.papeis);
  end loop;
end $$;

-- 5.2 Mestre de obras: atualiza progresso de tarefas (somente percentual e fim_real)
create policy tarefas_update_mestre on tarefas for update to authenticated
  using (tem_papel(organizacao_id, 'mestre_obras'))
  with check (tem_papel(organizacao_id, 'mestre_obras'));

create function public.fn_tarefas_restringe_mestre() returns trigger
language plpgsql as $$
begin
  if auth.uid() is not null
     and not public.tem_papel(new.organizacao_id, 'admin', 'engenheiro')
     and (to_jsonb(new) - 'percentual' - 'fim_real' - 'atualizado_em')
         is distinct from
         (to_jsonb(old) - 'percentual' - 'fim_real' - 'atualizado_em')
  then
    raise exception 'Seu papel só pode atualizar percentual e fim_real da tarefa'
      using errcode = '42501';
  end if;
  return new;
end $$;

create trigger trg_tarefas_restringe_mestre
  before update on tarefas
  for each row execute function public.fn_tarefas_restringe_mestre();

-- 5.3 Organizações, membros e auditoria
create policy organizacoes_select on organizacoes for select to authenticated
  using (id in (select minhas_orgs()));
create policy organizacoes_update on organizacoes for update to authenticated
  using (tem_papel(id, 'admin')) with check (tem_papel(id, 'admin'));
-- INSERT em organizacoes somente via RPC fn_criar_organizacao

create policy membros_select on membros for select to authenticated
  using (organizacao_id in (select minhas_orgs()));
create policy membros_write on membros for all to authenticated
  using (tem_papel(organizacao_id, 'admin'))
  with check (tem_papel(organizacao_id, 'admin'));

create policy audit_log_select on audit_log for select to authenticated
  using (tem_papel(organizacao_id, 'admin'));
-- sem políticas de escrita: apenas triggers (security definer) gravam

-- ---------------------------------------------------------------------
-- 6. Triggers
-- ---------------------------------------------------------------------
-- 6.1 atualizado_em automático
create function public.set_atualizado_em() returns trigger
language plpgsql as $$
begin
  new.atualizado_em = now();
  return new;
end $$;

do $$
declare t text;
begin
  for t in
    select table_name from information_schema.columns
    where table_schema = 'public' and column_name = 'atualizado_em'
  loop
    execute format(
      'create trigger trg_%1$s_atualizado_em before update on public.%1$I
         for each row execute function public.set_atualizado_em()', t);
  end loop;
end $$;

-- 6.2 Auditoria (obras, medições, compras)
create function public.fn_audit() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_old jsonb := case when tg_op in ('UPDATE','DELETE') then to_jsonb(old) end;
  v_new jsonb := case when tg_op in ('INSERT','UPDATE') then to_jsonb(new) end;
  v_row jsonb := coalesce(v_new, v_old);
begin
  insert into audit_log (organizacao_id, tabela, registro_id, acao, usuario_id, dados_antigos, dados_novos)
  values ((v_row->>'organizacao_id')::uuid, tg_table_name, v_row->>'id', tg_op, auth.uid(), v_old, v_new);
  return null;
end $$;

create trigger trg_audit_obras    after insert or update or delete on obras
  for each row execute function public.fn_audit();
create trigger trg_audit_medicoes after insert or update or delete on medicoes
  for each row execute function public.fn_audit();
create trigger trg_audit_compras  after insert or update or delete on compras
  for each row execute function public.fn_audit();

-- 6.3 Total da compra recalculado a partir dos itens
create function public.fn_recalcula_compra() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_compra uuid := coalesce(new.compra_id, old.compra_id);
begin
  update compras
     set valor_total = coalesce((select sum(total) from compra_itens where compra_id = v_compra), 0)
   where id = v_compra;
  return null;
end $$;

create trigger trg_compra_itens_total
  after insert or update or delete on compra_itens
  for each row execute function public.fn_recalcula_compra();

-- ---------------------------------------------------------------------
-- 7. Views (security_invoker => respeitam a RLS do usuário)
-- ---------------------------------------------------------------------
create view public.vw_obra_resumo with (security_invoker = true) as
select
  o.id as obra_id,
  o.organizacao_id,
  o.nome,
  o.status,
  o.data_inicio,
  o.data_prevista_fim,
  coalesce(t.avanco_fisico, 0)                         as avanco_fisico_pct,
  coalesce(t.tarefas_total, 0)                         as tarefas_total,
  coalesce(t.tarefas_atrasadas, 0)                     as tarefas_atrasadas,
  coalesce(nullif(b.custo_orcado, 0), o.orcamento_total, 0) as custo_orcado,
  coalesce(c.custo_realizado, 0)                       as custo_realizado,
  coalesce(nullif(b.custo_orcado, 0), o.orcamento_total, 0) - coalesce(c.custo_realizado, 0) as saldo_orcamento,
  case when o.status in ('em_andamento','pausada') and o.data_prevista_fim < current_date
       then current_date - o.data_prevista_fim else 0 end as dias_atraso
from obras o
left join lateral (
  select round(avg(tf.percentual), 2) as avanco_fisico,
         count(*) as tarefas_total,
         count(*) filter (where tf.percentual < 100 and tf.fim_previsto < current_date) as tarefas_atrasadas
  from tarefas tf join etapas e on e.id = tf.etapa_id
  where e.obra_id = o.id
) t on true
left join lateral (
  select sum(custo_total) as custo_orcado from orcamento_itens where obra_id = o.id
) b on true
left join lateral (
  select sum(valor_total) as custo_realizado from compras
  where obra_id = o.id and status in ('parcial','recebido')
) c on true;

create view public.vw_estoque_saldo with (security_invoker = true) as
select
  m.organizacao_id,
  m.obra_id,
  m.material_id,
  mat.nome as material,
  mat.unidade,
  mat.estoque_minimo,
  sum(case m.tipo when 'entrada' then m.quantidade else -m.quantidade end) as saldo
from movimentacoes_estoque m
join materiais mat on mat.id = m.material_id
group by m.organizacao_id, m.obra_id, m.material_id, mat.nome, mat.unidade, mat.estoque_minimo;

-- ---------------------------------------------------------------------
-- 8. Funções RPC
-- ---------------------------------------------------------------------
-- 8.1 Cria a organização e torna o usuário logado admin
create function public.fn_criar_organizacao(p_nome text, p_cnpj text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Não autenticado' using errcode = '28000';
  end if;
  insert into organizacoes (nome, cnpj) values (p_nome, p_cnpj) returning id into v_id;
  insert into membros (organizacao_id, user_id, papel) values (v_id, auth.uid(), 'admin');
  return v_id;
end $$;

-- 8.2 Registra (ou recalcula rascunho de) medição mensal da obra.
--     security invoker: a RLS de medicoes garante admin/engenheiro/financeiro.
create function public.fn_registrar_medicao(p_obra_id uuid, p_periodo date default current_date)
returns medicoes language plpgsql as $$
declare
  v_org       uuid;
  v_mes       date := date_trunc('month', p_periodo)::date;
  v_orcado    numeric(14,2);
  v_pct       numeric(5,2);
  v_acum      numeric(14,2);
  v_anterior  numeric(14,2);
  v_medicao   medicoes;
begin
  select organizacao_id, orcamento_total into v_org, v_orcado from obras where id = p_obra_id;
  if v_org is null then
    raise exception 'Obra não encontrada ou sem acesso';
  end if;

  if exists (select 1 from medicoes where obra_id = p_obra_id and periodo = v_mes and status = 'aprovada') then
    raise exception 'Medição de % já aprovada', to_char(v_mes, 'MM/YYYY');
  end if;

  select coalesce(nullif(sum(custo_total), 0), v_orcado, 0) into v_orcado
    from orcamento_itens where obra_id = p_obra_id;

  select coalesce(round(avg(t.percentual), 2), 0) into v_pct
    from tarefas t join etapas e on e.id = t.etapa_id where e.obra_id = p_obra_id;

  v_acum := round(v_orcado * v_pct / 100, 2);

  select coalesce(max(valor_acumulado), 0) into v_anterior
    from medicoes where obra_id = p_obra_id and periodo < v_mes;

  insert into medicoes (organizacao_id, obra_id, periodo, percentual_fisico_acum,
                        valor_periodo, valor_acumulado, criado_por)
  values (v_org, p_obra_id, v_mes, v_pct, v_acum - v_anterior, v_acum, auth.uid())
  on conflict (obra_id, periodo) do update
    set percentual_fisico_acum = excluded.percentual_fisico_acum,
        valor_periodo          = excluded.valor_periodo,
        valor_acumulado        = excluded.valor_acumulado
  returning * into v_medicao;

  return v_medicao;
end $$;

revoke all on function public.fn_criar_organizacao(text, text) from public, anon;
revoke all on function public.fn_registrar_medicao(uuid, date)  from public, anon;
grant execute on function public.fn_criar_organizacao(text, text) to authenticated;
grant execute on function public.fn_registrar_medicao(uuid, date)  to authenticated;

-- ---------------------------------------------------------------------
-- 9. Storage (buckets privados; path = {organizacao_id}/...)
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public) values
  ('diario-fotos', 'diario-fotos', false),
  ('documentos',   'documentos',   false),
  ('notas-fiscais','notas-fiscais',false)
on conflict (id) do nothing;

-- 1º segmento do path -> uuid da organização (null se inválido)
create function public.org_do_path(p_name text) returns uuid
language plpgsql stable as $$
begin
  return (storage.foldername(p_name))[1]::uuid;
exception when others then
  return null;
end $$;

grant execute on function public.org_do_path(text) to authenticated;

do $$
declare r record;
begin
  for r in select * from (values
    ('diario-fotos',  array['admin','engenheiro','mestre_obras']),
    ('documentos',    array['admin','engenheiro']),
    ('notas-fiscais', array['admin','suprimentos','financeiro'])
  ) as v(bucket, papeis)
  loop
    execute format(
      'create policy %I on storage.objects for select to authenticated
         using (bucket_id = %L and public.org_do_path(name) in (select public.minhas_orgs()))',
      r.bucket || '_select', r.bucket);

    execute format(
      'create policy %I on storage.objects for all to authenticated
         using (bucket_id = %L and public.tem_papel(public.org_do_path(name), variadic %L::public.papel_membro[]))
         with check (bucket_id = %L and public.tem_papel(public.org_do_path(name), variadic %L::public.papel_membro[]))',
      r.bucket || '_write', r.bucket, r.bucket, r.papeis, r.bucket, r.papeis);
  end loop;
end $$;

-- ---------------------------------------------------------------------
-- 10. Realtime (diário de obra e status de tarefas)
-- ---------------------------------------------------------------------
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.diario_obra, public.tarefas;
  end if;
end $$;

-- ---------------------------------------------------------------------
-- 11. Retenção do audit_log (requer pg_cron habilitado)
-- ---------------------------------------------------------------------
-- select cron.schedule('audit_log_retencao', '0 3 * * 0',
--   $$delete from public.audit_log where criado_em < now() - interval '5 years'$$);
