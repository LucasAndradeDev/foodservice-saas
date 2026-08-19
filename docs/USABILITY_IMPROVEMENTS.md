# Melhorias de usabilidade (fricção de interação)

Levantamento de 2026-08-17, motivado por um caso concreto: em `CashRegisterPage.tsx`,
fechar o caixa exigia digitar o valor contado na gaveta mesmo o sistema já sabendo o
"valor esperado" — corrigido com um botão "Usar valor esperado" que preenche o campo.

Esse achado não é sobre layout/visual (isso já foi coberto em auditoria anterior,
ver `project_admin_ui_audit_2026-08-15` na memória). É especificamente sobre casos em
que o usuário precisa digitar/repetir algo que o sistema já sabe ou já tem em algum
lugar, ou em que um fluxo de várias etapas poderia virar uma ação só.

Metodologia: leitura completa do código-fonte de cada página (não é suposição/visual),
feita por 3 varreduras paralelas cobrindo telas operacionais, de gestão/configuração e
públicas. Cada achado abaixo foi confirmado no código antes de entrar na lista.

Status de cada item: `[ ]` pendente, `[x]` implementado.

## Risco de abandono de cliente (fluxo público) — mais graves

- [x] **1. Endereço de entrega 100% digitado à mão, sem autopreenchimento por CEP**
  `frontend/src/pages/publicMenu/CartDrawer.tsx:250-336`
  O campo CEP (`zipCode`) existe mas é opcional e não aciona nada (não entra em
  `REQUIRED_DELIVERY_FIELDS`, `frontend/src/pages/publicMenu/utils.ts:24`). O cliente
  digita rua, número, complemento, bairro, cidade e CEP manualmente todo pedido.
  Fix: mover o campo CEP pro topo do formulário e, ao perder o foco (debounce, mesmo
  padrão já usado pro bairro em `PublicMenuPage.tsx:90-93`), consultar ViaCEP e
  auto-preencher rua/bairro/cidade — o cliente só confirma e digita número/complemento.

- [x] **2. Carrinho e endereço de entrega vivem só em memória**
  `frontend/src/pages/PublicMenuPage.tsx:64,82`
  Sem `localStorage`/`sessionStorage` em nenhum lugar do fluxo público (confirmado por
  grep no repo). Reload, "voltar" do navegador ou a tela travar apaga carrinho e
  formulário de endereço já preenchidos.
  Fix: persistir `cart`, `deliveryAddress` e `orderMode` em `localStorage` (chaveado
  por `slug`) e restaurar no mount.

## Espelham o exemplo do caixa (sistema já sabe, mas pede de novo)

- [x] **3. "Abrir caixa" não sugere o valor do último fechamento**
  `frontend/src/pages/CashRegisterPage.tsx:45-46,86-90,362-387`
  `openingAmount` sempre começa vazio. A query `sessions` (histórico) já é buscada
  incondicionalmente nessa mesma tela e cada sessão fechada tem `countedAmount` — o
  candidato natural para o novo fundo de caixa.
  Fix: botão "Usar valor do último fechamento" ao lado do campo, preenchendo com o
  `countedAmount` da sessão fechada mais recente (mesmo padrão do botão já adicionado
  no fechamento).

- [x] **4. Modal "Adicionar itens" da comanda não foca a busca ao abrir**
  `frontend/src/pages/TabDetailPage.tsx:495-501` (`openAddItemForm`) e `:1112-1119`
  (input de busca)
  Provavelmente a ação mais repetida do turno. O input tem um atalho `/` dedicado
  justamente porque não recebe foco automático — o garçom precisa apertar `/` ou
  clicar antes de digitar, toda vez.
  Fix: `autoFocus` no input (ou `useEffect` chamando `.focus()` ao abrir o modal).

- [x] **5. Nenhum modal operacional usa `autoFocus`**
  Confirmado por grep: `autoFocus` só aparece em `MonthlyGoalCard.tsx`. Modais como
  "Fazer sangria", "Fechar caixa", "Anular pagamento", "Desconto no item" abrem sem
  focar o primeiro campo relevante.
  Fix: focar o primeiro campo ao abrir. Ponderar em telas majoritariamente mobile
  (autofoco abre teclado virtual por cima de botões) — não é problema nas estações de
  caixa (desktop).

- [x] **6. Modificadores de produto sem "copiar de outro produto"**
  `frontend/src/pages/ProductModifiersPage.tsx:79-86` (`openCreateForm`)
  Todo grupo novo começa em branco. Restaurante com várias pizzas com o mesmo grupo
  "Tamanho" (P/M/G, mesmos preços) digita tudo de novo, produto por produto.
  Fix: botão "Copiar de outro produto" que abre um seletor de produto e copia a
  estrutura de `groups` (usuário revisa e salva); ou dropdown "Copiar grupo existente"
  ao criar um grupo novo.

- [x] **7. Horários de disponibilidade sem copiar de outro produto**
  `frontend/src/pages/ProductAvailabilityPage.tsx:93-99` (`openCreateForm`)
  Mesmo problema do item 6: sempre reseta para `dayOfWeek=''`, `11:00-15:00`. Um
  "cardápio de almoço" com 10 pratos na mesma janela exige repetir manualmente em cada
  produto.
  Fix: mesmo padrão do item 6, ou pelo menos lembrar o último horário usado na sessão.

- [x] **8. Cadastro de funcionário: papel padrão é "Gerente"**
  `frontend/src/pages/StaffPage.tsx:33-36,107`
  `setRole(assignableRoles[0])` sempre volta pro papel menos comum de cadastrar em
  lote. Ao contratar vários garçons de uma vez, o dono troca o dropdown manualmente
  toda vez (Garçom já é o único papel com ação extra "Ver desempenho" na listagem —
  o sistema sabe que é o mais frequente).
  Fix: default para `'WAITER'` quando disponível, ou lembrar o último papel
  selecionado na sessão.

## Volume alto, atrito menor

- [x] **9. Imprimir exige 2 cliques**
  `frontend/src/pages/TabDetailPage.tsx:970-976` (link "Imprimir") e
  `frontend/src/pages/CheckoutPage.tsx:963-970` (link "Imprimir recibo")
  `OrderTicketPrintPage.tsx` só imprime sozinho com `?auto=1` na URL (hoje usado só no
  fluxo automático ao enviar pra cozinha). `TabReceiptPrintPage.tsx` não tem suporte a
  auto-impressão nenhum. O clique no link já é uma declaração de intenção de imprimir.
  Fix: reaproveitar `?auto=1` nesses links manuais e implementar o mesmo em
  `TabReceiptPrintPage`.

- [x] **10. Filtro de datas do Relatórios sempre volta pra "Hoje"**
  `frontend/src/pages/ReportsPage.tsx:69-71`
  `useState(today)` sem persistência. Gerente que sempre confere "Este mês" precisa
  reescolher o preset toda vez que sai e volta pra tela.
  Fix: persistir o último range escolhido em `localStorage` (mesmo padrão já usado em
  `recentItemsStorage.ts`).

- [x] **11. Link de confirmação de reserva sem botão "copiar"**
  `frontend/src/pages/publicMenu/ReservationFormModal.tsx:42-57`
  Depois de reservar, só aparece um link cru com "guarde este link" — precisa
  selecionar e copiar manualmente. `PixPaymentModal.tsx:98-105` já resolve o mesmo
  problema com um botão "Copiar código" com feedback visual, só não foi replicado aqui.
  Fix: mesmo botão "Copiar link" (e opcionalmente "Enviar por WhatsApp") na confirmação
  de reserva.

- [x] **12. Status da reserva não atualiza sozinho**
  `frontend/src/pages/PublicReservationStatusPage.tsx:19-24`
  `useQuery` sem `refetchInterval` — única tela "de status" do fluxo público sem
  polling (o resto do app usa: `PublicMenuPage` 4000ms, `TableActionsMenu`,
  `CheckoutPage` 3000ms).
  Fix: adicionar `refetchInterval` igual ao resto do app.

- [x] **13. Importação de cardápio: botão desabilitado sem indicar o que falta**
  `frontend/src/pages/MenuImportPage.tsx:143-144,404`
  Quando 1-2 linhas vêm sem preço, o botão de confirmar só fica cinza (`disabled`, sem
  `title`), sem indicar qual linha corrigir — usuário precisa rolar a tabela inteira
  procurando. O sistema já sabe quais `draftRows` têm `!row.price || Number(row.price) <= 0`.
  Fix: destacar com borda vermelha os inputs de preço vazios/zerados e mostrar
  "N produtos sem preço — role para revisar" acima do botão.

## Bug de affordance (não é bem o mesmo padrão, mas vale registrar)

- [x] **14. Campo de cupom aparece no modo Delivery mas sempre falha**
  `frontend/src/pages/publicMenu/CartDrawer.tsx:116-136`,
  `frontend/src/api/publicMenu.ts:139-147`
  O bloco de cupom não é condicionado a `showDeliveryFields`. A rota de resgate exige
  `tableId` na URL, que não existe em modo Delivery (o toggle só aparece quando não há
  `tableId`) — o cliente digita o cupom, aperta "Aplicar" e recebe "Cupom inválido,
  expirado ou esgotado" mesmo que o cupom seja válido.
  Fix: esconder o campo de cupom quando `showDeliveryFields` for verdadeiro, ou dar
  suporte a cupom por delivery no backend.

## Segunda varredura (2026-08-17/18)

Depois de fechar os 14 itens acima, nova varredura completa sobre o restante do painel
admin (Produtos, Categorias, Combos, Cupons, Happy Hour, Zonas de entrega, Áreas do salão,
Mesas, Reservas, Cozinha, Dashboard, Avaliações, Configurações, Checkout/divisão por
pessoa, guias de Pix/Cartão) — leitura completa do código-fonte de cada tela, mesma
metodologia da primeira varredura. A maior parte já está limpa (bulk actions, `ConfirmDialog`,
valores padrão sensatos, botões de copiar, pré-preenchimento) depois da primeira rodada;
só um achado novo confirmado no código:

- [x] **15. "Nova reserva" não parte do dia que já está sendo visualizado**
  `frontend/src/pages/ReservationsPage.tsx:60,131-138`, `frontend/src/components/DateTimePicker.tsx`
  A tela já deixa navegar por dia (setas ‹ › + `DatePicker`) e guarda esse dia em `date`.
  Ao clicar em "Nova reserva", `reservationTime` sempre reseta pra `''` e o `DateTimePicker`
  abre no mês/dia de hoje — se o gerente está vendo sábado que vem e cria uma reserva pra
  esse mesmo dia, precisa navegar o calendário manualmente até lá de novo.
  Fix: novo prop opcional `initialViewDate` no `DateTimePicker` (não seleciona nada sozinho,
  só abre o calendário já no mês/dia certo) — passado como `date` a partir do
  `ReservationsPage`.

## Descartado (já bem resolvido, sem fricção real confirmada)

- Ações em massa em Produtos (`BulkActionsMenu` já existe).
- Preview de preço em Happy Hour e Combos (já existem).
- Diálogos de confirmação com recap (`ConfirmDialog` já usado nas telas mais críticas).
- Reordenação de Áreas do salão (já é drag-and-drop).
- Taxa de zona de entrega — bairro/taxa raramente se repetem entre zonas, "copiar" não
  traria ganho real.
- `CardPaymentModal`, `PixPaymentModal`, `ProductDetailModal`, `ModifierSheet`,
  `ComboSheet`, `TableActionsMenu`, `PostMealFeedbackPage`, `CardPaymentReturnPage` —
  já seguem o padrão desejado (sem redigitação evitável, com polling, com botão de
  copiar, com pré-preenchimento).
- "Pedir de novo" (`PublicMenuPage.tsx:366-417`) — já implementado, exemplo positivo
  do padrão que o resto da lista busca aplicar em outros lugares.
