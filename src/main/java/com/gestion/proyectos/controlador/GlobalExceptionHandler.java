package com.gestion.proyectos.controlador;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
        // Rutas inexistentes, parámetros faltantes, etc. traen su propio estado (404, 400...): no son un 500.
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            int status = er.getStatusCode().value();
            response.setStatus(status);
            model.addAttribute("status", status);
            model.addAttribute("mensaje", status == 404 ? "La página que buscas no existe." : "La solicitud no es válida.");
            return "error";
        }
        log.error("Error no controlado", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("status", 500);
        model.addAttribute("mensaje", "Ha ocurrido un error inesperado.");
        return "error";
    }
}
