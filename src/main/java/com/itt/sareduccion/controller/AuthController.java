package com.itt.sareduccion.controller;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.auth.AuthenticationService;
import com.itt.sareduccion.auth.LoginAttemptLimiter;
import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationRejectedException;
import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationUnavailableException;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.security.AuthorizationInterceptor;
import com.itt.sareduccion.security.CaptchaService;
import com.itt.sareduccion.security.OtpService;
import com.itt.sareduccion.security.OtpService.OtpUnavailableException;
import com.itt.sareduccion.security.SecurityAuditService;
import com.itt.sareduccion.security.SecurityEvent;
import com.itt.sareduccion.security.SecurityResponses;

/**
 * Autenticacion de los tres perfiles (estudiante, administrador y aspirante)
 * sobre {@code HttpSession}. Orden de controles en cada inicio de sesion:
 * CSRF, bloqueo temporal (usuario e IP), captcha y, solo entonces, credenciales.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String PRINCIPAL_ATTRIBUTE = SessionAttributes.PRINCIPAL_ATTRIBUTE;
    private static final String CSRF_HEADER = SessionAttributes.CSRF_HEADER;
    private static final String PERFIL_ESTUDIANTE = AuthRole.STUDENT.getPerfil();
    private static final String PERFIL_ADMINISTRADOR = AuthRole.ADMIN.getPerfil();
    private static final String PERFIL_ASPIRANTE = AuthRole.APPLICANT.getPerfil();
    private static final String STUDENT_PREFIX = "ALU_";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_$#]{0,29}$");
    private static final Pattern FICHA_PATTERN = Pattern.compile("^[A-Z0-9]{1,18}$");
    private static final Pattern CURP_PATTERN = Pattern.compile("^[A-Z0-9]{18}$");

    private final AuthenticationService authenticationService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final CaptchaService captchaService;
    private final OtpService otpService;
    private final SecurityAuditService auditService;

    public AuthController(AuthenticationService authenticationService, LoginAttemptLimiter loginAttemptLimiter,
            CaptchaService captchaService, OtpService otpService, SecurityAuditService auditService) {
        this.authenticationService = authenticationService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.captchaService = captchaService;
        this.otpService = otpService;
        this.auditService = auditService;
    }

    @GetMapping("/me")
    public ResponseEntity<SessionResponse> me(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String csrfToken = SessionAttributes.csrfToken(session);
        SessionPrincipal principal = SessionAttributes.principal(session);
        SessionResponse response = principal == null
                ? SessionResponse.anonymous(csrfToken, captchaService.isEnabled())
                : SessionResponse.authenticated(principal, csrfToken, session.getMaxInactiveInterval());
        return ResponseEntity.status(principal == null ? HttpStatus.UNAUTHORIZED : HttpStatus.OK).body(response);
    }

    /** Desafio de captcha: {@code tipo=imagen} (predeterminado) o {@code tipo=accesible}. */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> captcha(
            @RequestParam(value = "tipo", defaultValue = "imagen") String tipo, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        SessionAttributes.csrfToken(session);
        return ResponseEntity.ok(captchaService.nuevoDesafio(session, "accesible".equalsIgnoreCase(tipo)));
    }

    /** Inicio de sesion de estudiante y de administrador (credenciales Oracle). */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest loginRequest,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        // Un intento nuevo nunca conserva una identidad autenticada previa.
        clearAuthentication(session);

        String ip = request.getRemoteAddr();
        String username = normalize(loginRequest == null ? null : loginRequest.getUsername());
        String password = loginRequest == null ? null : loginRequest.getPassword();
        String perfilSolicitado = normalize(loginRequest == null ? null : loginRequest.getPerfil());
        String perfil = perfilSolicitado.isEmpty()
                ? (username.startsWith(STUDENT_PREFIX) ? PERFIL_ESTUDIANTE : PERFIL_ADMINISTRADOR)
                : perfilSolicitado;
        if (!PERFIL_ESTUDIANTE.equals(perfil) && !PERFIL_ADMINISTRADOR.equals(perfil)) {
            return credencialesInvalidas();
        }
        String usuarioAuditado = USERNAME_PATTERN.matcher(username).matches() ? username : "INVALIDO";
        String userKey = LoginAttemptLimiter.userKey(perfil, usuarioAuditado);
        String originKey = LoginAttemptLimiter.originKey(ip);

        ResponseEntity<?> bloqueo = bloqueado(userKey, originKey, usuarioAuditado, perfil, ip);
        if (bloqueo != null) {
            return bloqueo;
        }
        if (!captchaService.verificar(session, loginRequest == null ? null : loginRequest.getCaptcha())) {
            return captchaInvalido(originKey, usuarioAuditado, perfil, ip);
        }
        if (username.isEmpty() || password == null || password.isEmpty()) {
            registrarFallo(userKey, originKey, usuarioAuditado, perfil, ip, "Datos incompletos");
            return credencialesInvalidas();
        }

        AuthenticatedPrincipal principal;
        try {
            principal = authenticationService.authenticate(username, password);
        } catch (AuthenticationRejectedException exception) {
            registrarFallo(userKey, originKey, usuarioAuditado, perfil, ip, "Credenciales o perfil no validos");
            return credencialesInvalidas();
        }
        // La cuenta es valida pero pertenece a otro perfil que el del formulario usado.
        if (!perfil.equals(principal.getRole().getPerfil())) {
            registrarFallo(userKey, originKey, usuarioAuditado, perfil, ip, "Perfil distinto al solicitado");
            return credencialesInvalidas();
        }

        loginAttemptLimiter.reset(userKey);
        return ResponseEntity.ok(establecerSesion(request, session, principal, ip));
    }

    /**
     * Inicio de sesion de aspirantes (ficha + CURP). Oracle valida el anio
     * vigente. Si la verificacion por correo esta activa
     * ({@code sar.security.otp.enabled}), responde 202 y la sesion no queda
     * autenticada hasta confirmar el codigo en {@code /login/aspirante/otp}.
     */
    @PostMapping("/login/aspirante")
    public ResponseEntity<?> loginAspirante(@RequestBody(required = false) ApplicantLoginRequest loginRequest,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        clearAuthentication(session);

        String ip = request.getRemoteAddr();
        String ficha = normalize(loginRequest == null ? null : loginRequest.getFicha());
        String curp = normalize(loginRequest == null ? null : loginRequest.getCurp());
        String fichaAuditada = FICHA_PATTERN.matcher(ficha).matches() ? ficha : "INVALIDO";
        String userKey = LoginAttemptLimiter.userKey(PERFIL_ASPIRANTE, fichaAuditada);
        String originKey = LoginAttemptLimiter.originKey(ip);

        ResponseEntity<?> bloqueo = bloqueado(userKey, originKey, fichaAuditada, PERFIL_ASPIRANTE, ip);
        if (bloqueo != null) {
            return bloqueo;
        }
        if (!captchaService.verificar(session, loginRequest == null ? null : loginRequest.getCaptcha())) {
            return captchaInvalido(originKey, fichaAuditada, PERFIL_ASPIRANTE, ip);
        }
        if (!FICHA_PATTERN.matcher(ficha).matches() || !CURP_PATTERN.matcher(curp).matches()) {
            registrarFallo(userKey, originKey, fichaAuditada, PERFIL_ASPIRANTE, ip, "Formato de ficha o CURP");
            return credencialesInvalidas();
        }

        ApplicantPrincipal principal;
        try {
            principal = authenticationService.authenticateApplicant(ficha, curp);
        } catch (AuthenticationRejectedException exception) {
            registrarFallo(userKey, originKey, fichaAuditada, PERFIL_ASPIRANTE, ip, "Ficha o CURP no validas");
            return credencialesInvalidas();
        }

        if (!otpService.isEnabled()) {
            loginAttemptLimiter.reset(userKey);
            return ResponseEntity.ok(establecerSesion(request, session, principal, ip));
        }

        otpService.iniciar(session, principal, authenticationService.correoAspirante(principal));
        auditService.registrar(SecurityEvent.OTP_ENVIADO, principal.getFicha(), PERFIL_ASPIRANTE, ip, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", false);
        body.put("otpRequired", true);
        body.put("destino", otpService.destinoEnmascarado(session));
        body.put("expiraEnSegundos", otpService.getTtlSeconds());
        body.put("csrfToken", SessionAttributes.csrfToken(session));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /** Segundo paso del aspirante: confirma el codigo temporal recibido por correo. */
    @PostMapping("/login/aspirante/otp")
    public ResponseEntity<?> verificarOtp(@RequestBody(required = false) OtpRequest otpRequest,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        String ip = request.getRemoteAddr();
        String originKey = LoginAttemptLimiter.originKey(ip);
        if (loginAttemptLimiter.isBlocked(originKey)) {
            auditService.registrar(SecurityEvent.INTENTO_BLOQUEADO, null, PERFIL_ASPIRANTE, ip, "Verificacion OTP");
            return demasiadosIntentos(loginAttemptLimiter.retryAfterSeconds(originKey));
        }

        OtpService.Resultado resultado = otpService.verificar(session, otpRequest == null ? null : otpRequest.getCodigo());
        if (resultado != OtpService.Resultado.CORRECTO) {
            loginAttemptLimiter.recordFailure(originKey);
            auditService.registrar(SecurityEvent.OTP_FALLIDO, null, PERFIL_ASPIRANTE, ip, resultado.name());
            boolean reiniciar = resultado != OtpService.Resultado.INCORRECTO;
            Map<String, Object> body = SecurityResponses.body(SecurityResponses.OTP_INVALID, reiniciar
                    ? "El código ya no es válido. Inicie sesión de nuevo."
                    : "Código incorrecto. Verifíquelo e intente de nuevo.");
            body.put("restart", reiniciar);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        ApplicantPrincipal principal = otpService.consumir(session);
        loginAttemptLimiter.reset(LoginAttemptLimiter.userKey(PERFIL_ASPIRANTE, principal.getFicha()));
        return ResponseEntity.ok(establecerSesion(request, session, principal, ip));
    }

    @PostMapping("/login/aspirante/otp/reenviar")
    public ResponseEntity<?> reenviarOtp(@RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        if (!otpService.reenviar(session)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(SecurityResponses.body(
                    SecurityResponses.TOO_MANY_ATTEMPTS, "Espere un momento antes de solicitar otro código."));
        }
        auditService.registrar(SecurityEvent.OTP_ENVIADO, null, PERFIL_ASPIRANTE, request.getRemoteAddr(), "Reenvio");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        SessionPrincipal principal = SessionAttributes.principal(session);
        if (principal != null) {
            auditService.registrar(SecurityEvent.LOGOUT, principal.getIdentity(), principal.getRole().getPerfil(),
                    request.getRemoteAddr(), null);
        }
        // Se retira antes de invalidar para que no se audite como expiracion.
        session.removeAttribute(PRINCIPAL_ATTRIBUTE);
        session.invalidate();
        expirarCookieDeSesion(request, response);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AuthenticationRejectedException.class)
    public ResponseEntity<Map<String, Object>> authenticationRejected() {
        return credencialesInvalidas();
    }

    @ExceptionHandler({AuthenticationUnavailableException.class, OtpUnavailableException.class})
    public ResponseEntity<Map<String, Object>> authenticationUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SecurityResponses.body(
                SecurityResponses.AUTH_UNAVAILABLE, "Autenticación temporalmente no disponible"));
    }

    /**
     * Autentica la sesion: cambia su identificador (evita fijacion de sesion)
     * y registra el acceso. El token CSRF se conserva porque pertenece a la
     * misma sesion y nunca viaja en la URL ni en una cookie.
     */
    private SessionResponse establecerSesion(HttpServletRequest request, HttpSession session,
            SessionPrincipal principal, String ip) {
        request.changeSessionId();
        session.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        session.setAttribute(SessionAttributes.CLIENT_IP_ATTRIBUTE, ip);
        String csrfToken = SessionAttributes.csrfToken(session);
        auditService.registrar(SecurityEvent.LOGIN_EXITOSO, principal.getIdentity(), principal.getRole().getPerfil(),
                ip, null);
        return SessionResponse.authenticated(principal, csrfToken, session.getMaxInactiveInterval());
    }

    private ResponseEntity<?> bloqueado(String userKey, String originKey, String usuario, String perfil, String ip) {
        long espera = Math.max(loginAttemptLimiter.retryAfterSeconds(userKey),
                loginAttemptLimiter.retryAfterSeconds(originKey));
        if (espera <= 0) {
            return null;
        }
        auditService.registrar(SecurityEvent.INTENTO_BLOQUEADO, usuario, perfil, ip,
                "Bloqueo temporal por intentos repetidos");
        return demasiadosIntentos(espera);
    }

    private ResponseEntity<?> demasiadosIntentos(long esperaSegundos) {
        Map<String, Object> body = SecurityResponses.body(SecurityResponses.TOO_MANY_ATTEMPTS,
                "Demasiados intentos. Intente más tarde.");
        body.put("retryAfterSeconds", esperaSegundos);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(esperaSegundos))
                .body(body);
    }

    private ResponseEntity<?> captchaInvalido(String originKey, String usuario, String perfil, String ip) {
        loginAttemptLimiter.recordFailure(originKey);
        auditService.registrar(SecurityEvent.CAPTCHA_FALLIDO, usuario, perfil, ip, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SecurityResponses.body(
                SecurityResponses.CAPTCHA_INVALID, "La verificación de seguridad no es correcta. Intente de nuevo."));
    }

    private void registrarFallo(String userKey, String originKey, String usuario, String perfil, String ip,
            String detalle) {
        loginAttemptLimiter.recordFailure(userKey);
        loginAttemptLimiter.recordFailure(originKey);
        auditService.registrar(SecurityEvent.LOGIN_FALLIDO, usuario, perfil, ip, detalle);
    }

    private ResponseEntity<Map<String, Object>> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SecurityResponses.body(
                SecurityResponses.INVALID_CREDENTIALS, "Credenciales o perfil no válidos"));
    }

    private ResponseEntity<Map<String, Object>> csrfInvalido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SecurityResponses.body(
                SecurityResponses.CSRF_INVALID, "Solicitud no válida. Recargue la página e intente de nuevo."));
    }

    private void clearAuthentication(HttpSession session) {
        session.removeAttribute(PRINCIPAL_ATTRIBUTE);
        otpService.cancelar(session);
    }

    private void expirarCookieDeSesion(HttpServletRequest request, HttpServletResponse response) {
        String name = request.getServletContext().getSessionCookieConfig().getName();
        Cookie cookie = new Cookie(name == null ? "JSESSIONID" : name, "");
        String contextPath = request.getContextPath();
        cookie.setPath(contextPath == null || contextPath.isEmpty() ? "/" : contextPath);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static final class LoginRequest {
        private String username;
        private String password;
        private String captcha;
        private String perfil;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }

        /** Opcional: ESTUDIANTE o ADMINISTRADOR, segun el formulario de acceso usado. */
        public String getPerfil() {
            return perfil;
        }

        public void setPerfil(String perfil) {
            this.perfil = perfil;
        }
    }

    public static final class ApplicantLoginRequest {
        private String ficha;
        private String curp;
        private String captcha;

        public String getFicha() {
            return ficha;
        }

        public void setFicha(String ficha) {
            this.ficha = ficha;
        }

        public String getCurp() {
            return curp;
        }

        public void setCurp(String curp) {
            this.curp = curp;
        }

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }
    }

    public static final class OtpRequest {
        private String codigo;

        public String getCodigo() {
            return codigo;
        }

        public void setCodigo(String codigo) {
            this.codigo = codigo;
        }
    }

    public static final class SessionResponse {
        private final boolean authenticated;
        private final String username;
        private final String identity;
        private final String role;
        private final Integer periodo;
        private final Integer anio;
        private final String csrfToken;
        private final String home;
        private final Integer sessionTimeoutSeconds;
        private final Boolean captchaRequired;

        private SessionResponse(boolean authenticated, String username, String identity, String role,
                Integer periodo, Integer anio, String csrfToken, String home, Integer sessionTimeoutSeconds,
                Boolean captchaRequired) {
            this.authenticated = authenticated;
            this.username = username;
            this.identity = identity;
            this.role = role;
            this.periodo = periodo;
            this.anio = anio;
            this.csrfToken = csrfToken;
            this.home = home;
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
            this.captchaRequired = captchaRequired;
        }

        private static SessionResponse anonymous(String csrfToken, boolean captchaRequired) {
            return new SessionResponse(false, null, null, null, null, null, csrfToken, null, null, captchaRequired);
        }

        private static SessionResponse authenticated(SessionPrincipal principal, String csrfToken,
                int sessionTimeoutSeconds) {
            return new SessionResponse(true, principal.getUsername(), principal.getIdentity(),
                    principal.getRole().name(), principal.getPeriodo(), principal.getAnio(), csrfToken,
                    AuthorizationInterceptor.homePath(principal.getRole()), sessionTimeoutSeconds, null);
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public String getUsername() {
            return username;
        }

        public String getIdentity() {
            return identity;
        }

        public String getRole() {
            return role;
        }

        public Integer getPeriodo() {
            return periodo;
        }

        public Integer getAnio() {
            return anio;
        }

        public String getCsrfToken() {
            return csrfToken;
        }

        public String getHome() {
            return home;
        }

        public Integer getSessionTimeoutSeconds() {
            return sessionTimeoutSeconds;
        }

        public Boolean getCaptchaRequired() {
            return captchaRequired;
        }
    }
}
