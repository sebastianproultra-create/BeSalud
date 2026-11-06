package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.SitioWeb;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.repositorio.SitioWebRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Página web alojada: borrador desde el perfil, validación, publicación y lectura pública. */
class SitioWebServiceTest {

    private SitioWebRepositorio sitioRepo;
    private PersonaRepositorio personaRepo;
    private ConsultorioRepositorio consultorioRepo;
    private ConsultorioService consultorioService;
    private SitioWebService service;
    private Doctor doctor;

    @BeforeEach
    void preparar() {
        sitioRepo = mock(SitioWebRepositorio.class);
        personaRepo = mock(PersonaRepositorio.class);
        consultorioRepo = mock(ConsultorioRepositorio.class);
        consultorioService = mock(ConsultorioService.class);
        service = new SitioWebService(sitioRepo, personaRepo, consultorioRepo, consultorioService);
        when(sitioRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sitioRepo.findByDoctorId(anyString())).thenReturn(Optional.empty());

        doctor = new Doctor();
        doctor.setId("d1");
        doctor.setNombre("Leo");
        doctor.setApellido("Ruiz");
        doctor.setEspecialidad("Pediatría");
        doctor.setTelefono("300 123 4567");
        doctor.setDireccionConsultorio("Calle 10 # 5-20");
        doctor.setEstado("ACTIVO");
    }

    private static SitioWebService.Formulario form(String titulo, String servicios, String whatsapp) {
        return new SitioWebService.Formulario("familiar", "verde", titulo, "Hola", "Sobre mí", servicios, whatsapp,
                "Calle 1", "Montería");
    }

    @Test
    void borrador_nuevo_seLlenaConElPerfilDelMedico() {
        when(sitioRepo.existsBySlug("dr-leo-ruiz")).thenReturn(true);

        SitioWeb s = service.borrador(doctor);

        assertThat(s.getId()).isNull();
        assertThat(s.getSlug()).isEqualTo("dr-leo-ruiz-2");
        assertThat(s.getTitulo()).isEqualTo("Consultorio Dr. Leo Ruiz");
        assertThat(s.getFrase()).contains("pediatría");
        assertThat(s.getServicios()).first().isEqualTo("Consulta de pediatría");
        assertThat(s.getWhatsapp()).isEqualTo("3001234567");
        assertThat(s.getDireccion()).isEqualTo("Calle 10 # 5-20");
        assertThat(s.getPlantilla()).isEqualTo("clinica");
        verify(sitioRepo, never()).save(any());
    }

    @Test
    void borrador_usaElSlugDeSuPerfilPublicoSiLoTiene() {
        doctor.setSlug("leo-pediatra");
        assertThat(service.borrador(doctor).getSlug()).isEqualTo("leo-pediatra");
    }

    @Test
    void validar_marcaCadaCampoMalo() {
        var errores = service.validar(form("A", "1\n2\n3\n4\n5\n6\n7", "123"));
        assertThat(errores).containsKeys("titulo", "servicios", "whatsapp");

        assertThat(service.validar(form("Consultorio Sol", "Consulta\nControl", "300 123 4567"))).isEmpty();
    }

    @Test
    void guardar_aplicaElFormularioYDescartaPlantillaDesconocida() {
        var f = new SitioWebService.Formulario("<script>", "fucsia", "  Consultorio Sol ", "", null,
                "Consulta\n\n  Control  \n", "+57 300-123-4567", null, null);

        SitioWeb s = service.guardar(doctor, f);

        assertThat(s.getPlantilla()).isEqualTo("clinica");
        assertThat(s.getColor()).isEqualTo("azul");
        assertThat(s.getTitulo()).isEqualTo("Consultorio Sol");
        assertThat(s.getFrase()).isNull();
        assertThat(s.getServicios()).containsExactly("Consulta", "Control");
        assertThat(s.getWhatsapp()).isEqualTo("573001234567");
        assertThat(s.getActualizado()).isNotNull();
    }

    @Test
    void vistaPrevia_noGuarda() {
        SitioWeb s = service.vistaPrevia(doctor, form("Consultorio Sol", "Consulta", ""));
        assertThat(s.getPlantilla()).isEqualTo("familiar");
        verify(sitioRepo, never()).save(any());
    }

    @Test
    void publicar_medicoInactivo_noPublica() {
        SitioWeb s = new SitioWeb();
        when(sitioRepo.findByDoctorId("d1")).thenReturn(Optional.of(s));
        doctor.setEstado("INACTIVO");

        assertThatThrownBy(() -> service.publicar(doctor, "http://localhost:8080"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no está activa");
        verify(consultorioService, never()).alojar(any(), any(), any());
    }

    @Test
    void publicar_sinGuardar_pideGuardarPrimero() {
        assertThatThrownBy(() -> service.publicar(doctor, "http://localhost:8080"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publicar_aseguraElConsultorioYQuedaPublicado() {
        SitioWeb s = new SitioWeb();
        s.setTitulo("Consultorio Sol");
        when(sitioRepo.findByDoctorId("d1")).thenReturn(Optional.of(s));
        Consultorio c = new Consultorio();
        c.setId("dr-leo-ruiz");
        when(consultorioService.alojar(doctor, "http://localhost:8080", "Consultorio Sol")).thenReturn(c);

        SitioWeb publicado = service.publicar(doctor, "http://localhost:8080");

        assertThat(publicado.isPublicado()).isTrue();
        assertThat(publicado.getConsultorioId()).isEqualTo("dr-leo-ruiz");
    }

    @Test
    void publica_soloConSitioPublicadoMedicoActivoYConsultorioActivo() {
        SitioWeb s = new SitioWeb();
        s.setSlug("dr-leo-ruiz");
        s.setDoctorId("d1");
        s.setConsultorioId("c1");
        s.setWhatsapp("3001234567");
        when(sitioRepo.findBySlug("dr-leo-ruiz")).thenReturn(Optional.of(s));
        Consultorio c = new Consultorio();
        c.setDoctorIds(new ArrayList<>(List.of("d1")));
        when(consultorioRepo.findByIdAndActivoTrue("c1")).thenReturn(Optional.of(c));
        when(personaRepo.findById("d1")).thenReturn(Optional.of(doctor));

        assertThat(service.publica("dr-leo-ruiz")).as("sin publicar").isEmpty();

        s.setPublicado(true);
        var pagina = service.publica("dr-leo-ruiz").orElseThrow();
        assertThat(pagina.doctor()).isSameAs(doctor);
        assertThat(pagina.whatsappUrl()).startsWith("https://wa.me/573001234567?text=");

        doctor.setEstado("INACTIVO");
        assertThat(service.publica("dr-leo-ruiz")).as("médico inactivo").isEmpty();

        doctor.setEstado("ACTIVO");
        when(consultorioRepo.findByIdAndActivoTrue("c1")).thenReturn(Optional.empty());
        assertThat(service.publica("dr-leo-ruiz")).as("consultorio pausado").isEmpty();
    }

    @Test
    void mapaUrl_combinaDireccionYCiudad() {
        assertThat(SitioWebService.mapaUrl("Calle 10", "Montería"))
                .isEqualTo("https://www.google.com/maps/search/?api=1&query=Calle+10%2C+Monter%C3%ADa");
        assertThat(SitioWebService.mapaUrl(null, "Montería")).isNull();
    }
}
