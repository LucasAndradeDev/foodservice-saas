
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
- ~~Destaque de produto no cardápio digital~~ ✅ 2026-07-28 (dois selos independentes: `featured` manual em `Product` — o dono liga/desliga pela tela de Produtos — e "Mais pedido" automático, calculado dos top 3 produtos por quantidade vendida nos últimos 30 dias via a mesma query já usada nos Relatórios; os dois podem aparecer juntos no mesmo produto)
- ~~Alerta de "mesa esquecida"~~ ✅ 2026-07-28 (mesa ocupada há muito tempo sem pedido novo avisa o garçom; limiares próprios — `tableForgottenWarningThresholdMinutes`/`tableForgottenCriticalThresholdMinutes`, default 30/60 min — não os mesmos do alerta de cozinha, já que a semântica é diferente; `lastOrderAt` gravado na comanda a cada pedido novo, com fallback pro `openedAt` da comanda enquanto não há nenhum pedido; vale só pra mesas de verdade, não pra comandas de Balcão)
- ~~"Pedir de novo" rápido~~ ✅ 2026-07-28 (botão no painel de status do cardápio digital repopula o carrinho com os itens do último pedido enviado — escopo é a mesa toda, não um dispositivo específico, já que não existe sessão por cliente no autoatendimento; modificadores casados por nome contra o cardápio atual, já que o histórico só guarda nome/preço, não o id da opção; produto inativo ou esgotado hoje é descartado do reenvio e nomeado no aviso, pra não confundir o cliente)
- ~~Relatório de horário de pico~~ ✅ 2026-07-29 (grid dia da semana × hora com duas métricas — ocupação média de mesas e média de pedidos — em `GET /reports/peak-hours`; exclui comandas de Balcão da ocupação, mas conta os pedidos delas; cada célula mostra quantas vezes aquele dia da semana ocorreu no período (`sampleCount`), já que médias de 1 amostra são bem menos confiáveis que de várias; heatmap na tela de Relatórios com destaque de célula por hover/teclado e legenda de escala)
- ~~Ficha técnica / custo de produto~~ ✅ 2026-07-29 (`costPrice` opcional no Produto; `unitCostPrice` gravado no `OrderItem` no momento da venda, no mesmo padrão de snapshot do `unitPrice` — **decisão de design**: o custo usado é sempre o de quando o item foi vendido, não o custo atual do produto, pra não distorcer margens de períodos passados quando o custo muda; vendas registradas antes do primeiro cadastro de custo ficam com custo desconhecido pra sempre, sem estimativa retroativa; tabela "Produtos mais vendidos" nos Relatórios ganhou colunas de Margem e Margem %, calculadas só sobre a fração das vendas com custo conhecido, com aviso visual quando a cobertura é parcial)
- ~~Metas e comparativos no dashboard~~ ✅ 2026-07-29 (só na tela de Relatórios, não no Dashboard operacional, por ser informação sensível; comparativo usa "período anterior de mesma duração, imediatamente antes" — nunca "mesmo intervalo do mês anterior", que compararia quantidades de dias diferentes perto de virada de mês e distorceria o % de variação; cada stat tile mostra o valor anterior e o intervalo de datas exato comparado, não só a porcentagem solta; meta é uma por mês, editável a qualquer momento, sem sobrescrever meses passados — navegação de mês no card é independente do filtro de período principal da tela)
- ~~Cardápio por horário~~ ✅ 2026-07-30 (`ProductAvailabilityWindow`: um produto pode ter zero — sempre disponível, comportamento anterior — ou várias janelas, cada uma com dia da semana opcional (`null` = todo dia) e horário de início/fim; **decisão de design**: cada janela vale só dentro de um único dia, sem cruzar meia-noite, pra manter a comparação de horário simples — "22h–02h" precisa ser cadastrado como duas janelas; diferente do "esgotou hoje", que é só um aviso visual, aqui o bloqueio é de verdade no servidor (`OrderService`, mesmo padrão do produto inativo) pros dois fluxos — presencial e autoatendimento — já que os motivos típicos (cozinha não preparar, exigência legal de álcool) não podem depender do garçom lembrar de respeitar; "pedir de novo" no cardápio digital também passou a pular itens fora do horário, mesmo tratamento que já dava a itens esgotados)
- ~~Áreas do salão~~ ✅ 2026-07-30 (`DiningArea`: entidade nova por restaurante — nome + `displayOrder`, CRUD completo numa tela própria (`/dining-areas`), exclusão bloqueada enquanto houver mesa associada, mesmo padrão de restrição já usado em Categoria/Produto; `RestaurantTable` ganhou `area` opcional; a grade de Mesas passa a agrupar por área — com seção "Sem área" — só quando há mais de um grupo, pra não mudar a experiência de quem ainda não cadastrou nenhuma área; reordenação das áreas é por arrastar-e-soltar usando `framer-motion` (já era dependência do projeto, sem lib nova); **decisão de design**: em vez de reaproveitar o clique do card da mesa pra também iniciar um arrasto — frágil de diferenciar clique-rápido de arrastar, principalmente no touch — a atribuição de área por arrastar mesa vive num modo dedicado "Organizar mesas" (mesmo padrão do modo "Abrir comanda" já existente), que desliga o clique normal enquanto ativo e mostra até as áreas ainda vazias como alvo de soltar)
- ~~Avaliação pós-refeição~~ ✅ 2026-07-30 (`PostMealFeedback`: nota de 1 a 5 + comentário opcional, amarrada ao `Tab` — não à mesa — pra funcionar igual em mesa e Balcão; **decisão de design**: em vez do servidor calcular "aguardando avaliação" olhando o histórico de comandas fechadas (armadilha real: qualquer comanda fechada sem nota, mesmo de dias atrás, disparava o prompt pro próximo cliente que só ia fazer o primeiro pedido), o servidor só expõe `currentTabId` — a comanda aberta agora, se houver — e é o próprio navegador do cliente que detecta a transição "tinha comanda aberta → não tem mais" enquanto a página dele está viva, redirecionando só nesse caso; um cliente novo escaneando o QR pela primeira vez nunca vê a avaliação de outro cliente. Balcão não tem QR fixo, então o Caixa mostra um QR gerado na hora do fechamento (mesmo componente `QrCodeCard` do autoatendimento). Tela de avaliação tem botão de fechar (dispensa sem enviar nada) e estrelas com animação de humor. Relatórios ganhou um card-resumo (média + 5 mais recentes) com link pra uma tela dedicada "Avaliações" com paginação de verdade e filtro de período próprio — corrigindo um card anterior que tentava listar até 50 itens de uma vez com scroll interno)
- ~~Cupom de desconto avulso~~ ✅ 2026-07-30 (`Coupon`: código genérico por restaurante — sem cadastro de cliente por enquanto, mas sem fechar a porta pra isso no futuro —, com validade e limite de uso independentes e opcionais; **decisão de design**: cliente aplica sozinho no autoatendimento (campo "Tenho um cupom" no carrinho do cardápio digital), reaproveitando o mesmo desconto pontual que já existia na comanda (`discountType`/`discountValue`/`discountReason`), então o pagamento já consome o desconto automaticamente sem nenhuma mudança no fluxo de fechamento; limite de uso é reservado atomicamente (evita corrida perto do limite); reduzir o limite abaixo do uso já feito é permitido de propósito — o cupom só para de funcionar na hora, reaproveitando a mesma trava, sem precisar de validação extra. De quebra, corrigiu um bug latente em `TabService.openOrGetTabForTable` (lista imutável `List.of()` numa coleção do Hibernate, que quebrava ao salvar a comanda de novo na mesma transação — exatamente o que o resgate de cupom faz))
- ~~Combo/kit de produtos~~ ✅ 2026-08-01 (produto com `type = COMBO`: itens fixos + slots de escolha por categoria, com preço = soma dos componentes menos um desconto do combo — não depende de desconto manual toda vez; explode em item "cabeçalho" + um item filho por componente no momento do pedido, pra cozinha acompanhar o status de cada parte separadamente enquanto a comanda/cardápio continuam mostrando o combo como uma linha só; corrigiu de quebra um bug de itens de combo contados em dobro ao fechar comanda)
- ~~"Happy hour" automático~~ ✅ 2026-08-01 (`HappyHourRule`: desconto — percentual ou fixo — por categoria inteira, em dias da semana e horário configuráveis; uma regra guarda uma lista de dias, não um só, pra "sexta a domingo" não virar três regras separadas; aparece com preço riscado no cardápio digital enquanto está ativa e é aplicado sozinho no item quando lançado na comanda dentro do horário, sem job/cron — calculado a cada request, mesmo padrão do "Cardápio por horário"; **decisão de design**: desconto manual ou cupom sempre substitui o automático, nunca soma — não precisa de trava extra pra isso, já que o desconto manual sobrescreve os mesmos campos usados pelo automático)
- ~~Desempenho por garçom~~ ✅ 2026-08-02 (vendas e tempo médio de atendimento por garçom; `order.created_by` como FK real pro `User` — nullable, `null` pro autoatendimento, que aparece como linha própria "Autoatendimento" no relatório, não é excluído nem misturado com um garçom; vendas somam o `netSubtotal` já líquido de desconto por item, período ancorado em `tab.paidAt`, mesmo critério do faturamento nos Relatórios; item cancelado não conta em nenhuma métrica; **decisão de design**: desconto de comanda inteira (não por pedido) fica fora do rateio — mesmo tratamento que a taxa de serviço já recebe nos Relatórios — decisão explícita do usuário priorizando transparência (garçom bate o próprio número somando seus pedidos) sobre um rateio proporcional mais "correto" mas opaco; tempo médio reaproveita o padrão `createdAt`→`deliveredAt` por item, mesmo usado no tempo estimado de espera do cardápio digital; item transferido entre comandas ou comanda mesclada credita o pedido original, não a comanda de destino)

- ~~Abertura/fechamento de caixa com sangria~~ ✅ 2026-08-02 (`CashRegisterSession` + `CashWithdrawal`: um único caixa `OPEN` por restaurante por vez, abrir/fechar/sangria restritos a `CASHIER`/`OWNER`/`MANAGER`; pagamento em `CASH` — no fechamento normal da comanda ou numa correção de pagamento pra `CASH` — passou a exigir sessão de caixa aberta, Pix/débito/crédito continuam liberados sempre por não passarem pela gaveta física; **decisão de design**: o "esperado" no fechamento soma o `paidAmount` cheio, sem descontar a taxa de serviço como os Relatórios fazem, já que o cliente entrega esse valor em dinheiro físico também; diferença entre valor contado e esperado exige observação obrigatória pra conseguir fechar; sem reabertura de caixa já fechado, mesmo padrão da correção de pagamento; a tela de fechar comanda, que também se chamava "Caixa" no menu, foi renomeada pra "Fechar Conta" pra abrir espaço pro nome na nova tela)
- ~~Verificação de email no cadastro~~ ✅ 2026-08-04 (dono recebe email de verificação ao cadastrar o restaurante, reaproveitando o mesmo provedor Brevo/log já usado na recuperação de senha; **decisão de design**: não bloqueia login — só um banner flutuante lembrando de verificar, que some sozinho quando confirmado, pra não trancar o dono fora do próprio sistema que ele acabou de criar)
- ~~Divisão de conta~~ ✅ 2026-08-04 (substitui o antigo `payTab`/`cancelPayment` — que exigia `paidAmount` batendo exatamente com o total, Sprint 8 — por uma entidade `Payment` própria: `POST /tabs/{id}/payments` registra um ou mais pagamentos numa chamada só, cada um com seu método e valor, cobrindo tanto divisão em partes iguais quanto valores manuais por pessoa, fechando a comanda sozinha quando a soma bate com o total; `PATCH /tabs/{id}/payments/{paymentId}/void` marca um pagamento como `VOIDED` com motivo obrigatório em vez de apagar, mantendo o histórico pra auditoria — restrito a OWNER/MANAGER, mesmo padrão do antigo cancelamento; pagamento em `CASH` continua exigindo sessão de caixa aberta. **Decisões de design**: o total da comanda (`billTotal`) é calculado e travado no primeiro pagamento registrado — todo pagamento seguinte compara contra esse valor congelado, nunca um recalculado na hora, então editar itens/desconto depois que o pagamento começou passou a ser bloqueado (`applyDiscount`, `mergeTab`, `unmergeTab` rejeitam comandas com `billTotal` já travado); voidar todos os pagamentos de uma comanda `OPEN` destrava o total de novo; um débito deixado numa comanda `CLOSED` por um void é quitado depois pelo mesmo endpoint de registro de pagamento, sem nunca reabrir a comanda nem tocar nas mesas — mesmo princípio já usado na correção de pagamento antiga. UI de checkout e comanda — atalhos de divisão em 2x/3x/4x, método por pessoa, void com motivo, quitação de saldo — commitada separadamente em `29e7a3d`)
- ~~Termos de Uso e Política de Privacidade~~ ✅ 2026-08-05 (não estava no backlog original — identificado como lacuna ao discutir "o que falta pra vender" e implementado na mesma sessão; dois documentos separados, já que têm papéis diferentes na LGPD: **Termos de Uso** (`docs/TERMS_OF_SERVICE.md` / `/terms`) é o contrato B2B com o restaurante — aceite obrigatório via checkbox no cadastro do dono, gravado em `users.terms_accepted_at`; **Política de Privacidade** (`docs/PRIVACY_POLICY.md` / `/privacy`) é voltada ao cliente final do cardápio digital/reserva, linkada no rodapé do cardápio. **Decisão de design**: a obrigatoriedade do aceite é só no frontend (botão de cadastro desabilitado sem marcar o checkbox) — não virou uma validação bloqueante no backend (`@AssertTrue`) pra não quebrar ~29 testes de integração que montam o request de cadastro sem esse campo nenhum; o backend só grava o timestamp quando `termsAccepted=true` vem no request, sem rejeitar quando vem `false`/ausente. **Pendência real, sinalizada nas próprias páginas**: ambos os documentos são minutas, ainda não revisadas por advogado; o contrato B2B não tem CNPJ nem CPF do contratado (dono ainda não tem CNPJ/MEI aberto e decidiu conscientemente, em 2026-08-05, não usar o CPF pessoal ali) — isso enfraquece a exigibilidade jurídica do contrato até ser resolvido)

**Prioridade 1 do backlog anterior (discutido em 2026-07-25) concluída inteira** — todos os 6 itens de ganho rápido acima já foram entregues.

O que falta, organizado por prioridade. **Reorganizado em 2026-08-02 a pedido do usuário**, em torno de um objetivo novo: **tornar o produto vendável** pro uso presencial + autoatendimento que já existe — em vez de só ordenar por facilidade de implementação (critério da reorg anterior, 2026-07-28). Cobrança/assinatura do próprio SaaS continua fora de escopo, decisão reconfirmada em 2026-08-02: os primeiros clientes entram por venda manual (contrato/PIX/boleto combinado por fora), sem fluxo de trial/plano dentro do sistema — ver "Fora de escopo" no fim.

Nenhum dos itens que dependem de terceiro (gateway de pagamento, NFC-e, TEF, iFood, WhatsApp oficial) bloqueia a venda hoje: a obrigação fiscal (NFC-e) é do restaurante, não do SaaS, e a maioria resolve isso por fora; pagamento online só é pré-requisito de verdade pro delivery (ainda travado); TEF/iFood/WhatsApp são conveniências. Por isso caíram pra Prioridade 6, "só se um cliente concreto pedir", em vez de bloquear o lançamento.

#### Prioridade 2 — trivial (sem migration nem entidade nova, só query ou campo simples)
**Concluída inteira** ✅ 2026-07-29 — os 3 itens (relatório de horário de pico, ficha técnica/custo de produto, metas e comparativos) já foram entregues, ver lista de "já entregues" acima.

#### Prioridade 3 — fácil (campo novo + lógica simples, reaproveita padrão já validado)
**Concluída inteira** ✅ 2026-07-30 — os 2 itens (áreas do salão, avaliação pós-refeição) já foram entregues, ver lista de "já entregues" acima.

#### Prioridade 4 — fechar antes de vender
**Concluída inteira** ✅ 2026-08-05 — reabriu no mesmo dia com um item novo (isolamento multi-tenant, discutido depois de "o sistema está pronto pra vender pra qualquer restaurante?" virar a pergunta seguinte: "e pra vários clientes ao mesmo tempo?") e fechou de novo no mesmo dia, com as duas camadas que bloqueavam venda resolvidas (Camada 3 é opcional, ver abaixo). Itens 🎯 abaixo são os mesmos da seção "Gaps de infraestrutura", só destacados aqui por virarem bloqueio de venda:
- [x] **Divisão de conta** — resolvido em 2026-08-04, ver item na lista de "já entregues" acima (referenciado como "item 8" no item 13/Prioridade 5 abaixo).
- [x] **Backup do banco de dados** ✅ 2026-08-04 (dump completo via `pg_dump`, enviado pra um bucket privado no Supabase Storage, com poda das cópias além da retenção configurável; disparado por um cron diário do GitHub Actions chamando um endpoint interno protegido por segredo compartilhado, já que o banco — Postgres free do Render, temporário, ver `docs/DEPLOY.md` — não tinha estratégia de backup nenhuma; detalhe completo em `docs/BACKUP_RESTORE.md`)
- [x] 🎯 **Hospedagem definitiva do banco** ✅ 2026-08-05 (decisão final: Neon free tier, não upgrade pago no Render — banco migrado com `pg_dump`/`psql`, dados conferidos, backend em produção confirmado lendo e escrevendo no Neon depois de teste do usuário em produção; falta só, depois de alguns dias estável, apagar o `mora-db` antigo do Render como limpeza — não bloqueia mais nada, ver `docs/DEPLOY.md`)
- [x] **Verificação de email no cadastro** ✅ 2026-08-04 (dono recebe email de verificação no cadastro, reaproveitando o mesmo provedor Brevo/log da recuperação de senha; não bloqueia login — banner flutuante lembra de verificar e some sozinho quando confirmado, decisão consciente pra não trancar o dono fora do próprio sistema, ver `project_email_verification_design`)
- [x] 🎯 **Isolamento multi-tenant reforçado** ✅ 2026-08-05 (Camadas 1 e 2, ver abaixo — Camada 3 é opcional e não bloqueia venda) — hoje o isolamento entre restaurantes é garantido só por convenção: cada método de service recebe `restaurantId` e precisa lembrar de chamar a variante certa do repositório (`findByIdAndRestaurantId`, não `findById`). Nenhum framework barra um `findById` esquecido de vazar dado de outro cliente — é o tipo de bug que só aparece com 2+ clientes pagantes simultâneos, e que destrói confiança na hora se aparecer. Por isso entrou na Prioridade 4 (bloqueia venda pra múltiplos clientes reais ao mesmo tempo), não na 5. Resolvido em camadas, discutidas em 2026-08-05:
  - [x] **Camada 1 — teste automatizado sistemático** ✅ 2026-08-05 (`CrossTenantIsolationControllerIntegrationTest`: 14 testes criam restaurante A e B e batem em cada endpoint por id — mesa, produto, categoria, comanda, pedido, item de pedido, cupom, área do salão, happy hour, reserva, staff, grupo de modificador, janela de disponibilidade, chamado de mesa — com o token do restaurante errado, esperando rejeição; nenhum vazou dado. Não é a garantia em si, é a rede de segurança que pega regressão antes de ir pra produção. Achado no processo, não é bug: `GET /tabs/{id}/orders` devolve 200 com lista vazia pra comanda de outro restaurante em vez de 400 — `OrderService.listOrders` já filtra por `tabId AND restaurantId` na própria query, só não distingue "comanda não existe" de "existe mas não tem pedido", diferente dos outros endpoints por id; comportamento seguro, só inconsistente)
  - [x] **Camada 2 — filtro global no Hibernate** ✅ 2026-08-05 (`@Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")` nas 23 entidades que têm coluna `restaurant_id` direta, mais `OrderItem` — que só tem `order_id` mas é exposto por id em `/order-items/{id}` — com condição via subquery pela tabela `orders`; `@FilterDef` declarado uma vez em `domain/entity/package-info.java`. Ativado por `TenantFilterInterceptor`, um `HandlerInterceptor` do Spring MVC — não um filtro de servlet — registrado em `WebConfig`, porque só nesse ponto do ciclo de vida da requisição dá pra garantir, independente da ordem entre filtros, que a autenticação (Spring Security) e a sessão Hibernate da requisição (Open-Session-In-View) já estão prontas; lê o `restaurantId` do `TenantContext` já populado pelo `JwtAuthenticationFilter`. Suíte inteira rodada depois — 453 testes, 0 falhas — incluindo os 14 da Camada 1. Limitação conhecida e aceita: filtro Hibernate não cobre `nativeQuery = true` (usado só nos relatórios de `PaymentRepository`, que já recebem `restaurantId` explícito por parâmetro).
  - [x] **Camada 3 — Row-Level Security no Postgres** ✅ 2026-08-06, **local/dev apenas** (produção/Neon fica pra uma sessão dedicada depois, ver `docs/RLS_DESIGN.md`): role `app_runtime` nova (sem DDL, só CRUD) rodando o Hibernate, enquanto Flyway e o backup continuam na role dona; policy `restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid` nas 23 tabelas com coluna direta mais `order_items` via subquery em `orders`, com `FORCE ROW LEVEL SECURITY`. `app.tenant_id` é propagado por `TenantAwareDataSource` (stampa a conexão ao sair do pool, cobre o caso comum — JWT já populou `TenantContext` antes de qualquer query) mais `TenantActivator` (também escreve a variável de sessão via `EntityManager` na hora, pros ~11 fluxos que descobrem o tenant no meio da própria transação — os 6 controllers públicos e 3 pontos do `AuthService`: cadastro, reset de senha, verificação de email — um bug real achado durante a implementação: `TenantContext.setCurrentTenant()` sozinho não bastava, porque a conexão já tinha sido pega do pool antes da chamada). Achado extra fora do escopo original: `findByEmail`/login sofrem do mesmo problema ovo-e-galinha da reserva por token (email é chave global, não por restaurante) — resolvido com o mesmo padrão de função `SECURITY DEFINER` (`user_by_email`, `user_email_exists`, `user_by_id`, `reservation_by_access_token`). Suíte inteira rodando com a role restrita, não a dona — 470 testes, 0 falhas.

#### Prioridade 5 — ajuda a vender melhor, mas não bloqueia
11. **Cardápio multilíngue** — inglês/espanhol pro cardápio digital, bom pra restaurante com público turista. Precisa de campo traduzido em cada produto/categoria e troca de idioma na tela pública.
12. **Recibo digital** — enviar o recibo por WhatsApp/e-mail, além (ou em vez) da impressão física. Depende de integração com serviço externo (API do WhatsApp ou envio de e-mail).
13. **Split de comanda por pessoa** — diferente da divisão de conta simples: atribui cada item da mesa a uma pessoa específica, pra fechar "cada um paga o que consumiu". Mais complexo que dividir em partes iguais. Decisões já tomadas (2026-08-04) pra quando for implementar: item com quantidade > 1 vai inteiro pra uma pessoa (sem dividir unidade por unidade — quem quiser separar edita o pedido em linhas de 1x antes); desconto e taxa de serviço são rateados proporcional ao consumo de cada pessoa, não em partes iguais. Pré-requisito (divisão de conta, item 8, Prioridade 4) já entregue em 2026-08-04 — a UI pode reaproveitar o mesmo mecanismo de registrar vários pagamentos numa chamada só (`POST /tabs/{id}/payments`), só trocando como o valor de cada entrada é calculado (por pessoa em vez de por Nx igual).
14. **Reserva de mesa** — agendamento com horário, evita mesa vazia "travada" ou cliente sem lugar. **Em desenho** (decisões tomadas em 2026-08-04, nenhum código ainda, ver `project_table_reservation_design`): cliente também pode reservar pelo cardápio digital, informando só quantas pessoas + horário — o sistema escolhe a mesa (ou combinação de mesas, mesmo N:N já usado em `tab_tables`) automaticamente, sem lista de espera se não houver disponibilidade; recebe um link/token único pra ver/cancelar depois, sem conta. Mesa(s) viram um novo status `RESERVED` 30 min antes do horário e voltam a `FREE` automaticamente (`NO_SHOW`) 30 min depois se ninguém chegar — limiares configuráveis por restaurante, mesmo padrão dos outros avisos de tempo. Botão "Cliente chegou" numa tela dedicada `/reservations` (equipe também cria reservas manuais ali, por telefone) abre a comanda direto na(s) mesa(s) vinculada(s); sem duração própria — uma vez sentado vira uma comanda normal.
🎯 **Logging estruturado / observabilidade** (Sentry ou equivalente) — ajuda a dar suporte quando algo quebra pra um cliente real, mas não impede vender antes disso.
🎯 **Testes automatizados no frontend** — qualidade/manutenção a longo prazo, não bloqueia venda.

**Gestão de clientes pagantes** — levantado em 2026-08-05 ao discutir "e se um restaurante comprar e não pagar depois de um mês?". Quatro entregas distintas, decidido implementar uma de cada vez (documentando aqui + testando antes de seguir pra próxima), da mais simples/isolada pra mais arriscada:
- [x] **Fale conosco (suporte) dentro do sistema** ✅ 2026-08-05 (botão na sidebar desktop e no menu "Mais" do mobile, abre um modal com link direto pro WhatsApp e pro email do responsável pelo Morá — sem tela de triagem perguntando qual canal usar, os dois aparecem juntos. Puramente frontend, reaproveitando o componente `Modal` genérico já usado pelo menu "Mais")
- [x] **Bloqueio de restaurante inadimplente + banner de aviso** ✅ 2026-08-05 (`Restaurant.active`, que já existia mas nunca era checado, agora bloqueia de verdade em três pontos: `AuthService.login` e `AuthService.refreshToken` rejeitam com um erro específico — `RestaurantSuspendedException`/403 "Restaurant Suspended" — e o `JwtAuthenticationFilter` passou a checar `restaurantRepository.existsByIdAndActiveTrue(restaurantId)` a cada requisição autenticada, não só no login; **decisão de design**: como o access token dura 24h e não é revalidado contra o banco por padrão, só checar no login deixaria um restaurante bloqueado com acesso por até um dia — o filtro simplesmente não autentica a requisição quando o restaurante está inativo, reaproveitando o mesmo fallback que ele já usa pra token inválido/expirado, então o corte é quase imediato. Campo novo `payment_due_date` (`DATE`, nullable) no restaurante — só leitura pro tenant (não entra em `UpdateRestaurantRequest`), setável só via banco até o painel admin existir (próximo item da fila). `PaymentDueBanner` no `AppLayout`, mesmo padrão do banner de email não verificado, visível só pra OWNER/MANAGER, aparece quando faltam 5 dias ou menos (ou já venceu) e linka pro `SupportModal`. Tela de login também ganhou uma mensagem específica de "acesso suspenso" com os links de contato direto, em vez do "email ou senha inválidos" genérico. Suíte completa (453+ testes, incluindo os 14 de isolamento multi-tenant) rodada depois — sem quebras.)
- [ ] **Painel admin** — não existe hoje nenhum conceito de "admin da plataforma" fora do modelo tenant-scoped `User`/`Restaurant`. Decisão já tomada: mecanismo de login totalmente separado do JWT de restaurante (credenciais em variável de ambiente, não uma linha na tabela `users`), pra não arriscar enfraquecer o isolamento multi-tenant (Camada 1/2) já testado. Achado útil: `Restaurant` não tem `@Filter` de tenant (é a raiz do isolamento, não tem `restaurant_id` pra filtrar), então listar todos os restaurantes pro admin já funciona sem precisar desabilitar nenhum filtro. Tela lista restaurantes com botão de bloquear/desbloquear e campo de data de vencimento.
- [ ] **2FA (segundo fator) no login** — opcional, o dono ativa pra sua própria conta. Maior das quatro entregas por mexer no fluxo de login (`AuthService.login` precisaria de duas etapas: senha, depois código). Sem biblioteca TOTP no projeto ainda — a escolher na hora (candidato: `dev.samstevens:totp`, a confirmar disponibilidade/versão). QR code de enrollment reaproveita `qrcode.react`, já usado em `QrCodeCard` pro QR do cardápio digital/reserva.

#### Prioridade 6 — só se um cliente concreto pedir (depende de terceiro, dinheiro real ou hardware)
10. **Pagamento online — Pix e cartão via gateway** — hoje "Pix"/"Cartão" na comanda é só um registro manual de que o cliente pagou por fora (Pix direto pro banco do restaurante, ou maquininha do garçom); aqui é integrar um gateway de verdade (ex: Mercado Pago, PagSeguro, Asaas) que gera cobrança Pix real (QR Code) e cobra cartão online, com confirmação automática batendo na comanda. Só é pré-requisito de fato pro delivery (Prioridade 8) — pro presencial, o registro manual já resolve; mas já traz valor se um cliente quiser: autoatendimento (QR Code na mesa) pagando direto pelo celular, sem esperar o garçom. **Desenho já discutido antes de qualquer código**, ver `docs/PIX_PAYMENT.md`: só Pix na v1 (sem cartão), conta própria de cada restaurante no gateway (não uma conta única do Morá intermediando), estimativa de 3-5 dias.
15. **Impressora térmica (ESC/POS)** via rede/USB — hoje a impressão é `window.print()` (Sprint 19), depende de driver do sistema.
16. **Maquininha de cartão (TEF)** — integração com terminal físico de cartão usado pelo garçom/caixa; diferente do pagamento online acima, que é pro cliente pagar direto pelo celular.
17. **Integração com WhatsApp** — compartilhamento e notificações mais amplas (além do recibo digital simples da Prioridade 5).
18. **Integração com iFood**.
19. **Impressão fiscal (NFC-e)** — a obrigação fiscal é do restaurante, não do SaaS; a maioria dos primeiros clientes resolve isso por fora (contador, emissor separado). Integração pesada (SEFAZ, certificado digital, homologação) — só entra se um cliente concreto precisar de emissão integrada.

#### Prioridade 7 — bônus: escopo maior, só depois de validar com clientes reais
20. **Controle de estoque**.
21. **Promoções e cupons** — sistema completo, além do cupom avulso simples da Prioridade 4.
22. **Programa de fidelidade**.
23. **Aplicativo para garçons / Aplicativo para clientes**.
24. **Multiunidade (redes de restaurantes)**.

#### Prioridade 8 — Delivery (travado até o presencial estar completo)
> **Decisão explícita do usuário (2026-07-27): não começar nada de Prioridade 8 enquanto qualquer item das Prioridades 2 a 7 (operação presencial) ainda estiver em aberto.** Delivery só entra depois que o fluxo de pedido presencial estiver todo redondo. **Atualização 2026-08-02**: com a reorg em torno de "vendável", "presencial redondo" passa a significar a Prioridade 4 completa (não mais as Prioridades 5-7 inteiras, que agora são "não bloqueia"/"só se pedir"/"bônus") — se essa leitura não for a intenção, ajustar aqui.

25. **Cadastro de endereço de entrega** — capturado no pedido (cardápio digital em modo delivery, sem mesa associada).
26. **Cálculo de frete / raio de entrega** — por distância ou bairro atendido.
27. **Status de entrega** — separando → saiu pra entrega → entregue; extensão do fluxo de status de item que já existe (`PENDING`/`PREPARING`/`READY`/`DELIVERED`).
28. **Gestão de entregador** — próprio ou terceirizado, atribuição de pedido a entregador.
29. **Comanda específica pra delivery** — endereço, contato do cliente, forma de pagamento (depende do pagamento online da Prioridade 6, já que não dá pra cobrar na entrega sem risco).

#### Fora de escopo por enquanto (decisão explícita do usuário, 2026-07-25; reconfirmada em 2026-08-02)
- **Cobrança/assinatura do próprio SaaS** (planos, trial, gateway de pagamento) — mesmo com o foco em tornar o produto vendável, os primeiros clientes entram por venda manual (contrato/PIX/boleto combinado por fora); construir cobrança automatizada antes de validar se alguém paga foi visto como prematuro.
- **Onboarding self-service** — depende do item acima.

#### Gaps de infraestrutura identificados (2026-07-27, fora da lista de features acima)
🎯 = também listado na Prioridade 4 ("fechar antes de vender") ou 5 ("ajuda a vender melhor") do backlog acima, por bloquear ou não bloquear a venda:
- [x] **Recuperação de senha** ("esqueci minha senha") — resolvido em 2026-08-02: endpoints `forgot-password`/`reset-password` com token de uso único (expira em 30 min), sempre resposta genérica. Email via Brevo (`BREVO_API_KEY`/`BREVO_SENDER_EMAIL` no `.env`, remetente é um Gmail comum verificado, sem domínio próprio ainda); em dev/test sem chave configurada cai no `LogEmailService` (loga o link em vez de mandar email). Reset já loga o usuário direto e manda um email de aviso de "senha alterada".
- [x] **Rate limiting no login** — resolvido em 2026-08-01: bloqueio em memória por IP+email (5 tentativas falhas em 15 min → bloqueio de 15 min), retorna 429. Generalizado em 2026-08-02 (`RateLimitService`) pra também proteger o forgot-password.
- [x] 🎯 **Docker/deploy de produção** ✅ 2026-08-05 — backend e frontend no ar no Render, Dockerfile funcionando, banco definitivo no Neon (free tier, sem prazo de expiração), teste end-to-end em produção concluído em 2026-08-04 e confirmado de novo depois da migração do banco (ver `docs/DEPLOY.md`).
- [x] **Backup do banco de dados** — resolvido em 2026-08-04, ver item 8/Prioridade 4 acima.
- [x] **Verificação de email no cadastro** — resolvido em 2026-08-04, ver item 8/Prioridade 4 acima.
- [x] **Isolamento multi-tenant reforçado** — Camadas 1, 2 e 3 (RLS local/dev) resolvidas; RLS em produção (Neon) fica pra uma sessão dedicada depois. Ver item na Prioridade 4 acima.
- 🎯 **Logging estruturado / observabilidade** (Sentry ou equivalente) — hoje é só o log padrão do Spring Boot (Prioridade 5 — não bloqueia venda).
- 🎯 **Testes automatizados no frontend** — o backend tem suíte de integração sólida; o frontend não tem nenhum teste automatizado, nem Vitest nem Playwright/Cypress (Prioridade 5 — não bloqueia venda).
