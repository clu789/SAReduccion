package com.itt.sareduccion.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.auth.AuthenticationService;
import com.itt.sareduccion.auth.LoginAttemptLimiter;
import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationRejectedException;
import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationUnavailableException;
import com.itt.sareduccion.auth.SessionPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String PRINCIPAL_ATTRIBUTE = AuthController.class.getName() + ".principal";
    private static final String CSRF_ATTRIBUTE = AuthController.class.getName() + ".csrf";
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private static final Pattern FICHA_PATTERN = Pattern.compile("^[A-Z0-9]{1,18}$");
    private static final Pattern CURP_PATTERN = Pattern.compile("^[A-Z0-9]{18}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationService authenticationService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(AuthenticationService authenticationService,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.authenticationService = authenticationService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @GetMapping("/me")
    public ResponseEntity<SessionResponse> me(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String csrfToken = csrfToken(session);
        SessionPrincipal principal = principal(session);
        SessionResponse response = principal == null
                ? SessionResponse.anonymous(csrfToken)
                : SessionResponse.authenticated(principal, csrfToken);
        return ResponseEntity.status(principal == null ? HttpStatus.UNAUTHORIZED : HttpStatus.OK).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!validCsrf(session, csrfHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Solicitud no válida"));
        }
        if (loginRequest == null || loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Credenciales o perfil no válidos"));
        }

        AuthenticatedPrincipal principal = authenticationService.authenticate(
                loginRequest.getUsername(), loginRequest.getPassword());
        request.changeSessionId();
        session.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        return ResponseEntity.ok(SessionResponse.authenticated(principal, csrfToken(session)));
    }

    /**
     * Autenticacion provisional de aspirantes (ficha + CURP). Reutiliza el
     * mismo mecanismo de CSRF y HttpSession que el login de estudiante/admin.
     * No solicita anio ni periodo: Oracle valida el anio vigente.
     */
    @PostMapping("/login/aspirante")
    public ResponseEntity<?> loginAspirante(@RequestBody ApplicantLoginRequest loginRequest,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!validCsrf(session, csrfHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Solicitud no válida"));
        }
        // Un intento fallido no debe conservar una identidad autenticada previa.
        clearPrincipal(session);

        String ficha = normalize(loginRequest == null ? null : loginRequest.getFicha());
        String curp = normalize(loginRequest == null ? null : loginRequest.getCurp());
        String limiterKey = limiterKey(request, ficha);
        if (loginAttemptLimiter.isBlocked(limiterKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("Demasiados intentos. Intente más tarde."));
        }
        if (!FICHA_PATTERN.matcher(ficha).matches() || !CURP_PATTERN.matcher(curp).matches()) {
            loginAttemptLimiter.recordFailure(limiterKey);
            throw new AuthenticationRejectedException();
        }

        try {
            ApplicantPrincipal principal = authenticationService.authenticateApplicant(ficha, curp);
            loginAttemptLimiter.reset(limiterKey);
            request.changeSessionId();
            session.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            return ResponseEntity.ok(SessionResponse.authenticated(principal, csrfToken(session)));
        } catch (AuthenticationRejectedException exception) {
            loginAttemptLimiter.recordFailure(limiterKey);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!validCsrf(session, csrfHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Solicitud no válida"));
        }
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AuthenticationRejectedException.class)
    public ResponseEntity<ErrorResponse> authenticationRejected() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Credenciales o perfil no válidos"));
    }

    @ExceptionHandler(AuthenticationUnavailableException.class)
    public ResponseEntity<ErrorResponse> authenticationUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("Autenticación temporalmente no disponible"));
    }

    private SessionPrincipal principal(HttpSession session) {
        Object value = session.getAttribute(PRINCIPAL_ATTRIBUTE);
        return value instanceof SessionPrincipal ? (SessionPrincipal) value : null;
    }

    private void clearPrincipal(HttpSession session) {
        if (session != null) {
            session.removeAttribute(PRINCIPAL_ATTRIBUTE);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String limiterKey(HttpServletRequest request, String ficha) {
        String origin = request.getRemoteAddr();
        return (origin == null ? "unknown" : origin) + '|' + ficha;
    }

    private String csrfToken(HttpSession session) {
        Object value = session.getAttribute(CSRF_ATTRIBUTE);
        if (value instanceof String) {
            return (String) value;
        }
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        session.setAttribute(CSRF_ATTRIBUTE, token);
        return token;
    }

    private boolean validCsrf(HttpSession session, String suppliedToken) {
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

    public static final class LoginRequest {
        private String username;
        private String password;

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
    }

    public static final class ApplicantLoginRequest {
        private String ficha;
        private String curp;

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
    }

    public static final class SessionResponse {
        private final boolean authenticated;
        private final String username;
        private final String identity;
        private final String role;
        private final Integer periodo;
        private final Integer anio;
        private final String csrfToken;

        private SessionResponse(boolean authenticated, String username, String identity, String role,
                Integer periodo, Integer anio, String csrfToken) {
            this.authenticated = authenticated;
            this.username = username;
            this.identity = identity;
            this.role = role;
            this.periodo = periodo;
            this.anio = anio;
            this.csrfToken = csrfToken;
        }

        private static SessionResponse anonymous(String csrfToken) {
            return new SessionResponse(false, null, null, null, null, null, csrfToken);
        }

        private static SessionResponse authenticated(SessionPrincipal principal, String csrfToken) {
            return new SessionResponse(true, principal.getUsername(), principal.getIdentity(),
                    principal.getRole().name(), principal.getPeriodo(), principal.getAnio(), csrfToken);
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
    }

    public static final class ErrorResponse {
        private final String error;

        private ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}