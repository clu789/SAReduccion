package com.itt.sareduccion.security.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enmascara datos sensibles antes de escribirlos en un log. Lo aplica
 * automaticamente {@link MaskingMessageConverter} a todos los mensajes; tambien
 * puede usarse de forma directa ({@code SensitiveDataMasker.email(correo)}).
 */
public final class SensitiveDataMasker {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|contrase(?:n|ñ)a|clave|token|csrf|otp|codigo|secret|authorization|jsessionid|cookie)"
                    + "(\"?\\s*[=:]\\s*\"?)([^\\s\",;&}\\]]+)");
    private static final Pattern CURP = Pattern.compile("\\b([A-Z]{4})\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)\\b");

    private SensitiveDataMasker() {
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = KEY_VALUE.matcher(message).replaceAll("$1$2****");
        masked = CURP.matcher(masked).replaceAll("$1**************");
        masked = EMAIL.matcher(masked).replaceAll("$1***@$2");
        return masked;
    }

    /** {@code alumno@toluca.tecnm.mx -> a***@toluca.tecnm.mx}. */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        Matcher matcher = EMAIL.matcher(email.trim());
        return matcher.matches() ? matcher.group(1) + "***@" + matcher.group(2) : "***";
    }

    /** Conserva solo los dos ultimos caracteres: {@code 21281181 -> ******81}. */
    public static String identifier(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        if (value.length() <= 2) {
            return "**";
        }
        StringBuilder masked = new StringBuilder(value.length());
        for (int i = 0; i < value.length() - 2; i++) {
            masked.append('*');
        }
        return masked.append(value, value.length() - 2, value.length()).toString();
    }
}
