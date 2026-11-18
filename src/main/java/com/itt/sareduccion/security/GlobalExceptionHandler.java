package com.itt.sareduccion.security;

import java.util.Map;

import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manejo comun de errores no controlados: registra la excepcion completa en el
 * log (con su identificador de correlacion) y responde un 500 generico, sin
 * trazas ni mensajes internos.
 *
 * <p>No altera lo que ya funcionaba: los {@code @ExceptionHandler} de cada
 * controller tienen prioridad, y las excepciones que Spring ya traduce a un
 * estado HTTP (peticion mal formada, metodo no permitido, {@code @ResponseStatus},
 * {@code ResponseStatusException}) se relanzan para conservar ese estado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception exception) throws Exception {
        if (exception instanceof ServletException
                || exception instanceof HttpMessageConversionException
                || exception instanceof TypeMismatchException
                || exception instanceof BindException
                || exception instanceof ResponseStatusException
                || AnnotationUtils.findAnnotation(exception.getClass(), ResponseStatus.class) != null) {
            throw exception;
        }
        LOG.error("Error no controlado: {}", exception.getClass().getName(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponses.body(SecurityResponses.INTERNAL_ERROR,
                        "Ocurrió un error interno. Intente más tarde."));
    }
}
