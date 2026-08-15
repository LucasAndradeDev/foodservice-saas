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

- Confirmar se "status de entrega" vive na `Tab` (mais simples, uma comanda de delivery só tem um destino) ou precisa de granularidade por item — provavelmente `Tab`, já que entrega é uma unidade só, diferente do preparo na cozinha que é por item.
- Definir se "gestão de entregador" é só um cadastro simples (nome, telefone) ou precisa de mais campos (próprio vs. terceirizado, como já cogitado no backlog original) — v1 provavelmente só o cadastro simples.
- Confirmar layout do modo "Delivery" no cardápio digital: é uma tela separada de `/menu/:slug`, ou o mesmo cardápio com uma alternância "Comer no local" vs "Delivery" no topo?
- Reavaliar geo real (v2) só depois de ter dados de uso real de pelo menos um restaurante fazendo delivery de verdade — não antes.
