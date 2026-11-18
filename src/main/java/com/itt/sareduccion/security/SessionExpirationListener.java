package com.itt.sareduccion.security;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.springframework.stereotype.Component;

import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;

/**
 * Audita las sesiones autenticadas que el contenedor destruye por inactividad.
 * En el cierre de sesion explicito el controller retira antes el principal,
 * por lo que ese caso se registra como LOGOUT y no pasa por aqui.
 */
@Component
public class SessionExpirationListener implements HttpSessionListener {

    private final SecurityAuditService auditService;

    public SessionExpirationListener(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        // Sin accion: las sesiones anonimas no se auditan.
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        SessionPrincipal principal;
        Object ip;
        try {
            principal = SessionAttributes.principal(session);
            ip = session.getAttribute(SessionAttributes.CLIENT_IP_ATTRIBUTE);
        } catch (IllegalStateException alreadyInvalidated) {
            return;
        }
        if (principal != null) {
            auditService.registrar(SecurityEvent.SESION_EXPIRADA, principal.getIdentity(),
                    principal.getRole().getPerfil(), ip instanceof String ? (String) ip : null,
                    "Sesion cerrada por inactividad");
        }
    }
}
