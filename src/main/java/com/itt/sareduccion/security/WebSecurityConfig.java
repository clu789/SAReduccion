package com.itt.sareduccion.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.itt.sareduccion.auth.AuthRole;

/**
 * Registra el interceptor central de autorizacion sobre {@code /api/**} y
 * define las reglas de rol por prefijo. Para proteger rutas nuevas sin tocar
 * esta clase: usar un prefijo ya reglado, anotar el controller con
 * {@link RequiereRol} o declarar {@code sar.security.role-rules}.
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final SecurityAuditService auditService;
    private final String extraRules;
    private final String applicantLoginPath;
    private final String adminLoginPath;

    public WebSecurityConfig(SecurityAuditService auditService,
            @Value("${sar.security.role-rules:}") String extraRules,
            @Value("${sar.security.login-path.solicitante:/solicitante/login.html}") String applicantLoginPath,
            @Value("${sar.security.login-path.administrativo:/administrativo/login.html}") String adminLoginPath) {
        this.auditService = auditService;
        this.extraRules = extraRules;
        this.applicantLoginPath = applicantLoginPath;
        this.adminLoginPath = adminLoginPath;
    }

    @Bean
    public RoleRules roleRules() {
        return new RoleRules()
                // Rutas sin sesion: solo el flujo de autenticacion.
                .permitAll("/api/auth/me", "/api/auth/captcha", "/api/auth/login", "/api/auth/login/aspirante",
                        "/api/auth/login/aspirante/otp", "/api/auth/login/aspirante/otp/reenviar",
                        "/api/auth/logout")
                // Prefijos estandar para los endpoints nuevos de cada equipo.
                .require("/api/admin/**", AuthRole.ADMIN)
                .require("/api/estudiante/**", AuthRole.STUDENT)
                .require("/api/aspirante/**", AuthRole.APPLICANT)
                .require("/api/solicitante/**", AuthRole.STUDENT, AuthRole.APPLICANT)
                // Endpoints administrativos que ya existian fuera de /api/admin.
                .require("/api/solicitudes/**", AuthRole.ADMIN)
                .require("/api/convocatorias/**", AuthRole.ADMIN)
                .require("/api/montos/**", AuthRole.ADMIN)
                .require("/api/deducciones/**", AuthRole.ADMIN)
                .prepend(extraRules);
    }

    @Bean
    public AuthorizationInterceptor authorizationInterceptor(RoleRules roleRules) {
        return new AuthorizationInterceptor(roleRules, auditService, applicantLoginPath, adminLoginPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor(roleRules())).addPathPatterns("/api/**");
    }
}
