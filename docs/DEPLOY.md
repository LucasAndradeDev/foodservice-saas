# Deploy — Status e guia de referência

Documenta a infraestrutura de produção (Render + Supabase) montada em 2026-07-29/30: o que já está pronto, o que falta, e os problemas que já apareceram no caminho (pra não repetir o mesmo debug numa próxima sessão). Este arquivo **nunca deve conter segredos reais** (senha, chaves) — só nomes de variável e onde encontrar o valor.

**URLs reais em produção** (o Render adicionou sufixo aleatório porque os nomes "limpos" já estavam em uso):
- Backend: https://mora-backend-ubuw.onrender.com
- Frontend: https://mora-frontend-tdmc.onrender.com

## Arquitetura

| Peça | Onde roda | Papel |
|---|---|---|
| Backend (Spring Boot) | Render — Web Service, Docker, plano free | API, regras de negócio |
| Frontend (React/Vite) | Render — Static Site, plano free (sem campo `plan`) | build estático servido direto |
| Banco (Postgres) | Render — Postgres, plano free (`mora-db`) | dados da aplicação (temporariamente aqui, ver problema #6) |
| Storage de imagens | Supabase Storage (bucket `uploads`, público) | fotos de produto/logo |

> **2026-07-30**: banco temporariamente movido do Supabase pro Postgres do próprio Render (mesma plataforma do backend, rede interna) por causa do circuit breaker recorrente do Supavisor (problema #4). Storage de imagens continua no Supabase — só o banco mudou. Plano free do Postgres do Render expira depois de um tempo (checar prazo exato no dashboard) — serve pra destravar o teste agora, não é decisão definitiva de produção.

Frontend e backend ficam em domínios `onrender.com` diferentes. Em vez de CORS, o frontend usa uma regra de **Rewrite** (`/api/* → URL do backend/api/*`) configurada no painel do Render — o navegador nunca sabe que é cross-origin, e o código do frontend continua usando a base relativa `/api/v1` sem mudança nenhuma.

## Arquivos relevantes no repositório

- `render.yaml` (raiz) — Blueprint com os dois serviços (`mora-backend`, `mora-frontend`)
- `backend/Dockerfile` + `backend/.dockerignore` — build multi-stage (Maven → JRE)
- `backend/src/main/java/.../service/{FileStorageService,AbstractFileStorageService,LocalFileStorageService,SupabaseFileStorageService}.java` — upload local (dev) ou Supabase Storage (prod), trocado via `STORAGE_PROVIDER`
- `backend/.env.example` — lista completa de variáveis, incluindo as de storage

## Variáveis de ambiente do backend (preenchidas direto no painel do Render, nunca commitadas)

| Variável | De onde vem |
|---|---|
| `SPRING_DATASOURCE_URL` | Manual: criar/abrir o database `mora-db` no Render → página **Info** → pegar **Hostname** (interno) e **Port**. Formato: `jdbc:postgresql://<hostname-interno>:<port>/mora?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Automático via `fromDatabase` no `render.yaml` (property `user`) — não precisa preencher a mão |
| `SPRING_DATASOURCE_PASSWORD` | Automático via `fromDatabase` no `render.yaml` (property `password`) — não precisa preencher a mão |
| `JWT_SECRET` | gerado localmente (`openssl rand -base64 64`), só pra produção, não reaproveitar a de dev |
| `GEMINI_API_KEY` | https://aistudio.google.com/apikey |
| `SUPABASE_URL` | Supabase → Settings → API → Project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase → Settings → API → Secret keys (novo nome da antiga "service_role key") |
| `SUPABASE_STORAGE_BUCKET` | `uploads` |
| `STORAGE_PROVIDER` | `supabase` (já vem fixo no `render.yaml`, não precisa preencher) |

## Status atual

- [x] Dockerfile + `.dockerignore` do backend, testado localmente (build + run + `/actuator/health` respondendo)
- [x] `server.port` lendo `PORT` (Render injeta essa variável)
- [x] `render.yaml` com os dois serviços
- [x] Upload trocado pra Supabase Storage em produção
- [x] Banco trocado pro Postgres do Render (`mora-db`) — backend sobe com sucesso, `/actuator/health` responde `{"status":"UP"}`
- [x] Regra de Rewrite (`/api/*` → `https://mora-backend-ubuw.onrender.com/api/*`) configurada no `mora-frontend`
- [x] Frontend carregando e se comunicando com o backend em produção
- [x] Teste end-to-end completo em produção ✅ 2026-08-04 (login, abrir mesa, adicionar item, enviar pra cozinha, avançar status até entregue, fechar conta com pagamento Pix incluindo taxa de serviço, upload de foto de produto pro Supabase Storage — todos os passos funcionaram na conta de teste "Tatu Bola")

## Problemas conhecidos (e como foram resolvidos)

1. **`no such plan free for service type web`** — static site no Render não aceita o campo `plan`; ele só existe pra serviços de compute (o backend Docker). Removido do `render.yaml` do frontend (commit `dc77524`).
2. **`Driver claims to not accept jdbcUrl, postgresql://postgres:...@...`** — colamos a connection string crua do Supabase (usuário e senha embutidos dentro da própria URL) no campo `SPRING_DATASOURCE_URL`. O driver JDBC exige a URL **sem** credenciais embutidas, com prefixo `jdbc:`, e usuário/senha em variáveis separadas.
3. **`SocketException: Network unreachable`** — a "Direct connection" do Supabase só resolve por IPv6 por padrão; o Render só tem saída IPv4. Resolvido trocando pro **Session Pooler** do Supabase (compatível com IPv4). Usamos Session (não Transaction) pooler de propósito: Transaction pooler é otimizado pra funções serverless com conexões curtas e pode causar problemas com Hibernate/Flyway, que esperam uma sessão de conexão mais tradicional.
4. **`FATAL: (ECIRCUITBREAKER) too many authentication failures`** — as tentativas anteriores com senha/URL erradas dispararam um bloqueio temporário de segurança no Supavisor (pooler do Supabase). Não é erro de configuração — resolve sozinho depois de ~10-15 min sem novas tentativas.
5. Uma senha de banco chegou a aparecer em texto puro nos logs de deploy (por causa do problema #2, antes de separar em variáveis) — foi resetada como consequência. Lição: nunca embutir credenciais dentro da URL de conexão.
6. Depois de acertar as credenciais, o deploy continuou batendo no mesmo `ECIRCUITBREAKER` do Supavisor (bloqueio de ~10-15min por tentativas anteriores, não erro de config) — pra não ficar bloqueado testando, o banco foi trocado pro Postgres do próprio Render (`mora-db`, ver `render.yaml`). Mesma plataforma do backend = rede interna, sem os problemas de IPv4/pooler entre provedores diferentes.
7. **"Manual sync" do Blueprint dizia "Resources already up to date" e não criava o `mora-db`** — o commit com o `render.yaml` novo só existia local, nunca tinha sido enviado (`git push`) pro repositório que o Render acompanha. Sync no Render só lê o que está no remoto.
8. **`FATAL: (ENOIDENTIFIER) no tenant identifier provided`** — depois de trocar o banco, o `SPRING_DATASOURCE_USERNAME`/`PASSWORD` já vinham automáticos do `mora-db` (via `fromDatabase`), mas a `SPRING_DATASOURCE_URL` ainda apontava pro host antigo do Supabase — credenciais do Render tentando autenticar no pooler do Supabase. Resolvido preenchendo a URL com o Hostname/Port do `mora-db`.
9. Os nomes dos serviços no `render.yaml` (`mora-backend`, `mora-frontend`) já estavam em uso por outra conta, então o Render gerou URLs com sufixo aleatório (`mora-backend-ubuw`, `mora-frontend-tdmc`). Sempre confirmar a URL real na página do serviço em vez de assumir o nome "limpo".
10. Testar direto a rota raiz `/` do backend dá 403 — é o Spring Security bloqueando por padrão, não indica que o serviço está fora do ar. Usar `/actuator/health` pra checar se subiu.
11. Rewrite configurado sem o `*` no final (`/api/` em vez de `/api/*`) não pega os subcaminhos reais que o frontend chama (`/api/v1/...`) — sempre incluir o wildcard nos dois campos (Source e Destination).

## Hospedagem definitiva do banco — decisão pendente (pesquisado em 2026-08-04)

**Prazo real, não "quando sobrar tempo"**: banco free do Render (`mora-db`, criado em 2026-07-29/30) expira **30 dias depois de criado** (~28-29/08/2026), com mais **14 dias de carência** antes de apagar os dados de vez (~11-12/09/2026). Fonte: [changelog do Render](https://render.com/changelog/free-postgresql-instances-now-expire-after-30-days-previously-90).

**Custos das duas opções**:
- **Ficar no Render** (upgrade da mesma instância, sem migração): plano pago mais barato é **Basic-256mb, $6/mês**, mais disco à parte (~$0,30/GB/mês, em blocos mínimos de 5GB que não dá pra reduzir depois — uns $1,50/mês). Total: **~$7-8/mês**.
- **Voltar pro Supabase**: **Pro plan, $25/mês** — inclui backup diário de 7 dias, mas isso já ficou redundante depois que o próprio backend passou a fazer backup (ver `docs/BACKUP_RESTORE.md`). E o Supabase já causou os problemas #3/#4 desta página (circuit breaker do Supavisor, IPv4/IPv6) — voltar reabre esse risco.

**Recomendação**: ficar no Render e fazer upgrade do `mora-db` pro Basic-256mb antes de 28/08/2026 — mais barato, não exige migrar nada, e evita reabrir os problemas já resolvidos com o Supabase.

## Próximos passos

1. Fazer o upgrade do `mora-db` pro plano pago antes do prazo acima (ver seção "Hospedagem definitiva do banco")
2. Considerar Dockerfile/deploy também pro ambiente de staging, se fizer sentido mais pra frente

## Como retomar em um chat novo

1. Ler este arquivo e `docs/SCOPE.md`
2. Ver o painel do Render (serviços `mora-backend`/`mora-frontend`) e do Supabase pra saber o estado atual — este arquivo descreve o momento em que foi escrito, não necessariamente o estado agora
3. Se o backend ainda não estiver no ar, o log de deploy do Render (aba Logs) é o primeiro lugar pra olhar
