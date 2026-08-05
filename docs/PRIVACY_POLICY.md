# Política de Privacidade — Morá

> **Status: minuta / rascunho.** Reflete com precisão o que o sistema coleta e faz hoje (verificado no código em 2026-08-05), mas os campos entre `[colchetes]` precisam ser preenchidos e o texto revisado antes de publicar. Baixo risco jurídico comparado ao contrato B2B (não envolve dinheiro nem limitação de responsabilidade), mas ainda recomendo uma leitura por advogado antes do primeiro cliente real usar o cardápio digital.

Última atualização: [DATA]

Esta política descreve como o **Morá** trata dados pessoais ao operar o sistema de gestão usado pelo restaurante que você está visitando (pelo cardápio digital, QR Code da mesa, ou reserva online).

## 1. Quem é o controlador e quem é o operador

Pela LGPD (Lei 13.709/2018), existem dois papéis diferentes aqui:

- **O restaurante** (identificado no cardápio/reserva que você acessou) é o **controlador**: é quem decide coletar seus dados (por exemplo, seu telefone numa reserva) e para quê usá-los.
- **O Morá** é o **operador**: fornecemos o software e a infraestrutura que processam esse dado em nome do restaurante, mas não decidimos a finalidade da coleta nem usamos seus dados para fins próprios (marketing, revenda, etc.).

Se você quer exercer algum direito sobre seus dados, o primeiro contato é o próprio restaurante. Se preferir falar direto com o Morá, veja a seção 7.

## 2. Quais dados coletamos e para quê

| Onde | Dado coletado | Para quê | Obrigatório? |
|---|---|---|---|
| Reserva de mesa (online ou por telefone) | Nome, telefone, quantidade de pessoas, horário desejado, observação opcional | Confirmar a reserva, alocar mesa(s), avisar o restaurante | Sim, pra concluir a reserva |
| Cardápio digital / pedido pela mesa | Nenhum dado pessoal — o acesso é feito por um token vinculado à mesa/comanda, não a uma conta de cliente | Mostrar cardápio, permitir pedido, acompanhar status | — |
| Avaliação pós-refeição | Nota (1–5) e comentário opcional — **sem nome ou contato** | Feedback pro restaurante sobre a experiência | Não (pode fechar sem avaliar) |
| Chamar garçom / pedir a conta pelo celular | Nenhum dado pessoal | Notificar a equipe do restaurante | — |
| Cupom de desconto | Nenhum cadastro de cliente hoje — o código não é vinculado a uma pessoa | Aplicar desconto | — |

Dados de **quem trabalha no restaurante** (dono, gerente, garçom, cozinha, caixa) — nome, email, senha — são tratados à parte, cobertos pelo contrato entre o restaurante e o Morá, não por esta política voltada ao cliente final.

## 3. Por quanto tempo guardamos

Hoje o sistema **não exclui automaticamente** dados de reserva ou avaliação após um prazo — eles ficam retidos enquanto o restaurante mantiver a conta ativa no Morá, da mesma forma que ficariam num caderno de reservas físico. [Definir e publicar aqui um prazo de retenção quando essa política for criada — ex: reservas e avaliações são mantidas por X anos após a data do evento, ou até o restaurante solicitar exclusão.]

## 4. Com quem compartilhamos

Não vendemos nem compartilhamos dados para fins comerciais de terceiros. Usamos os seguintes prestadores de serviço como parte da infraestrutura (sub-operadores):

- **Hospedagem da aplicação e banco de dados** — Render e Neon (infraestrutura em nuvem).
- **Armazenamento de backup do banco** — Supabase Storage (bucket privado).
- **Envio de email transacional** (recuperação de senha, verificação de cadastro do restaurante) — Brevo.

Esses provedores têm acesso técnico aos dados como parte de operar o serviço, não para uso próprio.

## 5. Segurança

- Senhas de usuários da equipe são armazenadas com hash, nunca em texto puro.
- Toda comunicação com o sistema é feita via HTTPS.
- O acesso de cliente final ao cardápio/reserva usa tokens de uso restrito à mesa/comanda ou à reserva específica, não uma conta pessoal.

## 6. Cookies e rastreamento

O Morá **não usa cookies de rastreamento nem ferramentas de analytics de terceiros** (Google Analytics ou similares). A equipe do restaurante que usa o painel administrativo tem sua sessão de login guardada localmente no navegador (`localStorage`), não em cookie, e apenas para manter o login ativo.

## 7. Seus direitos (LGPD art. 18)

Você pode solicitar, a qualquer momento: confirmação de que tratamos seu dado, acesso a ele, correção de dado incompleto/desatualizado, anonimização ou eliminação, portabilidade, e revogação de consentimento (por exemplo, pedir a remoção do seu telefone de uma reserva já concluída).

- Pra dados de reserva/avaliação: contate diretamente o restaurante.
- Pra dúvidas sobre como o Morá processa esses dados como operador: [email de contato do Morá].

## 8. Alterações

Podemos atualizar esta política conforme o produto evolui. A data da última atualização fica sempre no topo desta página.
