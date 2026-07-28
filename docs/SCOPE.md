
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
- **Configurações:** Nome fantasia, Logo (upload), Quantidade de mesas, impressão automática de comandas de cozinha, limiares de alerta de demora na cozinha, taxa de serviço padrão (habilitada/percentual)

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
- **Desconto pontual** *(opcional, só OWNER/MANAGER)*: `discount_type` (`FIXED`/`PERCENTAGE`), `discount_value`, `discount_reason`, `discount_applied_by`, `discount_applied_at`.

#### 10. Pagamento
Para o MVP, manter simples.
- **Campos:** `payment_method`, `paid_amount`, `paid_at` *(dentro da própria Comanda/Tab)*
- **Métodos:** `PIX`, `CASH`, `DEBIT_CARD`, `CREDIT_CARD`
- **Desconto pontual na comanda** *(opcional, só OWNER/MANAGER)*: mesmos campos do item (`discount_type`, `discount_value`, `discount_reason`, `discount_applied_by`, `discount_applied_at`), aplicado sobre o total já líquido dos descontos de item.
- **Taxa de serviço**: `service_charge_percentage` e `service_charge_amount`, gravados na Comanda só no momento do pagamento. Percentual padrão configurável por restaurante (`service_charge_enabled`, `service_charge_percentage`); só OWNER/MANAGER podem waivar ou ajustar por comanda — outros papéis sempre recebem o padrão do restaurante, aplicado no servidor. Excluída do faturamento em Dashboard/Relatórios (é repasse à equipe, não receita).

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
- ~~Produto "esgotou hoje"~~ ✅ (indisponibilidade diária sem desativar o produto permanentemente)
- ~~"Chamar garçom" / "Pedir a conta" pelo celular~~ ✅ (`TableRequest`, fecha lacuna do autoatendimento)
- ~~Modificadores padronizados~~ ✅ (tamanho, ponto da carne, extras — em vez de observação em texto livre)
- ~~Alerta de demora na cozinha~~ ✅ (limiares configuráveis de aviso/crítico por restaurante)
- ~~Desconto pontual~~ ✅ 2026-07-27 (fixo ou percentual, em item ou comanda, restrito a OWNER/MANAGER, com motivo e auditoria de quem aplicou)
- ~~Gorjeta / taxa de serviço~~ ✅ 2026-07-27 (percentual configurável por restaurante, aplicada automaticamente no fechamento; só OWNER/MANAGER podem waivar/ajustar — outros papéis sempre recebem o padrão do restaurante, mesmo chamando a API direto; excluída do faturamento nos relatórios/dashboard por ser repasse, não receita)
- ~~Correção de pagamento em comanda fechada~~ ✅ 2026-07-27 (restrito a OWNER/MANAGER, com motivo obrigatório; **decisão de design**: não reabre a comanda pra atendimento nem mexe na mesa — só substitui forma/valor/taxa de serviço registrados, mantendo a comanda `CLOSED`. Acessível a qualquer momento via lista "Comandas fechadas hoje" no Caixa. Escopo reduzido deliberadamente do item original do backlog: reabrir a comanda de verdade arriscava duas comandas abertas na mesma mesa)
- ~~Transferir item entre comandas~~ ✅ 2026-07-27 (seleciona um ou mais itens em qualquer status exceto cancelado e move pra outra comanda aberta, mesmos papéis que mesclam mesa; itens movidos entram num pedido novo na comanda de destino, preservando preço/desconto/status/hora original; o pedido de origem nunca é apagado, só fica menor; desfazer reaproveita a mesma ação invertendo origem/destino, com janela curta de ~20s)
- ~~Log de auditoria — cancelamento de item~~ ✅ 2026-07-28 (`cancelledBy`/`cancelledAt` em `OrderItem`, no mesmo padrão do desconto pontual; exibido na comanda como "Cancelado por {nome} às {hora}")
- ~~Tempo estimado de espera no cardápio digital~~ ✅ 2026-07-28 (`deliveredAt` em `OrderItem`, mesmo padrão do cancelamento; tempo médio de preparo calculado por produto a partir do histórico `createdAt` → `deliveredAt` e exibido no cardápio público como "Pronto em ~X min")
- ~~Status do pedido em tempo real no cardápio digital~~ ✅ 2026-07-28 (`GET /public/menu/{slug}` passou a devolver os itens da comanda aberta da mesa com status; cliente acompanha pelo mesmo polling de 4s que o cardápio já usava, via pílula flutuante que abre um painel com stepper de progresso por item)

**Prioridade 1 do backlog anterior (discutido em 2026-07-25) concluída inteira** — todos os 6 itens de ganho rápido acima já foram entregues.

O que falta, organizado por prioridade — da mais fácil/importante até a mais difícil (bônus). Cobrança do próprio SaaS fica de fora por ora, produto ainda em desenvolvimento (ver "Fora de escopo" no fim).

#### Prioridade 2 — ganho rápido, reaproveita o que já existe (baixo esforço, alto valor)
1. **Destaque de produto no cardápio digital** — campo simples ("mais pedido" / "recomendado") pra aumentar o ticket médio sem mexer em preço.
2. **Alerta de "mesa esquecida"** — mesa aberta há muito tempo sem pedido novo avisa o garçom, reaproveitando os limiares configuráveis que o alerta de demora na cozinha já usa.
3. **Cupom de desconto avulso** (primeira visita / aniversário) — versão simples de incentivo, sem todo o sistema de fidelidade por pontos.
4. **"Pedir de novo" rápido** — no cardápio digital, repetir o último pedido da sessão sem precisar procurar o produto de novo.
5. **Relatório de horário de pico** — quantas mesas ocupadas por hora do dia / dia da semana. O dado já existe (`openedAt`/`closedAt` da comanda), falta só a agregação nos Relatórios.

#### Prioridade 3 — fecha lacunas do fluxo de pagamento/comanda (esforço médio)
6. **Pagamento online — Pix e cartão via gateway** — hoje "Pix"/"Cartão" na comanda é só um registro manual de que o cliente pagou por fora (Pix direto pro banco do restaurante, ou maquininha do garçom); aqui é integrar um gateway de verdade (ex: Mercado Pago, PagSeguro, Asaas) que gera cobrança Pix real (QR Code) e cobra cartão online, com confirmação automática batendo na comanda. **Pré-requisito técnico pro delivery** (Prioridade 8) — não dá pra vender delivery sem cobrar o cliente à distância — mas já traz valor agora: cliente do autoatendimento (QR Code na mesa) pode pagar direto pelo celular, sem esperar o garçom.
7. **Divisão de conta** — hoje `paidAmount` precisa bater exatamente com o total (decisão explícita da Sprint 8); sem suporte a pagamento parcial/dividido.
8. **Abertura/fechamento de caixa com sangria** — hoje "faturamento do dia" é só soma de pagamentos; não existe abertura de turno com valor inicial, conferência de dinheiro físico no fechamento, nem sangria.
9. **Split de comanda por pessoa** — diferente da divisão de conta simples: atribui cada item da mesa a uma pessoa específica, pra fechar "cada um paga o que consumiu". Mais complexo que dividir em partes iguais.

#### Prioridade 4 — cardápio, engajamento e gestão (esforço médio)
10. **Ficha técnica / custo de produto** — complementa os Relatórios (Sprint 20) com margem real, não só faturamento. Versão enxuta: campo `costPrice` no Produto, sem módulo de estoque completo.
11. **Combo/kit de produtos** — preço fixo pra um conjunto de produtos (ex: lanche + bebida + sobremesa), sem depender de desconto manual toda vez.
12. **Cardápio por horário** — bloquear produto fora de uma janela (ex: cardápio de almoço some à noite, bebida alcoólica só depois de certo horário). Hoje só existe "ativo/inativo" manual e "esgotou hoje".
13. **"Happy hour" automático** — desconto que liga/desliga sozinho num horário configurado, sem o garçom precisar lembrar de aplicar manualmente.
14. **Áreas do salão** — agrupar mesas por região (Salão interno, Varanda, Balcão) pra facilitar a visualização em restaurantes grandes. Hoje é só uma grade numerada.
15. **Avaliação pós-refeição** — tela rápida de feedback ("como foi?") pelo mesmo QR Code, depois do pagamento.
16. **Cardápio multilíngue** — inglês/espanhol pro cardápio digital, bom pra restaurante com público turista.
17. **Recibo digital** — enviar o recibo por WhatsApp/e-mail, além (ou em vez) da impressão física.
18. **Desempenho por garçom** — vendas e tempo médio de atendimento por funcionário.
19. **Metas e comparativos no dashboard** (mês a mês) — parte do "Dashboard analítico".

#### Prioridade 5 — reserva de mesa (escopo maior, fluxo novo)
20. **Reserva de mesa** — agendamento com horário, evita mesa vazia "travada" ou cliente sem lugar. Fluxo novo inteiro (agendamento, notificação, conflito com mesa já ocupada), maior escopo que os itens acima.

#### Prioridade 6 — integrações externas (maior esforço, dependem de terceiros)
21. **Impressora térmica (ESC/POS)** via rede/USB — hoje a impressão é `window.print()` (Sprint 19), depende de driver do sistema.
22. **Maquininha de cartão (TEF)** — integração com terminal físico de cartão usado pelo garçom/caixa; diferente do pagamento online da Prioridade 3, que é pro cliente pagar direto pelo celular.
23. **Integração com WhatsApp** — compartilhamento e notificações mais amplas (além do recibo digital simples da Prioridade 4).
24. **Integração com iFood**.
25. **Impressão fiscal (NFC-e)** — bloqueador legal real pra operação formal, mas integração pesada (SEFAZ, certificado digital, homologação).

#### Prioridade 7 — bônus: escopo maior, só depois de validar com clientes reais
26. **Controle de estoque**.
27. **Promoções e cupons** — sistema completo, além do cupom avulso simples da Prioridade 2.
28. **Programa de fidelidade**.
29. **Aplicativo para garçons / Aplicativo para clientes**.
30. **Multiunidade (redes de restaurantes)**.

#### Prioridade 8 — Delivery (travado até o presencial estar completo)
> **Decisão explícita do usuário (2026-07-27): não começar nada de Prioridade 8 enquanto qualquer item das Prioridades 2 a 7 (operação presencial) ainda estiver em aberto.** Delivery só entra depois que o fluxo de pedido presencial estiver todo redondo.

31. **Cadastro de endereço de entrega** — capturado no pedido (cardápio digital em modo delivery, sem mesa associada).
32. **Cálculo de frete / raio de entrega** — por distância ou bairro atendido.
33. **Status de entrega** — separando → saiu pra entrega → entregue; extensão do fluxo de status de item que já existe (`PENDING`/`PREPARING`/`READY`/`DELIVERED`).
34. **Gestão de entregador** — próprio ou terceirizado, atribuição de pedido a entregador.
35. **Comanda específica pra delivery** — endereço, contato do cliente, forma de pagamento (depende do pagamento online da Prioridade 3, já que não dá pra cobrar na entrega sem risco).

#### Fora de escopo por enquanto (decisão explícita do usuário, 2026-07-25)
- **Cobrança/assinatura do próprio SaaS** (planos, trial, gateway de pagamento) — o produto ainda está em desenvolvimento, não é hora de vender.
- **Onboarding self-service** — depende do item acima.

#### Gaps de infraestrutura identificados (2026-07-27, fora da lista de features acima)
Não bloqueiam as prioridades de produto, mas vão precisar de atenção antes de abrir o produto pra clientes reais:
- **Recuperação de senha** ("esqueci minha senha") — não existe hoje; só login, registro, refresh token e troca de senha autenticado.
- **Rate limiting no login** — sem proteção contra força bruta.
- **Testes automatizados no frontend** — o backend tem suíte de integração sólida; o frontend não tem nenhum teste automatizado (nem Vitest, nem Playwright/Cypress).
- **Docker/deploy de produção** — hoje só existe `docker-compose.yml` de desenvolvimento local (só o banco); sem Dockerfile da aplicação nem configuração de produção.
- **Logging estruturado / observabilidade** (Sentry ou equivalente) — hoje é só o log padrão do Spring Boot.
- **Backup do banco de dados** — sem estratégia definida ainda.
