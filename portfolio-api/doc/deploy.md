# Deploy da Portfolio API

Passo a passo para colocar a API em produção. O [README](../README.md#produção) explica **o que** o perfil `prod` muda e lista as variáveis de ambiente; este guia mostra **como** preparar cada peça.

| Peça | Serviço | Situação |
|---|---|---|
| Banco de dados | [Neon](https://neon.tech) (PostgreSQL 18, AWS São Paulo) | ✅ Pronto |
| E-mail do 2FA | [Resend](https://resend.com) (API HTTP) | ⏳ Criar a conta e a chave |
| API | [Render](https://render.com) (plano free, imagem pelo Dockerfile) | ⏳ Próxima etapa |

> **Segredos nunca entram no repositório nem em arquivos do projeto.** Guarde-os num gerenciador de senhas (Bitwarden, 1Password...) e, no Render, nas variáveis de ambiente do serviço.

## 1. Banco de dados no Neon

### Por que o Neon

O plano gratuito tem 1 GB, 100 CU-hora por mês e **scale-to-zero**: o banco "dorme" depois de 5 minutos sem uso e acorda sozinho na próxima conexão. O Supabase foi descartado porque pausa o projeto após 7 dias sem atividade e exige reativação manual.

### Criar o projeto

No painel do Neon: novo projeto `portfolio`, **PostgreSQL 18**, região **AWS São Paulo (`sa-east-1`)**. Os recursos extras (Neon Auth, Data API) não são necessários.

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
| `ADMIN_EMAIL` | (não é segredo) | E-mail que recebe o código do 2FA. Deve ter verificação em duas etapas ligada e ser **o mesmo e-mail da conta do Resend** (ver abaixo) |
| `JWT_SECRET` | Comando abaixo | Precisa ser o mesmo em todo lugar onde a API rodar. Se trocar, os tokens emitidos param de valer (basta fazer login de novo) |
| `RESEND_API_KEY` | Painel do Resend (ver abaixo) | Começa com `re_` |

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

### E-mail do 2FA no Resend

**Por que não SMTP (Gmail com senha de app)?** O plano gratuito do Render bloqueia as portas SMTP (25, 465 e 587). Por isso, em produção, a API envia o e-mail pela **API HTTP** do Resend (HTTPS, porta 443). Em desenvolvimento nada muda: continua o SMTP com o Mailpit.

O plano gratuito do Resend (sem cartão) permite 100 e-mails por dia, muito acima do necessário: só o administrador recebe o código.

1. Crie a conta em [resend.com](https://resend.com) **com o mesmo e-mail do `ADMIN_EMAIL`**. Sem um domínio próprio verificado, o Resend só entrega para o e-mail dono da conta, e o remetente é o do próprio Resend: `MAIL_FROM=onboarding@resend.dev`.
2. Em **API Keys → Create API Key**: nome `portfolio-api`, permissão **Sending access** (só enviar, não administra a conta). Copie a chave direto para o gerenciador de senhas: o Resend só a mostra uma vez.

Se um dia houver domínio próprio, basta verificá-lo no Resend e trocar o `MAIL_FROM` (ex.: `nao-responda@seu-dominio.dev`); o código não muda.

## 3. Testar a imagem localmente contra o Neon

Antes de ir para o Render, rode a imagem de produção no seu computador apontando para o Neon. Se algo falhar aqui, o problema só pode ser o banco ou as credenciais; na hospedagem, também poderia ser rede ou variáveis de ambiente.

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

```powershell
$env:RESEND_API_KEY = Read-Host "Chave do Resend" -MaskInput
```

Conferência (quatro números maiores que zero; o do JWT é `44`):

```powershell
$env:DB_PASSWORD.Length; $env:ADMIN_PASSWORD.Length; $env:JWT_SECRET.Length; $env:RESEND_API_KEY.Length
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
  -e DB_PASSWORD -e ADMIN_PASSWORD -e JWT_SECRET -e RESEND_API_KEY `
  -e ADMIN_EMAIL="$EMAIL_ADMIN" `
  -e MAIL_FROM="onboarding@resend.dev" `
  -e CORS_ORIGINS="http://localhost:3000" `
  portfolio-api
```

**O que conferir:**

1. No log: `Successfully applied 1 migration to schema "public"` (só na primeira vez) e, depois, `Started PortfolioApiApplication`.
2. Em outro terminal: `curl.exe http://localhost:8080/api/projetos` responde `[]`.
3. No SQL Editor do Neon (banco `portfolio`): as tabelas existem e `acessos` tem uma linha.
4. **Login com e-mail de verdade** (em outro terminal, um comando por vez):

```powershell
$cred = @{ email = (Read-Host "E-mail do admin"); senha = (Read-Host "Senha do admin" -MaskInput) } | ConvertTo-Json
```
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $cred
```

A resposta traz `"Codigo de acesso enviado por e-mail"` e o código chega na caixa de entrada do `ADMIN_EMAIL` (confira também o spam). Um `503` significa que o Resend recusou: o motivo exato aparece no log da API (chave errada, destinatário diferente do dono da conta...).

Para encerrar: **Ctrl+C** no terminal da API e **feche o terminal**, o que apaga as variáveis com os segredos.

## 4. Render (próxima etapa)

**Por que o Render:** plano gratuito sem cartão de crédito, com build direto do `Dockerfile` a partir do repositório do GitHub. O serviço "dorme" depois de ~15 minutos sem acesso e leva cerca de 1 minuto para acordar (o front vai mostrar um "carregando"). O Google Cloud Run foi descartado porque exigiu pré-pagamento.

A documentar conforme for feito:

- [ ] Web Service com runtime Docker, root directory `portfolio-api`, região mais próxima do Neon
- [ ] Variáveis de ambiente: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `JWT_SECRET`, `RESEND_API_KEY`, `MAIL_FROM`, `CORS_ORIGINS` (`SPRING_PROFILES_ACTIVE=prod` já vem da imagem; `PORT` é definida pelo Render)
- [ ] Health check em `/api/projetos`
- [x] Memória (512 MB no free), CPU (0,1) e pool do Hikari: imagem ajustada e medida (startup ~94 s, ~200 MB; ver "Imagem Docker" no README)
- [ ] Login com 2FA de ponta a ponta
