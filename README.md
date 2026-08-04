# 🍔 Morá

SaaS de gestão de mesas, comandas e pedidos para restaurantes presenciais (hamburguerias, pizzarias, bares, churrascarias, casas de sushi etc). Cobre a operação completa do salão: mesas → cardápio/pedidos → cozinha → pagamento, incluindo autoatendimento via QR Code.

Para o escopo completo do produto, regras de negócio e histórico de decisões, ver [`docs/SCOPE.md`](docs/SCOPE.md).

## Stack

**Backend**
- Java 21, Spring Boot 3, Spring Security (JWT + refresh token)
- Spring Data JPA, Flyway, PostgreSQL
- springdoc-openapi (Swagger UI)

**Frontend**
- React 19, TypeScript, Vite
- React Router, TanStack Query, Tailwind CSS, Axios

**Infraestrutura**
- Docker / Docker Compose (Postgres local)
- Deploy em produção: Render (backend em Docker + frontend estático) — detalhes em [`docs/DEPLOY.md`](docs/DEPLOY.md)
- Storage de imagens: disco local em dev, Supabase Storage em produção

## Arquitetura

Sistema **multi-tenant**: cada restaurante é isolado por `restaurant_id` em todas as tabelas relevantes; nunca há acesso cruzado entre restaurantes.

```
restaurant_saas/
├── backend/    # API Spring Boot
├── frontend/   # SPA React
├── docs/       # decisões de escopo, sprints, deploy, backup, Pix
└── docker-compose.yml
```

## Pré-requisitos

- Java 21 (JDK)
- Node.js 20+
- Docker Desktop (para o Postgres local)
- Maven Wrapper já incluso no backend (`mvnw`/`mvnw.cmd`)

## Rodando localmente

### 1. Banco de dados

```bash
docker compose up -d
```

Sobe um Postgres 16 em `localhost:5432` (`restaurant_saas` / `postgres` / `postgres`).

### 2. Backend

```bash
cd backend
copy .env.example .env   # preencher pelo menos JWT_SECRET
./mvnw spring-boot:run
```

Variáveis de ambiente relevantes estão documentadas em [`backend/.env.example`](backend/.env.example) (banco, JWT, importação de cardápio via Gemini, storage de imagens, e-mail transacional, backup). Sem preencher as variáveis opcionais, os recursos correspondentes caem em modo de desenvolvimento (ex: e-mail vai pro log em vez de ser enviado de verdade).

API sobe em `http://localhost:8080`. Documentação interativa em `http://localhost:8080/swagger-ui.html`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

SPA sobe em `http://localhost:5173`, consumindo a API em `/api/v1`.

## Testes

```bash
# backend (requer o Postgres do docker compose rodando — os testes de integração
# usam esse banco diretamente, não Testcontainers)
cd backend
./mvnw test

# frontend — sem suíte automatizada ainda (ver docs/SCOPE.md, gaps de infraestrutura)
```

## Build de produção

```bash
# backend
cd backend
./mvnw clean package

# frontend — usar sempre este comando antes de subir mudanças, ele roda o
# mesmo type-check (tsc -b) que o build de produção usa
cd frontend
npm run build
```

## Documentação adicional

- [`docs/SCOPE.md`](docs/SCOPE.md) — escopo do produto, módulos, regras de negócio e backlog priorizado
- [`docs/DEPLOY.md`](docs/DEPLOY.md) — infraestrutura de produção (Render/Supabase), variáveis de ambiente e problemas já resolvidos
- [`docs/BACKUP_RESTORE.md`](docs/BACKUP_RESTORE.md) — estratégia de backup do banco
- [`docs/PIX_PAYMENT.md`](docs/PIX_PAYMENT.md) — desenho da integração de pagamento via Pix
- [`docs/SPRINT*.md`](docs) — histórico de decisões por sprint
