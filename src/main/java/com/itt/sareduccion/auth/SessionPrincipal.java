package com.itt.sareduccion.auth;

/**
 * Identidad autenticada almacenada en {@code HttpSession}. Es la base comun
 * para estudiantes, administradores y aspirantes, de modo que la autorizacion
 * por rol se resuelva siempre en el backend a partir de la sesion.
 */
public interface SessionPrincipal {

    AuthRole getRole();

    /** Usuario mostrado en la sesion (cuenta Oracle o ficha del aspirante). */
    String getUsername();

    /** Identificador de perfil validado por Oracle. */
    String getIdentity();

    /** Periodo del aspirante; {@code null} para estudiante/administrador. */
    Integer getPeriodo();

    /** Anio del aspirante; {@code null} para estudiante/administrador. */
    Integer getAnio();
}
