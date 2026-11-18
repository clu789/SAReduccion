package com.itt.sareduccion.auth;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limitador de intentos de acceso en memoria (sin dependencias externas),
 * comun a los tres perfiles: estudiante, administrador y aspirante.
 *
 * <p>Maneja dos tipos de clave, cada una con su propio umbral:
 * <ul>
 *   <li>{@link #userKey(String, String)}: por perfil + identificador (cuenta,
 *       numero de control o ficha). Umbral {@code sar.auth.max-attempts}.</li>
 *   <li>{@link #originKey(String)}: por direccion IP de origen. Umbral
 *       {@code sar.auth.max-attempts-per-origin}, mas alto porque varias
 *       personas pueden compartir una IP (laboratorios, NAT).</li>
 * </ul>
 * El bloqueo es temporal y se libera solo al terminar la ventana
 * ({@code sar.auth.attempt-window-seconds}); no existe desbloqueo manual.
 */
@Component
public class LoginAttemptLimiter {

    private static final String USER_PREFIX = "U|";
    private static final String ORIGIN_PREFIX = "O|";
    private static final int PURGE_THRESHOLD = 10000;

    private final int maxAttempts;
    private final int maxAttemptsPerOrigin;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter(
            @Value("${sar.auth.max-attempts:5}") int maxAttempts,
            @Value("${sar.auth.max-attempts-per-origin:20}") int maxAttemptsPerOrigin,
            @Value("${sar.auth.attempt-window-seconds:900}") long windowSeconds) {
        this(maxAttempts, maxAttemptsPerOrigin, windowSeconds, System::currentTimeMillis);
    }

    public LoginAttemptLimiter(int maxAttempts, long windowSeconds) {
        this(maxAttempts, maxAttempts * 4, windowSeconds, System::currentTimeMillis);
    }

    /** Constructor para pruebas: permite controlar el reloj. */
    public LoginAttemptLimiter(int maxAttempts, int maxAttemptsPerOrigin, long windowSeconds, LongSupplier clock) {
        this.maxAttempts = maxAttempts;
        this.maxAttemptsPerOrigin = maxAttemptsPerOrigin;
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    /** Clave por perfil e identificador de usuario. */
    public static String userKey(String perfil, String identificador) {
        String id = identificador == null ? "" : identificador.trim().toUpperCase(Locale.ROOT);
        if (id.length() > 64) {
            id = id.substring(0, 64);
        }
        return USER_PREFIX + perfil + '|' + id;
    }

    /** Clave por direccion IP de origen. */
    public static String originKey(String ip) {
        return ORIGIN_PREFIX + (ip == null || ip.isEmpty() ? "unknown" : ip);
    }

    public boolean isBlocked(String key) {
        return retryAfterSeconds(key) > 0;
    }

    /** Segundos que faltan para liberar la clave; 0 si no esta bloqueada. */
    public long retryAfterSeconds(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return 0;
        }
        long elapsed = clock.getAsLong() - attempt.windowStart;
        if (elapsed >= windowMillis) {
            attempts.remove(key, attempt);
            return 0;
        }
        if (attempt.count < limitFor(key)) {
            return 0;
        }
        return Math.max(1L, (windowMillis - elapsed + 999L) / 1000L);
    }

    public void recordFailure(String key) {
        final long now = clock.getAsLong();
        attempts.compute(key, (k, existing) -> {
            Attempt attempt = existing;
            if (attempt == null || now - attempt.windowStart >= windowMillis) {
                attempt = new Attempt(now);
            }
            attempt.count++;
            return attempt;
        });
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.values().removeIf(attempt -> now - attempt.windowStart >= windowMillis);
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private int limitFor(String key) {
        return key.startsWith(ORIGIN_PREFIX) ? maxAttemptsPerOrigin : maxAttempts;
    }

    private static final class Attempt {
        private final long windowStart;
        private volatile int count;

        private Attempt(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
