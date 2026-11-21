package dev.gabriel.portfolio_api.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Teste UNITARIO do conteudo do e-mail, que e o mesmo para todos os provedores.
 * Como EmailService tem um unico metodo abstrato, um lambda serve de implementacao "falsa"
 * que so guarda o que receberia para enviar.
 */
class EmailServiceTest {

    record Enviado(String destino, String assunto, String texto) {
    }

    private final List<Enviado> enviados = new ArrayList<>();
    private final EmailService emailService = (destino, assunto, texto) -> enviados.add(new Enviado(destino, assunto, texto));

    @Test
    void montaMensagemComCodigoEValidade() {
        emailService.enviarCodigoDeAcesso("gabriel@exemplo.com", "042137", Duration.ofMinutes(10));

        assertThat(enviados).hasSize(1);
        Enviado msg = enviados.get(0);
        assertThat(msg.destino()).isEqualTo("gabriel@exemplo.com");
        assertThat(msg.assunto()).contains("042137");
        assertThat(msg.texto()).contains("042137").contains("10 minutos");
    }

    @Test
    void mascaraEmail() {
        assertThat(AcessoService.mascarar("gabriel@gmail.com")).isEqualTo("g******@gmail.com");
        assertThat(AcessoService.mascarar("a@gmail.com")).isEqualTo("*@gmail.com");
    }
}
