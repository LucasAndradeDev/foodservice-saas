# Armazém Morá — plano da plataforma de estoque

Discussão feita em 2026-08-11, antes de qualquer código, sobre o item 20 do backlog ("Controle de estoque", `docs/SCOPE.md`, Prioridade 7 — "bônus: escopo maior, só depois de validar com clientes reais"). Este arquivo existe pra registrar o raciocínio da arquitetura antes de começar a implementar, no mesmo espírito de `docs/PIX_PAYMENT.md` e `docs/RLS_DESIGN.md`.

## Por que plataforma separada, e não um módulo dentro do Morá

Decisão do usuário: em vez de adicionar "Estoque" como mais um módulo dentro do backend/frontend do Morá (like Produtos, Mesas etc.), o controle de estoque nasce como um produto próprio — **Armazém Morá** — que se conecta ao Morá em vez de viver dentro dele.

Justifica pensar assim porque estoque é um domínio com vida própria (fornecedores, compras, insumos, unidades de medida) que não depende do fluxo de salão pra existir — um restaurante poderia querer gerenciar estoque mesmo sem usar o Morá pra atender mesas. Separar cedo evita acoplar dois domínios que só têm um ponto de contato real (o consumo de insumos gerado pela venda).

## Arquitetura: monorepo, serviço separado

Discutido em 2026-08-11: em vez de repositório novo do zero, o Armazém Morá entra como um **serviço independente dentro do mesmo repositório `restaurant_saas`** — backend e frontend próprios, banco de dados próprio, deploy próprio. Nada de import cruzado entre os dois backends, nada de schema compartilhado.

**Por quê monorepo em vez de repo novo:** reaproveita o esqueleto já validado no Morá (Docker Compose, Flyway, JWT + refresh token, estrutura de testes de integração, `Dockerfile`, `render.yaml`) em vez de reconstruir isso do zero — alinhado com a prioridade de ir rápido (ver `project_backend_speed_priority`). O trade-off é o risco de acoplamento acidental (import cruzado, dependência de schema) — mitigado só por disciplina: nunca compartilhar banco, nunca importar classe de um backend no outro. Migrar pra repositório próprio depois, se fizer sentido (ex: vender/abrir código dos dois separadamente), é mecânico (`git filter-repo`), não exige replanejar nada.

**Estrutura de pastas proposta** (nomes em inglês, seguindo `feedback_code_in_english` — "Armazém Morá" é nome de produto voltado ao usuário, não vira identificador técnico):
```
restaurant_saas/
├── backend/              # Morá (já existe)
├── frontend/             # Morá (já existe)
├── warehouse-backend/    # Armazém Morá — Spring Boot novo, pom.xml próprio
├── warehouse-frontend/   # Armazém Morá — Vite/React novo, package.json próprio
├── docker-compose.yml    # ganha um segundo serviço de banco (warehouse_db)
└── render.yaml           # ganha dois serviços novos (warehouse-backend, warehouse-frontend)
```

Pacote Java sugerido: `com.example.warehouse` (a confirmar na hora). Mesma stack do Morá (Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL) — sem motivo pra divergir de tooling já dominado.

**Banco de dados separado desde o dia 1** — efeito colateral bom: elimina de saída o risco descrito em `project_flyway_migration_numbering_pitfall` (colisão de numeração de migration), porque o Armazém tem sua própria sequência `V1__...`, sem relação com a do Morá.

## Como se conecta ao Morá

Decidido em 2026-08-11: dois mecanismos de conexão bem separados — **acesso do usuário** (SSO) e **sincronização de dados** (automática, servidor-a-servidor). São dois problemas diferentes com dois credenciais diferentes, não devem ser confundidos.

### Acesso do usuário — SSO via sidebar

**Armazém Morá aparece como item na sidebar do Morá, mas funciona como app à parte** (frontend/origem diferente) — decisão do usuário. O dono não loga duas vezes: ao clicar no link, o Morá emite um **token de handoff de vida curtíssima** (~30-60s, uso único, mesmo espírito do token de reset de senha já existente), abre o Armazém Morá numa aba/janela nova passando esse código (`armazem.mora.../sso?code=XYZ`). O Armazém troca o código imediatamente por uma sessão própria; o código nunca é reutilizável.

**Por que não o token de acesso normal na URL:** o access token do Morá dura 24h — colocar ele numa querystring ficaria exposto em histórico do navegador, logs de proxy, referrer headers. O token de handoff é de propósito único, expira em segundos e não serve pra mais nada depois de trocado — reduz a superfície de risco a quase zero mesmo se vazar.

**Segredo dedicado:** os dois backends compartilham um segredo próprio pra isso (`WAREHOUSE_SSO_SECRET`, env var nova nos dois lados), separado do `JWT_SECRET` normal — isola o dano se um dos dois vazar (o SSO secret só forja tokens de handoff de curtíssima duração; não dá acesso à sessão normal do Morá, nem o contrário).

**Restrição de papel:** só `OWNER`/`MANAGER` veem o link e conseguem gerar o handoff — mesmo padrão de acesso já usado pra Relatórios/Configurações.

**Vínculo automático:** o token de handoff carrega o `restaurantId` (e nome do restaurante). Na primeira vez que um restaurante faz handoff, o Armazém cria o `RestaurantLink` sozinho — sem o dono copiar/colar nada manualmente.

**Sessão própria do Armazém:** depois de validar o handoff, o `warehouse-backend` emite seu próprio token de sessão (24h, mesmo tempo de vida do token do Morá), assinado com um terceiro segredo — `WAREHOUSE_JWT_SECRET`, gerado independentemente, nunca compartilhado com o Morá. Três segredos, três propósitos, nenhum reaproveitado: `JWT_SECRET` (sessão normal do Morá), `WAREHOUSE_SSO_SECRET` (compartilhado, só o handoff de ~60s), `WAREHOUSE_JWT_SECRET` (sessão do Armazém, interno a ele). Não existe login por senha no Armazém Morá — a única porta de entrada é trocar um handoff válido.

### Sincronização de dados — automática, a cada 15 minutos

Continua sendo o **Armazém Morá quem puxa dados do Morá** (pull, não webhook) — mais simples de operar, sem fila/retry, sem perder evento se um dos dois estiver fora do ar no momento da venda.

**Mecanismo, espelhando `docs/BACKUP_RESTORE.md`:** como o Render free tier "dorme" serviços sem tráfego HTTP recente, um `@Scheduled` interno no Spring não é confiável — por isso o backup já usa um cron do GitHub Actions batendo num endpoint, em vez de agendar dentro do próprio processo. Sincronização segue o mesmo padrão: workflow do GitHub Actions roda a cada 15 min e chama `POST /internal/sync` no `warehouse-backend`, que por sua vez itera os `RestaurantLink`s ativos e busca as vendas novas de cada um no Morá.

**Credencial pro servidor-a-servidor:** essa chamada do Armazém pro Morá não tem usuário logado por trás (é um cron), então não pode usar o token de handoff. No momento em que o `RestaurantLink` é criado (durante o primeiro SSO handoff), o Morá também gera uma **API key de integração** de vida longa, específica daquele restaurante, devolvida uma vez pro Armazém guardar (hash) — reaproveitando o padrão do `BackupController` (header comparado com `MessageDigest.isEqual`, tempo constante). O dono nunca vê nem copia essa chave — ela é criada e usada só entre os dois backends.

**Endpoint novo no Morá** (`warehouse-backend` chama, autenticado pela API key de integração acima):
```
GET /api/v1/internal/warehouse/sales?since={timestamp}
```
Devolve os itens de pedido **entregues** (`DELIVERED`) desde `since`: `productId`, `productName`, `quantity`, `deliveredAt`. Reaproveita o campo `deliveredAt` que já existe em `OrderItem` (mesmo usado no tempo estimado de espera do cardápio digital) — não precisa de campo novo no Morá pra isso.

**Desvantagem aceita:** baixa de estoque não é instantânea, tem o atraso de até 15 min do ciclo de sincronização — tudo bem pro caso de uso (ninguém precisa ver o saldo de insumo mudar em tempo real enquanto vende). Se isso incomodar na prática, o intervalo baixa sem redesenhar nada.

## Escopo da v1

Decidido em 2026-08-11, os quatro pilares:

1. **Cadastro de insumos + saldo** — a base: insumo (nome, unidade de medida — kg, l, un, etc.), quantidade atual em estoque, por restaurante.
2. **Baixa automática por venda** — ficha técnica (receita) por produto do Morá, mapeando `productId` → lista de `(insumoId, quantidade consumida por unidade vendida)`. A cada sincronização, o Armazém consulta o endpoint acima, explode as vendas em consumo de insumo via a receita, e desconta do saldo.
3. **Compras e fornecedores** — CRUD de fornecedor (nome, contato); registro de compra (fornecedor, data, itens com insumo/quantidade/preço pago) que soma no saldo do insumo — mesmo padrão de "entrada" que uma baixa de venda é "saída".
4. **Alertas de estoque baixo** — limiar configurável por insumo; aviso na tela quando o saldo cai abaixo dele. Sem canal externo (email/WhatsApp) na v1 — só sinalização visual, mesmo escopo mínimo que os outros alertas do Morá tiveram na primeira versão (ex: alerta de demora na cozinha).

## Modelo de dados (rascunho)

Só pra orientar a primeira migration — refinar na hora de implementar:

- **`RestaurantLink`** — `id`, `moraRestaurantId` (o `restaurant_id` do lado do Morá, sem FK real — bancos diferentes), `apiKeyHash` (chave de integração gerada no primeiro handoff, ver acima), `name`, `createdAt`. É o "tenant" do Armazém. Criado automaticamente no primeiro SSO handoff, nunca via cadastro manual.
- **`Ingredient`** (insumo) — `id`, `restaurantLinkId`, `name`, `unit`, `currentQuantity`, `lowStockThreshold`, `active`.
- **`Supplier`** (fornecedor) — `id`, `restaurantLinkId`, `name`, `contact`.
- **`Purchase`** (compra) — `id`, `restaurantLinkId`, `supplierId`, `purchasedAt`; **`PurchaseItem`** — `purchaseId`, `ingredientId`, `quantity`, `unitCost`.
- **`Recipe`** (ficha técnica) — `id`, `restaurantLinkId`, `moraProductId` (sem FK, referência externa), `moraProductName` (snapshot, pra exibir mesmo se o produto mudar de nome no Morá depois); **`RecipeItem`** — `recipeId`, `ingredientId`, `quantityPerUnit`.
- **`SyncState`** — `restaurantLinkId`, `lastSyncedAt`. Guarda o `since` da última chamada bem-sucedida ao endpoint do Morá, pra sincronização incremental.

Autenticação de usuário do Armazém é 100% via SSO (ver seção acima) — não existe cadastro/senha próprio do Armazém Morá.

## Fora de escopo da v1

- Múltiplos depósitos/localizações por restaurante.
- Controle de lote/validade.
- Leitura de código de barras.
- Geração automática de pedido de compra (sugestão de reposição).
- Integração com API de fornecedor.
- Combos: a explosão de combo em itens-filho já existe no Morá (`OrderItem` por componente) — o endpoint de vendas deve devolver os componentes já explodidos, não o combo como uma linha só, senão a ficha técnica do Armazém não bate. Confirmar isso na hora de implementar o endpoint.

## Perguntas em aberto

- **Multi-tenant de verdade no Armazém, ou 1:1 fixo com um restaurante do Morá?** Pra v1, 1:1 basta (um `RestaurantLink` por conta). Suporte a rede de restaurantes (multiunidade) fica pra quando o item 24 do backlog do Morá também for endereçado.
- **Nome de pacote/pastas definitivo** (`warehouse-backend` vs outro nome) — só uma sugestão acima, confirmar antes de gerar a estrutura do projeto.

## Ordem sugerida de implementação

Espelhando como o Morá foi construído (infra primeiro, depois núcleo, depois o que depende de integração):

1. ✅ Infra: `warehouse-backend` novo (Spring Boot, Flyway, Docker), banco próprio.
2. ✅ SSO: endpoint de handoff no Morá (`POST /api/v1/warehouse/handoff`, `WarehouseHandoffTokenService`, `WarehouseIntegrationService` gerando a API key de integração), endpoint de troca no Armazém (`POST /api/v1/auth/sso`, `WarehouseSsoService`), criação/atualização automática de `RestaurantLink` a cada handoff. Testado ponta a ponta com testes de integração dos dois lados (não manualmente pelo navegador ainda). **Falta**: o link de verdade na sidebar do Morá e a tela `/sso` do Armazém que recebe o token — feito só o backend das duas pontas por enquanto.
3. ✅ Insumos — CRUD + saldo (`Ingredient`/`IngredientController`, unidade em texto livre em vez de enum, saldo com 3 casas decimais). `lowStock` computado na resposta (saldo ≤ limiar), sem persistir o booleano. Testado ponta a ponta via SSO real (mint handoff → troca → CRUD), incluindo isolamento entre dois `RestaurantLink`s diferentes.
4. ✅ Fornecedores e compras — CRUD de fornecedor (`Supplier`/`SupplierController`, mesmo padrão de nome único case-insensitive e soft-deactivate via `active` do Insumos) + registro de compra (`Purchase`/`PurchaseItem`/`PurchaseController`), append-only (sem endpoint de update — é um lançamento de "entrada" de estoque, não um cadastro). Cada item da compra soma sua quantidade no saldo do insumo correspondente na mesma transação. Testado via testes de integração (isolamento entre restaurantes, fornecedor/insumo de outro restaurante rejeitado, saldo do insumo confirmado após a compra).
5. ✅ Endpoint novo no Morá (`GET /api/v1/internal/warehouse/sales?since=`), autenticado pela API key de integração já criada no passo 2 via header `X-Warehouse-Api-Key` (mesmo padrão do `X-Backup-Token` do `BackupController` - `/api/v1/internal/**` já é `permitAll` no `SecurityConfig`, a checagem é manual no controller). Devolve itens **já explodidos** (`children IS EMPTY` no JPQL exclui a linha-cabeçalho do combo, mantém os componentes - ver `ComboExplodeService`), com `productId`/`productName`/`quantity`/`deliveredAt`. Resolver o restaurante a partir da API key exigiu uma função SQL `SECURITY DEFINER` nova (`warehouse_integration_by_api_key_hash`, migration V59) - mesmo bypass de RLS "galinha e ovo" do `pix_charge_by_external_id` (V58) e `user_by_email` (V52): a query que descobre o tenant não pode, ela mesma, já estar filtrada por tenant. Testado via teste de integração ponta a ponta (registra restaurante → entrega um item → handoff real pra pegar a API key → chama o endpoint com ela → confere a venda), incluindo isolamento entre restaurantes e rejeição de key ausente/inválida com 401.
6. ✅ Ficha técnica (`Recipe`/`RecipeItem`/`RecipeController`, CRUD - `moraProductId` sem FK, `moraProductName` é snapshot, update substitui a lista de itens inteira) + `MoraApiClient` (chama o endpoint do passo 5 via `RestClient`) + `WarehouseSyncService`/`WarehouseSyncController` (`POST /api/v1/internal/sync`, autenticado por `X-Sync-Token` do mesmo jeito que o `X-Backup-Token` do Morá - `/api/v1/internal/**` já é `permitAll` no `SecurityConfig` do Armazém, checagem manual no controller) + `SyncState` (cursor `lastSyncedAt` por `RestaurantLink`, avança só até o `deliveredAt` mais recente processado, nunca pula à frente do que foi visto) + workflow `.github/workflows/warehouse-sync.yml` (cron a cada 15 min, mesmo padrão do `backup.yml`). Uma venda sem ficha técnica cadastrada é ignorada silenciosamente (não é erro); uma falha ao chamar o Morá pra um restaurante (rede, chave desatualizada) é logada e pulada sem travar a sincronização dos demais, e o cursor daquele restaurante não avança - o próximo ciclo de 15 min tenta de novo do mesmo ponto. Testado via teste de integração (CRUD de receita, isolamento entre restaurantes, baixa de estoque calculada certa, venda sem receita não quebra nada, uma falha isolada não impede os outros restaurantes de sincronizar) e teste do endpoint interno (401 sem token/com token errado, 200 com token certo).
7. ✅ Alertas de estoque baixo — na prática já estava pronto desde o passo 3: `Ingredient.lowStockThreshold` (persistido, configurável por insumo) e `IngredientResponse.lowStock` (booleano computado em `IngredientService#toResponse`, `saldo ≤ limiar`) já saem em `GET /api/v1/ingredients`. Confirmado em 2026-08-11 que isso já espelha o "escopo mínimo" citado acima: o alerta de demora na cozinha do Morá (`frontend/src/pages/KitchenPage.tsx`, `getDelayLevel`) também é só dado bruto (`createdAt`) + cálculo client-side, sem endpoint dedicado nem infraestrutura no backend - construir um endpoint de contagem/filtro agora seria mais backend do que o próprio precedente tem. O que falta é só a sinalização visual em si (badge/borda/filtro "só estoque baixo" na tela), que entra junto do passo 8 (`warehouse-frontend`).
8. 🔶 `warehouse-frontend` — em andamento, por partes (decidido 2026-08-11: SSO + Insumos primeiro). Feito: infra do projeto (Vite/React/TS/Tailwind 4, mesmo stack do `frontend/`, cor de destaque teal em vez do laranja do Morá pra diferenciar a aba), tela `/sso` que troca o token e guarda a sessão (token de sessão em `localStorage` direto, não memory-only como o Morá - Armazém não tem fluxo de refresh, ver comentário em `sessionStorage.ts`), e tela de Insumos (CRUD completo + badge visual "Estoque baixo"). Testado ponta a ponta pelo navegador (ver "Estado atual"). Faltam Fornecedores/Compras e Receitas.

## Estado atual (2026-08-11, referência)

Passos 1-7 concluídos e cobertos por teste de integração. Passo 8 (`warehouse-frontend`) começado pelo recorte SSO + Insumos, e **testado ponta a ponta de verdade pelo navegador** nesta mesma sessão: login real no Morá (restaurante de teste "Tatu Bola") → clique em "Armazém Morá" no menu → nova aba abre já autenticada no Armazém → CRUD de insumo funcionando, incluindo o badge "Estoque baixo" aparecendo/sumindo corretamente ao cruzar o limiar, e o toggle ativo/inativo.

**Bug real encontrado e corrigido durante esse teste**: o link "Armazém Morá" no `AppLayout.tsx` do Morá abria a nova aba com `window.open(url, '_blank', 'noopener,noreferrer')` - passar `noopener` nas *window features* faz o Chrome devolver `null` como referência da aba (é assim que `noopener` funciona tecnicamente), então a aba ficava presa em `about:blank` pra sempre, já que o código dependia dessa referência pra navegar a aba depois que o handoff assíncrono resolvesse. Corrigido removendo `noopener,noreferrer` (a URL de destino é emitida pelo próprio backend do Morá, não é link de terceiro/não-confiável, então o motivo de usar `noopener` não se aplica aqui). Achado só foi possível testando o clique de verdade no navegador - nenhum teste automatizado cobria esse caminho.

Ainda faltam: Fornecedores/Compras e Receitas no `warehouse-frontend` (passo 8 continua em aberto), nada commitado ainda, e o workflow `warehouse-sync.yml` ainda não foi disparado de verdade (falta configurar secret/var no GitHub). Backlog do Morá (`docs/SCOPE.md`, item 20, Prioridade 7) segue apontando pra cá.

**Pendência de deploy (2026-08-14)**: `warehouse-backend` no Render ainda não sobe — faltam preencher `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` (precisa criar um projeto novo no Neon, banco separado do `mora-backend`) e `WAREHOUSE_SSO_SECRET` (mesmo valor já configurado no `mora-backend`) nas env vars do serviço. `WAREHOUSE_JWT_SECRET` já foi preenchido.
