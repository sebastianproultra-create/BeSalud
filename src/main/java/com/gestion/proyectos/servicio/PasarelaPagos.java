package com.gestion.proyectos.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestion.proyectos.modelo.Reserva;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Integración con Wompi (Web Checkout). Sin llaves configuradas se usa una pasarela simulada dentro
 * de la app, para poder mostrar el flujo completo sin cuenta en Wompi.
 *
 * Wompi recomienda no confiar en la redirección: el pago se confirma consultando la transacción desde
 * el servidor o con el evento firmado que envía al webhook.
 */
@Service
public class PasarelaPagos {

    private static final Logger log = LoggerFactory.getLogger(PasarelaPagos.class);
    static final String CHECKOUT = "https://checkout.wompi.co/p/";
    private static final Pattern ID_TRANSACCION = Pattern.compile("[A-Za-z0-9-]{1,80}");

    public record Transaccion(String id, String estado, String referencia, long montoCentavos, String moneda) {
        public boolean aprobada() {
            return "APPROVED".equals(estado);
        }

        public boolean rechazada() {
            return "DECLINED".equals(estado) || "VOIDED".equals(estado) || "ERROR".equals(estado);
        }
    }

    private final String llavePublica;
    private final String secretoIntegridad;
    private final String secretoEventos;
    private final String llavePrivada;
    private final boolean simulados;
    private final ObjectMapper mapper;
    private final RestClient http;

    public PasarelaPagos(@Value("${app.pagos.wompi.llave-publica:}") String llavePublica,
            @Value("${app.pagos.wompi.secreto-integridad:}") String secretoIntegridad,
            @Value("${app.pagos.wompi.secreto-eventos:}") String secretoEventos,
            @Value("${app.pagos.wompi.llave-privada:}") String llavePrivada,
            @Value("${app.pagos.simulados:true}") boolean simulados,
            ObjectMapper mapper) {
        this.llavePublica = limpiar(llavePublica);
        this.secretoIntegridad = limpiar(secretoIntegridad);
        this.secretoEventos = limpiar(secretoEventos);
        this.llavePrivada = limpiar(llavePrivada);
        this.simulados = simulados;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(4_000);
        tiempos.setReadTimeout(8_000);
        this.http = RestClient.builder().requestFactory(tiempos).build();
        if (!wompiConfigurado())
            log.info(simulados ? "Wompi sin configurar: los abonos usan la pasarela simulada de demostración."
                    : "Wompi sin configurar y pasarela de pruebas apagada: las reservas en línea están pausadas.");
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }

    /** Sin Wompi, la pasarela de pruebas solo se usa si está permitida (PAGOS_SIMULADOS). */
    public boolean usaSimulada() {
        return !wompiConfigurado() && simulados;
    }

    /** ¿Se puede cobrar el abono? Con Wompi o, si está permitida, con la pasarela de pruebas. */
    public boolean pagosDisponibles() {
        return wompiConfigurado() || simulados;
    }

    public boolean wompiConfigurado() {
        return !llavePublica.isEmpty() && !secretoIntegridad.isEmpty();
    }

    /** Las llaves de pruebas empiezan por pub_test_; con ellas se habla con el sandbox. */
    public boolean esSandbox() {
        return !llavePublica.startsWith("pub_prod_");
    }

    String apiBase() {
        return esSandbox() ? "https://sandbox.wompi.co/v1" : "https://production.wompi.co/v1";
    }

    /** Firma de integridad: SHA-256 de referencia + monto en centavos + moneda + secreto de integridad. */
    String firmaIntegridad(String referencia, long montoCentavos) {
        return sha256(referencia + montoCentavos + "COP" + secretoIntegridad);
    }

    /** URL a la que se envía al paciente para pagar (Wompi o la pasarela simulada). */
    public String urlPago(Reserva reserva, String email, String nombre, String urlRetorno) {
        if (!wompiConfigurado())
            return "/pagos/simulado/" + reserva.getReferencia();
        return UriComponentsBuilder.fromHttpUrl(CHECKOUT)
                .queryParam("public-key", llavePublica)
                .queryParam("currency", "COP")
                .queryParam("amount-in-cents", reserva.getMontoCentavos())
                .queryParam("reference", reserva.getReferencia())
                .queryParam("signature:integrity", firmaIntegridad(reserva.getReferencia(), reserva.getMontoCentavos()))
                .queryParam("redirect-url", urlRetorno)
                .queryParam("customer-data:email", email)
                .queryParam("customer-data:full-name", nombre)
                .encode()
                .build()
                .toUriString();
    }

    /** Estado real de una transacción, preguntándole a Wompi (no se confía en lo que trae la URL). */
    public Optional<Transaccion> consultar(String transaccionId) {
        if (!wompiConfigurado() || transaccionId == null || !ID_TRANSACCION.matcher(transaccionId).matches())
            return Optional.empty();
        try {
            RestClient.RequestHeadersSpec<?> pedido = http.get().uri(apiBase() + "/transactions/" + transaccionId);
            if (!llavePrivada.isEmpty())
                pedido = pedido.header("Authorization", "Bearer " + llavePrivada);
            String cuerpo = pedido.retrieve().body(String.class);
            return Optional.ofNullable(leerTransaccion(mapper.readTree(cuerpo).path("data")));
        } catch (Exception e) {
            log.warn("No se pudo consultar la transacción {} en Wompi: {}", transaccionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Valida un evento del webhook: SHA-256 de los valores de signature.properties (en orden, leídos de
     * data) + timestamp + secreto de eventos debe coincidir con signature.checksum.
     */
    public Optional<Transaccion> eventoValido(JsonNode evento) {
        if (secretoEventos.isEmpty() || evento == null || !"transaction.updated".equals(evento.path("event").asText()))
            return Optional.empty();
        JsonNode firma = evento.path("signature");
        StringBuilder cadena = new StringBuilder();
        for (JsonNode propiedad : firma.path("properties")) {
            JsonNode valor = evento.path("data");
            for (String parte : propiedad.asText().split("\\."))
                valor = valor.path(parte);
            if (valor.isMissingNode())
                return Optional.empty();
            cadena.append(valor.asText());
        }
        cadena.append(evento.path("timestamp").asText()).append(secretoEventos);
        String esperado = firma.path("checksum").asText("");
        if (!MessageDigest.isEqual(sha256(cadena.toString()).getBytes(StandardCharsets.UTF_8),
                esperado.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Evento de Wompi con firma inválida: se ignora.");
            return Optional.empty();
        }
        return Optional.ofNullable(leerTransaccion(evento.path("data").path("transaction")));
    }

    private static Transaccion leerTransaccion(JsonNode t) {
        if (t == null || t.isMissingNode() || t.path("id").asText("").isEmpty())
            return null;
        return new Transaccion(t.path("id").asText(), t.path("status").asText(), t.path("reference").asText(),
                t.path("amount_in_cents").asLong(), t.path("currency").asText());
    }

    static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
