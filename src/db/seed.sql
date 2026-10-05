-- Seed de desenvolvimento (executado como superuser: ignora RLS)
-- Para vincular um usuário real: crie-o via Supabase Auth e depois rode
--   insert into membros (organizacao_id, user_id, papel)
--   values ('11111111-1111-1111-1111-111111111111', '<uuid do auth.users>', 'admin');

insert into organizacoes (id, nome, cnpj) values
  ('11111111-1111-1111-1111-111111111111', 'Construtora Demo Ltda', '00.000.000/0001-00');

insert into obras (id, organizacao_id, nome, endereco, status, data_inicio, data_prevista_fim, orcamento_total) values
  ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
   'Residencial Jardim das Flores', 'Rua das Acácias, 100', 'em_andamento',
   current_date - 60, current_date + 300, 1500000.00);

insert into etapas (id, organizacao_id, obra_id, nome, ordem, status, inicio_previsto, fim_previsto) values
  ('33333333-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Fundação',   1, 'em_andamento', current_date - 60, current_date + 30),
  ('33333333-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Estrutura',  2, 'pendente',     current_date + 31, current_date + 150),
  ('33333333-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Acabamento', 3, 'pendente',     current_date + 151, current_date + 300);

insert into tarefas (organizacao_id, etapa_id, titulo, percentual, inicio_previsto, fim_previsto) values
  ('11111111-1111-1111-1111-111111111111', '33333333-0000-0000-0000-000000000001', 'Escavação das sapatas', 100, current_date - 60, current_date - 40),
  ('11111111-1111-1111-1111-111111111111', '33333333-0000-0000-0000-000000000001', 'Armação e concretagem',   60, current_date - 39, current_date + 10),
  ('11111111-1111-1111-1111-111111111111', '33333333-0000-0000-0000-000000000002', 'Pilares do térreo',       0, current_date + 31, current_date + 70);

insert into orcamento_itens (organizacao_id, obra_id, etapa_id, base, codigo, descricao, unidade, quantidade, custo_unitario) values
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-0000-0000-0000-000000000001', 'SINAPI', '96995', 'Reaterro manual de valas', 'm3', 120, 45.50),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-0000-0000-0000-000000000001', 'SINAPI', '94971', 'Concreto fck 25 MPa',     'm3',  80, 380.00);

insert into materiais (organizacao_id, codigo, nome, categoria, unidade, estoque_minimo) values
  ('11111111-1111-1111-1111-111111111111', 'CIM-50',  'Cimento CP-II 50kg',   'Aglomerantes', 'saco', 50),
  ('11111111-1111-1111-1111-111111111111', 'ARE-M',   'Areia média',          'Agregados',    'm3',   10),
  ('11111111-1111-1111-1111-111111111111', 'AC-10',   'Aço CA-50 10mm (barra 12m)', 'Aço',    'un',   100);

insert into fornecedores (organizacao_id, razao_social, cnpj, email) values
  ('11111111-1111-1111-1111-111111111111', 'Depósito Central de Materiais', '11.111.111/0001-11', 'vendas@depositocentral.example');
