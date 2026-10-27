package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.servicio.AsistenteService;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PerfilPublicoService;
import com.gestion.proyectos.servicio.ReservaService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerfilPublicoControllerTest {

    @Test
    void perfilMuestraHorariosAunqueElDoctorAunNoPubliquePrecio() {
        PerfilPublicoService perfilService = mock(PerfilPublicoService.class);
        CitaService citaService = mock(CitaService.class);
        Doctor doctor = new Doctor();
        doctor.setId("doctor-1");
        when(perfilService.buscarActivoPorSlug("ana-ruiz")).thenReturn(Optional.of(doctor));
        LocalDate fecha = LocalDate.now().plusDays(1);
        LinkedHashMap<String, List<String>> slots = new LinkedHashMap<>();
        slots.put(fecha.toString(), List.of("09:00"));
        when(citaService.slotsDisponibles("doctor-1", null, null, null)).thenReturn(slots);

        PerfilPublicoController controller = new PerfilPublicoController(
                perfilService, citaService, mock(AsistenteService.class), mock(ReservaService.class));
        ExtendedModelMap model = new ExtendedModelMap();

        String vista = controller.perfil("ana-ruiz", model, mock(CsrfToken.class), null);

        assertThat(vista).isEqualTo("publico/profesional");
        assertThat(model.getAttribute("fechas")).isEqualTo(List.of(fecha));
        verify(citaService).slotsDisponibles("doctor-1", null, null, null);
    }
}
