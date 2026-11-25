package com.itt.sareduccion.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.security.logging.SensitiveDataMasker;

/**
 * Verificacion de identidad de aspirantes con un codigo temporal de un solo uso
 * enviado a su correo. El codigo nunca se guarda en claro: en la sesion solo
 * queda su hash con sal, la vigencia y el contador de intentos.
 */
@Service
public class OtpService {

    public enum Resultado { CORRECTO, INCORRECTO, EXPIRADO, AGOTADO, SIN_DESAFIO }

    static final String PENDING_ATTRIBUTE = "com.itt.sareduccion.security.otp.pending";

    private static final Logger LOG = LoggerFactory.getLogger(OtpService.class);

    private final SecureRandom random = new SecureRandom();
    private final OtpDelivery delivery;
    private final boolean enabled;
    private final boolean logCode;
    private final long ttlMillis;
    private final int maxAttempts;
    private final long resendCooldownMillis;

    @Autowired
    public OtpService(ObjectProvider<OtpDelivery> delivery,
            @Value("${sar.security.otp.enabled:false}") boolean enabled,
            @Value("${sar.security.otp.log-code:false}") boolean logCode,
            @Value("${sar.security.otp.ttl-seconds:600}") long ttlSeconds,
            @Value("${sar.security.otp.max-attempts:5}") int maxAttempts,
            @Value("${sar.security.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this(delivery.getIfAvailable(), enabled, logCode, ttlSeconds, maxAttempts, resendCooldownSeconds);
    }

    /** Constructor para pruebas. */
    public OtpService(OtpDelivery delivery, boolean enabled, boolean logCode, long ttlSeconds, int maxAttempts,
            long resendCooldownSeconds) {
        this.delivery = delivery;
        this.enabled = enabled;
        this.logCode = logCode;
        this.ttlMillis = ttlSeconds * 1000L;
        this.maxAttempts = maxAttempts;
        this.resendCooldownMillis = resendCooldownSeconds * 1000L;
        if (enabled && delivery == null) {
            LOG.warn("OTP de aspirantes activo sin servicio de correo (OtpDelivery). "
                    + "Solo es utilizable en desarrollo con sar.security.otp.log-code=true.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTtlSeconds() {
        return ttlMillis / 1000L;
    }

    /** Genera y envia un codigo para el aspirante ya validado por ficha y CURP. */
    public void iniciar(HttpSession session, ApplicantPrincipal principal, String correo) {
        if (correo == null || correo.trim().isEmpty()) {
            throw new OtpUnavailableException("El aspirante no tiene correo registrado");
        }
        Pending pending = new Pending(principal, correo.trim());
        emitir(pending);
        session.setAttribute(PENDING_ATTRIBUTE, pending);
    }

    /** Reenvia un codigo nuevo. Devuelve {@code false} si no hay desafio o aun no pasa el tiempo de espera. */
    public boolean reenviar(HttpSession session) {
        Pending pending = pending(session);
        if (pending == null || System.currentTimeMillis() - pending.sentAt < resendCooldownMillis) {
            return false;
        }
        emitir(pending);
        return true;
    }

    /** Correo enmascarado del desafio pendiente, o {@code null} si no hay. */
    public String destinoEnmascarado(HttpSession session) {
        Pending pending = pending(session);
        return pending == null ? null : SensitiveDataMasker.email(pending.correo);
    }

    public Resultado verificar(HttpSession session, String codigo) {
        Pending pending = pending(session);
        if (pending == null) {
            return Resultado.SIN_DESAFIO;
        }
        if (System.currentTimeMillis() > pending.expiresAt) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            return Resultado.EXPIRADO;
        }
        if (pending.attempts >= maxAttempts) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            return Resultado.AGOTADO;
        }
        pending.attempts++;
        String normalizado = codigo == null ? "" : codigo.replaceAll("\\s+", "");
        boolean correcto = normalizado.matches("\\d{6}")
                && MessageDigest.isEqual(pending.hash, hash(pending.salt, normalizado));
        if (!correcto) {
            if (pending.attempts >= maxAttempts) {
                session.removeAttribute(PENDING_ATTRIBUTE);
                return Resultado.AGOTADO;
            }
            return Resultado.INCORRECTO;
        }
        return Resultado.CORRECTO;
    }

    /** Tras un resultado CORRECTO: entrega el principal y elimina el desafio (un solo uso). */
    public ApplicantPrincipal consumir(HttpSession session) {
        Pending pending = pending(session);
        if (pending == null) {
            return null;
        }
        session.removeAttribute(PENDING_ATTRIBUTE);
        return pending.principal;
    }

    public void cancelar(HttpSession session) {
        if (session != null) {
            session.removeAttribute(PENDING_ATTRIBUTE);
        }
    }

    private void emitir(Pending pending) {
        String codigo = String.format("%06d", random.nextInt(1000000));
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        pending.salt = salt;
        pending.hash = hash(salt, codigo);
        pending.attempts = 0;
        pending.sentAt = System.currentTimeMillis();
        pending.expiresAt = pending.sentAt + ttlMillis;

        if (delivery != null) {
            try {
                delivery.enviar(pending.correo, codigo, (int) Math.max(1L, ttlMillis / 60000L));
            } catch (RuntimeException exception) {
                LOG.warn("No se pudo enviar el OTP a {} ({})", SensitiveDataMasker.email(pending.correo),
                        exception.getClass().getSimpleName());
                throw new OtpUnavailableException("No se pudo enviar el codigo");
            }
        } else if (!logCode) {
            throw new OtpUnavailableException("No hay servicio de correo configurado");
        }
        if (logCode) {
            // Solo desarrollo. Los corchetes evitan el enmascarado automatico del log.
            LOG.warn("OTP-DEV para {} [{}]", SensitiveDataMasker.email(pending.correo), codigo);
        }
    }

    private Pending pending(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PENDING_ATTRIBUTE);
        return value instanceof Pending ? (Pending) value : null;
    }

    private static byte[] hash(byte[] salt, String codigo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(codigo.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** El codigo no pudo generarse o enviarse; el controller responde 503. */
    public static final class OtpUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OtpUnavailableException(String message) {
            super(message);
        }
    }

    private static final class Pending {
        private final ApplicantPrincipal principal;
        private final String correo;
        private byte[] salt;
        private byte[] hash;
        private long sentAt;
        private long expiresAt;
        private int attempts;

        private Pending(ApplicantPrincipal principal, String correo) {
            this.principal = principal;
            this.correo = correo;
        }
    }
}
