import { Link } from 'react-router-dom'
import { LegalNotice, LegalPageLayout, LegalSection } from './legal/LegalPageLayout'

export function TermsOfServicePage() {
  return (
    <LegalPageLayout title="Termos de Uso" lastUpdated="agosto de 2026">
      <LegalNotice>
        Este documento é uma minuta e ainda não foi revisado por um advogado — em especial as seções de nível de
        serviço, limitação de responsabilidade e foro, que ainda têm prazos e valores a definir. Também não há
        CNPJ nem CPF associado ao Morá neste texto ainda; isso enfraquece a exigibilidade jurídica do contrato e
        deve ser corrigido assim que houver MEI/CNPJ.
      </LegalNotice>

      <LegalSection>
        <p>
          Este contrato é celebrado entre <strong>Morá</strong> ("Morá") e o restaurante identificado no cadastro
          ("Contratante"), e rege o uso do sistema Morá de gestão de mesas, comandas e pedidos.
        </p>
      </LegalSection>

      <LegalSection title="1. Objeto">
        <p>
          O Morá fornece ao Contratante acesso ao sistema (painel administrativo, cardápio digital, aplicativo de
          autoatendimento e demais módulos ativos) para uso na operação do salão do restaurante, conforme
          funcionalidades vigentes na data deste contrato ou adicionadas posteriormente.
        </p>
      </LegalSection>

      <LegalSection title="2. Cadastro e responsabilidades do Contratante">
        <ul className="list-disc space-y-1 pl-5">
          <li>
            O Contratante é responsável pela veracidade dos dados cadastrados (razão social, CNPJ, endereço) e pela
            guarda das credenciais de acesso da sua equipe.
          </li>
          <li>
            O Contratante é responsável por conceder a cada funcionário apenas o perfil de acesso compatível com
            sua função.
          </li>
          <li>
            O Contratante é responsável pelas obrigações fiscais da sua operação — o Morá não emite documento fiscal
            e não substitui essa obrigação.
          </li>
        </ul>
      </LegalSection>

      <LegalSection title="3. Papel de cada parte quanto a dados (LGPD)">
        <p>
          Conforme detalhado na <Link to="/privacy" className="text-brand-600 underline dark:text-brand-400">Política de Privacidade</Link>:
          para os dados de clientes finais do Contratante (nome/telefone em reservas, avaliações), o Contratante é o
          controlador e o Morá é o operador, processando esse dado apenas para viabilizar o serviço contratado, nunca
          para finalidade própria. O Contratante é responsável por ter base legal (LGPD) para coletar esses dados dos
          seus clientes.
        </p>
      </LegalSection>

      <LegalSection title="4. Pagamento">
        <p>
          A forma de pagamento, valor e periodicidade são os combinados fora deste sistema — não há cobrança
          automática nem plano self-service dentro do produto nesta fase. Em caso de inadimplência, o Morá pode
          suspender o acesso do Contratante ao sistema após <span className="italic">[X dias]</span> de atraso e
          aviso prévio por email, mantendo os dados armazenados por <span className="italic">[X dias]</span> antes de
          considerar o encerramento da conta.
        </p>
      </LegalSection>

      <LegalSection title="5. Nível de serviço e disponibilidade">
        <p>
          O Morá envida esforços razoáveis para manter o sistema disponível, mas não garante disponibilidade
          contínua (sem SLA formal). O serviço depende de provedores de infraestrutura terceiros e pode sofrer
          indisponibilidade por manutenção, falha de terceiro ou caso fortuito. O Contratante reconhece que, na
          ausência de conexão com a internet ou de disponibilidade do sistema, a operação do salão pode precisar de
          um processo manual de contingência até o restabelecimento do serviço.
        </p>
      </LegalSection>

      <LegalSection title="6. Limitação de responsabilidade">
        <p>
          O Morá não se responsabiliza por lucros cessantes, perda de vendas ou danos indiretos decorrentes de
          indisponibilidade do sistema, erro de configuração feito pelo próprio Contratante, ou decisão comercial do
          Contratante. A responsabilidade total do Morá perante o Contratante, em qualquer hipótese, fica limitada ao
          valor pago pelo Contratante nos últimos <span className="italic">[3/6]</span> meses.
        </p>
      </LegalSection>

      <LegalSection title="7. Backup e dados">
        <p>
          O Morá mantém rotina diária de backup do banco de dados, mas isso não substitui a recomendação de o
          Contratante exportar periodicamente seus próprios relatórios para registro próprio. Os dados operacionais
          inseridos pelo Contratante pertencem ao Contratante. Ao encerrar o contrato, o Contratante pode solicitar
          exportação dos seus dados em até <span className="italic">[X dias]</span>.
        </p>
      </LegalSection>

      <LegalSection title="8. Rescisão">
        <p>
          Qualquer parte pode encerrar este contrato mediante aviso prévio de <span className="italic">[X dias]</span>.
          Após o encerramento, os dados do Contratante ficam retidos por <span className="italic">[X dias]</span>{' '}
          (para eventual reativação) e depois são excluídos, salvo obrigação legal de retenção.
        </p>
      </LegalSection>

      <LegalSection title="9. Alterações, foro e lei aplicável">
        <p>
          O Morá pode atualizar estes termos, avisando o Contratante com <span className="italic">[X dias]</span> de
          antecedência para mudanças que afetem materialmente o serviço. Este contrato é regido pelas leis da
          República Federativa do Brasil, e as partes elegem o foro da comarca de{' '}
          <span className="italic">[cidade/UF]</span> para dirimir eventuais controvérsias.
        </p>
      </LegalSection>

      <LegalSection title="10. Aceite">
        <p>
          O aceite deste contrato ocorre no cadastro do restaurante no sistema, por meio de confirmação explícita,
          registrada com identificação do usuário e data/hora.
        </p>
      </LegalSection>
    </LegalPageLayout>
  )
}
