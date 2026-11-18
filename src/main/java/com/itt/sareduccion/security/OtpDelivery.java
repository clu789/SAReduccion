package com.itt.sareduccion.security;

/**
 * Punto de integracion con el servicio de correo (Equipo 6). Basta con declarar
 * un bean de Spring que implemente esta interfaz para que {@link OtpService}
 * lo use; mientras no exista, el codigo no se envia por correo.
 */
public interface OtpDelivery {

    /**
     * Envia el codigo temporal al correo del aspirante.
     *
     * @param correo          destinatario registrado en Oracle
     * @param codigo          codigo de un solo uso
     * @param minutosVigencia minutos durante los que el codigo es valido
     * @throws RuntimeException si el envio falla; el acceso se rechaza con 503
     */
    void enviar(String correo, String codigo, int minutosVigencia);
}
