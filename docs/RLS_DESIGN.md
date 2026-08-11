# Row-Level Security (Camada 3) — desenho pra quando formos implementar

> **Status (2026-08-06): implementado local/dev.** Os 3 achados abaixo (segunda role, 6 controllers públicos, token de reserva) foram confirmados e resolvidos, mais 2 achados novos durante a implementação — ver `docs/SCOPE.md` (Prioridade 4) pro resumo do que foi feito e `git log` pros detalhes (migrations `V45`–`V52`).
>
> **Status (2026-08-11): ativo em produção também**, com um achado novo e sério corrigido no caminho — ver "Achado 4 — pooler do Neon quebra o `set_config` de sessão" logo abaixo. As migrations já tinham rodado em produção há alguns dias (aplicadas em ordem junto das demais, confirmado pelo commit `a79f76d`) e as credenciais de `APP_DATASOURCE_RUNTIME_*` já estavam preenchidas no Render apontando pro endpoint **pooled** do Neon — ou seja, o Hibernate de produção já vinha rodando pela role restrita, sujeita a RLS, através de um caminho nunca testado. Corrigido trocando pro endpoint **direto** (sem `-pooler`) e rotacionando a senha da role (o valor hardcoded na migration `V45` nunca deveria ter chegado a produção). Validado com teste de isolamento cruzado direto contra a API de produção (duas contas reais, tentando acessar produto/categoria uma da outra por id) — rejeitado de forma consistente em 5 rodadas seguidas, sem flakiness.

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

## Achado 4 — pooler do Neon quebra o `set_config` de sessão

`TenantAwareDataSource` (ver Plano, passo 3, embora tenha sido implementado como `DelegatingDataSource` em vez de `Interceptor` — ver a classe pra mais detalhes) informa o tenant ao Postgres com `set_config('app.tenant_id', ..., false)` — a forma de **sessão**, não a de transação — chamada uma vez a cada conexão física obtida do pool. Isso funciona perfeitamente contra Postgres direto (é o que roda local/dev e o que a suíte de 470 testes valida), mas depende de uma premissa: a conexão que recebe o `set_config` é a mesma conexão que vai rodar as queries seguintes.

Essa premissa quebra no endpoint **pooled** do Neon (sufixo `-pooler`, PgBouncer em modo transação): a cada transação, o pooler pode reatribuir o cliente a um backend físico diferente, sem avisar — o `set_config` feito numa transação não necessariamente sobrevive pra próxima. Como a policy do RLS falha fechado (`NULLIF(current_setting(...), '')::uuid` vira `NULL` quando a variável não chegou, e `restaurant_id = NULL` nunca é verdadeiro), o sintoma mais provável seria dado voltando vazio pro tenant autenticado — mas o cenário pior (sobra de sessão de um cliente vazando pra outro, se o pooler não reseta o estado entre reatribuições) não pode ser descartado sem testar.

**Descoberto em 2026-08-11**, não durante a implementação original (2026-08-06) — na época a Camada 3 foi propositalmente restrita a local/dev, então esse descompasso nunca foi exercitado. Só apareceu ao revisar o estado real de produção antes de "ativar" o que já parecia estar pendente, e achar que as credenciais de `APP_DATASOURCE_RUNTIME_*` já tinham sido preenchidas no Render (não documentado em lugar nenhum) apontando pro pooled.

**Correção**: usar o endpoint **direto** do Neon (sem `-pooler`) só pro datasource do Hibernate (`APP_DATASOURCE_RUNTIME_URL`). A role dona (`SPRING_DATASOURCE_*`, usada por Flyway e pelo `pg_dump` do backup) continua no pooled sem problema — ela ignora RLS de qualquer forma, então a persistência de `app.tenant_id` nunca importou pra ela. Nenhuma mudança de código: o mecanismo já era correto, só o endpoint errado.

**Por que isso não tem teste automatizado ainda**: reproduzir o comportamento de um pooler em modo transação localmente exigiria rodar um PgBouncer de verdade no `docker-compose.yml` (Postgres sozinho, hoje, não passa por nenhum proxy) — escopo maior, não resolvido nesta sessão. A rede de segurança atual pra esse caso específico é só o teste manual em produção (ver abaixo); vale considerar um PgBouncer local no futuro se isso preocupar mais adiante.

**Validação em produção (2026-08-11)**: login via API em duas contas reais (`Teste`/restaurante vazio e `Tatu Bola`/restaurante com dados) contra `https://mora-backend-ubuw.onrender.com`. Confirmado: (1) cada conta lê a própria lista de produtos corretamente; (2) tentar acessar um produto ou categoria da Tatu Bola usando o token da conta `Teste` (por id, mesmo padrão do `CrossTenantIsolationControllerIntegrationTest`) é rejeitado com 400 "not found", nunca vaza o dado; (3) repetido 5 vezes seguidas alternando as duas contas — resultado idêntico em todas, sem intermitência.

## Plano de implementação — todos os passos concluídos

1. [x] Nova role no Neon (produção) + local (docker-compose) + grants (`SELECT/INSERT/UPDATE/DELETE`, sem DDL) + `ALTER DEFAULT PRIVILEGES` pra migrations futuras não esquecerem de conceder acesso à role nova automaticamente. (`V45`, `V46`)
2. [x] Segunda credencial de datasource só pro Hibernate (`APP_DATASOURCE_RUNTIME_*`); Flyway e `BackupService` continuam na role dona atual. (`RuntimeDataSourceConfig`)
3. [x] Propagação de `app.tenant_id` a partir do `TenantContext` — implementado como `TenantAwareDataSource` (`DelegatingDataSource`, `set_config` de sessão a cada conexão obtida do pool) em vez do `Interceptor` originalmente cogitado aqui, mais `TenantActivator` pros ~11 fluxos que descobrem o tenant no meio da própria transação. Ver Achado 4 acima pro ajuste feito em 2026-08-11 (endpoint direto, não pooled, em produção).
4. [x] Migrations de RLS: `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + policy nas ~24 tabelas com `restaurant_id` direto, mais `order_items` via subquery em `orders`. (`V50`, `V51`)
5. [x] Patch nos 6 controllers públicos pra setar `TenantContext` explicitamente (Achado 2).
6. [x] Funções `SECURITY DEFINER` pro lookup de reserva por token e de usuário por email/id (Achado 3, mais o achado extra de login/`findByEmail` descrito no `SCOPE.md`). (`V49`, `V52`)
7. [x] Suíte inteira (470 testes) rodando com o datasource de teste apontando pra role restrita, não pra role dona — 0 falhas.
8. [x] Rollout em produção: migrations aplicadas, credenciais de `APP_DATASOURCE_RUNTIME_*` preenchidas no Render, endpoint corrigido de pooled pra direto e senha rotacionada em 2026-08-11 (Achado 4), validado com teste de isolamento cruzado contra a API real.

## Histórico da decisão de adiar (2026-08-05, revertida em 2026-08-11)

Na sessão original, a decisão foi documentar e não implementar em produção ainda: o escopo descoberto (role nova em produção + patch em 6 fluxos voltados ao cliente final + caso especial de token) parecia bem maior e mais arriscado do que "Camada 3, opcional" sugeria no `SCOPE.md`, e um erro de execução derrubaria features em produção (cardápio digital, autoatendimento, reserva), não só "protegeria menos". Local/dev foi implementado com calma primeiro (2026-08-06), testando cada etapa.

Na prática, as migrations de produção acabaram rodando junto com o resto (aplicadas em ordem, confirmado pelo commit `a79f76d` de 2026-08-10) e as credenciais de `APP_DATASOURCE_RUNTIME_*` foram preenchidas no Render sem isso ficar registrado em lugar nenhum — ou seja, a "sessão dedicada depois" que este documento previa nunca aconteceu como um evento único; a Camada 3 foi ficando ativa em produção aos poucos, sem que o Achado 4 (pooler) fosse percebido a tempo. Retomado em 2026-08-11 ao revisar o estado real antes de "começar" o que já estava, na prática, em andamento.
