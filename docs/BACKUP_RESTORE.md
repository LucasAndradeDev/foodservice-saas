# Backup e restore do banco de dados

Como funciona hoje: um workflow do GitHub Actions (`.github/workflows/backup.yml`) roda todo dia e chama `POST /api/v1/internal/backups` no backend. Esse endpoint faz um `pg_dump` completo do banco (todos os restaurantes, um banco só), sobe o arquivo pra um bucket **privado** no Supabase Storage e apaga backups mais antigos que os últimos `BACKUP_RETENTION_COUNT` (padrão: 14).

Isso cobre recuperação de desastre (perda do banco, exclusão errada, corrupção). Não existe restore automático nem self-service pelo dono do restaurante — é sempre manual, seguindo os passos abaixo.

## Configuração necessária (feita uma vez, fora do código)

1. No painel do Supabase (mesmo projeto já usado pro bucket `uploads`), criar um bucket novo e **privado** chamado `backups` (ou o nome que for colocado em `SUPABASE_BACKUP_BUCKET`).
2. Gerar um segredo: `openssl rand -base64 32`.
3. Configurar esse valor em dois lugares:
   - Variável de ambiente `BACKUP_TRIGGER_TOKEN` no serviço do backend no Render.
   - Secret `BACKUP_TRIGGER_TOKEN` no repositório do GitHub (`Settings → Secrets and variables → Actions → Secrets`).
4. Configurar a variável `BACKEND_URL` no repositório do GitHub (`Settings → Secrets and variables → Actions → Variables`), com a URL de produção do backend (ver `docs/DEPLOY.md`).
5. Sem o `BACKUP_TRIGGER_TOKEN` configurado no backend, o endpoint rejeita todas as chamadas (falha fechado, de propósito).

## Restaurando um backup

1. No painel do Supabase, abrir Storage → bucket de backups, e baixar o `.dump` desejado (o nome inclui o timestamp UTC, ex. `backup-20260804T060000Z.dump`; o mais recente é o de nome "maior" em ordem alfabética).
2. **Nunca restaurar direto em cima do banco de produção sem ter certeza absoluta.** Se a dúvida for só "esse dump está bom", restaure primeiro num banco Postgres novo e descartável.
3. Rodar (localmente, com o `postgresql-client` instalado, ou de dentro de um container que tenha `pg_restore`):
   ```
   pg_restore --clean --if-exists --no-owner --dbname="<connection-string-do-banco-de-destino>" caminho/para/backup-XXXXXXXXTXXXXXXZ.dump
   ```
   - `--clean --if-exists`: apaga os objetos existentes no banco de destino antes de recriar — é isso que faz o restore ser destrutivo pro banco de destino.
   - `--no-owner`: evita erros de permissão por causa de usuários/owners que não existem no banco de destino.
4. Depois de restaurar, validar que os dados fazem sentido (login funciona, restaurantes/pedidos aparecem) antes de considerar o incidente resolvido.

## Testando o processo sem esperar um desastre de verdade

- Disparar o workflow manualmente: aba **Actions** do GitHub → "Database backup" → **Run workflow**.
- Ou chamar direto: `curl -X POST -H "X-Backup-Token: <token>" https://<backend-url>/api/v1/internal/backups`.
