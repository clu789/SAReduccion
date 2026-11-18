package com.itt.sareduccion.model;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionPrincipal;

/**
 * Identidad del estudiante o aspirante derivada exclusivamente de la sesion
 * autenticada. Nunca se construye con datos enviados por el navegador.
 */
public final class IdentidadSolicitante {

    private final String numeroControl;
    private final String ficha;
    private final Integer periodo;
    private final Integer anio;

    private IdentidadSolicitante(String numeroControl, String ficha, Integer periodo, Integer anio) {
        this.numeroControl = numeroControl;
        this.ficha = ficha;
        this.periodo = periodo;
        this.anio = anio;
    }

    /** Identidad del solicitante o {@code null} si el principal no es estudiante ni aspirante. */
    public static IdentidadSolicitante desde(SessionPrincipal principal) {
        if (principal == null || principal.getIdentity() == null) {
            return null;
        }
        if (principal.getRole() == AuthRole.STUDENT) {
            return new IdentidadSolicitante(principal.getIdentity(), null, null, null);
        }
        if (principal.getRole() == AuthRole.APPLICANT
                && principal.getPeriodo() != null && principal.getAnio() != null) {
            return new IdentidadSolicitante(null, principal.getIdentity(), principal.getPeriodo(),
                    principal.getAnio());
        }
        return null;
    }

    public static IdentidadSolicitante estudiante(String numeroControl) {
        return new IdentidadSolicitante(numeroControl, null, null, null);
    }

    public static IdentidadSolicitante aspirante(String ficha, int periodo, int anio) {
        return new IdentidadSolicitante(null, ficha, periodo, anio);
    }

    public boolean esEstudiante() {
        return numeroControl != null;
    }

    public String getNumeroControl() {
        return numeroControl;
    }

    public String getFicha() {
        return ficha;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public Integer getAnio() {
        return anio;
    }

    public String getTipo() {
        return esEstudiante() ? "ESTUDIANTE" : "ASPIRANTE";
    }

    /** Identificador enmascarado para logs: solo conserva los tres ultimos caracteres. */
    public String enmascarado() {
        String valor = esEstudiante() ? numeroControl : ficha;
        if (valor.length() <= 3) {
            return "***";
        }
        return "***" + valor.substring(valor.length() - 3);
    }
}
