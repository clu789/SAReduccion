package com.itt.sareduccion.auth;

public enum AuthRole {
    STUDENT("ESTUDIANTE"),
    ADMIN("ADMINISTRADOR"),
    APPLICANT("ASPIRANTE");

    private final String perfil;

    AuthRole(String perfil) {
        this.perfil = perfil;
    }

    /** Nombre del perfil usado en auditoria y logs (ESTUDIANTE, ADMINISTRADOR, ASPIRANTE). */
    public String getPerfil() {
        return perfil;
    }
}
