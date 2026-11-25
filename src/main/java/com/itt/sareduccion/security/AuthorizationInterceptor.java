package com.itt.sareduccion.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;

/**
 * Mecanismo central de seguridad de {@code /api/**}. En cada peticion:
 * <ol>
 *   <li>exige una sesion autenticada, salvo en las rutas publicas (401);</li>
 *   <li>valida el token CSRF en POST, PUT, PATCH y DELETE (403);</li>
 *   <li>comprueba el rol exigido por {@link RequiereRol} o por las reglas de
 *       prefijo de {@link RoleRules} (403).</li>
 * </ol>
 * Las reglas de negocio sobre datos concretos (por ejemplo, que una solicitud
 * pertenezca al alumno) siguen siendo responsabilidad del servicio de cada equipo.
 */
public class AuthorizationInterceptor implements HandlerInterceptor {

    private final RoleRules rules;
    private final SecurityAuditService auditService;
    private final String applicantLoginPath;
    private final String adminLoginPath;

    public AuthorizationInterceptor(RoleRules rules, SecurityAuditService auditService, String applicantLoginPath,
            String adminLoginPath) {
        this.rules = rules;
        this.auditService = auditService;
        this.applicantLoginPath = applicantLoginPath;
        this.adminLoginPath = adminLoginPath;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) {
            return true;
        }
        String path = path(request);
        HttpSession session = request.getSession(false);
        SessionPrincipal principal = SessionAttributes.principal(session);
        Set<AuthRole> requiredRoles = requiredRoles(handler, path);

        if (principal == null && !rules.isPublic(path)) {
            boolean expired = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
            SecurityResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    expired ? SecurityResponses.SESSION_EXPIRED : SecurityResponses.NOT_AUTHENTICATED,
                    expired ? "Su sesión expiró. Inicie sesión de nuevo." : "Inicie sesión para continuar.",
                    loginPath(requiredRoles));
            return false;
        }

        if (isMutating(method) && !SessionAttributes.validCsrf(session, suppliedCsrf(request))) {
            SecurityResponses.write(response, HttpServletResponse.SC_FORBIDDEN, SecurityResponses.CSRF_INVALID,
                    "Solicitud no válida. Recargue la página e intente de nuevo.", null);
            return false;
        }

        if (principal != null && requiredRoles != null && !requiredRoles.contains(principal.getRole())) {
            auditService.registrar(SecurityEvent.ACCESO_DENEGADO, principal.getIdentity(),
                    principal.getRole().getPerfil(), request.getRemoteAddr(), method + " " + path);
            SecurityResponses.write(response, HttpServletResponse.SC_FORBIDDEN, SecurityResponses.ACCESS_DENIED,
                    "No tiene permiso para realizar esta operación.", homePath(principal.getRole()));
            return false;
        }
        return true;
    }

    /** Pagina principal de cada rol; tambien la usa el login para redirigir. */
    public static String homePath(AuthRole role) {
        switch (role) {
            case ADMIN:
                return "/administrativo/panel-admin.html";
            case APPLICANT:
                return "/solicitante/panel-aspirante.html";
            default:
                return "/solicitante/panel-estatus.html";
        }
    }

    private Set<AuthRole> requiredRoles(Object handler, String path) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            RequiereRol annotation = handlerMethod.getMethodAnnotation(RequiereRol.class);
            if (annotation == null) {
                annotation = handlerMethod.getBeanType().getAnnotation(RequiereRol.class);
            }
            if (annotation != null && annotation.value().length > 0) {
                return EnumSet.copyOf(Arrays.asList(annotation.value()));
            }
        }
        return rules.rolesFor(path);
    }

    private String loginPath(Set<AuthRole> requiredRoles) {
        boolean adminOnly = requiredRoles != null && requiredRoles.size() == 1
                && requiredRoles.contains(AuthRole.ADMIN);
        return adminOnly ? adminLoginPath : applicantLoginPath;
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static String suppliedCsrf(HttpServletRequest request) {
        String header = request.getHeader(SessionAttributes.CSRF_HEADER);
        if (header != null) {
            return header;
        }
        // Solo formularios HTML clasicos; no fuerza la lectura de cuerpos JSON.
        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
            return request.getParameter(SessionAttributes.CSRF_PARAMETER);
        }
        return null;
    }

    /** Ruta ya decodificada y normalizada por el contenedor, sin el context path. */
    private static String path(HttpServletRequest request) {
        String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
        String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
        return servletPath + pathInfo;
    }
}
