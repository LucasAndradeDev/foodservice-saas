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

## Decisões em aberto pra quando entrar de verdade

- Confirmar o formato exato de assinatura de webhook do Mercado Pago contra `developers.mercadopago.com` (pode ter mudado desde esta sessão).
- Confirmar se o modelo "Access Token direto, colado em Configurações" (igual Woovi) é o fluxo padrão do Mercado Pago pra vendedor único, ou se eles empurram pra OAuth/Connect mesmo pra caso simples — isso muda a tela de configuração.
- Decidir se `DEBIT_CARD`/`CREDIT_CARD` (já existem no enum `PaymentMethod`) precisam de tratamento diferente no gateway, ou se o Checkout Pro trata os dois igual (provável, já que quem escolhe débito vs. crédito é o próprio cliente na página deles).
- Nome da tela de configuração — hoje existe `PixIntegrationCard`; caberia um `CardIntegrationCard` irmão na mesma tela de Configurações, ou uma seção nova.
