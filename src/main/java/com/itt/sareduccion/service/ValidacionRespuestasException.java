package com.itt.sareduccion.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Respuestas invalidas (HTTP 400). Lleva un error por pregunta para marcarlo en pantalla. */
public class ValidacionRespuestasException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ErrorCampo> errores;

    public ValidacionRespuestasException(String message, List<ErrorCampo> errores) {
        super(message);
        this.errores = errores == null
                ? Collections.<ErrorCampo>emptyList()
                : Collections.unmodifiableList(new ArrayList<ErrorCampo>(errores));
    }

    public List<ErrorCampo> getErrores() {
        return errores;
    }

    /** Error de una pregunta concreta ({@code idPregunta} nulo si es un error general). */
    public static final class ErrorCampo {
        private final Long idPregunta;
        private final String mensaje;

        public ErrorCampo(Long idPregunta, String mensaje) {
            this.idPregunta = idPregunta;
            this.mensaje = mensaje;
        }

        public Long getIdPregunta() {
            return idPregunta;
        }

        public String getMensaje() {
            return mensaje;
        }
    }
}
