# Row-Level Security (Camada 3) — desenho pra quando formos implementar

> **Status (2026-08-06): implementado local/dev.** Os 3 achados abaixo (segunda role, 6 controllers públicos, token de reserva) foram confirmados e resolvidos, mais 2 achados novos durante a implementação — ver `docs/SCOPE.md` (Prioridade 4) pro resumo do que foi feito e `git log` pros detalhes (migrations `V45`–`V52`). Produção (Neon) segue pendente, plano no passo 8 abaixo continua válido pra quando for a hora.

Investigação feita em 2026-08-05, antes de qualquer código, puxada pela pergunta "vamos focar na segurança da aplicação e dos dados". Este arquivo existe pra não perder o raciocínio entre agora e o dia em que isso entrar de fato — a conclusão da investigação foi que o escopo real é bem maior do que "adicionar umas migrations", e mexe em fluxos voltados ao cliente final (cardápio digital, autoatendimento, reserva), então não faz sentido implementar sem planejar com calma.

## Contexto: onde isso se encaixa

`docs/SCOPE.md`, Prioridade 4, já tem duas camadas de isolamento multi-tenant resolvidas em 2026-08-05:
- **Camada 1** — `CrossTenantIsolationControllerIntegrationTest`: 14 testes batendo em endpoints com o token do restaurante errado.
- **Camada 2** — filtro global do Hibernate (`@Filter(name = "tenantFilter")`) em 24 entidades, ativado por `TenantFilterInterceptor` a partir do `TenantContext` (populado pelo `JwtAuthenticationFilter` quando há JWT válido).

Camada 3 (RLS de verdade no Postgres) ficou marcada como opcional — "defesa que sobrevive até a um bug no código Java", "só compensa quando o volume de clientes pagantes simultâneos justificar". A pergunta de retomar isso agora veio de "o que falta pra vender com segurança", não de um incidente real.

## Achado 1 — conflito com o backup

Hoje só existe **um usuário Postgres** (`SPRING_DATASOURCE_USERNAME`), usado tanto pelo Hibernate quanto pelo `pg_dump` do backup (`BackupService.buildConnectionUri` reaproveita literalmente a mesma URL/usuário/senha do datasource, ver `BackupService.java:84-93,96`).

Isso importa porque no Postgres:
- O **dono da tabela sempre ignora RLS**, a menos que a tabela tenha `FORCE ROW LEVEL SECURITY`.
- Sem `FORCE`: como esse usuário é dono das tabelas, o RLS não protegeria nada na aplicação — falsa sensação de segurança.
- Com `FORCE`: protege a aplicação, mas o `pg_dump` passa pelo mesmo usuário sem nenhuma sessão de tenant configurada → cada tabela protegida por RLS voltaria **zero linhas** no dump. O backup ficaria vazio silenciosamente a partir do dia em que isso for ligado.

**Decisão (2026-08-05):** criar uma **segunda role Postgres, só de runtime** (sem ser dona de tabela, sem DDL), pro Hibernate usar no dia a dia — sujeita a RLS de verdade. A role atual (dona) continua sendo usada só pelo Flyway (migrations) e pelo `pg_dump` (backup), contornando RLS normalmente, como hoje.

## Achado 2 — fluxos públicos nunca populam o TenantContext

A nova role de runtime seria usada por **toda** query do Hibernate, inclusive nos 6 controllers públicos que hoje funcionam sem JWT:
- `MenuController`
- `PublicReservationController`
- `PublicCouponController`
- `PublicFeedbackController`
- `PublicTableRequestController`
- `PublicOrderController`

`JwtAuthenticationFilter.java:69-71` só chama `TenantContext.setCurrentTenant(restaurantId)` quando há JWT válido. Sem JWT, `TenantContext.getCurrentTenant()` é sempre `null`, e `TenantFilterInterceptor.java:32-36` não ativa o filtro do Hibernate nesse caso — esses fluxos hoje dependem só da Camada 1 (convenção + testes), nunca da Camada 2.

Se o RLS (Camada 3) usar a mesma variável de sessão (`app.tenant_id`) e ela nunca for setada nesses 6 fluxos, toda tabela protegida vira zero linhas pra eles — **cardápio digital, autoatendimento, reserva por token, cupom e "chamar garçom" quebrariam em produção**, não é uma questão de ficarem "menos protegidos", é queda total dessas features.

**Correção necessária:** cada um dos 6 controllers precisa resolver o `restaurantId` logo no início (a maioria já faz isso, olhando slug/mesa/comanda) e chamar `TenantContext.setCurrentTenant(id)` antes de tocar qualquer tabela protegida — replicando o que o `JwtAuthenticationFilter` já faz pro fluxo autenticado, com `clear()` num `finally`.

## Achado 3 — o caso ovo-e-galinha do token de reserva

`ReservationRepository.findByAccessToken` busca a reserva **pelo token secreto, sem saber ainda de qual restaurante é** — não dá pra exigir `restaurant_id` numa query cujo objetivo é justamente descobrir o `restaurant_id`. Isso não é um esquecimento, é a natureza do link de acesso (token opaco, funciona pra qualquer restaurante por design, sem vazar dado de outro porque o token não é adivinhável).

Uma policy comum de RLS não cobre esse caso. Precisa de uma exceção deliberada e estreita — candidato: função Postgres `SECURITY DEFINER` que ignora RLS só pra essa consulta específica (busca por token), sem abrir uma brecha genérica na tabela `reservations` inteira. Vale conferir se `TableRequest`/`PostMealFeedback` têm o mesmo padrão de busca por token/código antes de implementar (não confirmado nesta investigação).

## Plano de implementação (quando for a hora)

1. Nova role no Neon (produção) + local (docker-compose) + grants (`SELECT/INSERT/UPDATE/DELETE`, sem DDL) + `ALTER DEFAULT PRIVILEGES` pra migrations futuras não esquecerem de conceder acesso à role nova automaticamente.
2. Segunda credencial de datasource só pro Hibernate (`SPRING_DATASOURCE_RUNTIME_*` ou similar); Flyway e `BackupService` continuam na role dona atual.
3. Interceptor do Hibernate (`Interceptor.afterTransactionBegin`) setando `app.tenant_id` via `set_config(..., true)` a partir do `TenantContext` — mesma fonte que a Camada 2 já usa.
4. Migrations de RLS: `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + policy nas ~24 tabelas com `restaurant_id` direto, mais `order_items` via subquery em `orders` (mesmo tratamento que a Camada 2 já dá a essa tabela).
5. Patch nos 6 controllers públicos pra setar `TenantContext` explicitamente (Achado 2).
6. Função `SECURITY DEFINER` (ou equivalente) pro lookup de reserva por token, e conferir se `TableRequest`/`PostMealFeedback` precisam do mesmo tratamento (Achado 3).
7. Rodar a suíte inteira (453+ testes na Sprint mais recente) com o datasource de teste apontando pra role restrita — não pra role dona — senão os testes não validam o RLS de verdade, só a Camada 2. Corrigir o que quebrar.
8. Rollout em produção em ordem segura: criar role + grants no Neon **antes** de ativar `FORCE ROW LEVEL SECURITY`, pra nunca ter uma janela em que a role de runtime existe mas não tem acesso a nada.

## Por que não implementar agora

Decisão explícita do usuário (2026-08-05): documentar e não implementar nesta sessão. Motivo — o escopo descoberto (role nova em produção + patch em 6 fluxos voltados ao cliente final + caso especial de token) é bem maior e mais arriscado do que "Camada 3, opcional" sugeria no `SCOPE.md`, e um erro de execução derruba features em produção (cardápio digital, autoatendimento, reserva), não só "protege menos". Melhor implementar com calma, num momento dedicado, testando cada etapa antes de seguir pra próxima — mesmo padrão já usado pra "Gestão de clientes pagantes" no `SCOPE.md`.
