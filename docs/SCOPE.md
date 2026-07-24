# 🍔 Morá - MVP
## Sistema de Gestão de Mesas para Restaurantes

### Objetivo
Desenvolver um SaaS para restaurantes presenciais focado no gerenciamento de mesas, comandas e pedidos.

O objetivo do MVP é resolver apenas a operação do salão.

**Não fazem parte do MVP:**
- Delivery
- Estoque
- Fiscal
- QR Code
- Cardápio Digital
- Fidelidade
- Relatórios avançados
- Integrações

---

### Público-alvo
Pequenos e médios restaurantes que trabalham com atendimento em mesas.

**Exemplos:**
- Restaurantes
- Hamburguerias
- Pizzarias
- Churrascarias
- Bares
- Casas de sushi

---

### Arquitetura

#### Backend
- Java 21
- Spring Boot 3
- Spring Security
- JWT + Refresh Token
- Spring Data JPA
- PostgreSQL
- Flyway
- Docker

#### Frontend
- React
- TypeScript
- Vite
- React Query
- React Router
- TailwindCSS

#### Infraestrutura
- Docker Compose
- Nginx
- PostgreSQL

#### Arquitetura do Sistema
O sistema será **Multi-Tenant**.
- Cada restaurante possui seus próprios dados.
- Todos os registros possuem: `restaurant_id`
- Nunca haverá acesso entre restaurantes.

---

### Módulos

#### 1. Autenticação
**Funcionalidades:**
- Login
- Logout
- Refresh Token
- Alteração de senha
- Cadastro do restaurante
- Cadastro do proprietário

#### 2. Restaurante
- **Cadastro:** Nome, CNPJ, Telefone, Endereço
- **Configurações:** Nome fantasia, Logo, Cor principal, Quantidade de mesas

#### 3. Usuários
- **Perfis:** `OWNER`, `MANAGER`, `WAITER`, `KITCHEN`, `CASHIER`
- Cada perfil possui permissões específicas.

#### 4. Mesas
- **CRUD completo**
- **Campos:** `id`, `number`, `status`, `restaurant_id`, `active`
- **Status:** `FREE`, `OCCUPIED`, `CLOSING`

#### 5. Categorias
- **Campos:** `id`, `name`, `active`, `restaurant_id`
- **Exemplos:** Hambúrgueres, Bebidas, Sobremesas, Pizzas

#### 6. Produtos
- **Campos:** `id`, `name`, `description`, `price`, `active`, `category_id`, `restaurant_id`

#### 7. Comanda (Tab)
Representa a conta aberta da mesa.
- **Campos:** `id`, `table_id`, `status`, `opened_at`, `closed_at`, `restaurant_id`
- **Status:** `OPEN`, `CLOSED`
- **Regras:** Uma mesa só pode possuir uma comanda aberta.
- **Nota para Sprint 5 (decidido na Sprint 4):** quando um grupo precisa juntar mesas (ex: mesas 5 e 6 encostadas pra caber mais gente), a solução não deve mexer no cadastro de `Mesa` (fundir/desfundir entidades é frágil — não fica claro quem é a mesa "principal" nem como desfazer). O modelo correto é trocar `table_id` (1:1) por um relacionamento N:N entre Comanda e Mesa (tabela de junção `tab_tables`). Ao abrir uma comanda pra um grupo, ela referencia várias mesas de uma vez; todas ficam `OCCUPIED` e recebem os mesmos pedidos; ao fechar, todas voltam a `FREE` independentemente. As mesas em si continuam sendo entidades simples e permanentes.

#### 8. Pedido (Order)
Representa um envio para cozinha. Uma mesma comanda pode possuir vários pedidos.

**Exemplo:**
- Mesa 7
  - 19:00: 2 Hambúrgueres
  - 20:15: 1 Refrigerante
  - 21:00: 1 Sobremesa
  - *(São três pedidos diferentes.)*

- **Campos:** `id`, `tab_id`, `created_at`, `restaurant_id`

#### 9. Item do Pedido
- **Campos:** `id`, `order_id`, `product_id`, `quantity`, `unit_price`, `observation`, `status`
- **Status:** `PENDING`, `PREPARING`, `READY`, `DELIVERED`, `CANCELLED`
- O preço é salvo no momento da venda.

#### 10. Pagamento
Para o MVP, manter simples.
- **Campos:** `payment_method`, `paid_amount`, `paid_at` *(dentro da própria Comanda/Tab)*
- **Métodos:** `PIX`, `CASH`, `DEBIT_CARD`, `CREDIT_CARD`

---

### Regras de Negócio

#### Mesas
- Uma mesa livre pode abrir uma comanda.
- Ao abrir: `FREE` → `OCCUPIED`
- Ao fechar: `OCCUPIED` → `FREE`

#### Produtos
- Somente produtos ativos podem ser vendidos.

#### Comandas
- Uma mesa só possui uma comanda aberta.

#### Pedidos
- Um pedido nunca é apagado.

#### Itens
- Cada item possui seu próprio status.
- **Fluxo:** `PENDING` → `PREPARING` → `READY` → `DELIVERED` *(ou `CANCELLED`)*

#### Fechamento
- Uma comanda só pode ser fechada quando todos os itens estiverem `DELIVERED` ou `CANCELLED`.

---

### Fluxo Principal
```
Cliente chega
   ↓
Garçom abre mesa
   ↓
Sistema cria Comanda
   ↓
Garçom adiciona itens
   ↓
Sistema cria Pedido
   ↓
Cozinha recebe
   ↓
Cozinha prepara
   ↓
Garçom entrega
   ↓
Cliente pede conta
   ↓
Pagamento
   ↓
Comanda fecha
   ↓
Mesa volta para Livre
```

---

### Telas

#### Dashboard
- Mesas Livres
- Mesas Ocupadas
- Pedidos em preparo
- Faturamento do dia

#### Login
- Login / Senha

#### Mesas
- Grade: 🟢 Mesa 1 | 🔴 Mesa 2 | 🟢 Mesa 3 | 🟡 Mesa 4
- Ao clicar: Abre detalhes da mesa.

#### Mesa (Detalhes)
- **Mostrar:** Itens, Quantidade, Observação, Valor
- **Botões:** Adicionar Item, Enviar para Cozinha, Fechar Conta

#### Produtos
- CRUD
- Pesquisar
- Filtrar por categoria

#### Categorias
- CRUD

#### Cozinha
- Lista em tempo real (ex: Mesa 4 - Pedido 18 - 2 Hambúrguer Sem cebola)
- **Botões:** Recebido, Preparando, Pronto, Entregue

#### Caixa
- Mesa X, Itens, Subtotal, Total, Forma de pagamento, Fechar Conta

---

### Ordem de Desenvolvimento

- **Sprint 1:** Infraestrutura (Docker, PostgreSQL, Flyway, Spring Boot, React), Login (JWT), Multi-Tenant
- **Sprint 2:** Restaurante, Usuários, Permissões
- **Sprint 3:** Categorias, Produtos
- **Sprint 4:** Mesas (CRUD, Status)
- **Sprint 5:** Comandas (Abrir, Fechar)
- **Sprint 6:** Pedidos (Adicionar itens, Remover itens, Observações, Total)
- **Sprint 7:** Tela da Cozinha (Atualização dos status)
- **Sprint 8:** Pagamento, Fechamento, Dashboard

---

### Ordem de Desenvolvimento — Frontend

Backend (Sprints 1–8) concluído primeiro; frontend retomado a partir da Sprint 9, priorizando o fluxo operacional (Mesas → Produtos → Comanda → Cozinha → Caixa) antes de telas de apoio como o Dashboard.

- **Sprint 9:** Infraestrutura (Vite, React, TypeScript, Tailwind), React Router, cliente HTTP com refresh token, React Query, Login, rotas protegidas, layout base.
- **Sprint 10:** Categorias e Produtos (CRUD, busca, filtro por categoria).
- **Sprint 11:** Mesas (grade por status, CRUD, criação em lote).
- **Sprint 12:** Comanda e Pedidos — tela de Mesa (Detalhes): abrir comanda, listar itens, adicionar item, enviar para cozinha.
- **Sprint 13:** Cozinha — fila de itens por status com atualização quase em tempo real.
- **Sprint 14:** Caixa — resumo da comanda, forma de pagamento, fechamento.
- **Sprint 15:** Dashboard.
- **Sprint 16:** Configurações do Restaurante — nome fantasia, logo, cor principal, quantidade de mesas, telefone, endereço, CNPJ (`GET/PUT /restaurants/me`, já existe no backend desde a Sprint 2). **Concluída antecipadamente, junto da Sprint 10** (ver `docs/SPRINT10.md`).
- **Sprint 17:** Gestão de Funcionários — CRUD de usuários com papéis (`UserController`, já existe no backend desde a Sprint 2).

Sprints 16 e 17 ficam por último por serem telas de configuração/administração, não do fluxo operacional do salão — mesmo critério de priorização usado para deixar o Dashboard na Sprint 15.

Com o roadmap original (Sprints 1–17) concluído, o desenvolvimento passou a puxar itens do backlog abaixo:

- **Sprint 18:** Impressão de comandas/cozinha (backend) — `printed_at`/`receipt_printed_at`/`auto_print_kitchen_tickets`, endpoints de marcação (ver `docs/SPRINT18.md`).
- **Sprint 19:** Impressão de comandas/cozinha (frontend) — telas de impressão via `window.print()`, botão manual, disparo automático, checkbox de configuração (ver `docs/SPRINT19.md`). **Feature concluída ponta a ponta.**
- **Sprint 20:** Relatórios financeiros — `GET /reports/summary` (faturamento, ticket médio, quebra por forma de pagamento, produtos mais vendidos) por período, restrito a `OWNER`/`MANAGER`, e tela `/reports` com presets de período (ver `docs/SPRINT20.md`). **Feature concluída ponta a ponta.**
- **Sprint 21:** Balcão — comanda sem mesa (`tableIds` vazio em `POST /tabs`), botão "Balcão" e lista de retomada em Mesas (ver `docs/SPRINT21.md`). **Feature concluída ponta a ponta.**

---

### Funcionalidades Futuras (Backlog)
Após validar o produto com clientes reais, considerar:
- Comanda por QR Code
- Cardápio digital
- Pedidos pelo celular
- Delivery
- ~~Balcão~~ ✅ Sprint 21
- ~~Impressão de comandas~~ ✅ Sprint 19
- ~~Impressão de cozinha~~ ✅ Sprint 19
- Impressão fiscal (NFC-e)
- Controle de estoque
- Ficha técnica
- Promoções e cupons
- Programa de fidelidade
- Integração com iFood
- Integração com WhatsApp
- ~~Relatórios financeiros~~ ✅ Sprint 20
- Dashboard analítico
- Aplicativo para garçons
- Aplicativo para clientes
- Multiunidade (redes de restaurantes)
