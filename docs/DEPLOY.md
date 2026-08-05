# Deploy — Status e guia de referência

Documenta a infraestrutura de produção (Render + Neon + Supabase) montada em 2026-07-29/30 (backend/frontend) e 2026-08-05 (banco): o que já está pronto, o que falta, e os problemas que já apareceram no caminho (pra não repetir o mesmo debug numa próxima sessão). Este arquivo **nunca deve conter segredos reais** (senha, chaves) — só nomes de variável e onde encontrar o valor.

**URLs reais em produção** (o Render adicionou sufixo aleatório porque os nomes "limpos" já estavam em uso):
- Backend: https://mora-backend-ubuw.onrender.com
- Frontend: https://mora-frontend-tdmc.onrender.com

## Arquitetura

| Peça | Onde roda | Papel |
|---|---|---|
| Backend (Spring Boot) | Render — Web Service, Docker, plano free | API, regras de negócio |
| Frontend (React/Vite) | Render — Static Site, plano free (sem campo `plan`) | build estático servido direto |
| Banco (Postgres) | Neon — plano free (`mora-db`, região `us-east-2`/Ohio) | dados da aplicação |
| Storage de imagens | Supabase Storage (bucket `uploads`, público) | fotos de produto/logo |

> **2026-07-30**: banco temporariamente movido do Supabase pro Postgres do próprio Render, por causa do circuit breaker recorrente do Supavisor (problema #4).
> **2026-08-05**: banco migrado do Postgres do Render pro **Neon** (plano free, decisão final — ver seção "Hospedagem definitiva do banco" abaixo). Motivo: o Postgres free do Render expira 30 dias depois de criado, e o Neon free não tem esse prazo. Migração feita com `pg_dump`/`psql` (via Docker, imagem `postgres:18-alpine` pra bater com a versão de produção — ambos Render e Neon rodam Postgres 18.x), dados conferidos por contagem de linhas nas tabelas principais antes de trocar as variáveis de ambiente. Storage de imagens continua no Supabase — só o banco mudou.

Frontend e backend ficam em domínios `onrender.com` diferentes. Em vez de CORS, o frontend usa uma regra de **Rewrite** (`/api/* → URL do backend/api/*`) configurada no painel do Render — o navegador nunca sabe que é cross-origin, e o código do frontend continua usando a base relativa `/api/v1` sem mudança nenhuma.

## Arquivos relevantes no repositório

- `render.yaml` (raiz) — Blueprint com os dois serviços (`mora-backend`, `mora-frontend`)
- `backend/Dockerfile` + `backend/.dockerignore` — build multi-stage (Maven → JRE)
- `backend/src/main/java/.../service/{FileStorageService,AbstractFileStorageService,LocalFileStorageService,SupabaseFileStorageService}.java` — upload local (dev) ou Supabase Storage (prod), trocado via `STORAGE_PROVIDER`
- `backend/.env.example` — lista completa de variáveis, incluindo as de storage

## Variáveis de ambiente do backend (preenchidas direto no painel do Render, nunca commitadas)

| Variável | De onde vem |
|---|---|
| `SPRING_DATASOURCE_URL` | Manual: projeto `mora-db` no [Neon](https://neon.tech) → botão **Connect** → connection string **pooled** (host com sufixo `-pooler`). Montar como `jdbc:postgresql://<host-pooler>/<database>?sslmode=require` — **sem** usuário/senha embutidos na URL (ver problema #2) |
| `SPRING_DATASOURCE_USERNAME` | Mesmo modal **Connect** do Neon, campo do usuário (ex. `neondb_owner`) |
| `SPRING_DATASOURCE_PASSWORD` | Mesmo modal **Connect** do Neon, campo da senha |
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
- [x] Dados migrados do Postgres do Render pro Neon (`mora-db`, plano free, definitivo) via `pg_dump`/`psql`, contagem de linhas conferida
- [x] Variáveis `SPRING_DATASOURCE_*` do `mora-backend` atualizadas no painel do Render pra apontar pro Neon, redeploy feito e confirmado (`/actuator/health` UP, `/api/v1/public/menu/tatu-bola` retornando dados reais do Neon)
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

## Hospedagem definitiva do banco — decisão tomada (2026-08-05)

Pesquisa inicial em 2026-08-04 (Render pago vs. Supabase Pro, ambos com custo mensal) foi substituída pela escolha de usar o **Neon**, que não estava no comparativo original:

- **Neon free tier**: **$0/mês**. Sem prazo de expiração automática do banco (diferente do Postgres free do Render, que expira 30 dias após criado). Serverless, autosuspend quando sem tráfego (cold start no primeiro request depois de idle — aceitável pro volume atual do projeto).
- Motivo de trocar do Render: o `mora-db` do Render (criado em 2026-07-29/30) expiraria em ~28-29/08/2026, com 14 dias de carência até apagar os dados de vez (~11-12/09/2026) — fonte: [changelog do Render](https://render.com/changelog/free-postgresql-instances-now-expire-after-30-days-previously-90). Ficar no Render exigiria upgrade pago (~$7-8/mês); o Neon free resolve sem custo.
- Supabase Pro ($25/mês) descartado pelo mesmo motivo da decisão anterior: mais caro, e reabre os problemas #3/#4 desta página (circuit breaker do Supavisor, IPv4/IPv6).

**Migração feita em 2026-08-05**: `pg_dump` do Render (`--no-owner --no-privileges --no-comments`, pra não carregar o role `mora` que não existe no Neon) → `psql` no Neon, ambos rodando dentro do Docker com a imagem `postgres:18-alpine` (mesma versão major do servidor, ver [[project_postgres_prod_version_mismatch]] na memória). Contagem de linhas em `restaurants`, `users`, `restaurant_tables`, `tabs` e `products` conferida igual nos dois bancos antes de cortar o Render.

O `mora-db` do Render **ainda não foi apagado** — só depois de confirmar que o backend em produção está estável rodando contra o Neon por alguns dias.

## Próximos passos

1. Testar mais a fundo em produção (login, abrir mesa, pedido, pagamento) — confirmado até agora só leitura (`/actuator/health` e o cardápio público de "Tatu Bola" respondendo com dados reais do Neon); falta confirmar escrita (criar pedido, fechar comanda etc.)
2. Depois de alguns dias estável, apagar o `mora-db` antigo no painel do Render (ação manual, não faz parte do Blueprint)
3. Considerar Dockerfile/deploy também pro ambiente de staging, se fizer sentido mais pra frente

## Como retomar em um chat novo

1. Ler este arquivo e `docs/SCOPE.md`
2. Ver o painel do Render (serviços `mora-backend`/`mora-frontend`) e do Supabase pra saber o estado atual — este arquivo descreve o momento em que foi escrito, não necessariamente o estado agora
3. Se o backend ainda não estiver no ar, o log de deploy do Render (aba Logs) é o primeiro lugar pra olhar
