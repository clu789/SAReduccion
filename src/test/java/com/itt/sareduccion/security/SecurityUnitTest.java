package com.itt.sareduccion.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.LoginAttemptLimiter;
import com.itt.sareduccion.security.logging.SensitiveDataMasker;

/** Pruebas unitarias de las piezas comunes de seguridad. No requieren Spring ni Oracle. */
class SecurityUnitTest {

    // ------------------------------------------------------------ Rate limit

    @Test
    void limitadorBloqueaPorUsuarioYPorIpYSeLiberaSolo() {
        long[] reloj = {0L};
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, 20, 900, () -> reloj[0]);
        String usuario = LoginAttemptLimiter.userKey("ESTUDIANTE", "alu_21281181");
        String origen = LoginAttemptLimiter.originKey("10.0.0.5");

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(usuario);
        }
        assertFalse(limiter.isBlocked(usuario));
        limiter.recordFailure(usuario);
        assertTrue(limiter.isBlocked(usuario));
        assertEquals(900, limiter.retryAfterSeconds(usuario));
        // El mismo identificador en otro perfil es otra clave.
        assertFalse(limiter.isBlocked(LoginAttemptLimiter.userKey("ADMINISTRADOR", "alu_21281181")));
        // La clave no distingue mayusculas.
        assertTrue(limiter.isBlocked(LoginAttemptLimiter.userKey("ESTUDIANTE", "ALU_21281181")));

        for (int i = 0; i < 19; i++) {
            limiter.recordFailure(origen);
        }
        assertFalse(limiter.isBlocked(origen));
        limiter.recordFailure(origen);
        assertTrue(limiter.isBlocked(origen));

        reloj[0] = 899_000L;
        assertTrue(limiter.isBlocked(usuario));
        reloj[0] = 900_000L;
        assertFalse(limiter.isBlocked(usuario), "el bloqueo termina al cerrar la ventana");
        assertFalse(limiter.isBlocked(origen));

        limiter.recordFailure(usuario);
        limiter.reset(usuario);
        assertEquals(0, limiter.retryAfterSeconds(usuario));
    }

    // --------------------------------------------------------------- Captcha

    @Test
    void captchaDeImagenEsPngValidoYDeUnSoloUso() {
        CaptchaService captcha = new CaptchaService(true, 180);
        MockHttpSession session = new MockHttpSession();
        Map<String, Object> desafio = captcha.nuevoDesafio(session, false);

        String imagen = (String) desafio.get("imagen");
        assertTrue(imagen.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(imagen.substring("data:image/png;base64,".length()));
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
        assertFalse(desafio.containsValue(session.getAttribute(CaptchaService.ANSWER_ATTRIBUTE)),
                "la respuesta nunca viaja al cliente");

        String respuesta = (String) session.getAttribute(CaptchaService.ANSWER_ATTRIBUTE);
        assertTrue(captcha.verificar(session, " " + respuesta.toLowerCase() + " "));
        assertFalse(captcha.verificar(session, respuesta), "un desafio no puede reutilizarse");
    }

    @Test
    void captchaRechazaRespuestaIncorrectaVaciaOExpirada() {
        CaptchaService captcha = new CaptchaService(true, 180);
        MockHttpSession session = new MockHttpSession();
        captcha.nuevoDesafio(session, false);
        assertFalse(captcha.verificar(session, "ZZZZZZ"));
        captcha.nuevoDesafio(session, true);
        assertFalse(captcha.verificar(session, null));
        assertFalse(captcha.verificar(null, "1"));
        assertFalse(captcha.verificar(new MockHttpSession(), "1"), "sin desafio previo");

        CaptchaService expirado = new CaptchaService(true, -1);
        expirado.nuevoDesafio(session, true);
        assertFalse(expirado.verificar(session, (String) session.getAttribute(CaptchaService.ANSWER_ATTRIBUTE)));

        assertTrue(new CaptchaService(false, 180).verificar(null, null), "desactivado por configuracion");
    }

    @Test
    void captchaAccesibleSiempreTieneRespuestaNoNegativa() {
        CaptchaService captcha = new CaptchaService(true, 180);
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 200; i++) {
            Map<String, Object> desafio = captcha.nuevoDesafio(session, true);
            assertEquals("accesible", desafio.get("tipo"));
            assertTrue(((String) desafio.get("pregunta")).startsWith("¿Cuánto es "));
            assertTrue(Integer.parseInt((String) session.getAttribute(CaptchaService.ANSWER_ATTRIBUTE)) >= 0);
        }
    }

    // ---------------------------------------------------------- Enmascarado

    @Test
    void enmascaraDatosSensiblesEnLogs() {
        assertEquals("login password=**** ok", SensitiveDataMasker.mask("login password=Secreto123 ok"));
        assertEquals("{\"token\":\"****\",\"x\":1}", SensitiveDataMasker.mask("{\"token\":\"abc.def\",\"x\":1}"));
        assertEquals("curp MOPA************** fin", SensitiveDataMasker.mask("curp MOPA080512MDFRNN01 fin"));
        assertEquals("a j***@toluca.tecnm.mx", SensitiveDataMasker.mask("a juan.perez@toluca.tecnm.mx"));
        assertEquals("Contraseña: ****, CSRF=****", SensitiveDataMasker.mask("Contraseña: hola123, CSRF=xyz"));
        assertEquals("mensaje normal sin datos", SensitiveDataMasker.mask("mensaje normal sin datos"));
        assertEquals("******81", SensitiveDataMasker.identifier("21281181"));
        assertEquals("j***@example.com", SensitiveDataMasker.email("juan@example.com"));
        assertNull(SensitiveDataMasker.mask(null));
    }

    // -------------------------------------------------------- Reglas de rol

    @Test
    void reglasDeRolPorPrefijo() {
        RoleRules rules = new RoleRules()
                .permitAll("/api/auth/me")
                .require("/api/admin/**", AuthRole.ADMIN)
                .require("/api/solicitante/**", AuthRole.STUDENT, AuthRole.APPLICANT)
                .prepend("/api/reportes/**=admin, student");

        assertTrue(rules.isPublic("/api/auth/me"));
        assertFalse(rules.isPublic("/api/auth/me-otro"));
        assertEquals(EnumSet.of(AuthRole.ADMIN), rules.rolesFor("/api/admin/solicitudes/5"));
        assertEquals(EnumSet.of(AuthRole.ADMIN), rules.rolesFor("/api/admin"));
        assertNull(rules.rolesFor("/api/administrador"), "el prefijo no coincide con rutas parecidas");
        assertEquals(EnumSet.of(AuthRole.STUDENT, AuthRole.APPLICANT), rules.rolesFor("/api/solicitante/perfil"));
        assertEquals(EnumSet.of(AuthRole.ADMIN, AuthRole.STUDENT), rules.rolesFor("/api/reportes/1"));
        assertNull(rules.rolesFor("/api/otro"));
        assertThrows(IllegalArgumentException.class, () -> new RoleRules().prepend("sin-formato"));
    }

    // ------------------------------------------------------------------ OTP

    @Test
    void otpNoSeGuardaEnClaroEsDeUnSoloUsoYSeAgota() {
        String[] enviado = new String[1];
        OtpDelivery delivery = (correo, codigo, minutos) -> enviado[0] = codigo;
        OtpService otp = new OtpService(delivery, true, false, 600, 3, 60);
        ApplicantPrincipal principal = new ApplicantPrincipal("F2026A001", 1, 2026);
        MockHttpSession session = new MockHttpSession();

        otp.iniciar(session, principal, "aspirante@example.com");
        assertTrue(enviado[0].matches("\\d{6}"));
        assertEquals("a***@example.com", otp.destinoEnmascarado(session));
        String incorrecto = "000000".equals(enviado[0]) ? "111111" : "000000";
        assertEquals(OtpService.Resultado.INCORRECTO, otp.verificar(session, incorrecto));
        assertFalse(otp.reenviar(session), "el reenvio respeta el tiempo de espera");
        assertEquals(OtpService.Resultado.CORRECTO, otp.verificar(session, enviado[0]));
        assertSame(principal, otp.consumir(session));
        assertNull(otp.consumir(session), "el codigo no puede usarse dos veces");
        assertEquals(OtpService.Resultado.SIN_DESAFIO, otp.verificar(session, enviado[0]));

        otp.iniciar(session, principal, "aspirante@example.com");
        otp.verificar(session, incorrecto);
        otp.verificar(session, incorrecto);
        assertEquals(OtpService.Resultado.AGOTADO, otp.verificar(session, incorrecto));
        assertEquals(OtpService.Resultado.SIN_DESAFIO, otp.verificar(session, enviado[0]));
    }

    @Test
    void otpExpiraYNoFuncionaSinServicioDeCorreo() {
        ApplicantPrincipal principal = new ApplicantPrincipal("F2026A001", 1, 2026);
        MockHttpSession session = new MockHttpSession();

        OtpDelivery delivery = (correo, codigo, minutos) -> { };
        OtpService vencido = new OtpService(delivery, true, false, -1, 3, 60);
        vencido.iniciar(session, principal, "aspirante@example.com");
        assertEquals(OtpService.Resultado.EXPIRADO, vencido.verificar(session, "123456"));

        OtpService sinCorreo = new OtpService((OtpDelivery) null, true, false, 600, 3, 60);
        assertThrows(OtpService.OtpUnavailableException.class,
                () -> sinCorreo.iniciar(session, principal, "aspirante@example.com"));
        assertThrows(OtpService.OtpUnavailableException.class, () -> vencido.iniciar(session, principal, " "));
    }
}
