package com.gestion.proyectos.config;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Reserva;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Recalcula la clave de turno antes de cualquier guardado (citas y reservas), para que ningún
 * camino del código pueda olvidarla y el índice único siempre refleje el estado real.
 */
public final class TurnoCallback {

    private TurnoCallback() {
    }

    @Component
    static class DeCita implements BeforeConvertCallback<Cita> {
        @Override
        public Cita onBeforeConvert(Cita cita, String coleccion) {
            cita.actualizarTurno();
            return cita;
        }
    }

    @Component
    static class DeReserva implements BeforeConvertCallback<Reserva> {
        @Override
        public Reserva onBeforeConvert(Reserva reserva, String coleccion) {
            reserva.actualizarTurno();
            return reserva;
        }
    }
}
