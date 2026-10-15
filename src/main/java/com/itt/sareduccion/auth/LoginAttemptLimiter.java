package com.itt.sareduccion.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limitador de intentos de login en memoria (sin dependencias externas).
 * Reduce ataques automatizados contra el login provisional de aspirantes.
 * La clave combina origen de la peticion y ficha, de modo que un fallo solo
 * afecta a ese origen/ficha y no bloquea a otros usuarios.
 */
@Component
public class LoginAttemptLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(
            @Value("${sar.auth.max-attempts:5}") int maxAttempts,
            @Value("${sar.auth.attempt-window-seconds:900}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        synchronized (attempt) {
            if (isExpired(attempt)) {
                attempts.remove(key);
                return false;
            }
            return attempt.count >= maxAttempts;
        }
    }

    public void recordFailure(String key) {
        attempts.compute(key, (k, existing) -> {
            Attempt attempt = existing;
            if (attempt == null || isExpired(attempt)) {
                attempt = new Attempt();
            }
            attempt.count++;
            return attempt;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private boolean isExpired(Attempt attempt) {
        return System.currentTimeMillis() - attempt.windowStart >= windowMillis;
    }

    private static final class Attempt {
        private final long windowStart = System.currentTimeMillis();
        private int count;
    }
}
