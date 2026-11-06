package com.gestion.proyectos.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Cabecera Idempotency-Key, como en la API de Stripe: si la web reintenta un POST (doble clic, red que se cae,
 * timeout) con la misma clave, recibe la misma respuesta y no se aparta un segundo turno.
 *
 * - La clave vale 24 h y es por consultorio y paciente (ámbito): otro paciente no puede "adivinar" respuestas.
 * - Reusar la clave con otros datos es un error (422), igual que en Stripe.
 * - Si la primera petición falló, la clave se libera y el reintento vuelve a ejecutarse.
 * - Dos peticiones simultáneas con la misma clave: la segunda espera el resultado de la primera.
 * Vive en memoria: alcanza para una instancia; con varias habría que llevarlo a Mongo o Redis.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Component
public class Idempotencia {

    static final Duration VIGENCIA = Duration.ofHours(24);
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final long ESPERA_SEGUNDOS = 30;

    /** Valor devuelto y si salió de una petición anterior (cabecera Idempotent-Replayed). */
    public record Resultado<T>(T valor, boolean repetido) {
    }

    private record Entrada(String huella, CompletableFuture<Object> resultado, Instant creada) {
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final Clock reloj;

    @Autowired
    public Idempotencia() {
        this(Clock.systemUTC());
    }

    Idempotencia(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Ejecuta la acción una sola vez por (ámbito, clave). Sin clave, la ejecuta siempre.
     * @param huella resumen de los datos de la petición, para detectar la clave reusada con otros datos
     */
    @SuppressWarnings("unchecked")
    public <T> Resultado<T> ejecutar(String ambito, String clave, String huella, Supplier<T> accion) {
        if (clave == null || clave.isBlank()) return new Resultado<>(accion.get(), false);
        if (!CLAVE_VALIDA.matcher(clave).matches())
            throw new ConsultorioApiController.ApiError(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key debe tener de 8 a 64 letras, números, guiones o guiones bajos");
        limpiarVencidas();

        String id = ambito + "|" + clave;
        Entrada nueva = new Entrada(huella, new CompletableFuture<>(), reloj.instant());
        Entrada previa = entradas.putIfAbsent(id, nueva);
        if (previa != null) {
            if (!previa.huella().equals(huella))
                throw new ConsultorioApiController.ApiError(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Esa Idempotency-Key ya se usó con otros datos");
            return new Resultado<>((T) esperar(previa), true);
        }
        try {
            T valor = accion.get();
            nueva.resultado().complete(valor);
            return new Resultado<>(valor, false);
        } catch (RuntimeException e) {
            // Falló: se libera la clave para que el reintento se ejecute de verdad
            entradas.remove(id, nueva);
            nueva.resultado().completeExceptionally(e);
            throw e;
        }
    }

    private static Object esperar(Entrada previa) {
        try {
            return previa.resultado().get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new ConsultorioApiController.ApiError(HttpStatus.CONFLICT,
                    "Tu solicitud anterior sigue en proceso. Intenta de nuevo en unos segundos.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void limpiarVencidas() {
        Instant limite = reloj.instant().minus(VIGENCIA);
        entradas.values().removeIf(e -> e.creada().isBefore(limite) && e.resultado().isDone());
    }
}
