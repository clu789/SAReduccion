package com.itt.sareduccion.auth;

/**
 * Principal de aspirante autenticado de forma provisional (ficha + CURP).
 * Solo conserva la identidad validada por Oracle: ficha, periodo y anio.
 * Nunca almacena CURP, correo ni contrasenas.
 */
public final class ApplicantPrincipal implements SessionPrincipal {

    private final String ficha;
    private final int periodo;
    private final int anio;

    public ApplicantPrincipal(String ficha, int periodo, int anio) {
        this.ficha = ficha;
        this.periodo = periodo;
        this.anio = anio;
    }

    @Override
    public AuthRole getRole() {
        return AuthRole.APPLICANT;
    }

    @Override
    public String getUsername() {
        return ficha;
    }

    @Override
    public String getIdentity() {
        return ficha;
    }

    public String getFicha() {
        return ficha;
    }

    public int getPeriodoValue() {
        return periodo;
    }

    public int getAnioValue() {
        return anio;
    }

    @Override
    public Integer getPeriodo() {
        return periodo;
    }

    @Override
    public Integer getAnio() {
        return anio;
    }
}
