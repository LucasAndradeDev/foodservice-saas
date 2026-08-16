# Delivery — desenho pra quando começarmos

Discussão feita em 2026-08-14, antes de qualquer código, sobre os itens 25-29 do backlog ("Delivery", `docs/SCOPE.md`, Prioridade 8 — travada até o presencial estar completo). Mesmo objetivo dos documentos irmãos (`PIX_PAYMENT.md`, `CARD_PAYMENT.md`): não perder o raciocínio entre agora e o dia em que isso entrar de fato.

## Contexto: por que ainda é o último da fila

A trava original (decisão de 2026-07-27) era não começar Prioridade 8 enquanto qualquer item das Prioridades 2-7 (operação presencial) estivesse em aberto. A atualização de 2026-08-02 reduziu isso pra "Prioridade 4 completa" — e a Prioridade 4 já está 100% concluída desde 05/08. Ou seja, **delivery já está tecnicamente destravado**, só continua por último na fila por ordem de prioridade de negócio (decidida em conversa com o usuário em 2026-08-14: Sentry → split de comanda → import de cardápio por PDF/imagem → cartão → delivery), não por dependência técnica pendente.

A dependência técnica real que existe é pontual, não um bloqueio geral: o item 29 (comanda específica pra delivery) precisa de pagamento online (Prioridade 6), "já que não dá pra cobrar na entrega sem risco" — e isso já está coberto pela ordem escolhida, já que cartão (`docs/CARD_PAYMENT.md`) vem logo antes de delivery na fila.

## As 5 sub-features (não é uma feature só)

| # | Sub-feature | Estimativa | Observação |
|---|---|---|---|
| 25 | Endereço de entrega | ~1-2 dias | Reaproveita bastante — Balcão já suporta comanda sem mesa (`tableIds` vazio); só adiciona um modo "Delivery" no cardápio digital + formulário de endereço |
| 26 | Cálculo de frete/raio | ~1-2 dias (v1, ver escolha abaixo) | Maior variável do grupo inteiro — decisão detalhada na próxima seção |
| 27 | Status de entrega | ~1-2 dias | Copia o padrão de máquina de estado que já existe (`ItemStatus`, reserva computada) |
| 28 | Gestão de entregador | ~1 dia | Lista simples + atribuição manual, sem rastreamento ao vivo (isso seria projeto à parte — app de entregador, Prioridade 7, item 23) |
| 29 | Comanda de delivery (junta tudo) | ~1-2 dias | Integração final + UI de checkout em modo delivery; depende de pagamento online já estar pronto |

**Total estimado pra v1: 6-10 dias**, no mesmo espírito das estimativas de Pix/cartão — teórico, ainda sem passar pela implementação de verdade. O Pix real (`docs/PIX_PAYMENT.md`) mostrou que esse tipo de estimativa costuma ser conservador pro ritmo observado neste projeto (v1 do Pix, estimado em 3-5 dias, saiu concentrado em ~1 sessão de trabalho) — então até esse "6-10 dias" pode encolher na prática, mas só temos como confirmar quando começar de verdade.

## Cálculo de frete/raio — a decisão mais importante do grupo

Duas abordagens discutidas:

### v1 (recomendado): lista de bairros/CEP com taxa fixa

Dono cadastra uma lista de bairros ou CEPs atendidos, cada um com uma taxa de entrega fixa. Sem geocodificação, sem API externa, sem custo por chamada — mesmo padrão que o projeto já usa em outros lugares (ex: happy hour por dia/horário é configuração manual, não cálculo automático). Resolve o caso de uso real de um restaurante pequeno/médio sem dependência de terceiro.

### v2 (se algum cliente pedir raio de entrega de verdade): geo real

Endereço → coordenadas (geocodificação) → distância por Haversine (matemática pura, sem custo) → raio configurável em km a partir do restaurante.

**Existe opção 100% grátis pra geocodificação**: **Nominatim (OpenStreetMap)** — sem cadastro, sem cartão, sem chave de API. Limite de 1 requisição/segundo no serviço público, exige identificar a aplicação (`User-Agent`) e cachear resultado em vez de repetir a mesma consulta (política de uso, não é pensado pra volume alto contínuo sem hospedar instância própria) — viável pro volume de um restaurante pequeno/médio.

**Google Maps Geocoding API** também tem cota grátis (10.000 chamadas/mês, desde a mudança de política de março de 2025 que acabou com o crédito pooled de US$200), mas exige cartão de crédito cadastrado na conta Google Cloud pra ativar a cota, mesmo que nunca seja cobrado.

**Fallback Google → Nominatim quando estourar 10k**: tecnicamente viável (interface `GeocodingService` com duas implementações + contador mensal local, no mesmo padrão do `RateLimitService` já existente — preferível a capturar erro `OVER_QUERY_LIMIT` do Google, que é mais frágil) — mas **decisão: não construir isso na v1 de geo real**. 10.000/mês exigiria mais de 300 pedidos de delivery *por dia* de um restaurante só; nenhum cliente inicial vai chegar perto disso. Começar só com Nominatim (ou só Google, tanto faz nesse volume) e só adicionar o segundo provedor se o uso real mostrar necessidade — complexidade construída antes de precisar é o tipo de coisa que este projeto tem evitado.

Se/quando a v2 entrar: cache de coordenada por endereço ajuda os dois provedores (evita gastar cota em cliente que repete o mesmo endereço); qualidade de geocodificação pode variar entre provedores (Nominatim/OSM geralmente menos preciso que Google pra endereço informal/novo fora de capitais) — isso já seria verdade com um provedor só, não é problema introduzido pelo fallback.

## Dependência de pagamento online

Item 29 (comanda de delivery) precisa que o cliente consiga pagar sem estar na entrega — Pix (já pronto, `docs/PIX_PAYMENT.md`) resolve a maior parte, cartão (`docs/CARD_PAYMENT.md`, planejado antes de delivery) cobre quem prefere cartão. Não é um bloqueio novo: a ordem já escolhida (cartão antes de delivery) já resolve isso.

## Decisões em aberto pra quando entrar de verdade

- Reavaliar geo real (v2) só depois de ter dados de uso real de pelo menos um restaurante fazendo delivery de verdade — não antes.

As outras três decisões que estavam em aberto aqui (status na `Tab` vs. por item, escopo do cadastro de entregador, layout do cardápio em modo delivery) foram resolvidas em 2026-08-16 — ver seção "Arquitetura" abaixo.

## ⚠️ Dependência real ainda não fechada: pagamento com cartão

O item 29 (comanda de delivery) exige pagamento online funcionando, porque a v1 **não aceita pagamento na entrega** (decisão confirmada em 2026-08-16 — dinheiro/maquininha do entregador fica de fora, mesmo raciocínio de risco já registrado neste doc). Pix já está pronto e commitado. **Cartão (`docs/CARD_PAYMENT.md`) ainda não está commitado** — implementado e testado em sandbox, mas com passos pendentes ("Próximos passos (retomar amanhã)" no doc de cartão: teste com credenciais de produção reais, cenário de recusa, cenário de estorno, atualizar SCOPE.md, e só depois o commit). Enquanto isso não fechar, dá pra avançar nas tasks 25-28 abaixo (não dependem de cartão), mas a 29 fica bloqueada de verdade — não só por ordem de prioridade, por dependência técnica.

## Arquitetura (decidida em 2026-08-16, antes de qualquer código)

Mesmo espírito dos docs irmãos: registrar o raciocínio agora, implementar depois. Decisões abaixo confirmadas com o usuário.

### Modelo de dados

- **`Tab` não ganha campo de tipo novo.** Hoje Balcão já é "comanda sem mesa" (`tables` vazio); delivery também vai ser sem mesa, então só isso não distingue os dois casos. A distinção vem da **presença de uma entidade nova**, no mesmo padrão já usado pra `PixCharge`/`CardCharge`/`PostMealFeedback` (entidade filha ligada por `tab_id`, em vez de encher a `Tab` de colunas nullable que só fazem sentido pra um caso):
  - **`DeliveryDetails`** — `id`, `restaurant_id`, `tab_id` (FK única, 1:1), `customer_name`, `customer_phone`, endereço (`street`, `number`, `complement`, `neighborhood`, `city`, `zip_code`, `reference_point`), `delivery_fee` (congelado no pedido, nunca recalculado depois), `status` (`SEPARATING` / `OUT_FOR_DELIVERY` / `DELIVERED`, transição manual por clique da equipe — mesmo padrão do `ItemStatus`, sem cron), `courier_id` (FK nullable), `access_token` (único, mesmo padrão do `Reservation.accessToken` — ver seção de segurança).
  - Uma `Tab` com `DeliveryDetails` associado = comanda de delivery. `tables` continua vazio, igual Balcão.
- **`DeliveryZone`** — por restaurante: `neighborhood_or_zip_prefix`, `fee` (`BigDecimal`), `active`. CRUD simples em Configurações, mesmo padrão de `Category`/`DiningArea`.
- **`Courier`** — por restaurante: `name`, `phone`, `active`. Cadastro simples v1 (sem "próprio vs. terceirizado" — pode virar campo depois se algum cliente pedir).
- **Taxa de entrega no total da comanda**: entra no cálculo do `billTotal` do mesmo jeito que `serviceChargeAmount` já entra — somada no momento em que o total é congelado no primeiro pagamento (`TabService`), nunca recalculada depois disso. **Calculada sempre no servidor** a partir do `DeliveryZone` escolhido — o cliente nunca manda um valor de frete, só o bairro/CEP; o valor exibido no carrinho é uma prévia, a fonte de verdade é a mesma consulta refeita no backend ao criar a comanda.

### Fluxo do cardápio digital

- Alternância "Comer no local" / "Delivery" no topo do `/menu/:slug` já existente (`PublicMenuPage`), reaproveitando 100% do catálogo/carrinho atual — só o rodapé do checkout muda: em vez de "Enviar pra mesa X", pede endereço (com seleção de bairro/CEP que já mostra a taxa antes de confirmar) e telefone, e o pagamento é obrigatoriamente online (Pix ou cartão, reaproveitando os componentes que já existem em `CheckoutPage`) — sem opção de "gerar cobrança pra pagar depois", igual mesa/Balcão fazem hoje, porque delivery sem pagamento confirmado não pode sair da cozinha.
- **Acompanhamento pós-pedido sem conta/login**: mesmo problema que a Reserva já resolveu — cliente precisa ver o status sem estar numa mesa com QR fixo. Reaproveita o padrão `Reservation.accessToken`: ao concluir o pedido, o cliente recebe um link único (`/delivery/status/{token}`) que mostra status dos itens (já existe hoje pro cardápio digital) **mais** o status de entrega (`SEPARATING`/`OUT_FOR_DELIVERY`/`DELIVERED`) do `DeliveryDetails`. Token gerado como UUID aleatório, nunca sequencial — mesmo raciocínio de imprevisibilidade já usado na Reserva.

### Telas internas

- **Configurações**: nova seção "Zonas de entrega" (CRUD de `DeliveryZone`) e "Entregadores" (CRUD de `Courier`), mesmo padrão visual de `DiningArea`/`Category`.
- **Operação**: tela ou seção nova (a decidir na hora — provavelmente uma aba dentro de Mesas/Balcão, já que é a mesma lista de comandas abertas, só filtrada) listando comandas de delivery abertas, com os 3 botões de transição de status + atribuição de entregador.

### Segurança — pontos que exigem atenção deliberada (mesmo padrão dos outros docs de pagamento)

1. **Multi-tenant nas 3 tabelas novas**: `DeliveryDetails`, `DeliveryZone` e `Courier` precisam das mesmas 3 camadas já obrigatórias no projeto (`project_rls_design`) — `@Filter(name = "tenantFilter", ...)` na entidade (Camada 2) **e** entrar na policy de RLS `FORCE ROW LEVEL SECURITY` da migration (Camada 3), não só a Camada 1 (teste de isolamento cross-tenant, que também precisa ganhar um caso novo pra delivery).
2. **Endpoints públicos novos** (submeter endereço/telefone, consultar taxa por bairro, status por token) seguem o mesmo esqueleto dos 6 controllers públicos que já existem (`PublicOrderService`, `PublicReservationService` etc.): resolvem o tenant a partir da URL (slug ou token) e chamam `TenantActivator` explicitamente antes de qualquer query, porque descobrem o restaurante no meio da própria transação — o mesmo bug que `RLS_DESIGN.md` já documentou e corrigiu pros outros fluxos públicos se repete aqui se for esquecido.
3. **Taxa de frete nunca vem do cliente** (ponto já coberto acima, repetido aqui por ser especificamente uma questão de segurança, não só de correção): um valor de frete montado no client-side e aceito como está seria um vetor óbvio de manipulação de preço, igual desconto/taxa de serviço já são sempre recalculados no servidor.
4. **Token de acompanhamento (`access_token`)**: mesmo tratamento que o da Reserva — UUID aleatório, endpoint de consulta some com qualquer dado de outra comanda/restaurante, e vale a pena aplicar o mesmo `RateLimitService` já usado em login/forgot-password no endpoint de consulta por token, pra dificultar um scan de tokens por força bruta (probabilidade já é desprezível com UUID, mas a trava é barata de reaproveitar).
5. **Sem pagamento na entrega na v1** (decisão confirmada): fecha de vez o vetor de "pedido fantasma" — endereço falso, nada a perder pro cliente — que existiria se desse pra pedir sem pagar antecipado. Pedido de delivery só é criado depois que Pix/cartão confirma, mesmo gate que autoatendimento com pagamento já teria.
6. **Dados de entregador nunca aparecem em endpoint público**: `Courier` só é lido/atribuído pelos endpoints autenticados de staff (mesmos papéis que já lidam com comanda — `WAITER`/`MANAGER`/`OWNER`/`CASHIER`, a confirmar o corte exato quando a tela for desenhada); o cliente só vê o status agregado (`OUT_FOR_DELIVERY`), nunca nome/telefone do entregador.
7. **Validação de entrada**: endereço e telefone passam pelas mesmas validações de tamanho/formato já usadas em `Reservation`/`Tab.customerPhone` — nada novo de risco de XSS/injeção além do que o projeto já mitiga (React escapa por padrão no frontend; JPA/prepared statements no backend).
8. **Numeração de migration**: checar a maior versão em `main` antes de nomear (`project_flyway_migration_numbering_pitfall`) — hoje V62 é a mais alta, mas ainda não commitada (é do cartão); confirmar de novo na hora de codar delivery, não assumir V63 de cabeça.

## Divisão em tasks pequenas

Cada task abaixo é pensada pra ser um commit (ou poucos) testável isoladamente, seguindo o hábito já usado no projeto. Numeração própria, não substitui os itens 25-29 do backlog — é a quebra de cada um deles.

### 25 — Endereço de entrega (~1-2 dias)
- [x] **25.1** Migration (`V63__delivery_details.sql`) + entidade `DeliveryDetails` (sem UI ainda) com `@Filter`/RLS desde o commit inicial — validado subindo a app local contra o Postgres do docker-compose (migration aplicou limpo, mapeamento Hibernate carregou sem erro).
- [x] **25.2** Alternância "Comer no local" / "Delivery" no topo do `/menu/:slug` (`OrderModeToggle`), só troca visual — testado no navegador (Tatu Bola).
- [x] **25.3** Formulário de endereço + telefone no modo Delivery, dentro do `CartDrawer` (mesmo lugar do campo de WhatsApp do Balcão) — validado no frontend (nome/telefone/rua/número/bairro/cidade obrigatórios, resto opcional), botão "Enviar pedido" fica desabilitado até 25.4 existir. Testado no navegador ponta a ponta (alternar modo → adicionar item → abrir carrinho → preencher endereço → mensagem de completude atualiza).
- [x] **25.4** Endpoint público `POST /api/v1/public/menu/{slug}/delivery/orders` (`PublicDeliveryOrderController`/`PublicDeliveryOrderService`) — abre uma `Tab` sem mesa (mesmo formato do Balcão, via `TabService.openTab` com `tableIds` vazio), cria o `Order`/itens (reaproveitando `OrderService.createOrder`) e o `DeliveryDetails` (com `access_token` novo) numa transação só, com `TenantActivator` explícito e rate limit por telefone (mesmo padrão/config do self-order de mesa). Frontend (`CartDrawer`) já chama esse endpoint de verdade — o botão "Enviar pedido" habilita quando o endereço está completo, sem mais bloqueio manual. **Correção de desenho (2026-08-16)**: a `Tab`/`Order` são criadas normalmente neste passo, igual ao fluxo de mesa/Balcão hoje (cozinha pode preparar como sempre) — o gate de pagamento (ver item 29.1) trava a transição `SEPARATING → OUT_FOR_DELIVERY`, não a criação da comanda. Criar a comanda só depois do pagamento confirmado exigiria cobrar sem `tab_id` (`PixCharge`/`CardCharge` hoje têm `tab_id NOT NULL`), uma mudança bem maior de escopo que não é necessária pra resolver o risco real (cliente não pagar na entrega) — o risco já é resolvido travando a saída pra entrega, não a existência da comanda. Testado com 4 testes de integração (`PublicDeliveryOrderControllerIntegrationTest`) e ponta a ponta no navegador (endereço → enviar → comanda + `DeliveryDetails` confirmados no banco). **Pegadinha do teste manual**: o backend local já estava rodando de uma sessão anterior (sem hot-reload de classe nova, sem devtools) — precisou reiniciar (`spring-boot:run`) pra pegar o endpoint novo, senão 404 silencioso.
- [ ] **25.5** Teste de isolamento cross-tenant novo pra `DeliveryDetails` (estende a suíte da Camada 1).

### 26 — Cálculo de frete/raio (~1-2 dias)
- [ ] **26.1** Migration + entidade `DeliveryZone` + endpoints CRUD autenticados (`OWNER`/`MANAGER`).
- [ ] **26.2** Tela "Zonas de entrega" em Configurações.
- [ ] **26.3** Endpoint público de consulta de taxa por bairro/CEP (prévia no carrinho).
- [ ] **26.4** Cálculo server-side da taxa aplicado de verdade na criação da comanda (25.4) e integrado ao congelamento de `billTotal` — bloqueado até 25 estar pronto.

### 27 — Status de entrega (~1-2 dias)
- [ ] **27.1** Enum `DeliveryStatus` + campo em `DeliveryDetails` + endpoint autenticado de transição (`SEPARATING → OUT_FOR_DELIVERY → DELIVERED`).
- [ ] **27.2** Lista/aba de comandas de delivery abertas na operação, com os botões de transição.
- [ ] **27.3** Endpoint público + tela `/delivery/status/{token}` (token gerado em 25.4), mostrando status dos itens (já existe) mais o status de entrega.

### 28 — Gestão de entregador (~1 dia)
- [ ] **28.1** Migration + entidade `Courier` + CRUD autenticado.
- [ ] **28.2** Tela "Entregadores" em Configurações.
- [ ] **28.3** Atribuição de entregador na tela de operação (27.2) — `courier_id` em `DeliveryDetails`.

### 29 — Comanda de delivery, integração final (~1-2 dias) — **bloqueada até cartão ser commitado**
- [ ] **29.1** Endpoint de transição `SEPARATING → OUT_FOR_DELIVERY` (task 27.1) passa a exigir a comanda paga (mesma checagem que já existe pra fechar comanda) — gate de pagamento, não de criação (ver correção em 25.4). Checkout em modo delivery também passa a exigir Pix/cartão (sem opção de "gerar cobrança pra pagar depois") — junta 25+26.
- [ ] **29.2** Taxa de entrega exibida e somada no resumo de pagamento (Caixa/Checkout), mesmo padrão da taxa de serviço.
- [ ] **29.3** Teste de ponta a ponta manual: pedido completo (endereço → frete → pagamento → separando → saiu → entregue) num restaurante de teste.
- [ ] **29.4** Atualizar `docs/SCOPE.md` marcando Prioridade 8 como entregue, com o mesmo nível de detalhe dos itens anteriores.
