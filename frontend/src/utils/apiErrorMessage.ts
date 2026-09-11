import { isAxiosError } from 'axios'

function extractBackendMessage(err: unknown): string | undefined {
  if (isAxiosError(err) && typeof err.response?.data?.message === 'string') {
    return err.response.data.message
  }
  return undefined
}

// Backend business-rule messages are English by convention (see feedback_code_in_english memory) -
// they're meant for logs and API consumers, not restaurant staff or customers. Several screens
// surface them verbatim instead of a fixed generic string so the real cause stays visible (finding
// #10, 2026-09-07 review; da15b4e; c646afa) - this is what makes that raw text readable in
// Portuguese. Anything not covered here still falls back to the caller's generic message rather
// than leaking raw English, and anything backend code already sends in Portuguese passes through
// unchanged.
const EXACT_TRANSLATIONS: Record<string, string> = {
  'Only JPEG, PNG or WEBP images are allowed.': 'Só são aceitas imagens JPEG, PNG ou WEBP.',
  'File is empty.': 'O arquivo está vazio.',
  'File content does not match a JPEG, PNG or WEBP image.': 'O conteúdo do arquivo não corresponde a uma imagem JPEG, PNG ou WEBP.',
  'Could not read uploaded file.': 'Não foi possível ler o arquivo enviado.',
  'Invalid admin credentials.': 'Credenciais de administrador inválidas.',
  'Invalid or expired reset link.': 'Link de redefinição inválido ou expirado.',
  'Invalid or expired verification link.': 'Link de verificação inválido ou expirado.',
  'Email already registered.': 'Esse e-mail já está cadastrado.',
  'CNPJ already registered.': 'Esse CNPJ já está cadastrado.',
  'Slug already in use.': 'Esse link já está em uso.',
  'Refresh token not found.': 'Sessão expirada. Faça login novamente.',
  'Refresh token expired or revoked.': 'Sessão expirada. Faça login novamente.',
  'Current password is incorrect.': 'A senha atual está incorreta.',
  'Invalid username or password.': 'Email ou senha inválidos.',
  'Too many attempts. Try again in a few minutes.': 'Muitas tentativas. Tente novamente em alguns minutos.',
  'Restaurant access suspended. Contact support.': 'Acesso do restaurante suspenso. Entre em contato com o suporte.',
  'This request conflicts with an existing record.': 'Esse registro já existe ou entra em conflito com outro.',
  'Both the access token and webhook secret are required to connect for the first time.':
    'É necessário informar o token de acesso e o segredo do webhook na primeira conexão.',
  'Nothing to update - fill in the access token, the webhook secret, or both.':
    'Nada para atualizar: preencha o token de acesso, o segredo do webhook, ou ambos.',
  'Cannot start a card charge for a table with no delivered items yet.':
    'Não é possível iniciar uma cobrança no cartão pra uma mesa sem itens entregues ainda.',
  'Cannot start a Pix charge for a table with no delivered items yet.':
    'Não é possível gerar uma cobrança Pix pra uma mesa sem itens entregues ainda.',
  'Cannot request the bill for a table with no delivered items yet.':
    'Não é possível pedir a conta de uma mesa sem itens entregues ainda.',
  'Amount must not have more than 2 decimal places.': 'O valor não pode ter mais de 2 casas decimais.',
  'Payment is already voided.': 'Esse pagamento já foi cancelado.',
  'This card payment was already refunded.': 'Esse pagamento no cartão já foi estornado.',
  'A category with this name already exists.': 'Já existe uma categoria com esse nome.',
  'A product with this name already exists.': 'Já existe um produto com esse nome.',
  'Cannot deactivate a category that still has active products.': 'Não é possível desativar uma categoria que ainda tem produtos ativos.',
  'Cannot delete a category that has products. Remove or move them first.':
    'Não é possível excluir uma categoria que tem produtos. Remova ou mova-os primeiro.',
  'Duplicate selection for the same slot.': 'Seleção duplicada para o mesmo slot.',
  'Selection references a slot that does not belong to this combo.': 'A seleção referencia um slot que não pertence a esse combo.',
  'Product is not a combo. Set its type to COMBO first.': 'O produto não é um combo. Defina o tipo como COMBO primeiro.',
  'A combo must have at least one fixed item or slot.': 'Um combo precisa ter ao menos um item fixo ou slot.',
  'A combo cannot reference itself.': 'Um combo não pode referenciar a si mesmo.',
  'A combo cannot contain another combo.': 'Um combo não pode conter outro combo.',
  'A coupon with this code already exists.': 'Já existe um cupom com esse código.',
  'Expiration date must be in the future.': 'A data de expiração precisa ser no futuro.',
  'Percentage discount cannot exceed 100.': 'O desconto percentual não pode passar de 100.',
  'Couriers can only mark their own out-for-delivery orders as delivered.':
    'Entregadores só podem marcar como entregues os próprios pedidos que estão a caminho.',
  'Order still being prepared in the kitchen.': 'O pedido ainda está sendo preparado na cozinha.',
  'Order not fully paid yet.': 'O pedido ainda não foi totalmente pago.',
  'No courier assigned yet.': 'Nenhum entregador foi atribuído ainda.',
  'Courier can only be changed before the order is out for delivery.': 'O entregador só pode ser trocado antes de o pedido sair para entrega.',
  'Courier is not active.': 'O entregador não está ativo.',
  'A delivery zone with this neighborhood already exists.': 'Já existe uma zona de entrega com esse bairro.',
  'A dining area with this name already exists.': 'Já existe uma área com esse nome.',
  'Cannot delete an area that still has tables assigned.': 'Não é possível excluir uma área que ainda tem mesas atribuídas.',
  'Start time must be before end time.': 'O horário de início precisa ser antes do horário de término.',
  'This item is part of a combo; update the combo header instead.': 'Esse item faz parte de um combo; atualize o combo principal.',
  "This delivery order hasn't been paid yet.": 'Esse pedido de delivery ainda não foi pago.',
  "Cannot apply a manual discount to a combo header or its items; it already carries the combo's discount.":
    'Não é possível aplicar desconto manual a um combo ou seus itens; ele já tem o desconto do combo.',
  'Cannot discount a cancelled item.': 'Não é possível aplicar desconto a um item cancelado.',
  'Tab is not open.': 'A comanda não está aberta.',
  'Source tab is not open.': 'A comanda de origem não está aberta.',
  'Target tab is not open.': 'A comanda de destino não está aberta.',
  'Payment has already started for this tab.': 'O pagamento dessa comanda já foi iniciado.',
  'Payment has already started for the source tab.': 'O pagamento da comanda de origem já foi iniciado.',
  'Payment has already started for the target tab.': 'O pagamento da comanda de destino já foi iniciado.',
  'All items must belong to the same tab.': 'Todos os itens precisam pertencer à mesma comanda.',
  'Cannot transfer a cancelled item.': 'Não é possível transferir um item cancelado.',
  'Cannot transfer a combo item individually; combos must move as a whole order.':
    'Não é possível transferir um item de combo individualmente; combos precisam ser movidos inteiros.',
  'Cannot transfer items to the same tab.': 'Não é possível transferir itens para a mesma comanda.',
  'Discount value must be greater than zero.': 'O valor do desconto precisa ser maior que zero.',
  'Discount amount cannot exceed the amount being discounted.': 'O valor do desconto não pode ser maior que o valor sendo descontado.',
  'This coupon has expired.': 'Esse cupom expirou.',
  'This coupon has already reached its usage limit.': 'Esse cupom já atingiu o limite de uso.',
  'No coupon applied to this table.': 'Nenhum cupom aplicado a essa mesa.',
  'Feedback has already been submitted for this tab.': 'Esse feedback já foi enviado.',
  'Tab is not closed yet.': 'A comanda ainda não foi fechada.',
  'Start date must not be after end date.': 'A data inicial não pode ser depois da data final.',
  'No table available for the requested party size and time.': 'Nenhuma mesa disponível para o horário e número de pessoas solicitados.',
  'Reservation cannot be checked in from its current status.': 'Não é possível fazer check-in dessa reserva no status atual.',
  'Reservation cannot be cancelled from its current status.': 'Não é possível cancelar essa reserva no status atual.',
  'One or more tables were not found in this restaurant.': 'Uma ou mais mesas não foram encontradas nesse restaurante.',
  'One or more tables are not active.': 'Uma ou mais mesas não estão ativas.',
  'Selected tables do not have enough capacity for this party size.': 'As mesas selecionadas não têm capacidade suficiente para esse número de pessoas.',
  'One or more selected tables are already reserved around this time.': 'Uma ou mais mesas selecionadas já estão reservadas perto desse horário.',
  'One or more tables are reserved around this time.': 'Uma ou mais mesas estão reservadas perto desse horário.',
  'Critical threshold must be greater than the warning threshold.': 'O limite crítico precisa ser maior que o limite de aviso.',
  'A table with this number already exists.': 'Já existe uma mesa com esse número.',
  'RESERVED is computed automatically and cannot be set directly.': 'O status RESERVADA é calculado automaticamente e não pode ser definido diretamente.',
  'Cannot delete a table that is not FREE.': 'Não é possível excluir uma mesa que não está livre.',
  'A tab cannot be merged into itself.': 'Uma comanda não pode ser unida a ela mesma.',
  'Source tab was not merged into this tab.': 'A comanda de origem não foi unida a essa comanda.',
  'Only OWNER or MANAGER may complete a payment correction on a closed tab.':
    'Só o dono ou gerente pode corrigir um pagamento em uma comanda fechada.',
  'Tab has order items that are not DELIVERED or CANCELLED yet.': 'A comanda tem itens que ainda não foram entregues ou cancelados.',
  'Cannot cancel a tab that already has orders.': 'Não é possível cancelar uma comanda que já tem pedidos.',
  'Service charge percentage must be between 0 and 100.': 'A taxa de serviço precisa estar entre 0 e 100%.',
  'Phone and vehicle type are required for a courier.': 'Telefone e tipo de veículo são obrigatórios para um entregador.',
  'You cannot change your own role or activation status.': 'Você não pode alterar sua própria função ou status de ativação.',
  'You do not have permission to manage this user.': 'Você não tem permissão para gerenciar esse usuário.',
  "Cannot deactivate the restaurant's only active owner.": 'Não é possível desativar o único dono ativo do restaurante.',
  'Use the change-password flow to update your own password.': 'Use o fluxo de troca de senha pra atualizar sua própria senha.',
  "Cannot reset an owner's password from the staff panel.": 'Não é possível redefinir a senha de um dono pelo painel de equipe.',
  'Table is not active.': 'A mesa não está ativa.',
  'Table is not available for self-ordering right now. Please call a waiter.': 'A mesa não está disponível para autoatendimento agora. Chame um garçom.',
  'This product has no modifiers to select.': 'Esse produto não tem opções para escolher.',
  'Selected modifier option not found for this product.': 'A opção selecionada não foi encontrada para esse produto.',
}

// Backend messages that interpolate a name, status or amount, matched in declaration order - the
// first pattern whose regex matches wins.
const PATTERN_TRANSLATIONS: { pattern: RegExp; translate: (...groups: string[]) => string }[] = [
  { pattern: /^Product (.+) is not a combo\.$/, translate: (name) => `O produto "${name}" não é um combo.` },
  { pattern: /^Product (.+) is not active\.$/, translate: (name) => `O produto "${name}" não está ativo.` },
  { pattern: /^Product (.+) is not available at this time\.$/, translate: (name) => `O produto "${name}" não está disponível nesse horário.` },
  { pattern: /^Table (.+) is not active\.$/, translate: (number) => `A mesa ${number} não está ativa.` },
  { pattern: /^Table (.+) is not free\.$/, translate: (number) => `A mesa ${number} não está livre.` },
  {
    pattern: /^Cannot change delivery status from (.+) to (.+)\.$/,
    translate: (from, to) => `Não é possível mudar o status da entrega de ${from} para ${to}.`,
  },
  { pattern: /^Cannot cancel a delivery that is already (.+)\.$/, translate: (status) => `Não é possível cancelar uma entrega que já está ${status}.` },
  { pattern: /^Cannot change status from (.+) to (.+)\.$/, translate: (from, to) => `Não é possível mudar o status de ${from} para ${to}.` },
  { pattern: /^Role (.+) is not allowed to perform this status change\.$/, translate: (role) => `A função ${role} não pode fazer essa mudança de status.` },
  {
    pattern: /^You do not have permission to create a user with role (.+)\.$/,
    translate: (role) => `Você não tem permissão para criar um usuário com a função ${role}.`,
  },
  { pattern: /^You do not have permission to assign role (.+)\.$/, translate: (role) => `Você não tem permissão para atribuir a função ${role}.` },
  { pattern: /^Group (.+) allows only one option\.$/, translate: (group) => `O grupo ${group} permite apenas uma opção.` },
  { pattern: /^Select an option for (.+)\.$/, translate: (group) => `Selecione uma opção para ${group}.` },
  {
    pattern: /^Requested amount exceeds the tab's remaining uncommitted balance of (-?\d+(?:\.\d+)?)$/,
    translate: (remaining) => `O valor solicitado é maior que o saldo disponível da comanda (R$ ${Number(remaining).toFixed(2).replace('.', ',')}).`,
  },
  {
    pattern: /^Payment amount exceeds the remaining balance of (-?\d+(?:\.\d+)?)$/,
    translate: (remaining) => `O valor do pagamento é maior que o saldo restante (R$ ${Number(remaining).toFixed(2).replace('.', ',')}).`,
  },
]

/** Same lookup translateApiError uses, exposed directly for backend text that doesn't arrive as an
 * axios error - e.g. a per-row reason inside an otherwise-successful response, like the menu
 * import's skipped-product list. An exact or pattern match translates it; anything already in
 * Portuguese (or an English message this list doesn't know about yet) passes through unchanged
 * rather than being hidden behind a generic string. */
export function translateBackendMessage(message: string): string {
  const exact = EXACT_TRANSLATIONS[message]
  if (exact) return exact

  for (const { pattern, translate } of PATTERN_TRANSLATIONS) {
    const match = message.match(pattern)
    if (match) return translate(...match.slice(1))
  }

  return message
}

/** Turns a raw axios error into Portuguese text: an exact or pattern match from a known backend
 * message translates it, and anything already in Portuguese (or an English message this list
 * doesn't know about yet) passes through unchanged rather than being hidden behind a generic
 * string. */
export function translateApiError(err: unknown, fallback: string): string {
  const message = extractBackendMessage(err)
  if (!message) return fallback

  return translateBackendMessage(message)
}
