# Termos de Uso / Contrato de Prestação de Serviço — Morá

> **MINUTA — NÃO USAR SEM REVISÃO DE ADVOGADO.** Este documento envolve dinheiro (forma de pagamento, inadimplência) e limitação de responsabilidade real. Estruturei as cláusulas que, na minha avaliação, esse tipo de contrato SaaS B2B costuma precisar, com base em como o sistema funciona hoje — mas não é aconselhamento jurídico e não deve ser usado com o primeiro cliente pagante sem um advogado revisar (principalmente as seções 5, 6 e 9). Campos entre `[colchetes]` são placeholders a preencher.
>
> **Identificação do contratado em aberto.** Não há CNPJ nem CPF associado a "Morá" neste texto por decisão do responsável pelo produto (2026-08-05, ainda sem MEI/CNPJ constituído e sem uso do CPF pessoal aqui). Isso é uma limitação real: sem um identificador fiscal, este contrato tem exigibilidade jurídica mais fraca — não amarra o compromisso a uma pessoa ou empresa específica perante a lei. Aceitável como estágio temporário, mas deve ser corrigido assim que houver MEI/CNPJ.

Última atualização: [DATA]

Este contrato é celebrado entre **Morá** ("Morá") e o restaurante identificado no cadastro ("Contratante"), e rege o uso do sistema Morá de gestão de mesas, comandas e pedidos.

## 1. Objeto

O Morá fornece ao Contratante acesso ao sistema (painel administrativo, cardápio digital, aplicativo de autoatendimento e demais módulos ativos) para uso na operação do salão do restaurante, conforme funcionalidades vigentes na data deste contrato ou adicionadas posteriormente.

## 2. Cadastro e responsabilidades do Contratante

- O Contratante é responsável pela veracidade dos dados cadastrados (razão social, CNPJ, endereço) e pela guarda das credenciais de acesso da sua equipe.
- O Contratante é responsável por conceder a cada funcionário apenas o perfil de acesso (`OWNER`, `MANAGER`, `WAITER`, `KITCHEN`, `CASHIER`) compatível com sua função.
- O Contratante é responsável pelas obrigações fiscais da sua operação (emissão de nota fiscal, cumprimento de legislação do seu setor) — o Morá não emite documento fiscal e não substitui essa obrigação.

## 3. Papel de cada parte quanto a dados (LGPD)

Conforme detalhado na [Política de Privacidade](PRIVACY_POLICY.md): para os dados de clientes finais do Contratante (nome/telefone em reservas, avaliações), o **Contratante é o controlador** e o **Morá é o operador**, processando esse dado apenas para viabilizar o serviço contratado, nunca para finalidade própria. O Contratante é responsável por ter base legal (LGPD) para coletar esses dados dos seus clientes.

## 4. Pagamento

- A forma de pagamento, valor e periodicidade são os combinados fora deste sistema (contrato comercial, Pix ou boleto acordado diretamente com o Morá) — não há cobrança automática nem plano self-service dentro do produto nesta fase.
- Em caso de inadimplência, o Morá pode suspender o acesso do Contratante ao sistema após [X dias de atraso e aviso prévio por email], mantendo os dados armazenados por [X dias] antes de considerar o encerramento da conta.

## 5. Nível de serviço e disponibilidade

- O Morá envida esforços razoáveis para manter o sistema disponível, mas **não garante disponibilidade contínua (sem SLA formal)**. O serviço depende de provedores de infraestrutura terceiros (hospedagem, banco de dados) e pode sofrer indisponibilidade por manutenção, falha de terceiro ou caso fortuito.
- O Contratante reconhece que, na ausência de conexão com a internet ou de disponibilidade do sistema, a operação do salão pode precisar de um processo manual de contingência (papel/caneta) até o restabelecimento do serviço.

## 6. Limitação de responsabilidade

- O Morá não se responsabiliza por lucros cessantes, perda de vendas ou danos indiretos decorrentes de indisponibilidade do sistema, erro de configuração feito pelo próprio Contratante (ex: preço de produto cadastrado errado), ou decisão comercial do Contratante (ex: precificação, promoções).
- A responsabilidade total do Morá perante o Contratante, em qualquer hipótese, fica limitada ao valor pago pelo Contratante nos últimos [3/6] meses.

## 7. Backup e dados

- O Morá mantém rotina diária de backup do banco de dados (ver `docs/BACKUP_RESTORE.md`), mas isso **não substitui** a recomendação de o Contratante exportar periodicamente seus próprios relatórios (financeiro, cardápio) para registro próprio.
- Os dados operacionais inseridos pelo Contratante (cardápio, comandas, relatórios) pertencem ao Contratante. Ao encerrar o contrato, o Contratante pode solicitar exportação dos seus dados em formato [a definir] em até [X dias].

## 8. Rescisão

- Qualquer parte pode encerrar este contrato mediante aviso prévio de [X dias].
- Após o encerramento, os dados do Contratante ficam retidos por [X dias] (para eventual reativação) e depois são excluídos, salvo obrigação legal de retenção.

## 9. Alterações, foro e lei aplicável

- O Morá pode atualizar estes termos, avisando o Contratante com [X dias] de antecedência para mudanças que afetem materialmente o serviço.
- Este contrato é regido pelas leis da República Federativa do Brasil, e as partes elegem o foro da comarca de [cidade/UF] para dirimir eventuais controvérsias.

## 10. Aceite

O aceite deste contrato ocorre no cadastro do restaurante no sistema, por meio de confirmação explícita (`li e aceito os termos de uso`), registrada com identificação do usuário e data/hora.
