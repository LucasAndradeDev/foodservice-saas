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
- **Sprint 22:** Cardápio digital — página pública `/menu/:slug` (sem login), slug amigável gerado automaticamente, foto por produto, QR Code gerado em Configurações (ver `docs/SPRINT22.md`). **Feature concluída ponta a ponta.**

---

### Funcionalidades Futuras (Backlog)

Itens já entregues fora do roadmap original, puxados do backlog conforme o produto evoluiu:
- ~~Cardápio digital~~ ✅ Sprint 22
- ~~Balcão~~ ✅ Sprint 21
- ~~Impressão de comandas~~ ✅ Sprint 19
- ~~Impressão de cozinha~~ ✅ Sprint 19
- ~~Relatórios financeiros~~ ✅ Sprint 20
- ~~Comanda por QR Code / Pedidos pelo celular~~ ✅ Sprint 23 (autoatendimento)
- ~~Fusão de comandas mid-service~~ ✅ Sprint 24 (não estava no backlog original; fechou lacuna do autoatendimento)
- ~~Importação de cardápio via Excel + IA~~ ✅ Sprint A (não estava no backlog original)

O que falta, organizado por prioridade (discutido em 2026-07-25, foco em praticidade real pro restaurante; cobrança do próprio SaaS fica de fora por ora — produto ainda em desenvolvimento):

#### Prioridade 1 — ganho rápido, resolve dor imediata do dia a dia
1. **Produto "esgotou hoje"** — indisponibilidade diária sem desativar o produto permanentemente (hoje só existe `active`, que é definitivo).
2. **"Chamar garçom" pelo celular** — fecha lacuna do autoatendimento (Sprint 23): cliente sem garçom por perto não tem como pedir algo fora do pedido.
3. **"Pedir a conta" pelo celular** — mesma lógica: cliente sinaliza que quer fechar, caixa recebe o aviso.
4. **Modificadores padronizados** (sem cebola, ponto da carne, tamanho) em vez de observação em texto livre.
5. **Alerta de demora na cozinha** — item que passa de X minutos em `PENDING`/`PREPARING` muda de cor na fila.
6. **Desconto pontual** em item ou comanda (reclamação, promoção de última hora) — hoje não existe forma de abater valor sem mexer no preço do produto.

#### Prioridade 2 — fecha lacunas do fluxo de pagamento/comanda
7. **Divisão de conta** — hoje `paidAmount` precisa bater exatamente com o total (decisão explícita da Sprint 8); sem suporte a pagamento parcial/dividido.
8. **Gorjeta / taxa de serviço (10%)** — não existe no modelo de pagamento.
9. **Cancelamento de pagamento / reabertura de comanda** — não existe undo pra pagamento (só existe pra merge de mesas, Sprint 24).
10. **Transferir item entre comandas** — inverso do merge (Sprint 24): tira um item de uma comanda e joga em outra, sem mexer nas mesas.
11. **Abertura/fechamento de caixa com sangria** — hoje "faturamento do dia" é só soma de pagamentos; não existe abertura de turno com valor inicial, conferência de dinheiro físico no fechamento, nem sangria.

#### Prioridade 3 — gestão e visibilidade
12. **Ficha técnica / custo de produto** — complementa os Relatórios (Sprint 20) com margem real, não só faturamento. Versão enxuta: campo `costPrice` no Produto, sem módulo de estoque completo.
13. **Log de auditoria** — quem cancelou item, quem deu desconto.
14. **Desempenho por garçom** — vendas e tempo médio de atendimento por funcionário.
15. **Metas e comparativos no dashboard** (mês a mês) — parte do "Dashboard analítico".

#### Prioridade 4 — integrações externas (maior esforço, dependem de terceiros)
16. **Impressora térmica (ESC/POS)** via rede/USB — hoje a impressão é `window.print()` (Sprint 19), depende de driver do sistema.
17. **Maquininha de cartão (TEF)** — hoje "cartão" é só um registro manual da forma de pagamento.
18. **Integração com WhatsApp** — compartilhar link do cardápio digital / notificações.
19. **Integração com iFood**.
20. **Impressão fiscal (NFC-e)** — bloqueador legal real pra operação formal, mas integração pesada (SEFAZ, certificado digital, homologação).

#### Prioridade 5 — escopo maior, só depois de validar com clientes reais
21. **Controle de estoque**.
22. **Promoções e cupons**.
23. **Programa de fidelidade**.
24. **Aplicativo para garçons / Aplicativo para clientes**.
25. **Multiunidade (redes de restaurantes)**.

#### Fora de escopo por enquanto (decisão explícita do usuário, 2026-07-25)
- **Cobrança/assinatura do próprio SaaS** (planos, trial, gateway de pagamento) — o produto ainda está em desenvolvimento, não é hora de vender.
- **Onboarding self-service** — depende do item acima.
