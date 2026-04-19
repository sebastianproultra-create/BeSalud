package com.gestion.proyectos.excepcion;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Clase utilitaria para el manejo centralizado de errores en la aplicación
 */
public class ManejoDeErrores {

    // Constantes para tipos de error
    public static final String ERROR_HORARIO_INVALIDO = "horario_invalido";
    public static final String ERROR_CONFLICTO_CITA = "conflicto_cita";
    public static final String ERROR_ENTIDAD_NO_ENCONTRADA = "entidad_no_encontrada";
    public static final String ERROR_ACCESO_NO_AUTORIZADO = "acceso_no_autorizado";
    public static final String ERROR_GENERAL = "error_general";

    // Mensajes de error
    private static final String MSG_HORARIO_INVALIDO = "El horario seleccionado no está disponible para este médico";
    private static final String MSG_CONFLICTO_CITA = "El horario seleccionado ya está ocupado por otra cita";
    private static final String MSG_ENTIDAD_NO_ENCONTRADA = "No se encontró la información solicitada";
    private static final String MSG_ACCESO_NO_AUTORIZADO = "No tienes permisos para realizar esta acción";
    private static final String MSG_GENERAL = "Ha ocurrido un error. Por favor, inténtalo de nuevo";

    /**
     * Agrega un mensaje de error al modelo
     */
    public static void agregarErrorAlModelo(Model model, String tipoError) {
        String mensaje = obtenerMensajeError(tipoError);
        model.addAttribute("error", mensaje);
        model.addAttribute("errorCode", tipoError);
    }

    /**
     * Agrega un mensaje de error a los atributos de redirección
     */
    public static void agregarErrorARedireccion(RedirectAttributes redirectAttributes, String tipoError) {
        String mensaje = obtenerMensajeError(tipoError);
        redirectAttributes.addFlashAttribute("error", mensaje);
        redirectAttributes.addFlashAttribute("errorCode", tipoError);
    }

    /**
     * Agrega un mensaje de éxito al modelo
     */
    public static void agregarExitoAlModelo(Model model, String mensaje) {
        model.addAttribute("success", mensaje);
    }

    /**
     * Agrega un mensaje de éxito a los atributos de redirección
     */
    public static void agregarExitoARedireccion(RedirectAttributes redirectAttributes, String mensaje) {
        redirectAttributes.addFlashAttribute("success", mensaje);
    }

    /**
     * Obtiene el mensaje de error correspondiente al tipo
     */
    public static String obtenerMensajeError(String tipoError) {
        switch (tipoError) {
            case ERROR_HORARIO_INVALIDO:
                return MSG_HORARIO_INVALIDO;
            case ERROR_CONFLICTO_CITA:
                return MSG_CONFLICTO_CITA;
            case ERROR_ENTIDAD_NO_ENCONTRADA:
                return MSG_ENTIDAD_NO_ENCONTRADA;
            case ERROR_ACCESO_NO_AUTORIZADO:
                return MSG_ACCESO_NO_AUTORIZADO;
            case ERROR_GENERAL:
            default:
                return MSG_GENERAL;
        }
    }

    /**
     * Valida si un usuario tiene permisos para acceder a un recurso
     */
    public static boolean validarPermisosUsuario(String usuarioActual, String propietarioRecurso) {
        return usuarioActual != null && usuarioActual.equals(propietarioRecurso);
    }

    /**
     * Maneja errores de validación de citas
     */
    public static String manejarErrorValidacionCita(String tipoError, String doctorId, String citaId) {
        String baseUrl = "/pacientes/landing";

        if (citaId != null && !citaId.isEmpty()) {
            // Es reprogramación
            baseUrl = "/citas/" + citaId + "/reprogramar";
        } else if (doctorId != null && !doctorId.isEmpty()) {
            // Es agendamiento nuevo
            baseUrl = "/citas/nueva?doctorId=" + doctorId;
        }

        return "redirect:" + baseUrl + "?error=" + tipoError;
    }

    /**
     * Registra un error en los logs (puedes expandir esto)
     */
    public static void logError(String tipoError, String detalles) {
        System.err.println("ERROR [" + tipoError + "]: " + detalles);
        // Aquí podrías integrar con un logger como SLF4J
        // Logger logger = LoggerFactory.getLogger(ManejoDeErrores.class);
        // logger.error("Error {}: {}", tipoError, detalles);
    }
}