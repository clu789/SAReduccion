package com.itt.sareduccion.security;

/** Eventos admitidos por la restriccion CK_SEG_AUD_EVENTO de SEG_AUDITORIA_ACCESOS. */
public enum SecurityEvent {
    LOGIN_EXITOSO,
    LOGIN_FALLIDO,
    LOGOUT,
    INTENTO_BLOQUEADO,
    SESION_EXPIRADA,
    CAPTCHA_FALLIDO,
    OTP_ENVIADO,
    OTP_FALLIDO,
    ACCESO_DENEGADO
}
