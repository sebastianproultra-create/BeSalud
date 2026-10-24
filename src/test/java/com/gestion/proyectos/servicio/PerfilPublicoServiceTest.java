package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PerfilPublicoServiceTest {

    private final PersonaRepositorio personaRepo = mock(PersonaRepositorio.class);
    private final PerfilPublicoService service = new PerfilPublicoService(personaRepo, mock(MongoTemplate.class));

    @Test
    void slugSinTildesNiSimbolos() {
        assertThat(PerfilPublicoService.slugDe("  María José Peña-Núñez!! ")).isEqualTo("maria-jose-pena-nunez");
        assertThat(PerfilPublicoService.slugDe("Dr. Ángel  O'Neil")).isEqualTo("dr-angel-o-neil");
        assertThat(PerfilPublicoService.slugDe("a".repeat(59) + " bcd")).isEqualTo("a".repeat(59));
        assertThat(PerfilPublicoService.slugDe("x".repeat(200))).hasSize(60);
    }

    @Test
    void consultaValida_seGuardaConPorcentaje() {
        Doctor d = new Doctor();
        assertThat(service.guardarConsulta(d, 80_000, 20, "  Calle 10   # 5-20 ", 24)).isNull();
        assertThat(d.recibeReservas()).isTrue();
        assertThat(d.getAbono()).isEqualTo(16_000);
        assertThat(d.getDireccionConsultorio()).isEqualTo("Calle 10 # 5-20");
        verify(personaRepo).save(d);
    }

    @Test
    void sinPrecioOPorcentajeInvalido_noSeGuarda() {
        Doctor d = new Doctor();
        assertThat(service.guardarConsulta(d, null, 20, null, 24)).contains("precio");
        assertThat(service.guardarConsulta(d, 1_000, 20, null, 24)).contains("entre");
        assertThat(service.guardarConsulta(d, 80_000, 15, null, 24)).contains("porcentaje");
        assertThat(service.guardarConsulta(d, 80_000, null, null, 24)).contains("porcentaje");
        assertThat(service.guardarConsulta(d, 80_000, 20, null, 500)).isNotNull();
        verify(personaRepo, never()).save(d);
    }

    @Test
    void abono_redondeaYRespetaElMinimo() {
        Doctor d = new Doctor();
        assertThat(d.recibeReservas()).isFalse();
        assertThat(d.getAbono()).isZero();

        d.setPrecioConsulta(75_550);
        d.setPorcentajeAbono(30);
        assertThat(d.getAbono()).isEqualTo(22_700); // 22.665 → centena más cercana

        d.setPrecioConsulta(8_000);
        d.setPorcentajeAbono(10);
        assertThat(d.getAbono()).isEqualTo(Doctor.MIN_ABONO); // 800 sube al mínimo

        d.setPorcentajeAbono(null);
        assertThat(d.porcentajeAbonoEfectivo()).isEqualTo(Doctor.PORCENTAJE_ABONO_DEFECTO);
    }
}
