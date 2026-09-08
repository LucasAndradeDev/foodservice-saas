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
| 28 | Gestão de entregador | ~1 dia | **Redesenhado em 2026-08-31**: entregador é um `User` de verdade (role COURIER), com o mesmo convite/login por email que qualquer funcionário, e tela própria restrita (`/my-deliveries`) onde só marca o pedido dele como entregue. Despachar (`SEPARATING → OUT_FOR_DELIVERY`) continua sendo ação do staff na loja. |
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

## ✅ Dependência de pagamento com cartão, resolvida

O item 29 (comanda de delivery) exige pagamento online funcionando, porque a v1 **não aceita pagamento na entrega** (decisão confirmada em 2026-08-16 — dinheiro/maquininha do entregador fica de fora, mesmo raciocínio de risco já registrado neste doc). Pix já estava pronto e commitado; **cartão (`docs/CARD_PAYMENT.md`) foi commitado em 2026-08-17** (`3800cd6`, validado em produção com dinheiro real) — destravando a task 29 de verdade, não só por ordem de prioridade.

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
- [x] **25.5** Teste de isolamento cross-tenant pra `DeliveryDetails`. **Ajuste de escopo**: ainda não existe endpoint autenticado por id pra `DeliveryDetails` (só chega no 27.2, tela de operação) — não dava pra estender a `CrossTenantIsolationControllerIntegrationTest` (ela varre endpoints por id). Em vez disso, `DeliveryDetailsRepositoryTest` prova a Camada 2 direto (`@Filter` do Hibernate): restaurante A cria um pedido de delivery, `findByTab_Id` sob o tenant de B devolve vazio. O caso equivalente na suíte de Controller (varredura por endpoint) fica marcado pra entrar junto do 27.2, quando o endpoint existir.

### 26 — Cálculo de frete/raio (~1-2 dias)
- [x] **26.1** Migration (`V64__delivery_zones.sql`) + entidade `DeliveryZone` + endpoints CRUD (`/api/v1/delivery-zones`, `OWNER`/`MANAGER`), mesmo padrão de `DiningArea`/`Coupon` — bairro único por restaurante (case-insensitive), taxa fixa, ativo/inativo. Match por bairro exato (sem CEP/raio geográfico na v1). Testado com 7 testes de integração.
- [x] **26.2** Tela "Zonas de entrega" (`DeliveryZonesPage`) — vira uma aba própria na barra de gestão (`/delivery-zones`, entre Happy Hour e Funcionários), mesmo padrão de Cupons, não uma seção dentro da tela Geral. Testado ponta a ponta no navegador: criar, listar, ativar/desativar, editar, excluir.
- [x] **26.3** Endpoint público `GET /api/v1/public/menu/{slug}/delivery/fee?neighborhood=X` (sempre 200, `available:false` pra bairro não atendido — não é erro). Prévia no carrinho: campo Bairro dispara a consulta com debounce de 400ms, mostra a taxa ou "ainda não entregamos nesse bairro". Testado com 3 testes de integração + ponta a ponta no navegador (bairro atendido e não atendido).
- [x] **26.4** Cálculo server-side da taxa aplicado de verdade: `PublicDeliveryOrderService` busca a `DeliveryZone` ativa correspondente ao bairro **antes** de criar qualquer coisa (comanda/pedido) — bairro não atendido rejeita com 400, sem deixar registro órfão. `TabService.resolveBillTotal` passou a somar `delivery_fee` (já congelado desde a criação) no cálculo do total, mesmo padrão da taxa de serviço. Testado com teste de integração dedicado (pedido de delivery + item entregue + pagamento → `billTotal` bate com itens + frete) e ponta a ponta no navegador (bairro não atendido trava o botão "Enviar pedido"; bairro atendido envia e grava a taxa real no banco). Suíte completa (576 testes) sem regressões.

**Task 26 (cálculo de frete) concluída inteira.**

- [x] **26.5** (2026-09-06) Geo real (v2) promovida a método **prioritário**, com bairro/`DeliveryZone` virando o método alternativo (fallback), não mais o único caminho — decisão do usuário, revertendo a recomendação original deste doc de deixar v2 pra depois de validar uso real. `Restaurant` ganha `latitude`/`longitude` (geocodificados via Nominatim sempre que `address` é salvo em Configurações) e `deliveryBaseFee`/`deliveryFeePerKm` (taxa base + valor por km, configurados na aba "Entrega"). `DeliveryFeeResolver` decide: se o restaurante tem localização + preço por km configurados E o endereço do cliente geocodifica com sucesso, usa distância (Haversine); senão cai pro bairro, exatamente como antes. Geocodificação nunca lança erro pro chamador (`GeocodingService` sempre devolve `Optional`) — uma falha do Nominatim ou um endereço não localizável nunca bloqueia pedido nem salvamento de configurações. Testado com testes unitários (`HaversineUtilTest`, `DeliveryFeeResolverTest`) e de integração (Nominatim mockado via `@MockBean`, nunca chamado de verdade nos testes). A taxa por distância é arredondada pro múltiplo de R$ 0,50 mais próximo antes de exibir/cobrar — evita o cliente ver um valor tipo "R$ 15,45" (artefato do cálculo base + preço/km × distância), sem afetar a taxa por bairro (`DeliveryZone`), que já é um valor exato digitado pelo dono.

- [x] **26.6** (2026-09-06) Endereço do restaurante deixa de ser só texto livre: `Restaurant` ganha os mesmos campos estruturados que o endereço do cliente já usa (`street`/`number`/`complement`/`neighborhood`/`city`/`zipCode`), aditivos — a coluna `address` livre é mantida como fallback de exibição/geocodificação pros restaurantes que já a preencheram, sem forçar re-cadastro. `GeocodingService` ganha `geocodeStructured` (monta a consulta ao Nominatim com `street=`/`city=`/`postalcode=` em vez de um `q=` livre — mais preciso, conforme a própria recomendação do Nominatim), usado tanto por `RestaurantService` (endereço do restaurante, quando `street`+`city` estão preenchidos) quanto por `DeliveryFeeResolver` (endereço do cliente, que já chegava estruturado desde a task 25 mas até aqui era concatenado em texto livre pra geocodificar). Configurações reaproveita o autofill por CEP (`lookupCep`, mesmo hook `useDebouncedValue` da task 25.3) em vez de escrever um segundo componente do zero. Migration `V72__restaurant_structured_address.sql` (colunas nullable, sem backfill). Testado com suíte completa do backend sem regressões (testes de `DeliveryFeeResolver`/`PublicDeliveryZoneController`/`PublicDeliveryOrderController` atualizados pro novo método de geocodificação) e build do frontend (`npm run build`).

- [x] **26.7** (2026-09-06) "v3: rota real" — a distância usada pra calcular a taxa deixa de ser a linha reta (Haversine) entre restaurante e cliente e passa a ser a distância de rota de verdade (o quanto um motoboy realmente percorre nas ruas). `RouteDistanceService` tenta três provedores gratuitos nessa ordem, decisão do usuário: **OpenRouteService** (cota diária, precisa de `ORS_API_KEY`) → **Mapbox Directions** (cota mensal, precisa de `MAPBOX_API_KEY`) → **OSRM** (servidor de demonstração público, sem chave, sem cota, mas sem garantia de disponibilidade) — mesma lógica da cadeia de fallback de modelos do Gemini (`GeminiService`), só que entre provedores diferentes em vez de nomes de modelo. Uma chave em branco pula aquele provedor sem gastar uma chamada de rede; qualquer falha (sem chave, cota estourada, timeout, sem rota encontrada) cai pro próximo da cadeia, e se os três falharem, `DeliveryFeeResolver` cai pro Haversine de antes — nunca bloqueia um pedido. `DeliveryFeeMethod` continua `DISTANCE` de qualquer forma; só a precisão do km melhora. Testado com `DeliveryFeeResolverTest` (rota disponível vence o Haversine) e um teste de integração dedicado (`PublicDeliveryOrderControllerIntegrationTest`, `RouteDistanceService` mockado ponta a ponta até o pedido salvo).

### 27 — Status de entrega (~1-2 dias)
- [x] **27.1** (2026-08-18, commit `7880e63`) Enum `DeliveryStatus` (`SEPARATING`/`OUT_FOR_DELIVERY`/`DELIVERED`) + campo em `DeliveryDetails` + `PATCH /api/v1/deliveries/{tabId}/status` (`DeliveryController`/`DeliveryService.updateStatus`), transição só pra frente (`NEXT_STATUS`), com os gates de cozinha (itens prontos) e pagamento (ver 29.1) aplicados na transição pra `OUT_FOR_DELIVERY`. Testado com `DeliveryControllerIntegrationTest`.
- [x] **27.2** (2026-08-18/2026-08-31) Aba "Delivery" própria na barra de gestão (`DeliveryPage`) lista as comandas de delivery abertas com os botões de transição e atribuição de entregador.
- [x] **27.3** (2026-08-18, commit `7880e63`) `PublicDeliveryStatusController` + `DeliveryStatusPage` (`/delivery/status/{token}`), mostrando status dos itens junto com o status de entrega; ganhou depois (2026-08-31, commit `42c06c8`) o mapa ao vivo do entregador quando `OUT_FOR_DELIVERY`.

**Task 27 (status de entrega) concluída inteira.**

### 28 — Gestão de entregador (~1 dia)
**Correção de desenho (2026-08-31, commits `aa5fff0`/`5470b10`/`42c06c8`)**: em vez de uma entidade `Courier` própria com CRUD e tela dedicada, entregador virou um `User` normal com `UserRole.COURIER` — reaproveita autenticação, cadastro e permissões que já existiam pra Garçom/Cozinha, em vez de duplicar esse fluxo. A tela "Entregadores" chegou a existir separada (`CouriersPage`) e foi fundida na Funcionários (`StaffPage`) por ser, por baixo, a mesma lista de usuários — courier aparece como só mais um papel, com telefone/tipo de veículo no formulário quando esse papel é selecionado. Entregador tem tela própria restrita (`/entregador`, `CourierLayout`/`RequireCourierRole`) só com seus pedidos `OUT_FOR_DELIVERY` (`GET /api/v1/deliveries/mine`) e reporta sua posição periodicamente (`PATCH /api/v1/deliveries/mine/location`) pro mapa de despacho da equipe (`GET /api/v1/deliveries/couriers/live`, `CourierMap`).
- [x] **28.1** Substituída por `UserRole.COURIER` (migrations `V69__couriers.sql`/`V70__courier_location.sql`) — sem entidade/CRUD dedicados, ver correção de desenho acima.
- [x] **28.2** Substituída pela aba Funcionários (`StaffPage`) — sem tela "Entregadores" separada, ver correção de desenho acima.
- [x] **28.3** `PATCH /api/v1/deliveries/{tabId}/courier` (`DeliveryController.assignCourier`/`AssignCourierRequest`) na tela de operação (27.2) — `courier_id` em `DeliveryDetails`, exigido antes de sair pra entrega (gate em `updateStatus`).

**Task 28 (gestão de entregador) concluída inteira.**

### 29 — Comanda de delivery, integração final (~1-2 dias)
- [x] **29.1** (2026-08-18, commit `7880e63`) A transição `SEPARATING → OUT_FOR_DELIVERY` exige a comanda paga (`Tab.status == CLOSED`, mesma checagem que já existe pra fechar comanda) — gate de pagamento, não de criação (ver correção em 25.4). Checkout em modo delivery exige Pix/cartão, sem opção de "gerar cobrança pra pagar depois". Testado com `DeliveryControllerIntegrationTest`/`PublicDeliveryPaymentIntegrationTest`.
- [x] **29.2** (2026-08-18, commit `7880e63`) Taxa de entrega exibida e somada no resumo de pagamento (Caixa/Checkout, `CheckoutPage`), mesmo padrão da taxa de serviço.
- [x] **29.3** (2026-09-07) Teste de ponta a ponta manual no navegador contra o "Restaurante Teste": alternar pro modo Delivery → adicionar item → preencher endereço no `CartDrawer` (Meireles, Fortaleza) → cotação de frete real por rota (14,1 km — R$ 11,50) → "Enviar pedido" → QR Pix real gerado via sandbox da Woovi (confirmação por webhook não testável sem túnel ngrok ativo nesta sessão — cobrança cancelada e pagamento registrado manualmente pelo Caixa, o mesmo fallback documentado pra quando o aviso do gateway nunca chega) → item preparado na Cozinha → entregador atribuído na tela Delivery → "Saiu pra entrega" → "Marcar como entregue" → tela pública do cliente mostrou "Pedido entregue. Bom apetite!". **Bug real encontrado e corrigido no caminho**: `DeliveryService.updateStatus` nunca atualizava o `ItemStatus` dos itens ao mover a comanda pra `DELIVERED` — o item ficava preso em `READY` na fila da Cozinha pra sempre (`listKitchenQueue` só exclui `READY`/`DELIVERED`/`CANCELLED`), invisível na tela Delivery mas entulhando a Cozinha indefinidamente. `updateStatus` agora marca todos os itens da comanda como `DELIVERED` (com `deliveredAt`) na mesma transição, mesmo padrão que `OrderItemService.applyStatusChange` já usa. Validado reproduzindo o bug, aplicando o fix, reiniciando o backend e repetindo o pedido do zero — item some da Cozinha automaticamente ao marcar a entrega. Suíte automatizada (`DeliveryControllerIntegrationTest`, `PublicDeliveryOrderControllerIntegrationTest`, `PublicDeliveryPaymentIntegrationTest`, `OrderItemServiceTest`, `OrderItemControllerIntegrationTest`) sem regressões.
- [ ] **29.4** Atualizar `docs/SCOPE.md` marcando Prioridade 8 como entregue, com o mesmo nível de detalhe dos itens anteriores.
- [x] **29.5** ✅ 2026-09-06 (achado testando delivery localmente) `CardPaymentModal`/`PixPaymentModal` mostravam "Chame o garçom" pra qualquer erro que não fosse 400 — não fazia sentido no fluxo de delivery, sem garçom pra chamar. Extraído `paymentErrorMessage` (`frontend/src/utils/paymentErrorMessage.ts`, compartilhado pelos dois modais) que agora também distingue 403 (restaurante sem Mercado Pago/Woovi configurado): mesa continua "Chame o garçom" (staff pode configurar/registrar manual na hora); delivery passa a sugerir o outro método ou contato com o restaurante, nunca "garçom". Testado com testes unitários (`paymentErrorMessage.test.ts`) e ponta a ponta no navegador contra o pedido real do bug (restaurante sem `card_integrations` configurado).

## Melhorias de UX pós-lançamento (achados de uso real, 2026-09-08)

Levantamento motivado por um caso concreto relatado pelo usuário: na tela de status
(`DeliveryStatusPage`), "Fazer novo pedido" abria o cardápio na mesma aba — o cliente
perdia a tela de acompanhamento do pedido que acabou de fazer. A partir daí, revisão do
resto da jornada "cliente que já fez um pedido de delivery e volta a interagir com o
cardápio" em busca de fricção parecida.

### Implementadas nesta sessão

- [x] **1. "Fazer novo pedido" abria na mesma aba** — `frontend/src/pages/publicMenu/DeliveryStatusPage.tsx:361-368`.
  Trocado de `<Link>` (navegação client-side, mesma aba) para `<a target="_blank" rel="noopener noreferrer">`,
  preservando a tela de acompanhamento aberta.

- [x] **2. Nenhum indício no cardápio de que já existe um pedido em andamento** —
  novo `frontend/src/utils/activeDeliveryStorage.ts` (guarda `token`+`createdAt` no
  `localStorage`, chaveado por `slug`) integrado em `PublicMenuPage.tsx:94-115` (grava no
  `onSuccess` de `submitDeliveryOrderMutation`, consulta `getPublicDeliveryStatus` a cada
  15s, limpa sozinho quando o status vira `DELIVERED` ou o token para de resolver) e em
  `MenuHero.tsx` (ícone de sacola com indicador pulsante no topo do cardápio). Clicar no
  ícone abre um dropdown com status, ETA (quando disponível), itens e total, e um link
  "Ver pedido completo" pra tela cheia — em vez de navegar direto, seguindo sugestão do
  usuário de não tirar o cliente do cardápio só pra checar o pedido.
  **Bug corrigido no caminho**: o dropdown não aparecia visualmente (ficava com
  `opacity:1`/`display:block` mas invisível) porque tanto o `<header>` da `MenuHero`
  quanto o `<div>` da `CategoryNav` logo abaixo usam `backdrop-blur`, que cria um
  contexto de empilhamento próprio — como a `CategoryNav` vem depois no HTML, ela sempre
  pintava por cima de tudo dentro do header, independente do `z-index` do dropdown. Fix:
  `relative z-10` explícito no `<header>` (`MenuHero.tsx`).

- [x] **3. Endereço não é lembrado entre pedidos** — novo
  `frontend/src/utils/lastDeliveryAddressStorage.ts` (mesmo padrão de
  `activeDeliveryStorage.ts`, chaveado por `slug`, guarda o `DeliveryAddressForm`
  completo). Salvo em `PublicMenuPage.tsx` no `onSuccess` de
  `submitDeliveryOrderMutation` (depois de `clearPublicOrderState`, que continua zerando
  o carrinho-em-andamento normalmente); o estado inicial de `deliveryAddress` passa a
  cair nele quando não há rascunho de pedido em andamento (`loadPublicOrderState(...) ??
  loadLastDeliveryAddress(...) ?? emptyDeliveryAddress()`).
  **Bug real encontrado e corrigido no caminho**: o endereço restaurado (seja de um
  rascunho em andamento ou da memória de último pedido) era imediatamente sobrescrito
  pelo efeito de autofill de CEP (`lastCepLookedUpRef`, `PublicMenuPage.tsx`) assim que a
  página montava, porque o ref começava vazio (`''`) e não sabia que aquele CEP já tinha
  sido "resolvido" antes — qualquer correção manual que o cliente tivesse feito depois do
  autofill original (ex: trocar o bairro devolvido pelo CEP por um que realmente bate com
  a zona de entrega cadastrada) se perdia. Fix: `lastCepLookedUpRef` agora começa
  primado com o CEP do endereço restaurado, não com `''`, então o efeito só refaz o
  autofill se o cliente de fato editar o campo de CEP depois. Reproduzido e confirmado
  via `localStorage` (endereço salvo ficava com o bairro certo, mas o formulário exibia o
  bairro cru devolvido pelo ViaCEP) antes do fix, e via teste manual completo (dois
  pedidos seguidos, segundo com bairro corrigido preservado) depois.
  **Segundo bug real, achado pelo usuário testando manualmente**: o fix acima quebrou o
  autofill de CEP pra quem tinha um rascunho salvo com o CEP já digitado mas rua/bairro/
  cidade ainda vazios (ex: preencheu o CEP, saiu da página antes do autofill completar, ou
  antes desta sessão) — o ref primado tratava esse CEP "sem rua nenhuma" como já resolvido
  e nunca disparava a consulta ao ViaCEP, deixando os campos vazios pra sempre. Fix:
  `lastCepLookedUpRef` só prima com o CEP quando o endereço restaurado já tem uma `street`
  preenchida (indício de que o CEP foi de fato resolvido antes); com `street` vazia, o ref
  começa em `''` normalmente e o autofill roda assim que o CEP for digitado. Reproduzido
  no navegador real do usuário (mesmo `localStorage`, resgatado via automação) e
  confirmado com o mesmo teste de dois pedidos seguidos: autofill funciona no primeiro
  pedido (rascunho sem `street`) e o bairro corrigido continua preservado no segundo
  (endereço com `street` já resolvida).

- [x] **4. Cliente precisa manter a aba aberta olhando "Ao vivo" pra saber quando o pedido muda** —
  `DeliveryStatusPage.tsx` ganhou um botão de sino (ícone `Bell`/`BellOff` do lucide, ao
  lado do badge "Ao vivo") que pede permissão de notificação do navegador
  (`Notification.requestPermission`) sob clique explícito do cliente, não
  automaticamente ao carregar a página. Um `useEffect` compara o status anterior
  (`previousStatusRef`) com o atual a cada resposta do polling e dispara uma
  `Notification` só numa transição real de status, só com permissão concedida, e só
  quando `document.visibilityState !== 'visible'` (evita notificação redundante enquanto
  o cliente já está olhando a barra de progresso mudar ao vivo). **Limitação de teste**:
  o prompt nativo do navegador pra conceder permissão é UI do Chrome, fora da página —
  não dá pra confirmar via screenshot de automação que ele aparece corretamente; testado
  visualmente só até o clique no botão, falta validação manual do prompt + notificação
  real disparando.
  **Achado pelo usuário testando manualmente**: depois de negar a permissão uma vez, o
  ícone virava um `<span>` inerte (`BellOff` sem `onClick`) — sem explicar nada, parecia
  quebrado, e não tinha como reativar. Causa raiz é limitação do próprio navegador: uma
  vez em `denied`, `Notification.requestPermission()` nunca mostra o prompt de novo, só
  resolve direto pra `denied` — o site não tem como reverter isso via JS, só o usuário
  desbloqueando manualmente nas configurações do site. Fix: o `BellOff` virou `<button>`
  clicável que mostra um toast (`showNotificationBlockedHint`, mesmo padrão de
  `AnimatePresence` já usado pros toasts de sucesso/erro) explicando "toque no cadeado ao
  lado do endereço e permita notificações", em vez de ficar em silêncio.

- [x] **5. Barra de progresso não tem etapa de pagamento** — `STEPS` virou
  `DELIVERY_STEPS` (as 3 etapas originais) mais um novo array `STEPS` com "Pagamento"
  prependado como primeira etapa, sempre renderizada (removido o `{delivery.paid && (...)}`
  que escondia a barra inteira, e removido o badge de status solto que existia como
  alternativa quando `!delivery.paid` — virou redundante). `currentStepIndex` passa a ser
  `delivery.paid ? deliveryStatusStepIndex + 1 : 0`. Testado ponta a ponta: pedido recém
  criado mostra a etapa "Pagamento" destacada e as demais apagadas, sem o pulo visual que
  existia antes.

- [x] **6. Nada avisa se o cliente já tem um pedido em aberto ao montar outro** —
  `CartDrawer` ganhou a prop `activeDeliveryWarning` (`{ paid: boolean } | null`),
  passada por `PublicMenuPage.tsx` a partir do mesmo `activeDeliveryToken`/`activeDelivery`
  já usados pelo badge da melhoria 2. Primeiro clique em "Enviar pedido" com um aviso
  ativo só revela uma confirmação inline (⚠️ "Você já tem um pedido em aberto..." +
  "Cancelar"/"Enviar mesmo assim") em vez de enviar direto; segundo clique (ou o botão
  "Enviar mesmo assim") envia de fato. Testado ponta a ponta: segundo pedido de delivery
  com o primeiro ainda "Aguardando pagamento" mostrou o aviso; "Cancelar" manteve o
  carrinho intacto sem enviar.

- [x] **7. Sem botão de copiar/compartilhar o link de acompanhamento** — implementado
  primeiro como "Copiar link" (mesmo padrão de `ReservationFormModal.tsx`/
  `PixPaymentModal.tsx`), depois trocado por sugestão do usuário pra "Compartilhar
  pedido": usa a Web Share API (`navigator.share`) quando disponível — abre o menu nativo
  de compartilhamento do celular (WhatsApp, SMS, etc.) direto com o link, mais fluido que
  copiar e colar manualmente — e cai pra copiar o link (ícone `Copy`/`Check`, feedback
  "Link copiado!" por 2s) só em navegador sem suporte (a maioria dos desktops). Cancelar o
  compartilhamento (`AbortError`) é tratado como resultado normal, não erro.

**Observado durante o teste, não investigado**: a taxa de entrega cotada no carrinho
(`CartDrawer`, `deliveryFeeQuote`) às vezes difere da taxa cobrada de fato no pedido
criado (`DeliveryStatusPage`) pro mesmo endereço — ex. R$ 9,00 cotado vs. R$ 11,00
cobrado no teste local de hoje. Como o cálculo é por distância real de rota (`RouteDistanceService`,
task 26.3), suspeita é variação entre chamadas ao provedor de geocoding/roteamento
(ORS/Mapbox/OSRM) entre a cotação e a criação do pedido, não um bug introduzido pelas
melhorias desta sessão. Vale investigar numa próxima sessão se o usuário notar o mesmo
em produção.

## Melhorias na operação (staff) e no entregador, achados de uso real (2026-09-08)

Três pedidos diretos do usuário depois de usar a tela `DeliveryPage` (staff) e a tela do
entregador (`MyDeliveriesPage`) em produção.

- [x] **1. Aviso "Não conseguimos acessar sua localização" ficava preso até dar F5** -
  `MyDeliveriesPage.tsx`: o callback de erro do `watchPosition` setava `locationDenied`,
  mas nada limpava de volta quando a posição voltava a resolver (GPS momentaneamente
  indisponível, timeout pontual) - só um reload remontava o efeito do zero. Fix: o
  callback de sucesso agora também chama `setLocationDenied(false)`, já que
  `watchPosition` continua chamando os callbacks sozinho sem precisar de reload.

- [x] **2. Ponto de destino não aparecia em nenhum mapa** - `DeliveryDetails.customerLatitude/Longitude`
  (já existia no backend, geocodado na criação do pedido pra cobrar a taxa por distância)
  agora também é exposto em `DeliveryDetailsResponse`/`DeliveryDetails` (frontend).
  `CourierMap` ganhou uma prop `destinations` (pin de bandeira, não animado - ao contrário
  do entregador, um destino nunca se move) somada ao cálculo de enquadramento
  (`MapAutoView`) que já existia para os entregadores. Ligado em dois lugares: a tela do
  cliente (`DeliveryStatusPage`, o próprio endereço dele) e o mapa de despacho do staff
  (`DeliveryPage`, um pino por entregador atualmente "Saiu pra entrega", casado pelo
  `courierId`). Null sempre que o pedido foi precificado por bairro (`DeliveryZone`),
  nunca geocodado - mesma condição já usada por `deliveryDistanceKm`.

- [x] **3. Sem jeito de cancelar um pedido de delivery** - novo status `CANCELLED` em
  `DeliveryStatus`, tratado como saída lateral (não um "próximo passo" do fluxo normal)
  em `DeliveryService#updateStatus`: permitido a partir de `SEPARATING` ou
  `OUT_FOR_DELIVERY`, nunca a partir de um estado terminal, e nunca por um `COURIER`
  (mesma checagem de papel que já restringia "marcar como entregue" à própria entrega).
  Cancelar marca todos os itens da comanda como `CANCELLED` (mesmo padrão de
  `markItemsDelivered`, senão ficariam presos na fila da Cozinha pra sempre) e some da
  tela de operação (`listOpenDeliveries` passou a excluir `CANCELLED` junto com
  `DELIVERED`). Frontend: botão "Cancelar pedido" em cada card do `DeliveryPage`, com
  `ConfirmDialog` (mesmo componente/padrão do cancelamento de reserva), avisando quando o
  pedido já estava pago que o reembolso é manual. A tela pública do cliente
  (`DeliveryStatusPage`) troca a barra de progresso por um aviso simples quando o status é
  `CANCELLED`, já que uma barra de progresso não faz sentido pra um pedido parado.

**Testado**: backend compila limpo, suíte `DeliveryControllerIntegrationTest`/
`PublicDeliveryOrderControllerIntegrationTest`/`PublicDeliveryPaymentIntegrationTest` sem
regressões (exit code 0). Frontend: `npm run build` (o mesmo `tsc -b` do Render) e
`vitest run` (57 testes) limpos. **Testado ponta a ponta no navegador** contra o
"Restaurante Teste" local, depois de reiniciar o backend pra pegar o código novo:
cancelar um pedido de teste travado há 909 min (o mesmo tipo de pedido que motivou o
pedido 3) removeu ele da lista e a tela pública do cliente passou a mostrar "Este pedido
foi cancelado pelo restaurante." no lugar da barra de progresso, com o aviso de reembolso
manual aparecendo certo por já estar pago; atribuir um entregador e avançar pra "Saiu pra
entrega" mostrou os dois pinos no mapa de despacho do staff (entregador + "Destino de
{nome}") e o pino "Você" no mapa da tela pública do cliente. Não testado com GPS real de
celular (correção da melhoria 1) - a mudança é pequena e de baixo risco (só limpa um
estado que antes nunca era limpo).
