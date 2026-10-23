package com.itt.sareduccion.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.servlet.http.HttpSession;

/**
 * Claves de {@code HttpSession} y utilidades de CSRF compartidas por todos los
 * controllers. Las claves conservan exactamente los valores historicos de
 * {@code AuthController} ({@code class.getName() + sufijo}) para no invalidar
 * sesiones ya abiertas.
 */
public final class SessionAttributes {

    public static final String PRINCIPAL_ATTRIBUTE = "com.itt.sareduccion.controller.AuthController.principal";
    public static final String CSRF_ATTRIBUTE = "com.itt.sareduccion.controller.AuthController.csrf";
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private SessionAttributes() {
    }

    /** Principal autenticado de la sesion o {@code null} si no hay sesion valida. */
    public static SessionPrincipal principal(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PRINCIPAL_ATTRIBUTE);
        return value instanceof SessionPrincipal ? (SessionPrincipal) value : null;
    }

    /** Compara el token CSRF enviado contra el de la sesion de forma constante. */
    public static boolean validCsrf(HttpSession session, String suppliedToken) {
        if (session == null || suppliedToken == null) {
            return false;
        }
        Object expectedToken = session.getAttribute(CSRF_ATTRIBUTE);
        if (!(expectedToken instanceof String)) {
            return false;
        }
        return MessageDigest.isEqual(((String) expectedToken).getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }
}
