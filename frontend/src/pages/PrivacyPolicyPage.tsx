import { LegalNotice, LegalPageLayout, LegalSection } from './legal/LegalPageLayout'

export function PrivacyPolicyPage() {
  return (
    <LegalPageLayout title="Política de Privacidade" lastUpdated="agosto de 2026">
      <LegalNotice>
        Este documento é uma minuta e ainda não foi revisado por um advogado. O prazo de retenção de dados (seção 3)
        ainda precisa ser definido.
      </LegalNotice>

      <LegalSection title="">
        <p>
          Esta política descreve como o <strong>Morá</strong> trata dados pessoais ao operar o sistema de gestão
          usado pelo restaurante que você está visitando (pelo cardápio digital, QR Code da mesa, ou reserva online).
        </p>
      </LegalSection>

      <LegalSection title="1. Quem é o controlador e quem é o operador">
        <p>
          Pela LGPD (Lei 13.709/2018), existem dois papéis diferentes aqui: o <strong>restaurante</strong>{' '}
          (identificado no cardápio/reserva que você acessou) é o <strong>controlador</strong> — é quem decide coletar
          seus dados e para quê usá-los. O <strong>Morá</strong> é o <strong>operador</strong>: fornecemos o software
          e a infraestrutura que processam esse dado em nome do restaurante, mas não decidimos a finalidade da coleta
          nem usamos seus dados para fins próprios (marketing, revenda, etc).
        </p>
        <p>
          Se você quer exercer algum direito sobre seus dados, o primeiro contato é o próprio restaurante. Se
          preferir falar direto com o Morá, veja a seção 7.
        </p>
      </LegalSection>

      <LegalSection title="2. Quais dados coletamos e para quê">
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left text-xs">
            <thead>
              <tr className="border-b border-gray-200 dark:border-stone-800">
                <th className="py-2 pr-3 font-semibold text-gray-700 dark:text-stone-300">Onde</th>
                <th className="py-2 pr-3 font-semibold text-gray-700 dark:text-stone-300">Dado coletado</th>
                <th className="py-2 pr-3 font-semibold text-gray-700 dark:text-stone-300">Para quê</th>
                <th className="py-2 font-semibold text-gray-700 dark:text-stone-300">Obrigatório?</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-stone-800/70">
              <tr>
                <td className="py-2 pr-3 align-top">Reserva de mesa</td>
                <td className="py-2 pr-3 align-top">
                  Nome, telefone, quantidade de pessoas, horário desejado, observação opcional
                </td>
                <td className="py-2 pr-3 align-top">Confirmar a reserva, alocar mesa(s), avisar o restaurante</td>
                <td className="py-2 align-top">Sim, pra concluir a reserva</td>
              </tr>
              <tr>
                <td className="py-2 pr-3 align-top">Cardápio digital / pedido pela mesa</td>
                <td className="py-2 pr-3 align-top">
                  Nenhum dado pessoal — o acesso é feito por um token vinculado à mesa/comanda, não a uma conta de
                  cliente
                </td>
                <td className="py-2 pr-3 align-top">Mostrar cardápio, permitir pedido, acompanhar status</td>
                <td className="py-2 align-top">—</td>
              </tr>
              <tr>
                <td className="py-2 pr-3 align-top">Avaliação pós-refeição</td>
                <td className="py-2 pr-3 align-top">
                  Nota (1–5) e comentário opcional — sem nome ou contato
                </td>
                <td className="py-2 pr-3 align-top">Feedback pro restaurante sobre a experiência</td>
                <td className="py-2 align-top">Não (pode fechar sem avaliar)</td>
              </tr>
              <tr>
                <td className="py-2 pr-3 align-top">Chamar garçom / pedir a conta</td>
                <td className="py-2 pr-3 align-top">Nenhum dado pessoal</td>
                <td className="py-2 pr-3 align-top">Notificar a equipe do restaurante</td>
                <td className="py-2 align-top">—</td>
              </tr>
              <tr>
                <td className="py-2 pr-3 align-top">Cupom de desconto</td>
                <td className="py-2 pr-3 align-top">Nenhum cadastro de cliente hoje</td>
                <td className="py-2 pr-3 align-top">Aplicar desconto</td>
                <td className="py-2 align-top">—</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>
          Dados de quem trabalha no restaurante (dono, gerente, garçom, cozinha, caixa) — nome, email, senha — são
          tratados à parte, cobertos pelo contrato entre o restaurante e o Morá, não por esta política voltada ao
          cliente final.
        </p>
      </LegalSection>

      <LegalSection title="3. Por quanto tempo guardamos">
        <p>
          Hoje o sistema não exclui automaticamente dados de reserva ou avaliação após um prazo — eles ficam retidos
          enquanto o restaurante mantiver a conta ativa no Morá, da mesma forma que ficariam num caderno de reservas
          físico.
        </p>
      </LegalSection>

      <LegalSection title="4. Com quem compartilhamos">
        <p>
          Não vendemos nem compartilhamos dados para fins comerciais de terceiros. Usamos os seguintes prestadores de
          serviço como parte da infraestrutura (sub-operadores): hospedagem da aplicação e banco de dados (Render e
          Neon), armazenamento de backup do banco (Supabase Storage, bucket privado), e envio de email transacional
          para recuperação de senha e verificação de cadastro (Brevo).
        </p>
      </LegalSection>

      <LegalSection title="5. Segurança">
        <ul className="list-disc space-y-1 pl-5">
          <li>Senhas de usuários da equipe são armazenadas com hash, nunca em texto puro.</li>
          <li>Toda comunicação com o sistema é feita via HTTPS.</li>
          <li>
            O acesso de cliente final ao cardápio/reserva usa tokens de uso restrito à mesa/comanda ou à reserva
            específica, não uma conta pessoal.
          </li>
        </ul>
      </LegalSection>

      <LegalSection title="6. Cookies e rastreamento">
        <p>
          O Morá não usa cookies de rastreamento nem ferramentas de analytics de terceiros. A equipe do restaurante
          que usa o painel administrativo tem sua sessão de login guardada localmente no navegador, não em cookie, e
          apenas para manter o login ativo.
        </p>
      </LegalSection>

      <LegalSection title="7. Seus direitos (LGPD art. 18)">
        <p>
          Você pode solicitar, a qualquer momento: confirmação de que tratamos seu dado, acesso a ele, correção de
          dado incompleto/desatualizado, anonimização ou eliminação, portabilidade, e revogação de consentimento.
          Pra dados de reserva/avaliação, contate diretamente o restaurante. Pra dúvidas sobre como o Morá processa
          esses dados como operador, escreva para <span className="italic">[email de contato do Morá]</span>.
        </p>
      </LegalSection>

      <LegalSection title="8. Alterações">
        <p>
          Podemos atualizar esta política conforme o produto evolui. A data da última atualização fica sempre no
          topo desta página.
        </p>
      </LegalSection>
    </LegalPageLayout>
  )
}
