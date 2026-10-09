package com.gestion.proyectos.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lista única de especialidades: la usan el registro, el alta del admin y la validación del servidor. */
public final class Especialidades {

    public static final Map<String, List<String>> GRUPOS = new LinkedHashMap<>();

    static {
        GRUPOS.put("Atención Integral y Primaria", List.of("Geriatría", "Medicina Familiar", "Medicina General",
                "Medicina Interna", "Pediatría"));
        GRUPOS.put("Especialidades Médicas (Clínicas)", List.of("Alergología e Inmunología", "Cardiología",
                "Dermatología", "Endocrinología y Metabolismo", "Gastroenterología", "Ginecología y Obstetricia",
                "Hematología", "Infectología", "Nefrología", "Neumología", "Neurología", "Nutrición y Dietética",
                "Oftalmología", "Oncología Médica", "Otorrinolaringología", "Reumatología", "Urología"));
        GRUPOS.put("Especialidades Quirúrgicas", List.of("Cirugía Cardiovascular", "Cirugía General",
                "Cirugía Maxilofacial", "Cirugía Plástica y Estética", "Cirugía Vascular", "Neurocirugía",
                "Ortopedia y Traumatología"));
        GRUPOS.put("Salud Mental y Rehabilitación", List.of("Fisiatría (Medicina Física)", "Psicología Clínica",
                "Psiquiatría", "Terapia Ocupacional"));
        GRUPOS.put("Otras Especialidades", List.of("Medicina del Deporte",
                "Medicina del Dolor y Cuidados Paliativos", "Medicina Laboral", "Toxicología"));
    }

    private Especialidades() {}

    public static boolean esValida(String especialidad) {
        return especialidad != null && GRUPOS.values().stream().anyMatch(l -> l.contains(especialidad.trim()));
    }
}
