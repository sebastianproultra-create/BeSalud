package com.gestion.proyectos.controlador;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.NoSuchElementException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("mensaje", "El recurso solicitado no existe.");
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUpload(HttpServletRequest request, HttpServletResponse response, Model model) {
        if (request.getRequestURI().endsWith("/perfil/foto"))
            return "redirect:/doctores?fotoError=tamano#perfil";
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        model.addAttribute("status", 413);
        model.addAttribute("mensaje", "La imagen supera el máximo permitido de 2 MB.");
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("mensaje", "No tienes permisos para realizar esta acción.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception e, HttpServletResponse response, Model model) {
        // Las excepciones del propio Spring (ruta inexistente, parámetro faltante o mal escrito, método no permitido...)
        // traen su estado y cabeceras: se respetan en vez de convertirlas en 500.
        if (e instanceof ErrorResponse er) {
            er.getHeaders().forEach((nombre, valores) -> valores.forEach(v -> response.addHeader(nombre, v)));
            return vistaError(er.getStatusCode().value(), response, model);
        }
        if (e instanceof TypeMismatchException) {
            return vistaError(HttpStatus.BAD_REQUEST.value(), response, model);
        }
        log.error("Error no controlado", e);
        return vistaError(HttpStatus.INTERNAL_SERVER_ERROR.value(), response, model);
    }

    private String vistaError(int status, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("mensaje", switch (status) {
            case 404 -> "La página que buscas no existe.";
            case 405 -> "Esta acción no está permitida desde aquí.";
            default -> status < 500 ? "La solicitud no es válida." : "Ha ocurrido un error inesperado.";
        });
        return "error";
    }
}
