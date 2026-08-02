# Pagamento Pix integrado — desenho pra quando começarmos

Discussão feita em 2026-08-02, antes de qualquer código, sobre o item 10 do backlog ("Pagamento online — Pix e cartão via gateway", `docs/SCOPE.md`, Prioridade 6 — "só se um cliente concreto pedir"). Este arquivo existe pra não perder o raciocínio entre agora e o dia em que isso entrar de fato.

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
