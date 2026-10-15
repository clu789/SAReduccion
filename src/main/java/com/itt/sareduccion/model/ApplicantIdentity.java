package com.itt.sareduccion.model;

/**
 * Identidad de aspirante validada por {@code PKG_ASPIRANTE.validar_aspirante}.
 * Solo contiene los identificadores que Oracle confirmo; nunca CURP ni correo.
 */
public final class ApplicantIdentity {

    private final String ficha;
    private final int periodo;
    private final int anio;

    public ApplicantIdentity(String ficha, int periodo, int anio) {
        this.ficha = ficha;
        this.periodo = periodo;
        this.anio = anio;
    }

    public String getFicha() {
        return ficha;
    }

    public int getPeriodo() {
        return periodo;
    }

    public int getAnio() {
        return anio;
    }
}
