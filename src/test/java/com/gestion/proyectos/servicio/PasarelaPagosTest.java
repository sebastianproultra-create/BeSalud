package com.gestion.proyectos.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestion.proyectos.modelo.Reserva;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PasarelaPagosTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static Reserva reserva() {
        Reserva r = new Reserva();
        r.setReferencia("BS-abc123");
        r.setMontoCentavos(2_000_000);
        return r;
    }

    @Test
    void sinLlaves_usaPasarelaSimulada() {
        PasarelaPagos p = new PasarelaPagos("", "", "", "", mapper);
        assertThat(p.wompiConfigurado()).isFalse();
        assertThat(p.urlPago(reserva(), "a@b.co", "Ana", "http://localhost/pagos/retorno"))
                .isEqualTo("/pagos/simulado/BS-abc123");
        assertThat(p.consultar("123-abc")).isEmpty();
    }

    @Test
    void conLlaves_armaCheckoutFirmado() throws Exception {
        PasarelaPagos p = new PasarelaPagos("pub_test_X", "test_integrity_Y", "", "", mapper);
        String url = p.urlPago(reserva(), "a@b.co", "Ana Ruiz", "http://localhost:8080/pagos/retorno");

        assertThat(p.esSandbox()).isTrue();
        assertThat(url).startsWith("https://checkout.wompi.co/p/?")
                .contains("public-key=pub_test_X")
                .contains("amount-in-cents=2000000")
                .contains("reference=BS-abc123")
                .contains("signature:integrity=" + sha256("BS-abc1232000000COPtest_integrity_Y"))
                .doesNotContain("test_integrity_Y&");
    }

    @Test
    void idDeTransaccionRaro_noSeConsulta() {
        PasarelaPagos p = new PasarelaPagos("pub_test_X", "s", "", "", mapper);
        assertThat(p.consultar("../../admin")).isEmpty();
        assertThat(p.consultar(null)).isEmpty();
    }

    private JsonNode evento(String checksum, long monto) throws Exception {
        return mapper.readTree("""
                {"event":"transaction.updated",
                 "data":{"transaction":{"id":"1234-1610641025-49201","amount_in_cents":%d,
                   "reference":"BS-abc123","currency":"COP","status":"APPROVED"}},
                 "signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],
                   "checksum":"%s"},
                 "timestamp":1530291411}
                """.formatted(monto, checksum));
    }

    @Test
    void eventoConFirmaValida_seAcepta() throws Exception {
        PasarelaPagos p = new PasarelaPagos("pub_test_X", "s", "test_events_Z", "", mapper);
        String checksum = sha256("1234-1610641025-49201APPROVED20000001530291411test_events_Z");

        var t = p.eventoValido(evento(checksum, 2_000_000));

        assertThat(t).isPresent();
        assertThat(t.get().aprobada()).isTrue();
        assertThat(t.get().referencia()).isEqualTo("BS-abc123");
        assertThat(t.get().montoCentavos()).isEqualTo(2_000_000);
    }

    @Test
    void eventoAlterado_seIgnora() throws Exception {
        PasarelaPagos p = new PasarelaPagos("pub_test_X", "s", "test_events_Z", "", mapper);
        String checksum = sha256("1234-1610641025-49201APPROVED20000001530291411test_events_Z");

        // Alguien cambia el monto pero conserva la firma original
        assertThat(p.eventoValido(evento(checksum, 100))).isEmpty();
        assertThat(p.eventoValido(evento("0000", 2_000_000))).isEmpty();
    }

    @Test
    void sinSecretoDeEventos_ningunEventoEsValido() throws Exception {
        PasarelaPagos p = new PasarelaPagos("pub_test_X", "s", "", "", mapper);
        assertThat(p.eventoValido(evento(sha256("1234-1610641025-49201APPROVED20000001530291411"), 2_000_000))).isEmpty();
    }
}
