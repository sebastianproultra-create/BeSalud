package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.servicio.CitaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WhatsappControllerTest {

    private static final String TEL = "573001112233";

    @Mock CitaService citaService;
    @Mock PersonaRepositorio personaRepo;

    MockMvc mvc;
    Cita cita;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new WhatsappController(citaService, personaRepo, "secreto", "https://besalud.test/")).build();
        cita = new Cita();
        cita.setId("c1");
        cita.setPacienteId("p1");
        cita.setEstado(EstadoCita.PENDIENTE);
    }

    private void citaYPaciente() {
        Paciente paciente = new Paciente("Ana", "Ruiz", "300 111 2233", "123", "ana@gmail.com", "x");
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));
        when(personaRepo.findById("p1")).thenReturn(Optional.of(paciente));
    }

    private ResultActions llamar(String secreto, String payload, String tel) throws Exception {
        var req = post("/whatsapp/asistencia").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":" + (payload == null ? "null" : "\"" + payload + "\"")
                        + ",\"telefono\":\"" + tel + "\"}");
        if (secreto != null) req.header("X-Bot-Secret", secreto);
        return mvc.perform(req);
    }

    @Test
    void sinSecretoOSecretoIncorrecto_401() throws Exception {
        llamar(null, "ASISTIRE:c1", TEL).andExpect(status().isUnauthorized());
        llamar("otro", "ASISTIRE:c1", TEL).andExpect(status().isUnauthorized());
        verify(citaService, never()).responderAsistencia(any(), anyBoolean());
    }

    @Test
    void secretoNoConfigurado_401AunqueVengaVacio() throws Exception {
        var sinSecreto = MockMvcBuilders.standaloneSetup(
                new WhatsappController(citaService, personaRepo, "", "https://besalud.test")).build();
        sinSecreto.perform(post("/whatsapp/asistencia").header("X-Bot-Secret", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"ASISTIRE:c1\",\"telefono\":\"" + TEL + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payloadInvalido_404() throws Exception {
        llamar("secreto", "BASURA", TEL).andExpect(status().isNotFound());
        llamar("secreto", null, TEL).andExpect(status().isNotFound());
        llamar("secreto", "OTRO:c1", TEL).andExpect(status().isNotFound());
    }

    @Test
    void citaInexistente_404() throws Exception {
        when(citaService.buscarPorId("zz")).thenReturn(Optional.empty());
        llamar("secreto", "ASISTIRE:zz", TEL).andExpect(status().isNotFound());
    }

    @Test
    void telefonoQueNoEsDelPaciente_403() throws Exception {
        citaYPaciente();
        llamar("secreto", "ASISTIRE:c1", "573009998877").andExpect(status().isForbidden());
        verify(citaService, never()).responderAsistencia(any(), anyBoolean());
    }

    @Test
    void personaQueNoEsPaciente_403() throws Exception {
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));
        when(personaRepo.findById("p1")).thenReturn(Optional.of(new Doctor()));
        llamar("secreto", "ASISTIRE:c1", TEL).andExpect(status().isForbidden());
    }

    @Test
    void asistireYConfirmar_confirmanLaCita() throws Exception {
        citaYPaciente();
        when(citaService.responderAsistencia(cita, true)).thenReturn(true);
        llamar("secreto", "ASISTIRE:c1", TEL).andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(containsString("Confirmaste")));
        llamar("secreto", "CONFIRMAR:c1", TEL).andExpect(status().isOk());
        verify(citaService, times(2)).responderAsistencia(cita, true);
        verify(citaService, never()).responderAsistencia(cita, false);
    }

    @Test
    void noIreYCancelar_cancelanLaCita() throws Exception {
        citaYPaciente();
        when(citaService.responderAsistencia(cita, false)).thenReturn(true);
        llamar("secreto", "NO_IRE:c1", TEL).andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(containsString("cancelamos")));
        llamar("secreto", "CANCELAR:c1", TEL).andExpect(status().isOk());
        verify(citaService, times(2)).responderAsistencia(cita, false);
        verify(citaService, never()).responderAsistencia(cita, true);
    }

    @Test
    void citaNoPendiente_200ConTextoSinResponder() throws Exception {
        cita.setEstado(EstadoCita.CANCELADA);
        citaYPaciente();
        llamar("secreto", "ASISTIRE:c1", TEL).andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(containsString("ya no está pendiente")))
                .andExpect(jsonPath("$.texto").value(containsString("https://besalud.test/pacientes/landing")));
        verify(citaService, never()).responderAsistencia(any(), anyBoolean());
    }

    @Test
    void responderAsistenciaFalla_200ConAviso() throws Exception {
        citaYPaciente();
        when(citaService.responderAsistencia(cita, true)).thenReturn(false);
        llamar("secreto", "ASISTIRE:c1", TEL).andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(containsString("No pudimos")));
    }
}
