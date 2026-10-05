begin;
select plan(5);

-- Setup (como superuser)
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a1', 'eng@a.test'),
  ('00000000-0000-0000-0000-0000000000b1', 'adm@b.test'),
  ('00000000-0000-0000-0000-0000000000c1', 'leitor@a.test');

insert into organizacoes (id, nome) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'Org A'),
  ('bbbbbbbb-0000-0000-0000-000000000001', 'Org B');

insert into membros (organizacao_id, user_id, papel) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', 'engenheiro'),
  ('bbbbbbbb-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000b1', 'admin'),
  ('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000c1', 'leitor');

insert into obras (organizacao_id, nome) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'Obra A'),
  ('bbbbbbbb-0000-0000-0000-000000000001', 'Obra B');

-- Engenheiro da Org A
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}', true);

select is((select count(*) from obras), 1::bigint, 'engenheiro só enxerga obras da própria org');

select lives_ok(
  $$insert into obras (organizacao_id, nome) values ('aaaaaaaa-0000-0000-0000-000000000001', 'Nova A')$$,
  'engenheiro cria obra na própria org');

select throws_ok(
  $$insert into obras (organizacao_id, nome) values ('bbbbbbbb-0000-0000-0000-000000000001', 'Invasão')$$,
  '42501', null, 'engenheiro não cria obra em outra org');

select is((select count(*) from audit_log), 0::bigint, 'não-admin não lê audit_log');

-- Leitor da Org A
select set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-0000000000c1","role":"authenticated"}', true);

select throws_ok(
  $$insert into obras (organizacao_id, nome) values ('aaaaaaaa-0000-0000-0000-000000000001', 'Leitor tenta')$$,
  '42501', null, 'leitor não escreve');

select * from finish();
rollback;
