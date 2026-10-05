-- CI: falha se existir tabela em public sem Row Level Security
do $$
declare faltando text;
begin
  select string_agg(tablename, ', ') into faltando
  from pg_tables
  where schemaname = 'public' and not rowsecurity;

  if faltando is not null then
    raise exception 'Tabelas sem RLS: %', faltando;
  end if;
end $$;
