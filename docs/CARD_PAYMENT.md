# Pagamento com cartão integrado — desenho pra quando começarmos

Discussão feita em 2026-08-14, antes de qualquer código, sobre a metade "cartão" do item 10 do backlog ("Pagamento online — Pix e cartão via gateway", `docs/SCOPE.md`, Prioridade 6 — "só se um cliente concreto pedir"). A metade Pix já foi entregue (`docs/PIX_PAYMENT.md`, via Woovi) — este arquivo cobre só o que falta. Mesmo objetivo do documento irmão: não perder o raciocínio entre agora e o dia em que isso entrar de fato.

## Contexto: por que não é prioridade agora

Não bloqueia vender o produto hoje — o registro manual de "Débito"/"Crédito" na comanda (o garçom marca que o cliente pagou na maquininha própria dele) já resolve o uso presencial. Pagamento com cartão integrado só vira pré-requisito de verdade se formos abrir mão da maquininha física (não é o plano) ou quando entrarmos em delivery de verdade (Prioridade 8) sem entrega presencial pra cobrar na hora.

## Por que isso é mais caro que o Pix, mesmo evitando PCI DSS

Quando decidimos fazer só Pix na v1 (`docs/PIX_PAYMENT.md`), a razão foi: "cartão de verdade puxa tokenização e escopo de conformidade PCI — complexidade real que não vale pagar numa primeira versão." Isso continua verdade, mas dá pra reduzir bastante escolhendo o formato certo de checkout (próxima seção). O que **não** dá pra evitar, mesmo com o formato mais simples, são situações que cartão tem e Pix nunca teve:

- **Recusa de cartão** — precisa de UX pra "cartão recusado, tenta outro" (Pix nunca falha depois de gerado, só expira).
- **Chargeback/disputa** — cliente pode contestar a cobrança semanas depois com o banco dele; não existe equivalente em Pix.
- **3D Secure** — autenticação extra que bancos brasileiros vêm exigindo cada vez mais; mais um passo no fluxo que o gateway conduz, mas que precisamos prever na UX (redirecionamento/popup).
- **Parcelamento** — expectativa comum no Brasil pra tickets de restaurante mais altos; se usarmos checkout hospedado (ver abaixo), o próprio gateway já resolve a UI de escolha de parcelas de graça, então isso não vira trabalho nosso.
- **Estorno/void via API** — equivalente à "correção de pagamento" que já existe pra Pix/dinheiro, mas chamando a API do gateway em vez de só editar o registro interno.

## Escolha de formato de checkout

Dois padrões, mesma escolha que discutimos verbalmente:

1. **Checkout hospedado (recomendado pra v1)** — redireciona o cliente pra uma página do próprio gateway (ex: Mercado Pago Checkout Pro), ele digita o cartão lá, o gateway avisa o backend depois. Nosso servidor e nosso frontend nunca veem o número do cartão. Escopo de PCI cai pro nível mais baixo que existe (SAQ A — autodeclaração, não auditoria). É essencialmente o mesmo esqueleto do Pix: gerar cobrança → mandar o cliente pra algum lugar → esperar confirmação por webhook.
2. **Campos tokenizados embutidos (SDK.js/Elements)** — os campos de cartão vivem dentro da nossa própria tela (via iframe do gateway), mais parecido com a experiência atual do checkout, mas mais peças móveis (SDK novo no frontend, tratamento de erro de tokenização, sem a UI de parcelamento pronta). Não vale o esforço extra numa primeira versão — cortar pra uma v2 se algum cliente reclamar de sair da tela do Morá pra pagar.

**Decisão pra v1: checkout hospedado.** Mesma filosofia do Pix — cobre a maior parte do valor percebido (pagar com cartão sem esperar a maquininha do garçom) com a fração mais simples do esforço.

## Escolha de gateway

| Gateway | Pix + cartão num só lugar? | Conta própria por restaurante | Observação |
|---|---|---|---|
| **Mercado Pago (Checkout Pro)** | Sim | Self-service, mesmo modelo simples da Woovi (Access Token gerado no painel do próprio restaurante) | Era o candidato natural original antes de escolhermos Woovi só pra Pix; documentação e adoção no Brasil são as maiores do mercado; parcelamento já vem pronto na página hospedada |
| **Asaas** | Sim | Tem conceito de subconta pensado pra plataformas/SaaS — pode encaixar bem, mas é mais peça nova (não usamos Asaas em nada ainda) | Vale considerar se o modelo de subconta deles for mais vantajoso, mas entra como segunda opção por não ser um gateway que já conhecemos |
| **Stripe** | Fraco em Pix no Brasil | Exige onboarding mais burocrático por conta (Connect) | Melhor doc do mundo, mas não compensa abrir uma peça de integração nova só pra cartão quando já resolvemos Pix com Woovi |
| **PagSeguro/PagBank** | Sim | Self-service | Jogador legado, menos documentação moderna que Mercado Pago |

**Recomendação: Mercado Pago Checkout Pro, como uma segunda integração de gateway ao lado da Woovi — não uma substituição.** A Woovi continua resolvendo Pix (já testada, já funciona, taxa menor pra Pix). O Mercado Pago entra só pra cartão. Isso significa manter duas tabelas de credenciais por restaurante (`pix_integrations` já existe; entraria uma `card_integrations` no mesmo padrão) em vez de migrar tudo pra um gateway só — mais simples que re-arquitetar o que já está em produção.

## Desenho técnico (esqueleto, mesmo padrão da Woovi)

Reaproveita quase tudo que já existe:

1. **`CredentialEncryptionService`** — já é genérico ("reutilizável pra qualquer segredo de terceiro", não específico de Pix), sem mudança nenhuma.
2. **Nova tabela `card_integrations`** — mesmo padrão de `pix_integrations`: uma linha por restaurante, `access_token_encrypted`, endpoint `GET`/`PUT /api/v1/card-integration` (OWNER/MANAGER), nunca devolve o token de volta.
3. **Nova entidade `CardCharge`** — mesmo padrão de `PixCharge`: `id`, `tab_id`, `external_charge_id` (a "preference id" do Mercado Pago), `amount`, `status` (`PENDING`/`PAID`/`DECLINED`/`EXPIRED`).
4. **`MercadoPagoApiClient`** — `POST /checkout/preferences`, mesmo estilo sem SDK (`RestClient`) já usado pra Woovi/Brevo/Supabase. Devolve a URL da página de checkout hospedada.
5. **Endpoint de webhook público novo** (`POST /public/payments/mercadopago/webhook`, separado do `/public/payments/webhook` da Woovi — formatos de payload diferentes entre gateways).
6. **Verificação de assinatura** — Mercado Pago usa um esquema próprio (`x-signature`/`x-request-id` com HMAC-SHA256, diferente do RSA fixo da Woovi) — **confirmar o formato exato contra a doc atual antes de codar**, histórico mostra que esses detalhes mudam entre gateways e não dá pra assumir que é igual ao da Woovi.
7. **Idempotência** — mesmo princípio: só marcar `PAID` se ainda não estiver.
8. **Fallback manual** — mesmo já existente: se o webhook nunca chegar, a comanda continua fechável manualmente.
9. **Tratamento de recusa** — novo em relação ao Pix: o webhook/retorno pode trazer status `rejected`; a comanda deve continuar `OPEN` com uma mensagem clara pro cliente tentar de novo, em vez de ficar num limbo.

## Como testar sozinho

Mercado Pago tem cartões de teste dedicados no ambiente sandbox (números fixos documentados que simulam aprovação, recusa, e cada motivo de recusa específico — "saldo insuficiente", "código de segurança inválido" etc.), então dá pra validar os principais fluxos de erro sem gastar nada e sem precisar de um cartão real, diferente do Pix (que exigiu um pagamento real de R$1 pra validar ponta a ponta, já que não existe simulação de "PIX pago" fora do sandbox de forma tão granular).

## Estimativa

Maior que o Pix (3-5 dias) por causa da superfície operacional nova, não pela mecânica do checkout em si:

- Esqueleto de cobrança + webhook + idempotência (equivalente ao Pix): ~2-3 dias, reaproveitando o padrão já validado.
- Tela de credenciais por restaurante (`card_integrations` + UI): ~0,5-1 dia, cópia quase direta do `PixIntegrationCard`.
- Tratamento de recusa + estados de erro na UI do Checkout/autoatendimento: ~1-2 dias (não existe no Pix).
- Estorno/void via API do Mercado Pago, integrado ao fluxo de correção de pagamento já existente: ~1 dia.
- Testes automatizados (mock do client, casos de recusa/duplicidade) + validação manual em sandbox: ~1-2 dias.

**Total estimado: 6 a 9 dias.** Cortar parcelamento próprio (o checkout hospedado já resolve isso de graça) e a tokenização embutida (fica pra v2, se pedirem) é o que mantém essa estimativa perto da do Pix em vez de virar semanas.

## Decisões que estavam em aberto — resolvidas antes de codar (2026-08-15)

Pesquisa feita contra `developers.mercadopago.com` antes de escrever qualquer linha de código de segurança, mesma disciplina que a chave RSA do Woovi já tinha ensinado a não pular:

- **Formato do `x-signature`**: header vem como `ts=<epoch_ms>,v1=<hmac_hex>`; o manifesto assinado é `id:{data.id};request-id:{x-request-id};ts:{ts};`.
- **Segredo do webhook é por aplicação/integração** (painel "Suas integrações" de cada restaurante, não expira) — diferente do Woovi (chave pública fixa igual pra todo mundo). `CardIntegrationCard` pede dois campos: Access Token e Webhook Secret.
- **Modelo "Access Token direto, colado em Configurações"** confirmado como suficiente — credenciais de produção são liberadas preenchendo dados do negócio, sem fluxo OAuth/Marketplace/Connect pra vendedor único.
- **`DEBIT_CARD`/`CREDIT_CARD`**: um fluxo só. O Checkout Pro devolve `payment_type_id` (`credit_card`/`debit_card`) na resposta de `GET /v1/payments/{id}`, usado só pra rotular o pagamento internamente — não muda nada na criação da cobrança.
- **Tela de configuração**: `CardIntegrationCard`, irmão do `PixIntegrationCard`, mesma seção de Configurações.

## Implementação (2026-08-15)

Backend e frontend implementados ponta a ponta nesta sessão (`V61__card_gateway_payment.sql`/`V62__card_charge_payment_link.sql`), reaproveitando quase tudo do esqueleto do Pix e desviando só onde cartão é genuinamente diferente:

- **`card_integrations`**: mesmo padrão de `pix_integrations`, mas com dois segredos por restaurante (`access_token_encrypted` + `webhook_secret_encrypted`, ambos via `CredentialEncryptionService`). Endpoints em `CardIntegrationController` (`GET`/`PUT /api/v1/card-integration`, OWNER/MANAGER), nunca devolve os valores de volta.
- **`card_charges`**: mesmo padrão de `pix_charges`, com `status` ganhando um estado a mais (`DECLINED` — cartão pode falhar de verdade, diferente de Pix), `status_detail` (código bruto de recusa), `mp_payment_id` (distinto do `external_reference` — é o id que um refund usa depois) e `refunded_at`/`refund_id`.
- **`MercadoPagoApiClient`**: `createPreference`/`getPayment`/`refundPayment` em `https://api.mercadopago.com`, header `Authorization: Bearer <token>`. `refundPayment` manda `X-Idempotency-Key` (UUID por tentativa) como reforço extra contra refund duplicado.
- **`MercadoPagoWebhookSignatureVerifier`**: HMAC-SHA256 sobre o manifesto confirmado acima, com o segredo daquele restaurante.
- **Modelo de confiança do webhook — mais forte que o do Pix**: o corpo do webhook do Mercado Pago é minimalista de propósito (`{action, data: {id}, type, user_id}` — sem status, sem `external_reference`), então `CardChargeService#handleWebhook` nunca confia em nada do corpo além do id do pagamento. A assinatura é verificada, e a **fonte de verdade é sempre um `GET /v1/payments/{id}` independente** — diferente do Pix, que confia no corpo do webhook depois de verificar a assinatura. Justificativa: cartão carrega risco de disputa/fraude que Pix nunca teve, e o custo marginal de mais uma chamada é baixo.
- **Resolução de tenant no webhook — problema novo que o Pix não tinha**: o `correlationID` do Pix já vem no corpo do webhook; o Mercado Pago não ecoa `external_reference` no corpo. Resolvido com um segmento de restaurante na própria URL da notificação: `POST /api/v1/public/payments/mercadopago/webhook/{restaurantId}`, embutido no `notification_url` de cada preference criada. Depois de resolver o tenant e buscar o pagamento pelo id, o `external_reference` da resposta é comparado contra o restaurante que a URL alegou — defesa em profundidade, testada explicitamente (`webhook_toWrongRestaurantPath_shouldNotCreditTheRealTenant`).
- **Fluxo de redirect (`back_urls`) — novidade que o Pix não tem**: depois do cliente pagar (ou falhar) na página hospedada, o Mercado Pago devolve o navegador pra uma rota nossa (`/pagamento/retorno`) com parâmetros de query. **Invariante de segurança**: essa rota só lê a query string pra mostrar um texto de feedback imediato — nunca chama endpoint que muda estado. A confirmação real sempre vem do webhook verificado; o cliente poderia visitar essa URL com qualquer status só editando a query string, então nunca é fonte de verdade.
- **Recusa**: `CardChargeService` mapeia o `status_detail` cru do Mercado Pago pra mensagens curtas em português (`CardDeclineMessages`) — "saldo insuficiente", "CVV incorreto" etc. — expostas como `declineMessage` no `CardChargeResponse`, nunca o código bruto. Comanda descongela sozinha (`unfreezeBillTotalIfNoCommitments`) pra permitir retry imediato.
- **Refund**: `TabController.voidPayment` foi roteado pra `CardChargeService.voidPayment`, que detecta se o pagamento tem `CardCharge` associado — se tiver, chama `POST /v1/payments/{id}/refunds` (só refund total, sem parcial no V1) **antes** de marcar o pagamento como `VOIDED` internamente, pra nunca deixar o registro dizer que o dinheiro voltou sem ter voltado de verdade. Pagamento manual/Pix continua indo direto pro `TabService.voidPayment` como sempre.
- **Guarda cruzada entre gateways — gap real que existia antes desta sessão**: uma cobrança Pix `PENDING` agora também bloqueia overcommit de cartão, e vice-versa (`TabService.registerPayments`/`voidPayment` somam as duas tabelas, não só a própria).
- **Frontend do Caixa**: botão "Cobrar no cartão" ao lado do de Pix, em pagamento único ou por parcela numa comanda dividida (`Dividir em Nx`/`Dividir por pessoa`) — o backend já suportava valor parcial desde o início (`CardChargeService#createCharge` aceita `requestedAmount`), só faltava o botão por parcela no split existir de verdade (achado pelo usuário testando dividir a conta em 2026-08-16, corrigido no mesmo dia: `CheckoutPage.tsx` ganhou `entryCardChargeMutation`/`entryCancelCardChargeMutation` e o polling por parcela via `verifyCardCharge`, espelhando exatamente o que o Pix já tinha com `entryPixChargeMutation`). No sucesso, renderiza um **QR Code do link de checkout** (`qrcode.react`, já dependência do projeto) — diferente do QR do Pix, que codifica o payload de pagamento; aqui é literalmente a URL da página hospedada, escaneada com a câmera do celular (sem precisar de app de banco específico), abrindo o checkout no navegador do próprio cliente.
- **Cardápio digital**: `CardPaymentModal` redireciona o navegador do próprio cliente direto pro `initPointUrl` (sem QR, já que ele está no próprio celular). `GET /public/menu/{slug}` ganhou `table.cardConfigured`.
- **Testes**: `CardChargeIntegrationTest` cobre criação/idempotência de webhook, o invariante de "nunca confiar no corpo do webhook" (o corpo de teste nunca carrega status, só o id), recusa + descongelamento, refund com ordem correta (falha do gateway não voida o pagamento interno), webhook endereçado ao restaurante errado, guarda cruzada Pix↔cartão, e exposição pública de `cardConfigured`.

## Pegadinha do teste manual no sandbox (pra não repetir)

Validado em 2026-08-16 contra o sandbox real, backend local exposto via ngrok: webhooks de pagamento **simulados** pelo botão "Simular notificação" do painel do Mercado Pago (corpo com `live_mode: false`) validam a assinatura certinho contra o Webhook Secret mostrado no painel — confirmado batendo byte a byte com o HMAC calculado manualmente. Mas o webhook de um **pagamento sandbox real** (comprado de verdade com um cartão de teste e o usuário "Buyer Test User", corpo vem com `live_mode: true`) **não bateu com esse mesmo segredo**, mesmo confirmando que o valor é idêntico entre as abas "Modo de teste" e "Modo de produção" do painel.

Não é bug nosso: a fórmula do manifesto (`id:{data.id};request-id:{x-request-id};ts:{ts};`, HMAC-SHA256 hex) foi confirmada batendo exatamente com a doc oficial e com webhooks simulados reais. É uma peculiaridade do sandbox do Mercado Pago — pagamentos com `live_mode: true` parecem assinados com uma chave diferente da exibida no painel. **Rejeitar esses webhooks com assinatura inválida é o comportamento correto e não deve ser enfraquecido** — só significa que não dá pra validar o fluxo assíncrono completo com um pagamento sandbox real de ponta a ponta usando o Webhook Secret do painel.

**Como validamos a lógica de negócio mesmo assim**: usando "Simular notificação" com o `Data ID` do pagamento sandbox real já aprovado (em vez do `123456` padrão) — isso gera um webhook com `live_mode: false` (que valida certo) mas apontando pro pagamento de verdade, e o `CardChargeService` busca esse pagamento real via `GET /v1/payments/{id}`, registra e fecha a comanda normalmente. Confirmado funcionando ponta a ponta assim.

**Investigação adicional (mesma sessão)**: descartada a hipótese de formato legado vs moderno — o Mercado Pago manda em paralelo um segundo aviso no formato antigo "IPN" (`topic=payment&id=X` em vez de `type=payment&data.id=X`), também confirmado chegando de verdade via ngrok, mas com a **mesma assinatura quebrada** pros mesmos pagamentos `live_mode: true`. Ou seja, o problema é especificamente o `live_mode: true`, não o formato de entrega.

**Alternativa de design considerada, ainda não implementada (decisão em aberto)**: a segurança real desse fluxo não depende só da assinatura — depende de sempre buscar o pagamento de verdade via `GET /v1/payments/{id}` com a credencial do próprio restaurante antes de confirmar qualquer coisa (`CardChargeService#handleWebhook`, já implementado assim). A assinatura é uma camada extra de defesa em profundidade, não a única proteção: um invasor não consegue forjar uma aprovação porque a resposta de "foi aprovado" sempre vem direto do Mercado Pago, nunca do aviso em si. Cogitado (com o usuário, 2026-08-15) trocar o comportamento de "assinatura inválida → rejeita (400), não confirma nada" para "assinatura inválida → só loga um aviso, mas continua confirmando via API" — resolveria esse problema de vez, inclusive se ele aparecer em produção. **Não implementado ainda** por ser uma mudança na postura de segurança documentada desde o desenho original da feature — decidido esperar confirmar com um pagamento de produção real antes de decidir.

## Pegadinha nº2 do teste manual: o resultado é decidido pelo nome do titular, não pelos dados do cartão (2026-08-16)

No checkout hospedado do Mercado Pago, em sandbox/modo de teste, o **CVV e o número do cartão digitados são irrelevantes pro resultado** — mesmo digitando exatamente o CVV `123` da tabela oficial de cartões de teste, o pagamento pode ser recusado. Quem decide o resultado é uma palavra mágica no campo "Nome do titular":

| Nome do titular | Resultado simulado |
|---|---|
| `APRO` | Aprovado |
| `OTHE` | Recusado - erro geral |
| `CONT` | Pendente |
| `CALL` | Recusado - requer validação |
| `FUND` | Recusado - saldo insuficiente |
| `SECU` | Recusado - código de segurança inválido |
| `EXPI` | Recusado - problema na validade |
| `FORM` | Recusado - erro no formulário |

Reproduzido nesta sessão: usar qualquer nome diferente de `APRO` gerou "O código de segurança do cartão é inválido" mesmo com o CVV certo da tabela — bateu exatamente com o resultado `SECU`. Pra testar aprovação, o nome do titular precisa ser literalmente `APRO`; o resto dos dados (número, CVV, validade) segue a tabela normal de cartões de teste já documentada acima.

## Pegadinha nº3: checkout como convidado no sandbox não é confiável pra testar (2026-08-16)

Investigado se dava pra testar sem logar em nenhuma conta Mercado Pago, usando o modo "Sem conta Mercado Pago → Cartão" (o mesmo que já confirmamos que o cliente final usa em produção, sem precisar ter conta). Tentativa real:

- **E-mail do formulário de identificação**: o campo pré-preencheu automaticamente com `jardellucas078@gmail.com` — o mesmo e-mail da conta vendedora (dona do restaurante) — o que reacende a mesma trava de "comprador não pode ser o vendedor" já documentada acima, mesmo sem estar logado. Trocar pra um e-mail qualquer diferente (ex: `teste@teste.com`) é obrigatório antes de continuar.
- **Resultado mesmo com e-mail trocado**: `Não foi possível processar seu pagamento` — um erro genérico, diferente dos erros específicos e previsíveis que os cartões de teste simulados costumam devolver (tipo `SECU`/`FUND` por nome do titular). Isso sugere que o motor de simulação de cartões de teste (que reconhece `APRO`, `SECU`, etc.) espera um contexto de conta de teste "de verdade" (Buyer Test User logado) — o checkout anônimo/convidado no sandbox parece não entrar nesse mesmo caminho de simulação.

**Conclusão prática**: em sandbox, testar sempre logado como o **Buyer Test User** da conta (visível no painel de contas de teste do desenvolvedor) — não como convidado. O modo convidado em si já está validado como funcional pro cliente final **em produção** (pagamento real de R$1,00 concluído com sucesso, ver seção "Validação final em produção" abaixo); a limitação é específica de sandbox/simulação, não do produto.

**Confirmado de novo em 2026-08-17**, com uma mensagem de erro mais explícita desta vez: `Uma das partes com as quais você está tentando efetuar o pagamento é de teste` — mesma causa raiz (checkout convidado não amarra o comprador a nenhuma conta de teste, e as credenciais do vendedor são de uma aplicação de teste), só que o Mercado Pago às vezes nomeia o problema diretamente em vez de dar o genérico de antes. Resolvido logando como Buyer Test User: pagamentos aprovados com sucesso em Mastercard/Visa/Amex de teste, comanda fechou sozinha (webhook/polling funcionando).

**Pista falsa investigada nessa mesma sessão, registrada pra não repetir**: cogitado que o Access Token de teste estivesse errado por começar com `APP_USR-` em vez do prefixo clássico `TEST-...`. Descartado — a aplicação de teste ("Mora-teste", criada dentro de uma Conta de Teste do Mercado Pago) usa `APP_USR-` mesmo na aba "Teste" do painel, porque essa Conta de Teste é uma conta "real" internamente, só que sandboxed; o prefixo `TEST-` clássico é de um modelo antigo de credenciais (conta real com um par de credenciais de teste anexado), não do modelo de aplicação-de-teste-dedicada usado aqui. Não é sinal de credencial errada.

## Segundo caminho pra confirmação: verificação disparada pelo redirect (2026-08-16)

Enquanto o item 3 abaixo (assinatura inválida vira aviso, não bloqueio) continua em aberto — decisão de postura de segurança que só deve mudar depois de confirmar com produção real — implementado um caminho complementar que já resolve o sintoma prático de hoje sem mexer em nada da confiança do webhook:

- **`MercadoPagoApiClient#searchPaymentByExternalReference`**: `GET /v1/payments/search?external_reference=X`, mesmo `PaymentResult` de `getPayment` (ganhou um campo `id`, antes só existia no path do webhook).
- **`CardChargeService#verifyPendingChargeByExternalReference`**: mesmo miolo de aplicação de resultado que o webhook (`applyResolvedPayment`, extraído dos dois em comum) — só troca *como* a cobrança PENDING é encontrada (por `externalReference` direto, não via id de pagamento) e *quem* dispara a verificação.
- **Novo endpoint público**: `POST /api/v1/public/payments/mercadopago/verify/{externalReference}`.
- **`back_urls` (Caixa e cardápio digital)** ganharam um `&ref={externalReference}` — nosso próprio identificador opaco, nunca algo que o Mercado Pago acrescente. `CardPaymentReturnPage` lê esse `ref` e chama o novo endpoint assim que o navegador de quem pagou aterrissa na página, como reforço ao webhook.
- **Por que isso não enfraquece o invariante de segurança já documentado**: a página de retorno continua nunca confiando no próprio `status` da query string pra mutar estado (isso é só cosmético, pode ser forjado por qualquer um digitando a URL). O que mudou é que agora ela também dispara uma pergunta direta ao Mercado Pago (`searchPaymentByExternalReference`, com o token do próprio restaurante) — a mesma fonte de verdade que o webhook já usa, só chegando por um gatilho diferente. Um `ref` forjado não adianta nada: só resolve pra pagamentos que realmente pertencem à conta MP daquele restaurante, e só é aplicado se o `external_reference` devolvido bater com uma cobrança PENDING de verdade.
- **Validado contra o sandbox real**: usado pra resolver exatamente a comanda que ficou travada nesta sessão (pagamento aprovado, `live_mode: true`, webhook rejeitado por assinatura) — chamar o endpoint manualmente encontrou o pagamento via `search`, marcou o `card_charge` como `PAID` e fechou a comanda, sem precisar de "Simular notificação".
- **Testes**: `CardChargeIntegrationTest#verify_byExternalReference_shouldClosePaidTabWithoutAnyWebhook` (fecha a comanda sem nenhum webhook, idempotente) e `#verify_forUnknownOrAlreadyResolvedReference_shouldBeANoOp` (referência inexistente, ou cobrança ainda não paga).

**Gap descoberto testando de verdade**: o gatilho pelo redirect (acima) depende do navegador de quem pagou voltar pra `/pagamento/retorno` — e isso não é automático. O Mercado Pago só redireciona sozinho (`auto_return`) se `back_urls.success` for HTTPS, o que nunca é o caso em dev local (`http://localhost`); mesmo em produção, `createPreference` nunca configura `auto_return` hoje. Sem um clique manual em "voltar ao site" na própria tela do Mercado Pago, o navegador nunca chega na nossa página, e o `ref` nunca é lido — reproduzido de verdade nesta sessão (Mesa 3, pagamento aprovado, comanda travada). Corrigido pro fluxo do Caixa: o polling que `CheckoutPage` já roda a cada 3s enquanto a tela do QR Code está aberta agora também chama `verifyCardCharge` a cada tick, antes de reler o estado da comanda — não depende de nada no celular de quem pagou, só da tela do Caixa continuar aberta (que já é a premissa do fluxo).

**Gap do cardápio digital, fechado em 2026-08-18** (reproduzido de verdade num pedido de delivery: pagamento aprovado no Mercado Pago, comanda ficou travada em `OPEN`, item nunca apareceu na fila da cozinha — mesma causa raiz de sempre, `live_mode: true` + navegador do cliente nunca clicou "voltar ao site"): igual ao Caixa, mas o lado do cliente não tinha nenhuma tela de staff fazendo polling por ele. Resolvido reaproveitando o polling que `DeliveryStatusPage` já roda a cada 4s (`getPublicDeliveryStatus`) — `DeliveryService#getByAccessToken` agora, antes de montar a resposta, busca qualquer `CardCharge` `PENDING` da comanda e chama `verifyPendingChargeByExternalReference` pra cada uma, best-effort (nunca deixa uma falha do gateway quebrar a leitura do status pelo cliente). `verifyPendingChargeByExternalReference` virou `REQUIRES_NEW` porque `getByAccessToken` é `readOnly = true` — sem isso, a escrita (`applyResolvedPayment`) falharia dentro da transação somente-leitura do chamador; e `DeliveryDetailsResponse.paid` passou a ler o status da comanda via uma query de projeção (`TabRepository#findStatusById`) em vez do `Tab` já carregado em memória, porque esse objeto fica desatualizado assim que a verificação (rodando numa transação separada, já commitada) muda o status por baixo do tapete. Validado ponta a ponta: comanda que estava travada fechou sozinha e o item apareceu na cozinha na primeira chamada de status depois do fix, sem nenhuma ação manual.

## Estorno não é testável em sandbox (investigado a fundo, 2026-08-16)

Tentativa de estornar um pagamento aprovado de verdade (conta de teste, cartão de teste, checkout real - não simulado) falhou com `401 Unauthorized: "Unauthorized use of live credentials"` (código de erro 7) vindo direto da API do Mercado Pago. `TabController.voidPayment`/`CardChargeService.voidPayment` se comportaram corretamente: o pagamento continuou `ACTIVE` internamente, nada foi corrompido - só o erro real do gateway foi propagado (mesmo caminho já coberto por `refund_whenGatewayCallFails_shouldNotVoidThePayment`).

**Investigação**: instrumentado temporariamente pra comparar `GET /users/me` (conta do token) contra `GET /v1/payments/{id}` (o pagamento em si). Resultado: `collector_id` do pagamento bate exatamente com o `id` da conta do token (`3618790940` nos dois) - não é token errado nem pagamento de outra conta. A única diferença é `live_mode: true` no pagamento. Pesquisa corrobora: contas de teste conseguem criar/receber pagamentos com `live_mode: true` (via checkout real, não simulação), mas a API de estorno recusa operar sobre esses pagamentos com um token de teste, mesmo sendo a própria conta dona do dinheiro - restrição de plataforma do Mercado Pago, não hipótese nossa, sem correção possível do lado do código.

**Consequência prática**: **estorno só é validável com credenciais de produção reais** - mesmo requisito que já existia pra resolver em definitivo a flakiness do webhook (item 1 abaixo). Não vale a pena tentar mais nada em sandbox para isso.

## Bloqueio ao testar produção: comprador e vendedor não podem ser a mesma conta (2026-08-16)

Trocadas as credenciais do `card_integrations` do Tatu Bola pelas de produção reais (via `CardIntegrationCard`, já com o campo por-credencial opcional implementado nesta sessão - ver seção acima). Gerada uma cobrança real (`pref_id` com prefixo `2516901985`, batendo com o `user_owner` da conta de teste vista antes - confirma que a preferência foi criada mesmo pela conta real). Botão "Pagar" ficou cinza/desabilitado em **todo** valor testado (R$0,55, R$1,10, R$3,30, R$30,69) - inicialmente levantada a hipótese de valor mínimo de transação, descartada pela repetição do bloqueio em R$30,69.

**Causa real, confirmada com o usuário**: a conta usada pra pagar (com o cartão salvo "Nubank") é a mesma conta cujas credenciais de produção foram coladas no Morá - Mercado Pago não permite que comprador e vendedor sejam a mesma conta (proteção antifraude padrão da plataforma, não uma restrição nossa). Explica por que sandbox funcionou sem problema (lá o comprador de teste "Lucas" é uma conta de teste separada da conta vendedora de teste) e produção não.

**Para concluir esse teste**: precisa de uma segunda conta real de Mercado Pago, de outra pessoa, pagando com o próprio cartão - não dá pra validar sozinho com a própria conta.

## Validação final em produção (2026-08-16, concluída)

Resolvido com uma conta de comprador de verdade, diferente da conta do restaurante (aba anônima, checkout como convidado - confirmado que o cliente final **não precisa ter conta no Mercado Pago**: `Checkout Pro` já tem o modo convidado, "Sem conta Mercado Pago → Cartão", só pede identificação por CPF/e-mail/telefone, não login):

- **Pagamento real aprovado**: R$1,00, pago com cartão de verdade por uma conta diferente da do restaurante. `card_charge` foi pra `PAID` sozinho (`mp_payment_id 174168944964`), comanda fechou sozinha - confirma que webhook e/ou o polling ativo do Caixa funcionam em produção, não só em sandbox.
- **Estorno real bem-sucedido**: o mesmo pagamento de R$1,00 foi estornado pelo Caixa (`refund_id 3242312536`, `refunded_at` gravado) - **sem** o erro "Unauthorized use of live credentials" visto antes. Confirma que aquele erro era específico do cenário comprador=vendedor (mesma conta); um pagamento de terceiro de verdade estorna normalmente.
- Também testados com sucesso nesse fluxo (cliente de verdade, conta separada): R$0,55, R$1,10, R$3,30, R$5,01, R$30,69 e R$1,00 - **não existe valor mínimo de transação** que bloqueie o cartão; a suspeita inicial de "piso mínimo" era, na verdade, sempre o bloqueio de comprador=vendedor.

**Card payment está 100% validado em produção.** Falta só marcar `docs/SCOPE.md` como entregue e commitar.

## Próximos passos

1. Atualizar `docs/SCOPE.md` marcando "cartão" como entregue.
2. Commit final.

### Estado do ambiente ao concluir (2026-08-16)

- `card_integrations` do restaurante de teste (**Tatu Bola**, id `341e8209-334c-40db-aab5-856730fd4fb0`) está com as credenciais de **produção** reais coladas via `CardIntegrationCard` - trocar de volta pras de teste antes de continuar testando outras coisas em sandbox, se for o caso.
- Túnel ngrok usado nos testes: `https://baguette-playing-premises.ngrok-free.dev` (domínio estático da conta, não muda a cada reinício).
- Migrations `V61`/`V62`, todo o código do backend/frontend do cartão (incluindo o segundo caminho de confirmação por `ref` e a rotação independente de credenciais): implementados e validados ponta a ponta em produção (ver seções acima), nada commitado ainda.
- `render.yaml`: já tem `FRONTEND_URL`/`BACKEND_URL` de produção adicionados (achado numa sessão anterior, corrige também o link de recuperação de senha que nunca tinha `FRONTEND_URL` configurado) — também não commitado ainda.
