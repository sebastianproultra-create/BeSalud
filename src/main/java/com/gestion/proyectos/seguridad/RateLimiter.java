package com.gestion.proyectos.seguridad;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// En memoria: se reinicia con la app y no se comparte entre instancias. Suficiente con una sola instancia.
@Component
public class RateLimiter {

    private static final int MAX_CLAVES = 10_000;

    private final Map<String, Deque<Long>> eventos = new ConcurrentHashMap<>();

    public boolean permitir(String clave, int max, Duration ventana) {
        Deque<Long> cola = eventos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            purgar(cola, ventana);
            if (cola.size() >= max) return false;
            cola.addLast(System.currentTimeMillis());
        }
        limpiarSiCrece(ventana);
        return true;
    }

    public boolean bloqueado(String clave, int max, Duration ventana) {
        Deque<Long> cola = eventos.get(clave);
        if (cola == null) return false;
        synchronized (cola) {
            purgar(cola, ventana);
            return cola.size() >= max;
        }
    }

    public void registrar(String clave, Duration ventana) {
        Deque<Long> cola = eventos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            cola.addLast(System.currentTimeMillis());
        }
        limpiarSiCrece(ventana);
    }

    public void reiniciar(String clave) {
        eventos.remove(clave);
    }

    private void purgar(Deque<Long> cola, Duration ventana) {
        long limite = System.currentTimeMillis() - ventana.toMillis();
        while (!cola.isEmpty() && cola.peekFirst() < limite) cola.pollFirst();
    }

    private void limpiarSiCrece(Duration ventana) {
        if (eventos.size() <= MAX_CLAVES) return;
        eventos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                purgar(e.getValue(), ventana);
                return e.getValue().isEmpty();
            }
        });
    }
}
