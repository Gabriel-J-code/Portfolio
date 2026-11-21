package dev.gabriel.portfolio_api.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.gabriel.portfolio_api.config.EmailProperties;
import dev.gabriel.portfolio_api.exceptions.EnvioDeEmailException;

/**
 * Teste UNITARIO do cliente HTTP do Resend, sem rede.
 *
 * O MockRestServiceServer se "pluga" no RestClient.Builder e faz o papel da API do Resend:
 * confere a requisicao que chegaria (URL, cabecalhos, JSON) e devolve a resposta que o teste escolher.
 * Assim testamos o contrato com o Resend sem enviar e-mail de verdade nem precisar da chave real.
 */
class ResendEmailServiceTest {

    private static final String CHAVE = "re_chave_de_teste";

    private MockRestServiceServer servidor;
    private ResendEmailService emailService;

    private static EmailProperties props(String chave) {
        return new EmailProperties(EmailProperties.Provedor.RESEND, "onboarding@resend.dev",
                new EmailProperties.Resend(URI.create("https://api.resend.com"), chave));
    }

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        emailService = new ResendEmailService(builder, props(CHAVE));
    }

    @Test
    @DisplayName("Envia POST /emails com a chave no cabecalho e o JSON no formato do Resend")
    void enviaNoFormatoDoResend() {
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CHAVE))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("onboarding@resend.dev"))
                .andExpect(jsonPath("$.to[0]").value("gabriel@exemplo.com"))
                .andExpect(jsonPath("$.subject").value("Assunto"))
                .andExpect(jsonPath("$.text").value("Texto"))
                .andRespond(withSuccess("{ \"id\": \"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\" }",
                        MediaType.APPLICATION_JSON));

        emailService.enviar("gabriel@exemplo.com", "Assunto", "Texto");

        servidor.verify(); // falha se a requisicao esperada nao foi feita
    }

    @Test
    @DisplayName("Resend recusa (ex.: chave invalida, 403) -> EnvioDeEmailException com o motivo")
    void erroDoResend() {
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"statusCode\": 403, \"message\": \"API key is invalid\" }"));

        assertThatThrownBy(() -> emailService.enviar("gabriel@exemplo.com", "Assunto", "Texto"))
                .isInstanceOf(EnvioDeEmailException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("API key is invalid");
    }

    @Test
    @DisplayName("Resend fora do ar / timeout -> EnvioDeEmailException")
    void semResposta() {
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> emailService.enviar("gabriel@exemplo.com", "Assunto", "Texto"))
                .isInstanceOf(EnvioDeEmailException.class)
                .hasRootCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Sem chave, ou com o placeholder literal ${RESEND_API_KEY} -> nao sobe")
    void chaveAusenteOuInvalida() {
        for (String chave : new String[] { null, "", "${RESEND_API_KEY}" }) {
            assertThatThrownBy(() -> new ResendEmailService(RestClient.builder(), props(chave)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RESEND_API_KEY");
        }
    }
}
