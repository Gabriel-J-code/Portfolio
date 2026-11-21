package dev.gabriel.portfolio_api.services;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import dev.gabriel.portfolio_api.config.EmailProperties;
import dev.gabriel.portfolio_api.exceptions.EnvioDeEmailException;

/**
 * Envio pela API HTTP do Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Ativado com app.email.provedor=resend (perfil prod).
 *
 * Por que HTTP e nao SMTP? O plano gratuito do Render bloqueia as portas SMTP (25, 465, 587).
 * Uma chamada HTTPS (porta 443) passa normalmente.
 *
 * Por que RestClient e nao a biblioteca Java do Resend? E uma unica chamada POST com JSON;
 * o cliente HTTP do proprio Spring resolve sem dependencia extra, e o teste simula o servidor
 * com o MockRestServiceServer. Timeouts: spring.http.clients.* no application.properties.
 */
@Service
@ConditionalOnProperty(name = "app.email.provedor", havingValue = "resend")
public class ResendEmailService implements EmailService {

    /** Corpo do POST /emails. O Jackson transforma o record em JSON: {"from": ..., "to": [...], ...} */
    record EmailResend(String from, List<String> to, String subject, String text) {
    }

    private final RestClient restClient;
    private final String remetente;

    /**
     * Recebe o RestClient.Builder configurado pelo Spring Boot (conversores JSON, timeouts)
     * e fixa o endereco base e a chave, que vao em toda requisicao.
     */
    public ResendEmailService(RestClient.Builder builder, EmailProperties emailProperties) {
        String apiKey = emailProperties.resend().apiKey();
        // A chave so e obrigatoria com este provedor, por isso a conferencia fica aqui e nao no EmailProperties.
        // Falha AO SUBIR (o bean e criado na inicializacao), em vez de no primeiro login.
        // Tambem barra o texto literal "${RESEND_API_KEY}", que sobra quando a variavel nao existe.
        if (apiKey == null || !apiKey.startsWith("re_")) {
            throw new IllegalStateException(
                    "app.email.resend.api-key (RESEND_API_KEY) ausente ou invalida: a chave do Resend comeca com \"re_\"");
        }
        this.remetente = emailProperties.remetente();
        this.restClient = builder
                .baseUrl(emailProperties.resend().url().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public void enviar(String destino, String assunto, String texto) {
        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmailResend(remetente, List.of(destino), assunto, texto))
                    .retrieve() // resposta 4xx/5xx vira RestClientResponseException
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // O Resend explica o erro no corpo (ex.: chave invalida, destinatario nao permitido)
            throw new EnvioDeEmailException("Resend respondeu " + ex.getStatusCode() + ": "
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            // Sem resposta: timeout, DNS, conexao recusada...
            throw new EnvioDeEmailException("Falha ao chamar o Resend: " + ex.getMessage(), ex);
        }
    }
}
