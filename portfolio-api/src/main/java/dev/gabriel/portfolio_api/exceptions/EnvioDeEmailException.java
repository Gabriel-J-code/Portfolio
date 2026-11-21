package dev.gabriel.portfolio_api.exceptions;

/**
 * O provedor de e-mail (SMTP ou API HTTP) nao conseguiu enviar a mensagem. Vira HTTP 503.
 *
 * Por que uma excecao propria? Cada provedor falha com uma excecao diferente (MailException no SMTP,
 * RestClientException na API HTTP). As implementacoes de EmailService traduzem para esta,
 * e o resto da aplicacao (AcessoService, GlobalExceptionHandler) nao precisa saber qual provedor esta em uso.
 */
public class EnvioDeEmailException extends RuntimeException {
    public EnvioDeEmailException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
