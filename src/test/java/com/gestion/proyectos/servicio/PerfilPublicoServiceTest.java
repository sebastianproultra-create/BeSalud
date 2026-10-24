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
    void consultaValida_seGuarda() {
        Doctor d = new Doctor();
        assertThat(service.guardarConsulta(d, 80_000, 20_000, "  Calle 10   # 5-20 ", 24)).isNull();
        assertThat(d.cobraAbono()).isTrue();
        assertThat(d.getDireccionConsultorio()).isEqualTo("Calle 10 # 5-20");
        verify(personaRepo).save(d);
    }

    @Test
    void abonoInvalido_noSeGuarda() {
        Doctor d = new Doctor();
        assertThat(service.guardarConsulta(d, 80_000, 500, null, 24)).contains("mínimo");
        assertThat(service.guardarConsulta(d, 80_000, 90_000, null, 24)).contains("mayor");
        assertThat(service.guardarConsulta(d, 80_000, -1, null, 24)).isNotNull();
        assertThat(service.guardarConsulta(d, 80_000, 0, null, 500)).isNotNull();
        verify(personaRepo, never()).save(d);
    }

    @Test
    void sinAbono_noCobra() {
        Doctor d = new Doctor();
        assertThat(service.guardarConsulta(d, null, null, null, null)).isNull();
        assertThat(d.cobraAbono()).isFalse();
        assertThat(d.horasCancelacionEfectivas()).isEqualTo(24);
    }
}
