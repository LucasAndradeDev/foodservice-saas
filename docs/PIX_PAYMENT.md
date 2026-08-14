# Pagamento Pix integrado — desenho pra quando começarmos

Discussão feita em 2026-08-02, antes de qualquer código, sobre o item 10 do backlog ("Pagamento online — Pix e cartão via gateway", `docs/SCOPE.md`, Prioridade 6 — "só se um cliente concreto pedir"). Este arquivo existe pra não perder o raciocínio entre agora e o dia em que isso entrar de fato.

**Atualização 2026-08-11**: gateway escolhido é a **Woovi** (ex-OpenPix), não Mercado Pago como cogitado inicialmente. Motivo: Woovi é especializada em Pix (não uma plataforma genérica com Pix encaixado), taxa menor pra tickets típicos de restaurante (0,8%, mín. R$0,50/máx. R$5,00, sem mensalidade — vs. ~0,99% do Mercado Pago) e API/webhook mais direto, no mesmo padrão do resto do projeto (sem SDK). O modelo de conta continua o mesmo já decidido: cada restaurante cria a própria conta na Woovi e gera credenciais próprias — o Morá nunca toca no dinheiro. Referências: [taxa Woovi](https://developers.woovi.com/en/docs/pix-machine/what-is-the-fee-charged-by-woovi), [docs Woovi](https://developers.woovi.com/en/docs). Onde este documento ainda cita "Mercado Pago" abaixo, é resquício da discussão original — vale ler como "Woovi" até uma revisão completa do texto.

## Contexto: por que não é prioridade agora

Não bloqueia vender o produto hoje — o registro manual de "Pix"/"Cartão" na comanda (o garçom marca que o cliente pagou por fora, no Pix direto pro banco do restaurante ou na maquininha própria) já resolve o uso presencial. Pagamento integrado só é pré-requisito de verdade pro delivery (Prioridade 8, ainda travada). Pro autoatendimento (QR Code na mesa), é um "a mais" — cliente paga pelo celular sem chamar o garçom — não um bloqueador.

## Escopo da primeira versão

- **Só Pix, sem cartão.** Cartão de verdade puxa tokenização e escopo de conformidade PCI — complexidade real que não vale pagar numa primeira versão. Pix cobre a maior parte do valor percebido (pagar pelo celular sem esperar o garçom) com uma fração do esforço.
- **Cada restaurante usa a própria conta no gateway** (Mercado Pago é o candidato natural — ver seção seguinte), não uma conta única do Morá recebendo por todos. Isso é mais simples *e* mais seguro: o Morá nunca chega a tocar no dinheiro, só orienta a cobrança pra conta de quem é o dono dela. A alternativa (conta própria do Morá, repassando depois pro restaurante) transformaria o SaaS num intermediário financeiro regulado — ordem de grandeza mais complexa, não vale a pena nesse estágio.

## Por que a burocracia aqui é rápida (diferente de NFC-e/TEF)

Errei inicialmente ao colocar "pagamento online" no mesmo balaio de burocracia lenta que NFC-e (homologação com a SEFAZ) e TEF (certificação com a adquirente) — não é o mesmo nível de demora:
- A maioria dos restaurantes já tem Pix configurado na conta bancária da empresa (gratuito, obrigatório pelo Banco Central) — não é uma barreira de entrada.
- Abrir conta num provedor como Mercado Pago ou Asaas é self-service: geralmente aprova em minutos a 1 dia útil pra um CNPJ (ou até CPF, pra teste). Não depende de homologação/certificação como NFC-e/TEF.

## Desenho técnico

Novidade real em relação a tudo que já foi construído: o pagamento é **assíncrono** (cliente escaneia o QR Code, paga no banco dele, o gateway avisa o backend depois — segundos a minutos depois) e envolve **dinheiro de verdade**, então pede mais cuidado que uma feature comum, mas não é um território algorítmico novo — é o padrão conhecido de webhook.

Peças:
1. **Nova entidade `PixCharge`** (migration `V38__pix_charges.sql`, seguindo o mesmo padrão de `RefreshToken`/`PasswordResetToken`): `id`, `tab_id` (FK), `external_charge_id` (id da cobrança no gateway), `amount`, `status` (`PENDING`/`PAID`/`EXPIRED`), `created_at`.
2. **Criar a cobrança**: backend chama a API do Mercado Pago (`RestClient`, mesmo estilo do `BrevoEmailService`/`SupabaseFileStorageService` — sem SDK) passando o valor e uma referência externa (o id da comanda), recebe de volta o QR Code (imagem + código copia-e-cola) pra mostrar no autoatendimento.
3. **Endpoint de webhook público** (`POST /public/payments/webhook`, mesmo grupo do `PublicOrderController`): recebe o aviso de pagamento.
4. **Verificação de assinatura**: o Mercado Pago manda uma chave secreta pra validar que o aviso é legítimo — sem isso, qualquer um poderia forjar um "paguei" falso.
5. **Idempotência**: só marcar como `PAID` se o status atual não for já `PAID` (evita processar o mesmo aviso duas vezes, já que webhooks podem chegar duplicados).
6. **Fallback manual**: se o aviso nunca chegar (rede caiu, gateway fora do ar), a comanda continua podendo ser marcada como paga manualmente — reaproveita o fluxo que já existe hoje, nunca fica travada esperando o webhook.
7. **Credencial por restaurante**: token de acesso do Mercado Pago de cada restaurante guardado por tenant (não uma chave global no `.env`, como `BREVO_API_KEY` — aqui são N chaves, uma por restaurante). Provavelmente uma tela nova em Configurações pra cada dono colar o próprio token.

## Como testar sozinho, sem precisar de um restaurante de verdade como cobaia

Duas formas, discutidas em 2026-08-02:

1. **Sandbox (sem dinheiro real)**: credenciais de teste do Mercado Pago; a cobrança é criada normalmente, mas em vez de escanear com o banco de verdade, o pagamento é "aprovado" simulado direto no painel/API deles. Bom pra validar a lógica (criação da cobrança, webhook, marcar como pago) sem gastar nada — mas não testa a experiência real de escanear o QR Code.
2. **Produção, com valor pequeno de verdade (recomendado)**: conta real no Mercado Pago (pode ser com CPF, não precisa ser CNPJ pra um teste), credenciais de produção, o backend cria uma cobrança Pix real de R$1, paga com o app do banco de *outra* conta Pix própria. Testa o fluxo inteiro de ponta a ponta — geração do QR, pagamento real, webhook chegando, comanda marcada como paga sozinha — sem precisar esperar um cliente de verdade.

## Estimativa

Com esse escopo (só Pix, conta própria por restaurante, sem cartão): **3 a 5 dias** de trabalho no ritmo já observado no projeto — bem mais rápido que a estimativa inicial de 2-4 semanas, que misturava indevidamente isso com a complexidade de cartão e com a burocracia mais lenta de NFC-e/TEF.

## Estado atual (2026-07-27 → 2026-08-02, referência)

Hoje "Pix"/"Cartão" na comanda é só um campo (`PaymentMethod` enum: `PIX`, `CASH`, `DEBIT_CARD`, `CREDIT_CARD`) preenchido manualmente pelo garçom/caixa no fechamento — nenhuma integração com gateway ainda. Última migration aplicada até este documento: `V37__password_reset_tokens.sql`.

## Implementação (2026-08-11)

Backend implementado ponta a ponta nesta sessão (`V58__pix_gateway_payment.sql`, seguinte migration livre em `main` na hora — checar de novo antes de mesclar, já que outro agente trabalha em paralelo no Armazém Morá e pode ter tomado o número primeiro):

- **`CredentialEncryptionService`** (`security/`): AES/256-GCM, chave mestra em `PIX_ENCRYPTION_KEY` (env var, nunca no banco). Reutilizável pra qualquer segredo de terceiro no futuro, não é específico de Pix.
- **`pix_integrations`**: uma linha por restaurante, `api_key_encrypted` (reversível, diferente do `api_key_hash` do Armazém). Endpoints em `PixIntegrationController` (`GET`/`PUT /api/v1/pix-integration`, OWNER/MANAGER), nunca devolve o AppID de volta, só `configured: true/false`.
- **`pix_charges`**: uma linha por cobrança, `external_charge_id` = `correlationID` que mandamos pra Woovi (UUID gerado por nós, não o id interno deles). Função `SECURITY DEFINER` `pix_charge_by_external_id` (mesmo padrão de `reservation_by_access_token`) resolve o problema ovo-e-galinha do webhook, que chega sem tenant.
- **`WooviApiClient`**: `POST /api/v1/charge` em `https://api.woovi.com`, header `Authorization: <AppID>` (sem "Bearer"). Resposta parseada como `Map` solto (não um DTO fortemente tipado) porque a doc pública não deixou 100% claro o formato completo — **checar contra o sandbox real antes do primeiro teste com dinheiro de verdade**.
- **`WooviWebhookSignatureVerifier`** (`security/`): assinatura `x-webhook-signature` é RSA/SHA-256 contra uma chave pública **fixa, igual pra todo mundo** (não HMAC por conta, como o desenho original tinha cogitado) — capturada da documentação pública da Woovi durante essa sessão, embutida no código. **Revalidar essa chave contra developers.woovi.com antes do primeiro webhook real** — se estiver errada, todo webhook legítimo é rejeitado silenciosamente.
- **`TabService`**: `resolveBillTotal`/`resolveServiceChargePercentage` refatorados de `UserRole role` pra `boolean allowServiceChargeOverride` (só OWNER/MANAGER podem sobrescrever a taxa de serviço; um pagamento Pix nunca tem papel de staff por trás, então sempre usa o padrão do restaurante). Novo método público `freezeBillTotalForPixCharge` — trava o total no momento em que o QR é gerado, não só no primeiro pagamento, pra não deixar o valor mostrado ao cliente derivar se alguém mexer em item/desconto enquanto a cobrança está pendente (reaproveita a mesma trava que já bloqueia `applyDiscount`/`mergeTab`/`unmergeTab`). `registerPayments` passou a aceitar `actingUserId` nulo (pagamento confirmado pelo webhook, sem staff por trás — mesmo padrão nulo já usado em `Order.createdBy` pro autoatendimento).
- **Fluxo**: `POST /api/v1/tabs/{id}/pix-charges` (staff autenticado — OWNER/MANAGER/WAITER/CASHIER, pensado pro Caixa gerar o QR numa comanda) congela o total, cria a cobrança na Woovi, devolve `brCode`/`qrCodeImage`. `POST /api/v1/public/payments/webhook` (sem auth, verificado por assinatura) confirma o pagamento chamando `TabService.registerPayments` internamente — reaproveita o mesmo mecanismo do pagamento manual/split, não um caminho paralelo. Idempotente (segunda entrega do mesmo evento não duplica o pagamento).
- **Frontend do Caixa**: tela de Configurações (`PixIntegrationCard`) pra colar/trocar o AppID, e o Checkout ganhou um botão "Gerar QR Code Pix" que mostra o QR + copia-e-cola e faz polling da comanda até ela fechar sozinha.
- **Autoatendimento pelo cardápio digital**: o cliente também pode gerar e pagar o Pix sozinho, sem chamar o garçom. `POST /api/v1/public/menu/{slug}/tables/{tableId}/pix-charges` (sem autenticação, mesmo padrão de `PublicTableRequestService`) resolve o restaurante pelo slug e a comanda aberta da mesa, exige o mesmo gate que "Pedir a conta" (pelo menos um item `DELIVERED`) e reaproveita `PixChargeService#createCharge` por baixo. `GET /public/menu/{slug}` passou a expor `table.pixConfigured`, então o botão "Pagar com Pix" só aparece quando o restaurante já configurou a Woovi. Confirmação continua assíncrona via webhook — o cardápio digital já fazia polling e já redireciona pro feedback quando a comanda fecha, então nenhum polling novo foi necessário no modal (`PixPaymentModal`).

## Pendências antes de testar com dinheiro real

Ambas confirmadas em 2026-08-12 contra o sandbox real (`https://api.woovi-sandbox.com`, conta de teste própria do usuário), backend local exposto via ngrok pra receber o webhook:

- ~~**Formato da resposta da Woovi**~~ Uma tentativa com AppID inventado bateu na API real e voltou `401` estruturado (`{"data":null,"errors":[{"message":"appID inválido"}]}`), tratado corretamente (`PixGatewayException` → 502 → mensagem legível no Checkout). Com um AppID real do sandbox, `POST /api/v1/tabs/{id}/pix-charges` gerou um QR Code de verdade pelo Checkout — `brCode` e `qrCodeImage` vieram no formato esperado por `WooviApiClient#createCharge`, sem precisar ajustar nada no parsing.
- ~~**Chave pública fixa do webhook**~~ Ciclo completo testado ponta a ponta: QR gerado → pagamento simulado no painel do sandbox → Woovi chamou `POST /api/v1/public/payments/webhook` (via túnel ngrok) → assinatura RSA validada com a chave hardcoded em `WooviWebhookSignatureVerifier` (200 OK, sem precisar trocar a chave) → `TabService.registerPayments` disparado automaticamente → comanda fechou sozinha sem intervenção manual.

Nenhuma pendência técnica conhecida restante antes de ligar isso pra um restaurante de verdade com conta de produção própria.

## Divisão de conta com Pix — split-bill (2026-08-13)

O V1 acima cobre só uma cobrança por comanda (o total inteiro). Esta sessão estendeu o mesmo mecanismo pra comandas divididas: cada pessoa gera sua própria cobrança Pix parcial, com seu próprio QR Code, em vez de uma pessoa só pagando o total.

- **`PixChargeService#createCharge`** ganhou um parâmetro opcional de valor: omitido, cobre o saldo restante inteiro (comportamento antigo do V1, inalterado); informado, é validado contra o que ainda está genuinamente em aberto — total congelado menos pagamentos já ativos menos outras cobranças Pix ainda `PENDING` na mesma comanda — e rejeitado **antes** de chamar a Woovi se ultrapassar isso.
- **Novos endpoints** em `TabController`: `GET /api/v1/tabs/{id}/pix-charges` (lista as cobranças `PENDING`/`PAID` da comanda) e `DELETE /api/v1/tabs/{id}/pix-charges/{chargeId}` (cancela uma cobrança específica por id — diferente do `DELETE /api/v1/tabs/{id}/pix-charges` já existente, que cancela todas de uma vez).
- **`pix_charges`** ganhou `br_code`/`qr_code_image`/`payment_link_url` persistidos (migration `V60`) — no V1 esses dados só viviam no estado React de um navegador; agora sobrevivem a reload/poll, necessário porque pode haver várias cobranças `PENDING` simultâneas na mesma comanda.
- **`TabService`**: `registerPayments` agora desconta cobranças Pix `PENDING` de terceiros do saldo restante (uma pessoa não pode pagar em dinheiro o que a cobrança Pix de outra pessoa já reservou); novo `unfreezeBillTotalIfNoCommitments` descongela o total só se não sobrar nenhum pagamento ativo nem cobrança `PENDING` — usado pelo cancelamento individual, que não deve destravar a comanda se ainda houver outra cobrança pendente.
- **Frontend** (`CheckoutPage.tsx`): o modo "Dividir em 2x/3x/4x" (já existente pra pagamento manual) ganhou, por pessoa, um botão próprio "Gerar QR Code Pix" — cada QR fica independente, com seu próprio "Copiar código"/"Cancelar", sem interferir nas demais entradas.
- Cobertura de teste automatizado nova em `PixChargeIntegrationTest`/`TabControllerIntegrationTest`: concorrência entre cobranças parciais somando certo, validação de saldo antes de chamar a Woovi, cancelamento individual sem afetar as demais, e a trava do total enquanto restar qualquer `PENDING`.

### Validado manualmente em 2026-08-13

Rodado no navegador contra o sandbox real da Woovi (AppID de teste do usuário, configurado só numa comanda de teste local, nunca commitado nem exposto neste arquivo): dividir uma comanda em 2x e gerar as duas cobranças gerou dois QR Codes reais e distintos ao mesmo tempo; cancelar um deles pela UI marcou só aquela cobrança como `CANCELLED` no banco, mantendo a outra `PENDING` intacta com seu QR; com a cobrança restante ainda pendente, uma tentativa de aplicar desconto continuou recebendo `403` (total permanece congelado), como esperado.

Não testado neste passe: o fechamento completo via webhook real de uma cobrança parcial (exigiria simular o pagamento no painel da Woovi + túnel ngrok, como no V1). Esse trecho específico — confirmação assíncrona via webhook — é o mesmo mecanismo já validado ponta a ponta no V1 acima, e está coberto pelos testes automatizados que exercitam duas cobranças parciais sendo confirmadas por webhook em sequência.
