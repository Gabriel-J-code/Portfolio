package dev.gabriel.portfolio_api.services;

import java.time.Duration;

import dev.gabriel.portfolio_api.exceptions.EnvioDeEmailException;

/**
 * Envio de e-mails. Hoje so o codigo do 2FA.
 *
 * E uma interface com duas implementacoes, escolhidas pela propriedade app.email.provedor:
 * - SmtpEmailService (padrao): SMTP; em desenvolvimento, o Mailpit do docker-compose.
 * - ResendEmailService: API HTTP do Resend, usada em producao (o Render free bloqueia SMTP).
 * Quem usa (AcessoService) depende so da interface e nao sabe qual provedor esta ativo.
 *
 * O CONTEUDO da mensagem fica aqui, no metodo default, igual para todos os provedores;
 * cada implementacao so sabe ENTREGAR um texto (metodo enviar).
 *
 * O envio e sincrono: o login espera o e-mail sair. Mais adiante, com RabbitMQ, ele pode virar
 * um evento processado em segundo plano.
 */
public interface EmailService {

    /**
     * Entrega uma mensagem de texto simples.
     *
     * @throws EnvioDeEmailException se o provedor nao conseguir enviar (vira HTTP 503)
     */
    void enviar(String destino, String assunto, String texto);

    default void enviarCodigoDeAcesso(String destino, String codigo, Duration validade) {
        enviar(destino, "Seu codigo de acesso ao painel: " + codigo, """
                Ola!

                Seu codigo de acesso ao painel do portfolio e:

                    %s

                Ele vale por %d minutos e so pode ser usado uma vez.
                Se nao foi voce que tentou entrar, troque sua senha: alguem a conhece.
                """.formatted(codigo, validade.toMinutes()));
    }
}
