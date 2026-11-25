package com.itt.sareduccion.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Formato uniforme de error de seguridad para toda la API:
 * <pre>{"error":"SESSION_EXPIRED","message":"...","redirect":"/solicitante/login.html","correlationId":"..."}</pre>
 * {@code error} es un codigo estable para el frontend; {@code message} es el
 * texto para la persona usuaria.
 */
public final class SecurityResponses {

    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String NOT_AUTHENTICATED = "NOT_AUTHENTICATED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String CSRF_INVALID = "CSRF_INVALID";
    public static final String CAPTCHA_INVALID = "CAPTCHA_INVALID";
    public static final String TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String AUTH_UNAVAILABLE = "AUTH_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecurityResponses() {
    }

    public static Map<String, Object> body(String code, String message) {
        return body(code, message, null);
    }

    public static Map<String, Object> body(String code, String message, String redirect) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        if (redirect != null) {
            body.put("redirect", redirect);
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        return body;
    }

    public static void write(HttpServletResponse response, int status, String code, String message, String redirect)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(body(code, message, redirect)));
    }
}
