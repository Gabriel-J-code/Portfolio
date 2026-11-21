package dev.gabriel.portfolio_api.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import dev.gabriel.portfolio_api.config.EmailProperties;
import dev.gabriel.portfolio_api.exceptions.EnvioDeEmailException;

/**
 * Envio por SMTP. E o provedor PADRAO (matchIfMissing): desenvolvimento e testes usam o Mailpit.
 *
 * O JavaMailSender e configurado pelo Spring Boot a partir de spring.mail.* (host, porta...).
 * Este service so monta a mensagem.
 */
@Service
@ConditionalOnProperty(name = "app.email.provedor", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public SmtpEmailService(JavaMailSender mailSender, EmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Override
    public void enviar(String destino, String assunto, String texto) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(emailProperties.remetente());
        mensagem.setTo(destino);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        try {
            mailSender.send(mensagem);
        } catch (MailException ex) {
            throw new EnvioDeEmailException("Falha no envio por SMTP: " + ex.getMessage(), ex);
        }
    }
}
