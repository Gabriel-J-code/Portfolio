package dev.gabriel.portfolio_api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import dev.gabriel.portfolio_api.config.EmailProperties;
import dev.gabriel.portfolio_api.exceptions.EnvioDeEmailException;

/**
 * Teste UNITARIO: sem Spring, sem banco, sem container. Roda em milissegundos.
 * O JavaMailSender e um mock; o teste so confere a mensagem SMTP que o service monta.
 */
class SmtpEmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailProperties props = new EmailProperties(EmailProperties.Provedor.SMTP,
            "nao-responda@portfolio.local", new EmailProperties.Resend(URI.create("https://api.resend.com"), null));
    private final SmtpEmailService emailService = new SmtpEmailService(mailSender, props);

    @Test
    void montaMensagemSmtpComRemetenteConfigurado() {
        emailService.enviar("gabriel@exemplo.com", "Assunto", "Texto");

        ArgumentCaptor<SimpleMailMessage> enviada = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(enviada.capture());
        SimpleMailMessage msg = enviada.getValue();

        assertThat(msg.getTo()).containsExactly("gabriel@exemplo.com");
        assertThat(msg.getFrom()).isEqualTo("nao-responda@portfolio.local");
        assertThat(msg.getSubject()).isEqualTo("Assunto");
        assertThat(msg.getText()).isEqualTo("Texto");
    }

    @Test
    void falhaDoSmtpViraEnvioDeEmailException() {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.enviar("gabriel@exemplo.com", "Assunto", "Texto"))
                .isInstanceOf(EnvioDeEmailException.class)
                .hasCauseInstanceOf(MailSendException.class);
    }
}
