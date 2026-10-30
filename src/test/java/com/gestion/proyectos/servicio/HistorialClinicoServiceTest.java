package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Antecedentes;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Diagnostico;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Medicamento;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistorialClinicoServiceTest {

    @Mock CitaRepositorio citaRepo;
    @Mock PersonaRepositorio personaRepo;

    @InjectMocks HistorialClinicoService service;

    private static Paciente paciente() {
        Paciente p = new Paciente();
        p.setId("p1");
        p.setNombre("Ana");
        p.setApellido("Gil");
        return p;
    }

    private static Doctor doctor(String id, String nombre, String especialidad) {
        Doctor d = new Doctor();
        d.setId(id);
        d.setNombre(nombre);
        d.setApellido("Ruiz");
        d.setEspecialidad(especialidad);
        return d;
    }

    private static Cita cita(String id, String doctorId, EstadoCita estado, LocalDate fecha, Dictamen dictamen) {
        Cita c = new Cita();
        c.setId(id);
        c.setDoctorId(doctorId);
        c.setPacienteId("p1");
        c.setEstado(estado);
        c.setFecha(fecha);
        c.setHora(LocalTime.of(9, 0));
        c.setDictamen(dictamen);
        return c;
    }

    private static Dictamen dictamen(String codigo, String descripcion, String alergias, String patologicos) {
        Dictamen d = new Dictamen();
        Diagnostico dx = new Diagnostico();
        dx.setCodigo(codigo);
        dx.setDescripcion(descripcion);
        d.setDiagnosticoPrincipal(dx);
        Antecedentes a = new Antecedentes();
        a.setAlergicos(alergias);
        a.setPatologicos(patologicos);
        d.setAntecedentes(a);
        return d;
    }

    @Test
    void historial_uneLosDictamenesDeTodosLosMedicosDelMasRecienteAlMasAntiguo() {
        Dictamen viejo = dictamen("J039", "Amigdalitis aguda", "Penicilina", "Asma");
        Medicamento m = new Medicamento();
        m.setNombre("Amoxicilina 500 mg");
        viejo.setMedicamentos(List.of(m));
        Dictamen nuevo = dictamen("I10X", "Hipertensión esencial", null, "Asma e hipertensión");
        when(personaRepo.findById("p1")).thenReturn(Optional.of(paciente()));
        when(citaRepo.findByPacienteId("p1")).thenReturn(List.of(
                cita("c1", "d1", EstadoCita.COMPLETADA, LocalDate.of(2026, 3, 1), viejo),
                cita("c2", "d2", EstadoCita.COMPLETADA, LocalDate.of(2026, 9, 1), nuevo),
                cita("c3", "d1", EstadoCita.PENDIENTE, LocalDate.of(2026, 11, 1), null),
                cita("c4", "d2", EstadoCita.CANCELADA, LocalDate.of(2026, 8, 1), null)));
        when(personaRepo.findAllById(any())).thenReturn(List.of(
                doctor("d1", "Laura", "Medicina General"), doctor("d2", "Pedro", "Cardiología")));

        var h = service.historialDe("p1").orElseThrow();

        // Solo las citas completadas con dictamen, de cualquier médico, la más reciente primero
        assertThat(h.entradas()).extracting(e -> e.cita().getId()).containsExactly("c2", "c1");
        assertThat(h.entradas().get(0).especialidad()).isEqualTo("Cardiología");
        // Antecedentes vigentes: lo más reciente de cada campo; las alergias vienen del dictamen viejo
        assertThat(h.antecedentes().getPatologicos()).isEqualTo("Asma e hipertensión");
        assertThat(h.antecedentes().getAlergicos()).isEqualTo("Penicilina");
        assertThat(h.antecedentesFecha().get("alergicos")).isEqualTo(LocalDateTime.of(2026, 3, 1, 9, 0));
        assertThat(h.alergias()).isEqualTo("Penicilina");
        assertThat(h.diagnosticos()).extracting(d -> d.diagnostico().getCodigo()).containsExactly("I10X", "J039");
        // La última fórmula es la del dictamen más reciente que tiene medicamentos
        assertThat(h.ultimaFormula()).extracting(Medicamento::getNombre).containsExactly("Amoxicilina 500 mg");
        assertThat(h.getFilasAntecedentes()).extracting(HistorialClinicoService.Fila::etiqueta)
                .containsExactly("Patológicos", "Alérgicos");
    }

    @Test
    void diagnosticosAgrupados_unControlCronicoNoSeRepite() {
        when(personaRepo.findById("p1")).thenReturn(Optional.of(paciente()));
        when(citaRepo.findByPacienteId("p1")).thenReturn(List.of(
                cita("c1", "d1", EstadoCita.COMPLETADA, LocalDate.of(2026, 1, 10), dictamen("I10X", "Hipertensión", null, null)),
                cita("c2", "d1", EstadoCita.COMPLETADA, LocalDate.of(2026, 5, 10), dictamen("J039", "Amigdalitis", null, null)),
                cita("c3", "d2", EstadoCita.COMPLETADA, LocalDate.of(2026, 9, 10), dictamen("I10X", "Hipertensión", null, null))));
        when(personaRepo.findAllById(any())).thenReturn(List.of(
                doctor("d1", "Laura", "Medicina General"), doctor("d2", "Pedro", "Cardiología")));

        var agrupados = service.historialDe("p1").orElseThrow().getDiagnosticosAgrupados();

        assertThat(agrupados).hasSize(2);
        var hta = agrupados.get(0);
        assertThat(hta.diagnostico().getCodigo()).isEqualTo("I10X");
        assertThat(hta.veces()).isEqualTo(2);
        assertThat(hta.ultima()).isEqualTo(LocalDateTime.of(2026, 9, 10, 9, 0));
        assertThat(hta.primera()).isEqualTo(LocalDateTime.of(2026, 1, 10, 9, 0));
        assertThat(hta.ultimoMedico()).isEqualTo("Dr(a). Pedro Ruiz");
    }

    @Test
    void historial_sinDictamenesUsaLasAlergiasQueDeclaroElPaciente() {
        Paciente p = paciente();
        Antecedentes declarado = new Antecedentes();
        declarado.setAlergicos("Ibuprofeno");
        p.setHistorialPrevio(declarado);
        when(personaRepo.findById("p1")).thenReturn(Optional.of(p));
        when(citaRepo.findByPacienteId("p1")).thenReturn(List.of());
        when(personaRepo.findAllById(any())).thenReturn(List.of());

        var h = service.historialDe("p1").orElseThrow();

        assertThat(h.isVacio()).isTrue();
        assertThat(h.alergias()).isEqualTo("Ibuprofeno");
        assertThat(h.isTieneDeclarado()).isTrue();
    }

    @Test
    void historial_deUnMedicoNoEsUnHistorialDePaciente() {
        when(personaRepo.findById("d1")).thenReturn(Optional.of(doctor("d1", "Laura", "Medicina General")));
        assertThat(service.historialDe("d1")).isEmpty();
    }

    @Test
    void medicoPuedeVer_soloConUnaCitaActivaOAtendida() {
        when(citaRepo.findByPacienteId("p1")).thenReturn(List.of(
                cita("c1", "d1", EstadoCita.CANCELADA, LocalDate.of(2026, 3, 1), null),
                cita("c2", "d2", EstadoCita.PENDIENTE, LocalDate.of(2026, 9, 1), null)));

        assertThat(service.medicoPuedeVer("d2", "p1")).isTrue();
        assertThat(service.medicoPuedeVer("d1", "p1")).isFalse(); // solo tuvo una cita cancelada
        assertThat(service.medicoPuedeVer("d9", "p1")).isFalse();
        assertThat(service.medicoPuedeVer(null, "p1")).isFalse();
    }

    @Test
    void antecedentesParaDictamen_completaConLoQueDeclaroElPaciente() {
        Paciente p = paciente();
        Antecedentes declarado = new Antecedentes();
        declarado.setPatologicos("Lo que dice el paciente");
        declarado.setQuirurgicos("Apendicectomía");
        p.setHistorialPrevio(declarado);
        when(personaRepo.findById("p1")).thenReturn(Optional.of(p));
        when(citaRepo.findByPacienteId("p1")).thenReturn(List.of(
                cita("c1", "d1", EstadoCita.COMPLETADA, LocalDate.of(2026, 3, 1),
                        dictamen("J039", "Amigdalitis", "Niega", "Asma"))));
        when(personaRepo.findAllById(any())).thenReturn(List.of());

        Antecedentes a = service.antecedentesParaDictamen("p1");

        assertThat(a.getPatologicos()).isEqualTo("Asma"); // gana lo que registró el médico
        assertThat(a.getQuirurgicos()).isEqualTo("Apendicectomía"); // el médico no lo había llenado
        assertThat(a.getAlergicos()).isEqualTo("Niega");
    }

    @Test
    void guardarDeclarado_limpiaEspaciosYGuardaConFecha() {
        Paciente p = paciente();
        Antecedentes datos = new Antecedentes();
        datos.setAlergicos("  Penicilina  ");
        datos.setFamiliares("   ");

        assertThat(service.guardarDeclarado(p, datos, " Exámenes de sangre normales ")).isNull();

        assertThat(p.getHistorialPrevio().getAlergicos()).isEqualTo("Penicilina");
        assertThat(p.getHistorialPrevio().getFamiliares()).isNull();
        assertThat(p.getHistorialPrevioNotas()).isEqualTo("Exámenes de sangre normales");
        assertThat(p.getHistorialPrevioActualizado()).isNotNull();
        verify(personaRepo).save(p);
    }

    @Test
    void guardarDeclarado_sinCambiosNoGuardaYTextoLargoSeRechaza() {
        Paciente p = paciente();
        Antecedentes igual = new Antecedentes();
        igual.setAlergicos("Penicilina");
        p.setHistorialPrevio(igual);
        Antecedentes mismo = new Antecedentes();
        mismo.setAlergicos("Penicilina ");

        assertThat(service.guardarDeclarado(p, mismo, null)).isNull();
        verify(personaRepo, never()).save(any());

        Antecedentes largo = new Antecedentes();
        largo.setPatologicos("x".repeat(HistorialClinicoService.MAX_TEXTO + 1));
        assertThat(service.guardarDeclarado(p, largo, null)).contains("1000");
        assertThat(service.guardarDeclarado(p, new Antecedentes(), "y".repeat(HistorialClinicoService.MAX_TEXTO + 1)))
                .contains("1000");
        verify(personaRepo, never()).save(any());
    }
}
