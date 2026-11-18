package com.itt.sareduccion.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.auth.AuthenticationService;
import com.itt.sareduccion.auth.LoginAttemptLimiter;
import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationRejectedException;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.controller.AuthController;

/**
 * Pruebas de seguridad de los accesos (Equipo 1): CSRF, captcha, fijacion de
 * sesion, expiracion, intentos repetidos, autorizacion por rol y OTP.
 *
 * <p>No requieren Oracle ni Docker: la validacion de credenciales se sustituye
 * por un doble de prueba y el resto es el codigo real (controller, interceptor,
 * filtros, limitador, captcha y OTP).
 */
class SecurityFlowTest {

    private static final String CSRF_HEADER = SessionAttributes.CSRF_HEADER;
    private static final int MAX_POR_USUARIO = 3;
    private static final int MAX_POR_IP = 6;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthenticationService authenticationService;
    private SecurityAuditService auditService;
    private String otpEnviado;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = construir(true, false);
    }

    private MockMvc construir(boolean captchaActivo, boolean otpActivo) {
        authenticationService = mock(AuthenticationService.class);
        auditService = mock(SecurityAuditService.class);
        // Un solo stub por metodo: las credenciales conocidas entran y el resto se rechaza.
        when(authenticationService.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            String password = invocation.getArgument(1);
            if ("ALU_21281181".equals(username) && "correcta".equals(password)) {
                return new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT);
            }
            if ("SA01".equals(username) && "correcta".equals(password)) {
                return new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN);
            }
            throw new AuthenticationRejectedException();
        });
        when(authenticationService.authenticateApplicant(anyString(), anyString())).thenAnswer(invocation -> {
            if ("F2026A001".equals(invocation.getArgument(0))
                    && "MOPA080512MDFRNN01".equals(invocation.getArgument(1))) {
                return new ApplicantPrincipal("F2026A001", 1, 2026);
            }
            throw new AuthenticationRejectedException();
        });
        when(authenticationService.correoAspirante(any(ApplicantPrincipal.class)))
                .thenReturn("aspirante@example.com");

        OtpDelivery delivery = (correo, codigo, minutos) -> otpEnviado = codigo;
        AuthController controller = new AuthController(authenticationService,
                new LoginAttemptLimiter(MAX_POR_USUARIO, MAX_POR_IP, 900, System::currentTimeMillis),
                new CaptchaService(captchaActivo, 180),
                new OtpService(delivery, otpActivo, false, 600, 3, 60),
                auditService);
        WebSecurityConfig config = new WebSecurityConfig(auditService, "", "/solicitante/login.html",
                "/administrativo/login.html");
        return MockMvcBuilders.standaloneSetup(controller, new ProbeController(), new AnnotatedProbeController())
                .addInterceptors(config.authorizationInterceptor(config.roleRules()))
                .addFilters(new CorrelationIdFilter(), new SecurityHeadersFilter(31536000))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------ CSRF

    @Test
    void loginSinTokenCsrfEsRechazadoAntesDeValidarCredenciales() throws Exception {
        MockHttpSession session = new MockHttpSession();
        SessionAttributes.csrfToken(session);
        mockMvc.perform(post("/api/auth/login").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "SA01", "password", "correcta")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        verify(authenticationService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void operacionDeNegocioQueModificaDatosExigeCsrf() throws Exception {
        MockHttpSession session = sesionDe(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        mockMvc.perform(put("/api/admin/probe").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        mockMvc.perform(put("/api/admin/probe").session(session).header(CSRF_HEADER, "token-falso"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/probe").session(session)
                .header(CSRF_HEADER, SessionAttributes.csrfToken(session)))
                .andExpect(status().isOk());
        // Las lecturas no requieren token.
        mockMvc.perform(get("/api/admin/probe").session(session)).andExpect(status().isOk());
    }

    // --------------------------------------------------------------- Captcha

    @Test
    void captchaIncorrectoImpideConsultarCredenciales() throws Exception {
        MockHttpSession session = new MockHttpSession();
        resolverCaptcha(session);
        mockMvc.perform(login(session, "SA01", "correcta", "RESPUESTA-MALA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CAPTCHA_INVALID"));
        verify(authenticationService, never()).authenticate(anyString(), anyString());
        verify(auditService).registrar(eq(SecurityEvent.CAPTCHA_FALLIDO), anyString(), anyString(), any(), any());
    }

    @Test
    void captchaEsDeUnSoloUso() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String respuesta = resolverCaptcha(session);
        mockMvc.perform(login(session, "SA01", "incorrecta", respuesta)).andExpect(status().isUnauthorized());
        // Reutilizar la misma respuesta ya no sirve: hay que pedir un desafio nuevo.
        mockMvc.perform(login(session, "SA01", "correcta", respuesta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CAPTCHA_INVALID"));
    }

    @Test
    void captchaAccesibleEntregaPreguntaEnTexto() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/auth/captcha").param("tipo", "accesible").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("accesible"))
                .andExpect(jsonPath("$.pregunta").isNotEmpty())
                .andExpect(jsonPath("$.imagen").doesNotExist());
        String respuesta = (String) session.getAttribute(CaptchaService.ANSWER_ATTRIBUTE);
        mockMvc.perform(login(session, "SA01", "correcta", respuesta)).andExpect(status().isOk());
    }

    // ------------------------------------------------- Sesion y fijacion de sesion

    @Test
    void loginCorrectoCambiaElIdentificadorDeSesion() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idAnterior = session.getId();
        mockMvc.perform(login(session, "ALU_21281181", "correcta", resolverCaptcha(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.identity").value("21281181"))
                .andExpect(jsonPath("$.home").value("/solicitante/panel-estatus.html"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertNotEquals(idAnterior, session.getId(), "el id de sesion debe cambiar al autenticar");
        SessionPrincipal principal = SessionAttributes.principal(session);
        assertNotNull(principal);
        assertEquals(AuthRole.STUDENT, principal.getRole());
        verify(auditService).registrar(eq(SecurityEvent.LOGIN_EXITOSO), eq("21281181"), eq("ESTUDIANTE"), any(),
                any());
    }

    @Test
    void cuentaDeOtroPerfilNoEntraPorElFormularioEquivocado() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String captcha = resolverCaptcha(session);
        mockMvc.perform(post("/api/auth/login").session(session)
                .header(CSRF_HEADER, SessionAttributes.csrfToken(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "SA01", "password", "correcta", "captcha", captcha,
                        "perfil", "ESTUDIANTE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        assertNull(SessionAttributes.principal(session));
    }

    @Test
    void logoutInvalidaLaSesionYExpiraLaCookie() throws Exception {
        MockHttpSession session = sesionDe(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        String csrf = SessionAttributes.csrfToken(session);
        mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        MvcResult result = mockMvc.perform(post("/api/auth/logout").session(session).header(CSRF_HEADER, csrf))
                .andExpect(status().isNoContent())
                .andReturn();
        assertTrue(session.isInvalid());
        Cookie cookie = result.getResponse().getCookie("JSESSIONID");
        assertNotNull(cookie);
        assertEquals(0, cookie.getMaxAge());
        verify(auditService).registrar(eq(SecurityEvent.LOGOUT), eq("SA01"), eq("ADMINISTRADOR"), any(), any());
    }

    @Test
    void sesionExpiradaRespondeFormatoUniforme() throws Exception {
        mockMvc.perform(get("/api/solicitante/probe").with(request -> {
            request.setRequestedSessionId("SESION-QUE-YA-EXPIRO");
            request.setRequestedSessionIdValid(false);
            return request;
        }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.redirect").value("/solicitante/login.html"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---------------------------------------------------- Autorizacion por rol

    @Test
    void sinSesionRespondeNoAutenticado() throws Exception {
        mockMvc.perform(get("/api/admin/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NOT_AUTHENTICATED"))
                .andExpect(jsonPath("$.redirect").value("/administrativo/login.html"));
        // Una ruta sin regla de rol tambien exige sesion.
        mockMvc.perform(get("/api/otro-modulo/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void rolSinPermisoRecibeAccesoDenegado() throws Exception {
        MockHttpSession estudiante = sesionDe(new AuthenticatedPrincipal("ALU_21281181", "21281181",
                AuthRole.STUDENT));
        mockMvc.perform(get("/api/admin/probe").session(estudiante))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.redirect").value("/solicitante/panel-estatus.html"));
        // Endpoints administrativos que existian fuera de /api/admin.
        mockMvc.perform(get("/api/solicitudes/probe").session(estudiante)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/aspirante/probe").session(estudiante)).andExpect(status().isForbidden());
        verify(auditService).registrar(eq(SecurityEvent.ACCESO_DENEGADO), eq("21281181"), eq("ESTUDIANTE"), any(),
                eq("GET /api/admin/probe"));
    }

    @Test
    void cadaRolAccedeSoloASuPrefijo() throws Exception {
        MockHttpSession admin = sesionDe(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        MockHttpSession estudiante = sesionDe(new AuthenticatedPrincipal("ALU_21281181", "21281181",
                AuthRole.STUDENT));
        MockHttpSession aspirante = sesionDe(new ApplicantPrincipal("F2026A001", 1, 2026));

        mockMvc.perform(get("/api/admin/probe").session(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/solicitudes/probe").session(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/estudiante/probe").session(estudiante)).andExpect(status().isOk());
        mockMvc.perform(get("/api/aspirante/probe").session(aspirante)).andExpect(status().isOk());
        mockMvc.perform(get("/api/solicitante/probe").session(estudiante)).andExpect(status().isOk());
        mockMvc.perform(get("/api/solicitante/probe").session(aspirante)).andExpect(status().isOk());
        mockMvc.perform(get("/api/solicitante/probe").session(admin)).andExpect(status().isForbidden());
        // Sin regla de rol: basta con estar autenticado.
        mockMvc.perform(get("/api/otro-modulo/probe").session(aspirante)).andExpect(status().isOk());
    }

    @Test
    void anotacionRequiereRolTienePrioridadSobreLasReglasDePrefijo() throws Exception {
        MockHttpSession admin = sesionDe(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        MockHttpSession aspirante = sesionDe(new ApplicantPrincipal("F2026A001", 1, 2026));
        mockMvc.perform(get("/api/anotado/probe").session(admin)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/anotado/probe").session(aspirante)).andExpect(status().isOk());
    }

    // ------------------------------------------------------ Intentos repetidos

    @Test
    void intentosRepetidosBloqueanTemporalmenteAlUsuario() throws Exception {
        for (int intento = 0; intento < MAX_POR_USUARIO; intento++) {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(login(session, "SA01", "incorrecta", resolverCaptcha(session)))
                    .andExpect(status().isUnauthorized());
        }
        // Ni siquiera la contrasena correcta entra durante el bloqueo.
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(login(session, "SA01", "correcta", resolverCaptcha(session)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));
        assertNull(SessionAttributes.principal(session));
        verify(auditService).registrar(eq(SecurityEvent.INTENTO_BLOQUEADO), eq("SA01"), eq("ADMINISTRADOR"), any(),
                any());
        // El bloqueo es por usuario: otra cuenta desde otra IP no se ve afectada.
        MockHttpSession otra = new MockHttpSession();
        mockMvc.perform(login(otra, "ALU_21281181", "correcta", resolverCaptcha(otra))
                .with(request -> {
                    request.setRemoteAddr("10.0.0.77");
                    return request;
                }))
                .andExpect(status().isOk());
    }

    @Test
    void intentosRepetidosBloqueanTemporalmenteLaIpDeOrigen() throws Exception {
        for (int intento = 0; intento < MAX_POR_IP; intento++) {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(login(session, "ALU_" + (1000 + intento), "incorrecta", resolverCaptcha(session))
                    .with(request -> {
                        request.setRemoteAddr("10.0.0.50");
                        return request;
                    }))
                    .andExpect(status().isUnauthorized());
        }
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(login(session, "SA01", "correcta", resolverCaptcha(session))
                .with(request -> {
                    request.setRemoteAddr("10.0.0.50");
                    return request;
                }))
                .andExpect(status().isTooManyRequests());
    }

    // ------------------------------------------------------- Aspirante y OTP

    @Test
    void aspiranteIngresaConFichaYCurpCuandoElOtpEstaDesactivado() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(loginAspirante(session, "F2026A001", "MOPA080512MDFRNN01", resolverCaptcha(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("APPLICANT"))
                .andExpect(jsonPath("$.curp").doesNotExist());
        assertNotNull(SessionAttributes.principal(session));
    }

    @Test
    void aspiranteDebeConfirmarElCodigoTemporalCuandoElOtpEstaActivo() throws Exception {
        mockMvc = construir(false, true);
        MockHttpSession session = new MockHttpSession();
        String csrf = SessionAttributes.csrfToken(session);

        mockMvc.perform(loginAspirante(session, "F2026A001", "MOPA080512MDFRNN01", ""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.otpRequired").value(true))
                .andExpect(jsonPath("$.destino").value("a***@example.com"));
        assertNull(SessionAttributes.principal(session), "sin codigo la sesion no queda autenticada");
        assertNotNull(otpEnviado);
        mockMvc.perform(get("/api/aspirante/probe").session(session)).andExpect(status().isUnauthorized());

        String incorrecto = "000000".equals(otpEnviado) ? "111111" : "000000";
        mockMvc.perform(post("/api/auth/login/aspirante/otp").session(session).header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON).content(json("codigo", incorrecto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("OTP_INVALID"));
        assertNull(SessionAttributes.principal(session));

        mockMvc.perform(post("/api/auth/login/aspirante/otp").session(session).header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON).content(json("codigo", otpEnviado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("APPLICANT"));
        assertNotNull(SessionAttributes.principal(session));
        mockMvc.perform(get("/api/aspirante/probe").session(session)).andExpect(status().isOk());
    }

    // ------------------------------------------------ Cabeceras y correlacion

    @Test
    void respuestasIncluyenCabecerasDeSeguridadYCorrelacion() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(CorrelationIdFilter.HEADER, "peticion-12345678"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "peticion-12345678"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
        // Un identificador con caracteres no permitidos se sustituye por uno nuevo.
        MvcResult result = mockMvc.perform(get("/api/auth/me").header(CorrelationIdFilter.HEADER, "malo\r\nX: 1"))
                .andReturn();
        String generado = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertNotNull(generado);
        assertTrue(generado.matches("[A-Za-z0-9-]{8,64}"));
        // HSTS solo por HTTPS.
        mockMvc.perform(get("/api/auth/me").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    // ---------------------------------------------------------------- Apoyo

    private MockHttpSession sesionDe(SessionPrincipal principal) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.PRINCIPAL_ATTRIBUTE, principal);
        SessionAttributes.csrfToken(session);
        return session;
    }

    /** Pide un captcha de imagen y devuelve la respuesta esperada guardada en la sesion. */
    private String resolverCaptcha(MockHttpSession session) throws Exception {
        mockMvc.perform(get("/api/auth/captcha").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("imagen"))
                .andExpect(jsonPath("$.imagen").isNotEmpty());
        return (String) session.getAttribute(CaptchaService.ANSWER_ATTRIBUTE);
    }

    private MockHttpServletRequestBuilder login(MockHttpSession session, String username, String password,
            String captcha) throws Exception {
        return post("/api/auth/login").session(session)
                .header(CSRF_HEADER, SessionAttributes.csrfToken(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", password, "captcha", captcha));
    }

    private MockHttpServletRequestBuilder loginAspirante(MockHttpSession session, String ficha, String curp,
            String captcha) throws Exception {
        return post("/api/auth/login/aspirante").session(session)
                .header(CSRF_HEADER, SessionAttributes.csrfToken(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("ficha", ficha, "curp", curp, "captcha", captcha));
    }

    private byte[] json(String... pares) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            body.put(pares[i], pares[i + 1]);
        }
        return objectMapper.writeValueAsBytes(body);
    }

    /** Endpoints de prueba bajo cada prefijo protegido. */
    @RestController
    static class ProbeController {

        @GetMapping({"/api/admin/probe", "/api/estudiante/probe", "/api/aspirante/probe",
                "/api/solicitante/probe", "/api/solicitudes/probe", "/api/otro-modulo/probe"})
        public String leer() {
            return "ok";
        }

        @PutMapping("/api/admin/probe")
        public String modificar() {
            return "ok";
        }

        @PostMapping("/api/admin/probe")
        public String crear() {
            return "ok";
        }
    }

    @RestController
    @RequiereRol(AuthRole.APPLICANT)
    static class AnnotatedProbeController {

        @GetMapping("/api/anotado/probe")
        public String leer() {
            return "ok";
        }
    }
}
