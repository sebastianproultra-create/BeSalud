package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Reserva;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué página de resultado ve el paciente según el estado de su reserva. */
class PagoVistaTest {

    private static Reserva reserva(Reserva.Estado estado, String transaccion) {
        Reserva r = new Reserva();
        r.setEstado(estado);
        r.setTransaccionId(transaccion);
        return r;
    }

    @Test
    void cadaEstadoTieneSuVista() {
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.APROBADA, "t"))).isEqualTo(PagoController.Vista.CONFIRMADA);
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.RECHAZADA, "t"))).isEqualTo(PagoController.Vista.RECHAZADA);
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.CONFLICTO, "t"))).isEqualTo(PagoController.Vista.CONFLICTO);
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.PROCESANDO, "t"))).isEqualTo(PagoController.Vista.PROCESANDO);
    }

    @Test
    void sinPagoIniciado_vence_conPagoEnCurso_espera() {
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.EXPIRADA, null))).isEqualTo(PagoController.Vista.EXPIRADA);
        // PSE o Nequi en curso: aunque el apartado venza, el pago aún puede aprobarse
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.EXPIRADA, "tx-1"))).isEqualTo(PagoController.Vista.PROCESANDO);
        assertThat(PagoController.vistaDe(reserva(Reserva.Estado.ESPERANDO_PAGO, "tx-1"))).isEqualTo(PagoController.Vista.PROCESANDO);
    }
}
