package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Alta de consultorios desde el panel: normalización de la URL y creación/fusión del tenant. */
class ConsultorioServiceTest {

    private ConsultorioRepositorio repo;
    private PersonaRepositorio personaRepo;
    private ConsultorioService service;
    private Doctor doctor;

    @BeforeEach
    void preparar() {
        repo = mock(ConsultorioRepositorio.class);
        personaRepo = mock(PersonaRepositorio.class);
        service = new ConsultorioService(repo, personaRepo);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repo.buscarPorDominio(anyString())).thenReturn(List.of());
        when(repo.buscarPorDoctor(anyString())).thenReturn(List.of());
        doctor = new Doctor();
        doctor.setId("doc1");
        doctor.setNombre("Sebastián");
        doctor.setApellido("Fontalvo");
    }

    @Test
    void normalizarOrigen_dejaSoloEsquemaHostYPuerto() {
        assertThat(ConsultorioService.normalizarOrigen("https://Consultorio.com/agenda?x=1")).isEqualTo("https://consultorio.com");
        assertThat(ConsultorioService.normalizarOrigen("consultorio.com")).isEqualTo("https://consultorio.com");
        assertThat(ConsultorioService.normalizarOrigen("http://localhost:5501/")).isEqualTo("http://localhost:5501");
        assertThat(ConsultorioService.normalizarOrigen("https://consultorio.com:443")).isEqualTo("https://consultorio.com");
    }

    @Test
    void normalizarOrigen_rechazaLoQueNoEsUnSitio() {
        assertThat(ConsultorioService.normalizarOrigen("")).isNull();
        assertThat(ConsultorioService.normalizarOrigen("no es una url")).isNull();
        assertThat(ConsultorioService.normalizarOrigen("ftp://consultorio.com")).isNull();
        assertThat(ConsultorioService.normalizarOrigen("https://intranet")).isNull();
    }

    @Test
    void vincular_sinConsultorioPrevio_creaUnoConElDoctorYElDominio() {
        Consultorio c = service.vincularSitio(doctor, "https://fontalvo.com/").orElseThrow();

        assertThat(c.getId()).isEqualTo("dr-sebastian-fontalvo");
        assertThat(c.getDominios()).containsExactly("https://fontalvo.com");
        assertThat(c.getDoctorIds()).containsExactly("doc1");
        assertThat(c.isActivo()).isTrue();
    }

    @Test
    void vincular_localhost_autorizaTambien127001() {
        Consultorio c = service.vincularSitio(doctor, "http://localhost:5501").orElseThrow();
        assertThat(c.getDominios()).containsExactly("http://localhost:5501", "http://127.0.0.1:5501");
    }

    @Test
    void vincular_dominioDeOtraClinica_sumaElDoctorAEsaClinica() {
        Consultorio clinica = new Consultorio();
        clinica.setId("san-juan");
        clinica.setDominios(new ArrayList<>(List.of("https://sanjuan.com")));
        clinica.setDoctorIds(new ArrayList<>(List.of("otroDoc")));
        when(repo.buscarPorDominio("https://sanjuan.com")).thenReturn(List.of(clinica));

        Consultorio c = service.vincularSitio(doctor, "sanjuan.com").orElseThrow();

        assertThat(c.getId()).isEqualTo("san-juan");
        assertThat(c.getDoctorIds()).containsExactly("otroDoc", "doc1");
        assertThat(c.getDominios()).containsExactly("https://sanjuan.com");
    }

    @Test
    void vincular_idOcupado_agregaSufijo() {
        when(repo.existsById("dr-sebastian-fontalvo")).thenReturn(true);
        Consultorio c = service.vincularSitio(doctor, "https://otro.com").orElseThrow();
        assertThat(c.getId()).isEqualTo("dr-sebastian-fontalvo-2");
    }

    @Test
    void vincular_urlInvalida_noGuardaNada() {
        assertThat(service.vincularSitio(doctor, "no es una url")).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    void integracionDe_sinSitio_vacia() {
        assertThat(service.integracionDe("doc1")).isEmpty();
    }

    @Test
    void integracionDe_ocultaElDuplicado127YCuentaLosPacientesDeLaWeb() {
        Consultorio c = new Consultorio();
        c.setId("pequenos-pasos");
        c.setDominios(new ArrayList<>(List.of("http://localhost:5502", "http://127.0.0.1:5502", "https://pequenospasos.com")));
        c.setDoctorIds(new ArrayList<>(List.of("doc1")));
        when(repo.buscarPorDoctor("doc1")).thenReturn(List.of(c));
        when(personaRepo.contarPacientesDeConsultorio("pequenos-pasos")).thenReturn(23L);

        ConsultorioService.Integracion i = service.integracionDe("doc1").orElseThrow();
        assertThat(i.dominios()).containsExactly("http://localhost:5502", "https://pequenospasos.com");
        assertThat(i.pacientesWeb()).isEqualTo(23L);
    }

    // ── Página alojada por BeSalud ──

    @Test
    void alojar_sinConsultorio_creaUnoConElOrigenDeBesaludYElNombreDeLaPagina() {
        Consultorio c = service.alojar(doctor, "http://localhost:8080", "Consultorio Pediátrico Sol");

        assertThat(c.getId()).isEqualTo("dr-sebastian-fontalvo");
        assertThat(c.getNombre()).isEqualTo("Consultorio Pediátrico Sol");
        assertThat(c.getDominios()).containsExactly("http://localhost:8080", "http://127.0.0.1:8080");
        assertThat(c.getDoctorIds()).containsExactly("doc1");
    }

    @Test
    void alojar_conConsultorioPropio_leSumaElOrigenSinCambiarNombreNiEstado() {
        Consultorio propio = new Consultorio();
        propio.setId("san-juan");
        propio.setNombre("Clínica San Juan");
        propio.setActivo(false);
        propio.setDominios(new ArrayList<>(List.of("https://sanjuan.com")));
        propio.setDoctorIds(new ArrayList<>(List.of("doc1")));
        when(repo.buscarPorDoctor("doc1")).thenReturn(List.of(propio));

        Consultorio c = service.alojar(doctor, "https://besalud.co", "Otro nombre");

        assertThat(c.getId()).isEqualTo("san-juan");
        assertThat(c.getNombre()).isEqualTo("Clínica San Juan");
        assertThat(c.isActivo()).isFalse();
        assertThat(c.getDominios()).containsExactly("https://sanjuan.com", "https://besalud.co");
    }

    @Test
    void alojar_noSeFusionaConOtroConsultorioQueYaUseElOrigenDeBesalud() {
        Consultorio demo = new Consultorio();
        demo.setId("demo");
        demo.setDominios(new ArrayList<>(List.of("https://besalud.co")));
        when(repo.buscarPorDominio("https://besalud.co")).thenReturn(List.of(demo));

        Consultorio c = service.alojar(doctor, "https://besalud.co", null);

        assertThat(c.getId()).isEqualTo("dr-sebastian-fontalvo");
        assertThat(demo.getDoctorIds()).isEmpty();
    }
}
