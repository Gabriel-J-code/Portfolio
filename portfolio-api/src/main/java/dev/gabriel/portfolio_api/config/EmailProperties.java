package dev.gabriel.portfolio_api.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuracoes "app.email.*": por onde o e-mail sai e quem aparece como remetente.
 *
 * - provedor: SMTP (padrao; Mailpit em desenvolvimento) ou RESEND (API HTTP, usado em producao
 *   porque o Render free bloqueia as portas SMTP). Decide qual implementacao de EmailService
 *   o Spring cria (ver @ConditionalOnProperty nelas).
 * - resend: so e usado quando provedor=RESEND. A chave e conferida pelo proprio ResendEmailService
 *   ao subir, porque so e obrigatoria nesse caso.
 */
@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(

        @NotNull
        Provedor provedor,

        @NotBlank @Email
        String remetente,

        @Valid @NotNull
        Resend resend) {

    public enum Provedor { SMTP, RESEND }

    /**
     * @param url    endereco base da API (padrao https://api.resend.com; trocado so nos testes)
     * @param apiKey chave criada no painel do Resend (comeca com "re_")
     */
    public record Resend(@NotNull URI url, String apiKey) {
    }
}
