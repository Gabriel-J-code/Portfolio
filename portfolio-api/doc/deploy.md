# Deploy da Portfolio API

Passo a passo para colocar a API em produção. O [README](../README.md#produção) explica **o que** o perfil `prod` muda e lista as variáveis de ambiente; este guia mostra **como** preparar cada peça.

| Peça | Serviço | Situação |
|---|---|---|
| Banco de dados | [Neon](https://neon.tech) (PostgreSQL 18, AWS São Paulo) | ✅ Pronto |
| API | Google Cloud Run (`southamerica-east1`) | ⏳ Próxima etapa |
| E-mail do 2FA | Gmail com senha de app | ⏳ Junto com o Cloud Run |

> **Segredos nunca entram no repositório nem em arquivos do projeto.** Guarde-os num gerenciador de senhas (Bitwarden, 1Password...) e, no Cloud Run, no Secret Manager.

## 1. Banco de dados no Neon

### Por que o Neon

O plano gratuito tem 1 GB, 100 CU-hora por mês e **scale-to-zero**: o banco "dorme" depois de 5 minutos sem uso e acorda sozinho na próxima conexão. O Supabase foi descartado porque pausa o projeto após 7 dias sem atividade e exige reativação manual.

### Criar o projeto

No painel do Neon: novo projeto `portfolio`, **PostgreSQL 18**, região **AWS São Paulo (`sa-east-1`)**, perto do Cloud Run em `southamerica-east1`. Os recursos extras (Neon Auth, Data API) não são necessários.

### Criar o banco com ordenação em português

O banco padrão do Neon (`neondb`) usa a collation `C.UTF-8`, que ordena por código de caractere: "Ética" viria depois de "Java". Para ordenar como nos outros ambientes (ICU `pt-BR`), crie um banco próprio. No **SQL Editor**, com o banco `neondb` selecionado:

```sql
CREATE DATABASE portfolio LOCALE_PROVIDER icu ICU_LOCALE 'pt-BR' TEMPLATE template0;
```

O `TEMPLATE template0` é obrigatório: o `template1` (padrão) já tem uma collation definida, e o Postgres não deixa trocá-la ao copiar.

Troque o SQL Editor para o banco `portfolio` e confira:

```sql
SELECT datlocprovider, datlocale FROM pg_database WHERE datname = 'portfolio';
-- esperado: i | pt-BR
```

As tabelas **não** são criadas à mão: o Flyway cria tudo na primeira vez que a API sobe.

### Da connection string às variáveis da API

O Neon mostra a conexão no formato do `libpq` (o cliente em C do Postgres):

```
postgresql://USUARIO:SENHA@ep-xxxx-yyyy-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

O driver JDBC usa outro formato e recebe usuário e senha separados. Monte assim:

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://ep-xxxx-yyyy.sa-east-1.aws.neon.tech/portfolio?sslmode=require` |
| `DB_USER` | o trecho antes do `:` (ex.: `neondb_owner`) |
| `DB_PASSWORD` | o trecho entre o `:` e o `@` |

Ajustes em relação à string original:

- **Prefixo `jdbc:`**, exigido pelo driver JDBC.
- **Host sem `-pooler`** (no painel, desligue "Connection pooling"). O pooler do Neon é um PgBouncer; a API já tem o HikariCP como pool, e dois pools em cadeia quebram os prepared statements do JDBC.
- **`/portfolio` no lugar de `/neondb`**, o banco criado acima.
- **`sslmode=require` fica**: o Neon só aceita conexões criptografadas.
- **`channel_binding` sai**: é o nome do parâmetro no `libpq` e o driver JDBC não o reconhece.
- Se a senha tiver sequências como `%40`, são caracteres codificados para URL: use o caractere real (`%40` = `@`).

## 2. Segredos de produção

Gere valores **novos**, diferentes dos de desenvolvimento, e guarde no gerenciador de senhas:

| Segredo | Como gerar | Observação |
|---|---|---|
| `DB_PASSWORD` | Vem do Neon | |
| `ADMIN_PASSWORD` | Gerador do gerenciador de senhas | Senha do painel. O seed só cria a conta se a tabela `acessos` estiver vazia, então use já a definitiva |
| `ADMIN_EMAIL` | (não é segredo) | E-mail que recebe o código do 2FA. Deve ter verificação em duas etapas ligada |
| `JWT_SECRET` | Comando abaixo | Precisa ser o mesmo em todo lugar onde a API rodar. Se trocar, os tokens emitidos param de valer (basta fazer login de novo) |

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

## 3. Testar a imagem localmente contra o Neon

Antes de ir para o Cloud Run, rode a imagem de produção no seu computador apontando para o Neon. Se algo falhar aqui, o problema só pode ser o banco ou as credenciais; no Cloud Run, também poderia ser rede, segredos ou permissões.

Com o Docker Desktop aberto, use **o mesmo terminal** do início ao fim e rode **um comando por vez** (cada `Read-Host` espera uma resposta; colar tudo junto faz ele ler a linha seguinte como resposta).

```powershell
cd portfolio-api
docker build -t portfolio-api .
```

Segredos, com `-MaskInput` para não aparecerem na tela nem no histórico. Cole o valor e tecle Enter:

```powershell
$env:DB_PASSWORD    = Read-Host "Senha do Neon" -MaskInput
```
```powershell
$env:ADMIN_PASSWORD = Read-Host "Senha do admin" -MaskInput
```
```powershell
$env:JWT_SECRET     = Read-Host "JWT secret" -MaskInput
```

Conferência (três números maiores que zero; o do JWT é `44`):

```powershell
$env:DB_PASSWORD.Length; $env:ADMIN_PASSWORD.Length; $env:JWT_SECRET.Length
```

Valores que não são segredo:

```powershell
$HOST_NEON = Read-Host "Host do Neon (sem -pooler)"
```
```powershell
$EMAIL_ADMIN = Read-Host "E-mail do admin"
```

Subir a API. No `docker run`, um `-e VAR` sem `=` repassa a variável da sessão do PowerShell, então a senha não aparece no comando:

```powershell
docker run --rm -p 8080:8080 `
  -e DB_URL="jdbc:postgresql://$HOST_NEON/portfolio?sslmode=require" `
  -e DB_USER="neondb_owner" `
  -e DB_PASSWORD -e ADMIN_PASSWORD -e JWT_SECRET `
  -e ADMIN_EMAIL="$EMAIL_ADMIN" `
  -e MAIL_HOST="smtp.gmail.com" -e MAIL_USERNAME="x" -e MAIL_PASSWORD="x" -e MAIL_FROM="x@x.com" `
  -e CORS_ORIGINS="http://localhost:3000" `
  portfolio-api
```

Os `MAIL_*` são falsos de propósito: o SMTP só é usado no login, que fica para a etapa do Cloud Run.

**O que conferir:**

1. No log: `Successfully applied 1 migration to schema "public"` e, depois, `Started PortfolioApiApplication`.
2. Em outro terminal: `curl.exe http://localhost:8080/api/projetos` responde `[]`.
3. No SQL Editor do Neon (banco `portfolio`): as tabelas existem e `acessos` tem uma linha.

Para encerrar: **Ctrl+C** no terminal da API e **feche o terminal**, o que apaga as variáveis com os segredos.

## 4. Cloud Run (próxima etapa)

A documentar conforme for feito:

- [ ] Instalar o `gcloud` e criar o projeto no GCP
- [ ] Artifact Registry (onde a imagem fica guardada)
- [ ] Secret Manager com `DB_PASSWORD`, `ADMIN_PASSWORD`, `JWT_SECRET` e a senha de app do Gmail
- [ ] Conta Gmail remetente + senha de app (`MAIL_*`)
- [ ] Deploy manual no Cloud Run (`southamerica-east1`), avaliando `max-instances` e o pool do Hikari por causa do limite de conexões do Neon free
- [ ] Deploy automático pelo GitHub Actions com Workload Identity Federation
