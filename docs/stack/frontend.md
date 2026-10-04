# Frontend — Sistema de Construção Civil

## 1. Visão geral

SPA estática em **TypeScript**, compilada com **Node.js** e publicada no **GitHub Pages**. Comunica-se diretamente com o **Supabase** (Auth, CRUD sob RLS, Storage, Realtime) e com a **API Kotlin** para regras de negócio.

| Item | Escolha |
|---|---|
| Linguagem | TypeScript 5 (`strict: true`) |
| Runtime de build | Node.js 22 LTS + pnpm |
| Framework | **React 18** |
| Bundler | **Vite** |
| Roteamento | React Router (modo **HashRouter** ou fallback `404.html`, por causa do GH Pages) |
| Dados / cache | **TanStack Query** |
| Estado local | Zustand (somente estado de UI) |
| Formulários | React Hook Form + **Zod** |
| UI | Tailwind CSS + **shadcn/ui** (Radix) |
| Tabelas | TanStack Table |
| Gráficos | Recharts (curva S, custo orçado × realizado) |
| Cronograma | Gantt: `frappe-gantt` ou `gantt-task-react` |
| Mapas (opcional) | MapLibre GL |
| Datas | date-fns (locale `pt-BR`) |
| Cliente Supabase | `@supabase/supabase-js` |
| Testes | Vitest + Testing Library; **Playwright** para E2E |
| Lint / formato | ESLint + Prettier |

## 2. Estrutura do projeto

```
frontend/
├── src/
│   ├── app/              # providers, rotas, layout
│   ├── features/
│   │   ├── auth/
│   │   ├── obras/
│   │   ├── cronograma/   # etapas, tarefas, Gantt
│   │   ├── medicoes/
│   │   ├── orcamento/
│   │   ├── estoque/
│   │   ├── compras/
│   │   ├── diario/       # RDO + upload de fotos
│   │   └── documentos/
│   ├── shared/
│   │   ├── components/   # UI reutilizável
│   │   ├── hooks/
│   │   └── lib/
│   │       ├── supabase.ts
│   │       ├── api.ts    # cliente da API Kotlin (injeta JWT)
│   │       └── env.ts    # validação das variáveis VITE_*
│   └── types/
│       └── database.ts   # gerado: supabase gen types typescript
├── public/
│   ├── 404.html          # fallback SPA
│   └── CNAME             # domínio customizado (opcional)
├── vite.config.ts        # base: '/<repo>/'
└── package.json
```

Cada **feature** contém `api/` (queries/mutations), `components/`, `pages/` e `schemas/` (Zod), mantendo o domínio isolado.

## 3. Integração com o backend

- **Tipos do banco** gerados automaticamente:
  `supabase gen types typescript --project-id <id> > src/types/database.ts`
- **Auth:** `supabase.auth` (sessão persistida); o `access_token` é enviado como `Authorization: Bearer` à API Kotlin.
- **Leitura/escrita simples:** `supabase.from('obras').select(...)`, protegido por RLS.
- **Regras complexas:** `api.post('/v1/obras/:id/medicoes')` → API Kotlin.
- **Realtime:** canal em `diario_obra` e `tarefas` para atualização ao vivo.
- **Uploads:** direto ao Supabase Storage (com compressão no cliente antes do envio), caminho `{org}/{obra}/{rdo}/…`.
- **Contrato da API Kotlin:** gerar cliente TS a partir do OpenAPI (`openapi-typescript`).

> **Segurança:** o front usa apenas a `anon key` (pública por design). Nunca incluir `service_role` ou segredos no bundle; tudo em `VITE_*` é público.

## 4. Telas principais

| Módulo | Telas |
|---|---|
| Dashboard | Visão geral das obras, KPIs, alertas de atraso |
| Obras | Lista, detalhe, criação/edição |
| Cronograma | Gantt, quadro Kanban de tarefas, % de avanço |
| Medições | Registro, histórico, curva S |
| Orçamento | Itens, composição, importação de planilha |
| Estoque / Compras | Materiais, movimentações, pedidos, fornecedores |
| Diário de Obra (RDO) | Formulário diário, clima, efetivo, fotos, exportação PDF |
| Documentos | Upload, versionamento simples, busca |
| Administração | Membros, papéis, configurações da organização |

Controle de acesso na UI por papel (`<Can role="engenheiro">`), **sempre complementar** à RLS (a segurança real está no banco).

## 5. Requisitos específicos para canteiro de obra

- **Mobile-first / responsivo** (mestre de obras usa celular).
- **PWA** (`vite-plugin-pwa`): instalável, cache de assets.
- **Modo offline básico** para RDO: rascunho salvo em IndexedDB (Dexie) e sincronizado ao reconectar (fila de mutações do TanStack Query persistida).
- Captura de foto pela câmera (`<input type="file" accept="image/*" capture>`).
- Internacionalização `pt-BR` (react-i18next) e formatação de moeda/medidas.
- Acessibilidade (WCAG AA): componentes Radix, contraste, navegação por teclado.

## 6. Deploy no GitHub Pages

`vite.config.ts`:

```ts
export default defineConfig({
  base: '/nome-do-repo/', // ou '/' com domínio customizado
  plugins: [react()],
});
```

Workflow `.github/workflows/deploy.yml`:

```yaml
name: Deploy front
on:
  push:
    branches: [main]
permissions:
  contents: read
  pages: write
  id-token: write
jobs:
  build:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: frontend } }
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: 22, cache: pnpm, cache-dependency-path: frontend/pnpm-lock.yaml }
      - run: pnpm install --frozen-lockfile
      - run: pnpm lint && pnpm typecheck && pnpm test
      - run: pnpm build
        env:
          VITE_SUPABASE_URL: ${{ vars.VITE_SUPABASE_URL }}
          VITE_SUPABASE_ANON_KEY: ${{ vars.VITE_SUPABASE_ANON_KEY }}
          VITE_API_URL: ${{ vars.VITE_API_URL }}
      - run: cp dist/index.html dist/404.html   # fallback de rotas SPA
      - uses: actions/upload-pages-artifact@v3
        with: { path: frontend/dist }
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment: { name: github-pages }
    steps:
      - uses: actions/deploy-pages@v4
```

## 7. Limitações do GH Pages a considerar

- Somente conteúdo estático: sem SSR e sem variáveis de ambiente em runtime (tudo é resolvido no build).
- Sem headers customizados (CSP, HSTS parcial): mitigar com `<meta>` CSP e, se necessário, colocar **Cloudflare** na frente do domínio.
- Repositório privado com Pages exige plano pago do GitHub; o site publicado é público, então **autenticação obrigatória** deve ser tratada na aplicação e no Supabase.
- Rotas profundas exigem HashRouter ou o truque do `404.html`.

## 8. Qualidade

- Pre-commit: Husky + lint-staged.
- Cobertura mínima sugerida: 70% em lógica (hooks/schemas), E2E nos fluxos críticos (login, criar obra, registrar RDO, registrar medição).
- Lighthouse CI para performance/PWA.
- Dependabot/Renovate para atualização de dependências.
