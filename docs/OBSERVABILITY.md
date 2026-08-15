# Sentry (observabilidade) — status

Item 1 da ordem de prioridade decidida em 2026-08-14 (Sentry → split de comanda → import de cardápio por PDF/imagem → cartão → delivery, ver `docs/DELIVERY.md`). Trabalho começado e testado ponta a ponta (backend + frontend) em 2026-08-14/15 — pronto pra commitar.

## O que já está pronto

**Backend**
- `sentry-spring-boot-starter-jakarta` 8.28.0 no `pom.xml`.
- `application.yml`: bloco `sentry:` — `dsn: ${SENTRY_DSN:}` (vazio = SDK desligado, postura padrão em dev/test), `environment`, `traces-sample-rate: 0.0` (só erro, sem performance tracing, pra ficar no plano grátis).
- `render.yaml`/`backend/.env.example`: variáveis documentadas (`SENTRY_DSN`, `SENTRY_ENVIRONMENT`).
- **Testado ponta a ponta em 2026-08-15, funcionando**: endpoint temporário (`SentryTestController`, já removido) forçou uma exceção não tratada, e o evento apareceu no painel do Sentry (projeto `java-spring-boot-2`, org `mora-w5`) com stack trace completo, linha exata do código, URL da requisição.

**Bug real encontrado e corrigido durante esse teste (não relacionado ao Sentry em si):** a rota interna `/error` do Spring Boot — pra onde qualquer exceção não tratada é redirecionada internamente pra montar a resposta HTTP — não estava na lista `permitAll` do `SecurityConfig`. Resultado: **qualquer exceção não tratada em qualquer endpoint público** (`/api/v1/public/**`, login, etc.) devolvia um 403 confuso pro cliente em vez do 500 real. Corrigido adicionando `.requestMatchers("/error").permitAll()`. Fix já aplicado em `SecurityConfig.java`, vale manter independente do resto do Sentry.

**Frontend**
- `@sentry/react` instalado.
- `src/lib/sentry.ts` — inicializa só se `VITE_SENTRY_DSN` estiver setado (mesma postura desligado-por-padrão do backend).
- `src/components/CrashScreen.tsx` — tela de fallback em português ("Algo deu errado" + botão Recarregar).
- `main.tsx` — `App` envolvido em `Sentry.ErrorBoundary`.
- `render.yaml` — `VITE_SENTRY_DSN`/`VITE_SENTRY_ENVIRONMENT` pro build do frontend.
- `npm run build` passou limpo (mesmo comando que o Render usa).
- **Testado ponta a ponta em 2026-08-15, funcionando**: projeto `mora-frontend` criado na org `mora-w5`, DSN em `frontend/.env` (`VITE_SENTRY_DSN`, gitignored), evento capturado com sucesso (`MORA-FRONTEND-2`, "Sentry frontend test error", stack trace apontando pro componente de teste).

## Pegadinha do teste local (pra não repetir)

Ao testar o backend localmente fora do IntelliJ, carregar `backend/.env` num loop de shell (`while IFS='=' read ...`) pula silenciosamente a **última linha do arquivo se ela não terminar com quebra de linha** — foi exatamente isso que fez o `SENTRY_DSN` parecer "não estar funcionando" por várias tentativas, quando na real a variável nunca chegava a ser exportada. `backend/.env` já foi corrigido pra terminar com newline. Se for repetir esse tipo de teste manual, confirmar que o arquivo termina com linha em branco.

## Pegadinha do teste do frontend (pra não repetir)

Bloqueadores de anúncio/tracker do navegador (ex: **Brave Shields**, uBlock, Privacy Badger) tratam `*.ingest.sentry.io` como domínio de rastreamento e bloqueiam a requisição — o SDK tenta enviar, a requisição sai mas volta **503** (ou nem completa, dependendo do bloqueador). Sintoma: o evento nunca aparece no painel, mesmo com DSN e código corretos. Pra confirmar/descartar isso durante teste manual: abrir o DevTools → Network **antes** de disparar o erro, filtrar por "sentry" e conferir o status code da requisição pro domínio `ingest.us.sentry.io`. Se vier bloqueada ou com erro, desativar o Shields/adblock só para o site em teste.

## Status final

Testado ponta a ponta e commitado em 2026-08-15 (backend + frontend + fix do `/error` + este doc). Projeto React criado no Sentry (`mora-frontend`, org `mora-w5`); evento de teste confirmado no painel (`MORA-FRONTEND-2`). Gatilho temporário do frontend (`SentryTestPage.tsx` + rota `/sentry-test`) removido após o teste, mesmo espírito do `SentryTestController` do backend.
