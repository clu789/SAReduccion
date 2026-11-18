package com.itt.sareduccion.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

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
    /** IP del cliente con la que se abrio la sesion; la usa la auditoria de expiracion. */
    public static final String CLIENT_IP_ATTRIBUTE = "com.itt.sareduccion.security.clientIp";
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";
    /** Alternativa al encabezado para formularios HTML clasicos. */
    public static final String CSRF_PARAMETER = "_csrf";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    /** Token CSRF de la sesion; lo genera si todavia no existe. */
    public static String csrfToken(HttpSession session) {
        Object value = session.getAttribute(CSRF_ATTRIBUTE);
        if (value instanceof String) {
            return (String) value;
        }
        return rotateCsrfToken(session);
    }

    /** Sustituye el token CSRF de la sesion por uno nuevo. */
    public static String rotateCsrfToken(HttpSession session) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        session.setAttribute(CSRF_ATTRIBUTE, token);
        return token;
    }
}
