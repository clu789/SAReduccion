package com.itt.sareduccion.security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeceras de seguridad de todas las respuestas del backend. La API solo
 * devuelve JSON, asi que su CSP es la mas estricta posible; la CSP de las
 * paginas HTML la define Nginx. HSTS solo se emite cuando la peticion llego
 * por HTTPS (directo o segun X-Forwarded-Proto del proxy autorizado).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final long hstsMaxAgeSeconds;

    public SecurityHeadersFilter(@Value("${sar.security.hsts-max-age-seconds:31536000}") long hstsMaxAgeSeconds) {
        this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        if (request.isSecure() && hstsMaxAgeSeconds > 0) {
            response.setHeader("Strict-Transport-Security", "max-age=" + hstsMaxAgeSeconds + "; includeSubDomains");
        }
        chain.doFilter(request, response);
    }
}
