package com.gestion.proyectos.servicio;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.util.ValidacionUtil;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;
    private final CitaRepositorio citaRepo;
    private final RegistroService registroService;
    private final ApplicationEventPublisher eventos;

    public DoctorService(PersonaRepositorio personaRepo, HorarioAtencionRepositorio horarioRepo,
            CitaRepositorio citaRepo, RegistroService registroService, ApplicationEventPublisher eventos) {
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
        this.citaRepo = citaRepo;
        this.registroService = registroService;
        this.eventos = eventos;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public Optional<Doctor> buscarPorEmail(String email) {
        return personaRepo.findDoctorByEmail(email);
    }

    public Optional<Doctor> buscarPorId(String id) {
        return personaRepo.findById(id).filter(p -> p instanceof Doctor).map(p -> (Doctor) p);
    }

    public Page<Doctor> listarDoctoresPaginated(int page, int size, String especialidad, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasEspecialidad = especialidad != null && !especialidad.isBlank();

        if (hasSearch && hasEspecialidad) {
            return personaRepo.searchDoctoresCombinado(ValidacionUtil.literalRegex(search.trim()), ValidacionUtil.literalRegex(especialidad.trim()), pageable);
        } else if (hasSearch) {
            return personaRepo.searchDoctoresByNombreApellidoEmail(ValidacionUtil.literalRegex(search.trim()), pageable);
        } else if (hasEspecialidad) {
            return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(ValidacionUtil.literalRegex(especialidad.trim()), pageable);
        } else {
            return personaRepo.findAllDoctores(pageable);
        }
    }

    public List<HorarioAtencion> horariosDelDoctor(String doctorId) {
        return horarioRepo.findByDoctorId(doctorId);
    }

    // ── Dashboard data ───────────────────────────────────────────────────────

    public List<Cita> citasDelDoctor(String doctorId, String filtroEstado) {
        if (filtroEstado == null || filtroEstado.isEmpty() || "TODAS".equals(filtroEstado)) {
            return citaRepo.findByDoctorId(doctorId);
        }
        return citaRepo.findByDoctorIdAndEstado(doctorId, EstadoCita.valueOf(filtroEstado));
    }

    public Map<String, String> mapPacienteNombres(List<Cita> citas) {
        List<String> ids = citas.stream()
                .map(Cita::getPacienteId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<String, String> nombres = new HashMap<>();
        personaRepo.findAllById(ids)
                .forEach(p -> nombres.put(p.getId(), p.getNombre() + " " + p.getApellido()));
        return nombres;
    }

    public Map<String, Map<String, String>> dictamenDataMap(List<Cita> citas) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Cita c : citas) {
            if (c.getDictamen() != null) {
                Map<String, String> d = new HashMap<>();
                d.put("diagnostico", c.getDictamen().getDiagnostico() != null ? c.getDictamen().getDiagnostico() : "");
                d.put("tratamiento", c.getDictamen().getTratamiento() != null ? c.getDictamen().getTratamiento() : "");
                d.put("observaciones",
                        c.getDictamen().getObservaciones() != null ? c.getDictamen().getObservaciones() : "");
                result.put(c.getId(), d);
            }
        }
        return result;
    }

    public String actualizarFoto(Doctor doctor, org.springframework.web.multipart.MultipartFile foto) {
        if (foto == null || foto.isEmpty())
            return "vacio";
        String tipo = foto.getContentType();
        if (tipo == null
                || !(tipo.equals("image/png") || tipo.equals("image/jpeg") || tipo.equals("image/webp")))
            return "tipo";
        if (foto.getSize() > 2L * 1024 * 1024)
            return "tamano";
        try {
            byte[] bytes = foto.getBytes();
            String base64 = "data:" + tipo + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
            doctor.setFoto(base64);
            personaRepo.save(doctor);
            log.info("Foto actualizada para doctor {}", doctor.getEmail());
            return null;
        } catch (Exception e) {
            log.warn("Error al procesar foto del doctor {}: {}", doctor.getEmail(), e.getMessage());
            return "procesando";
        }
    }

    public List<Paciente> pacientesDelDoctor(String doctorId) {
        List<String> ids = citaRepo.findByDoctorId(doctorId).stream()
                .map(Cita::getPacienteId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Paciente> pacientes = new ArrayList<>();
        personaRepo.findAllById(ids).forEach(p -> {
            if (p instanceof Paciente pac)
                pacientes.add(pac);
        });
        return pacientes;
    }

    public List<Map<String, Object>> weeklyAvailability(String doctorId) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<Map<String, Object>> result = new ArrayList<>();
        Locale localeEs = Locale.forLanguageTag("es-CO");
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            int count = 0;
            for (HorarioAtencion h : horarios) {
                if (h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana() == dow && h.getDuracionCitaMinutos() > 0) {
                    LocalTime t = h.getHoraInicio();
                    while (t.isBefore(h.getHoraFin())) {
                        count++;
                        t = t.plusMinutes(h.getDuracionCitaMinutos());
                    }
                }
            }
            String diaNombre = dow.getDisplayName(TextStyle.FULL, localeEs);
            diaNombre = diaNombre.substring(0, 1).toUpperCase(localeEs) + diaNombre.substring(1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fecha", date);
            row.put("diaSemana", diaNombre);
            row.put("cantidad", count);
            result.add(row);
        }
        return result;
    }

    public int weeklySlotTotal(List<Map<String, Object>> weeklyAvailability) {
        return weeklyAvailability.stream().mapToInt(r -> (int) r.get("cantidad")).sum();
    }

    public Map<LocalDate, Integer> dailySlotCounts(List<Map<String, Object>> weeklyAvailability) {
        Map<LocalDate, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> row : weeklyAvailability) {
            map.put((LocalDate) row.get("fecha"), (int) row.get("cantidad"));
        }
        return map;
    }

    // ── Doctor CRUD ──────────────────────────────────────────────────────────

    // ── Registro de doctores por el administrador ────────────────────────────

    private static final java.util.regex.Pattern PATRON_NOMBRE = java.util.regex.Pattern
            .compile("^\\p{L}[\\p{L} '.\\-]*$");
    private static final java.util.regex.Pattern PATRON_ESPECIALIDAD = java.util.regex.Pattern
            .compile("^\\p{L}[\\p{L} .,'()\\-]*$");
    private static final java.util.regex.Pattern PATRON_TELEFONO = java.util.regex.Pattern.compile("^3\\d{9}$");
    private static final java.util.regex.Pattern PATRON_IDENTIFICACION = java.util.regex.Pattern
            .compile("^\\d{6,10}$");
    private static final java.util.regex.Pattern PATRON_EMAIL = java.util.regex.Pattern
            .compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Valida y registra un doctor creado desde el panel de administración.
     * Reutiliza las reglas del registro público ({@link RegistroService}) y añade
     * reglas más
     * estrictas (formato, longitudes, contraseña). La contraseña se guarda con
     * BCrypt y el correo
     * en minúsculas, igual que en el registro público.
     *
     * @return null si el doctor quedó registrado, o el mensaje de error para
     *         mostrar al admin.
     */
    public String registrarPorAdmin(UserRegistrationDTO dto) {
        if (dto == null)
            return "Faltan los datos del doctor";

        // Estos campos nunca deben venir del formulario del admin
        dto.setRole("DOCTOR");
        dto.setFotoFile(null);
        dto.setFotoUrl(null);

        normalizar(dto);

        String error = registroService.validar(dto);
        if (error == null)
            error = validarReglasAdmin(dto);
        if (error == null)
            error = registroService.verificarDuplicado(dto);
        if (error == null)
            error = registroService.registrar(dto);
        if (error == null)
            log.info("Doctor registrado por el admin: {}", dto.getEmail());
        return error;
    }

    private static String limpiar(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    private static void normalizar(UserRegistrationDTO d) {
        d.setNombre(limpiar(d.getNombre()));
        d.setApellido(limpiar(d.getApellido()));
        d.setEspecialidad(limpiar(d.getEspecialidad()));
        if (d.getTelefono() != null)
            d.setTelefono(d.getTelefono().replaceAll("\\s+", ""));
        if (d.getIdentificacion() != null)
            d.setIdentificacion(d.getIdentificacion().replaceAll("\\s+", ""));
        if (d.getEmail() != null)
            d.setEmail(d.getEmail().trim().toLowerCase());
        if (d.getBiografia() != null) {
            String bio = d.getBiografia().trim();
            d.setBiografia(bio.isEmpty() ? null : bio);
        }
    }

    private String validarReglasAdmin(UserRegistrationDTO d) {
        String nombre = d.getNombre();
        if (esVacio(nombre) || nombre.length() < 2 || nombre.length() > 50
                || !PATRON_NOMBRE.matcher(nombre).matches())
            return "El nombre debe tener entre 2 y 50 letras (sin números ni símbolos)";

        String apellido = d.getApellido();
        if (esVacio(apellido) || apellido.length() < 2 || apellido.length() > 50
                || !PATRON_NOMBRE.matcher(apellido).matches())
            return "El apellido debe tener entre 2 y 50 letras (sin números ni símbolos)";

        if (esVacio(d.getIdentificacion()) || !PATRON_IDENTIFICACION.matcher(d.getIdentificacion()).matches())
            return "La identificación debe tener entre 6 y 10 dígitos";

        if (esVacio(d.getTelefono()) || !PATRON_TELEFONO.matcher(d.getTelefono()).matches())
            return "El teléfono debe tener 10 dígitos y empezar por 3 (ej: 3001234567)";

        String email = d.getEmail();
        if (esVacio(email))
            return "El correo electrónico es obligatorio";
        if (email.length() > 100)
            return "El correo no puede superar los 100 caracteres";
        if (!PATRON_EMAIL.matcher(email).matches())
            return "El correo electrónico no tiene un formato válido";

        String password = d.getPassword();
        if (esVacio(password))
            return "La contraseña es obligatoria";
        if (password.length() < 8)
            return "La contraseña debe tener al menos 8 caracteres";
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            return "La contraseña no puede superar los 72 caracteres";
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit))
            return "La contraseña debe incluir al menos una letra y un número";

        String especialidad = d.getEspecialidad();
        if (esVacio(especialidad) || especialidad.length() < 3 || especialidad.length() > 60
                || !PATRON_ESPECIALIDAD.matcher(especialidad).matches())
            return "La especialidad debe tener entre 3 y 60 caracteres y contener solo letras";

        if (d.getBiografia() != null && d.getBiografia().length() > 500)
            return "La biografía no puede superar los 500 caracteres";

        return null;
    }

    public boolean emailDuplicado(String email) {
        return personaRepo.findDoctorByEmail(email.trim()).isPresent();
    }

    public void guardar(Doctor doctor) {
        personaRepo.save(doctor);
        log.info("Doctor guardado: {}", doctor.getEmail());
    }

    /**
     * Elimina un doctor solo si nunca tuvo citas (si las tuvo, hay que desactivarlo para no dejar
     * citas huérfanas). Retorna null si se eliminó o el código de error.
     */
    public String eliminar(String id) {
        if (!citaRepo.findByDoctorId(id).isEmpty())
            return "tiene_citas";
        horarioRepo.deleteAll(horarioRepo.findByDoctorId(id));
        personaRepo.deleteById(id);
        log.info("Doctor eliminado: id={}", id);
        return null;
    }

    // ── Horarios ─────────────────────────────────────────────────────────────

    /**
     * Valida y guarda horarios para múltiples días en lote (un delete + saveAll).
     * Retorna null si OK, o el código de error si hay validación fallida.
     */
    @Transactional
    public String guardarHorariosSemanales(String doctorId, List<String> days,
            Map<String, String> allParams, int duracionCitaMinutos) {
        if (duracionCitaMinutos <= 0)
            return "duracion_invalida";
        if (duracionCitaMinutos > 480)
            return "duracion_excesiva";
        List<HorarioAtencion> nuevos = new ArrayList<>();
        List<DayOfWeek> diasAfectados = new ArrayList<>();

        for (String day : days) {
            String startStr = allParams.get("startTimes[" + day + "]");
            String endStr = allParams.get("endTimes[" + day + "]");
            if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty()) {
                return "missing_time_" + day;
            }

            DayOfWeek diaSemana;
            try {
                diaSemana = DayOfWeek.valueOf(day);
            } catch (IllegalArgumentException e) {
                return "dia_invalido_" + day;
            }

            LocalTime horaInicio;
            LocalTime horaFin;
            try {
                horaInicio = LocalTime.parse(startStr);
                horaFin = LocalTime.parse(endStr);
            } catch (DateTimeParseException e) {
                return "formato_hora_" + day;
            }

            if (!horaInicio.isBefore(horaFin))
                return "invalid_time_" + day;

            diasAfectados.add(diaSemana);
            HorarioAtencion h = new HorarioAtencion(doctorId, diaSemana, horaInicio, horaFin);
            h.setDuracionCitaMinutos(duracionCitaMinutos);
            nuevos.add(h);

            String start2 = allParams.get("startTimes2[" + day + "]");
            String end2 = allParams.get("endTimes2[" + day + "]");
            if ((start2 != null && !start2.isEmpty()) || (end2 != null && !end2.isEmpty())) {
                if (start2 == null || start2.isEmpty() || end2 == null || end2.isEmpty()) {
                    return "missing_time2_" + day;
                }
                LocalTime horaInicio2;
                LocalTime horaFin2;
                try {
                    horaInicio2 = LocalTime.parse(start2);
                    horaFin2 = LocalTime.parse(end2);
                } catch (DateTimeParseException e) {
                    return "formato_hora2_" + day;
                }
                if (!horaInicio2.isBefore(horaFin2))
                    return "invalid_time2_" + day;
                if (horaInicio2.isBefore(horaFin) && horaFin2.isAfter(horaInicio)) {
                    return "solapamiento_intervalos_" + day;
                }
                HorarioAtencion h2 = new HorarioAtencion(doctorId, diaSemana, horaInicio2, horaFin2);
                h2.setDuracionCitaMinutos(duracionCitaMinutos);
                nuevos.add(h2);
            }
        }

        // Un solo delete batch + un solo insert batch
        horarioRepo.deleteByDoctorIdAndDiaSemanaIn(doctorId, diasAfectados);
        horarioRepo.saveAll(nuevos);
        log.info("Horarios guardados para doctor {}: {} registros", doctorId, nuevos.size());
        return null;
    }

    public void eliminarHorario(String horarioId, String doctorId) {
        HorarioAtencion horario = horarioRepo.findById(horarioId).orElseThrow();
        if (!horario.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        horarioRepo.deleteById(horarioId);
    }

    // ── Estado de citas ──────────────────────────────────────────────────────

    public void marcarAsistio(String citaId, String doctorId) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        cita.setEstado(EstadoCita.ASISTIO);
        citaRepo.save(cita);
        log.info("Cita {} marcada ASISTIO por doctor {}", citaId, doctorId);
    }

    public void marcarNoAsistio(String citaId, String doctorId) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        cita.setEstado(EstadoCita.NO_ASISTIO);
        citaRepo.save(cita);
        log.info("Cita {} marcada NO_ASISTIO por doctor {}", citaId, doctorId);
    }

    public void cancelarCita(String citaId, String doctorId) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        cita.setEstado(EstadoCita.CANCELADA);
        citaRepo.save(cita);
        log.info("Cita {} CANCELADA por doctor {}", citaId, doctorId);
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CANCELADA, citaId));
    }

    public void guardarDictamen(String citaId, String doctorId,
            String diagnostico, String tratamiento, String observaciones) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        Dictamen dictamen = new Dictamen();
        dictamen.setDiagnostico(diagnostico);
        dictamen.setTratamiento(tratamiento);
        dictamen.setObservaciones(observaciones);
        cita.setDictamen(dictamen);
        cita.setEstado(EstadoCita.COMPLETADA);
        citaRepo.save(cita);
        log.info("Dictamen guardado para cita {} por doctor {}", citaId, doctorId);
    }
}