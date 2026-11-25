package com.itt.sareduccion.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Captcha de los tres inicios de sesion. Ofrece dos modalidades:
 * <ul>
 *   <li><b>imagen</b>: cinco caracteres dibujados en un PNG.</li>
 *   <li><b>accesible</b>: una operacion aritmetica redactada en texto, que un
 *       lector de pantalla puede leer (alternativa para quien no ve la imagen).</li>
 * </ul>
 * La respuesta esperada vive solo en la {@code HttpSession}, caduca y es de un
 * solo uso: cualquier verificacion (correcta o no) la consume, de modo que un
 * intento fallido obliga a pedir un desafio nuevo.
 */
@Service
public class CaptchaService {

    static final String ANSWER_ATTRIBUTE = "com.itt.sareduccion.security.captcha.answer";
    static final String EXPIRES_ATTRIBUTE = "com.itt.sareduccion.security.captcha.expires";

    private static final String[] NUMEROS = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete",
            "ocho", "nueve", "diez"};
    private static final int CODE_LENGTH = 5;

    private final SecureRandom random = new SecureRandom();
    private final boolean enabled;
    private final long ttlMillis;

    public CaptchaService(
            @Value("${sar.security.captcha.enabled:true}") boolean enabled,
            @Value("${sar.security.captcha.ttl-seconds:180}") long ttlSeconds) {
        this.enabled = enabled;
        this.ttlMillis = ttlSeconds * 1000L;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Crea un desafio nuevo (sustituye al anterior) y devuelve lo que debe mostrar el cliente. */
    public Map<String, Object> nuevoDesafio(HttpSession session, boolean accesible) {
        Map<String, Object> desafio = new LinkedHashMap<>();
        desafio.put("enabled", enabled);
        if (!enabled) {
            return desafio;
        }
        String respuesta;
        if (accesible) {
            int a = 1 + random.nextInt(9);
            int b = 1 + random.nextInt(9);
            boolean suma = random.nextBoolean() || a == b;
            if (!suma && a < b) {
                int swap = a;
                a = b;
                b = swap;
            }
            respuesta = String.valueOf(suma ? a + b : a - b);
            desafio.put("tipo", "accesible");
            desafio.put("pregunta", "¿Cuánto es " + NUMEROS[a] + (suma ? " más " : " menos ") + NUMEROS[b]
                    + "? Escriba el resultado con números.");
        } else {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CaptchaImageRenderer.ALPHABET.charAt(
                        random.nextInt(CaptchaImageRenderer.ALPHABET.length())));
            }
            respuesta = code.toString();
            byte[] png = CaptchaImageRenderer.render(respuesta, random);
            desafio.put("tipo", "imagen");
            desafio.put("imagen", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
            desafio.put("longitud", CODE_LENGTH);
        }
        session.setAttribute(ANSWER_ATTRIBUTE, respuesta);
        session.setAttribute(EXPIRES_ATTRIBUTE, System.currentTimeMillis() + ttlMillis);
        desafio.put("expiraEnSegundos", ttlMillis / 1000L);
        return desafio;
    }

    /**
     * Verifica y consume el desafio de la sesion. Si el captcha esta
     * desactivado por configuracion, siempre devuelve {@code true}.
     */
    public boolean verificar(HttpSession session, String respuesta) {
        if (!enabled) {
            return true;
        }
        if (session == null) {
            return false;
        }
        Object esperado = session.getAttribute(ANSWER_ATTRIBUTE);
        Object expira = session.getAttribute(EXPIRES_ATTRIBUTE);
        session.removeAttribute(ANSWER_ATTRIBUTE);
        session.removeAttribute(EXPIRES_ATTRIBUTE);
        if (!(esperado instanceof String) || !(expira instanceof Long) || respuesta == null) {
            return false;
        }
        if (System.currentTimeMillis() > (Long) expira) {
            return false;
        }
        String normalizada = respuesta.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (normalizada.isEmpty() || normalizada.length() > 16) {
            return false;
        }
        return MessageDigest.isEqual(((String) esperado).getBytes(StandardCharsets.UTF_8),
                normalizada.getBytes(StandardCharsets.UTF_8));
    }
}
