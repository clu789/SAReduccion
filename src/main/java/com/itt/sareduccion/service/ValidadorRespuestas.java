package com.itt.sareduccion.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itt.sareduccion.model.EnvioSolicitudRequest.RespuestaEnviada;
import com.itt.sareduccion.model.FormularioDetalle;
import com.itt.sareduccion.model.FormularioOpcion;
import com.itt.sareduccion.model.FormularioPregunta;
import com.itt.sareduccion.service.ValidacionRespuestasException.ErrorCampo;

/**
 * Valida las respuestas contra el formulario y arma el JSON que esperan los
 * procedimientos de Oracle ({@code id_pregunta, id_opcion, texto_respuesta}).
 * Aplica las mismas reglas que el trigger de Respuestas para responder con
 * errores por pregunta antes de llegar a la base. Oracle las vuelve a validar.
 */
final class ValidadorRespuestas {

    static final int MAX_TEXTO = 4000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ValidadorRespuestas() {
    }

    static String validarYConstruirJson(FormularioDetalle formulario, List<RespuestaEnviada> enviadas) {
        List<ErrorCampo> errores = new ArrayList<ErrorCampo>();
        Map<Long, RespuestaEnviada> porPregunta = new LinkedHashMap<Long, RespuestaEnviada>();
        if (enviadas != null) {
            for (RespuestaEnviada enviada : enviadas) {
                if (enviada == null || enviada.getIdPregunta() == null) {
                    errores.add(new ErrorCampo(null, "Hay una respuesta sin pregunta asociada."));
                    continue;
                }
                Long idPregunta = enviada.getIdPregunta();
                if (formulario.buscarPregunta(idPregunta) == null) {
                    errores.add(new ErrorCampo(idPregunta, "La pregunta no pertenece al formulario."));
                    continue;
                }
                if (porPregunta.put(idPregunta, enviada) != null) {
                    errores.add(new ErrorCampo(idPregunta, "La pregunta está repetida."));
                }
            }
        }

        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (FormularioPregunta pregunta : formulario.getPreguntas()) {
            validarPregunta(pregunta, porPregunta.get(pregunta.getIdPregunta()), errores, salida);
        }

        if (!errores.isEmpty()) {
            throw new ValidacionRespuestasException("Revise las respuestas marcadas.", errores);
        }
        try {
            return MAPPER.writeValueAsString(salida);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No fue posible serializar las respuestas", exception);
        }
    }

    private static void validarPregunta(FormularioPregunta pregunta, RespuestaEnviada enviada,
            List<ErrorCampo> errores, List<Map<String, Object>> salida) {
        Long idPregunta = pregunta.getIdPregunta();
        String texto = enviada == null ? null : limpiar(enviada.getTexto());
        Long idOpcion = enviada == null ? null : enviada.getIdOpcion();

        if (texto == null && idOpcion == null) {
            if (pregunta.isObligatoria()) {
                errores.add(new ErrorCampo(idPregunta, "Esta pregunta es obligatoria."));
            }
            return; // una pregunta opcional sin respuesta no se envia
        }

        String tipo = pregunta.getTipo() == null ? "" : pregunta.getTipo();
        if (FormularioPregunta.TIPO_ABIERTA.equals(tipo)) {
            if (idOpcion != null) {
                errores.add(new ErrorCampo(idPregunta, "Esta pregunta no admite opciones."));
                return;
            }
            if (!textoValido(idPregunta, texto, errores)) {
                return;
            }
            salida.add(fila(idPregunta, null, texto));
        } else if (FormularioPregunta.TIPO_OPCION_UNICA.equals(tipo)) {
            if (idOpcion == null) {
                errores.add(new ErrorCampo(idPregunta, "Seleccione una opción."));
            } else if (pregunta.buscarOpcion(idOpcion) == null) {
                errores.add(new ErrorCampo(idPregunta, "La opción no pertenece a la pregunta."));
            } else if (texto != null) {
                errores.add(new ErrorCampo(idPregunta, "Esta pregunta no admite texto adicional."));
            } else {
                salida.add(fila(idPregunta, idOpcion, null));
            }
        } else if (FormularioPregunta.TIPO_MIXTA.equals(tipo)) {
            validarMixta(pregunta, idOpcion, texto, errores, salida);
        } else {
            errores.add(new ErrorCampo(idPregunta, "Tipo de pregunta no soportado."));
        }
    }

    private static void validarMixta(FormularioPregunta pregunta, Long idOpcion, String texto,
            List<ErrorCampo> errores, List<Map<String, Object>> salida) {
        Long idPregunta = pregunta.getIdPregunta();
        if (idOpcion == null) {
            errores.add(new ErrorCampo(idPregunta, "Seleccione una opción."));
            return;
        }
        FormularioOpcion opcion = pregunta.buscarOpcion(idOpcion);
        if (opcion == null) {
            errores.add(new ErrorCampo(idPregunta, "La opción no pertenece a la pregunta."));
            return;
        }
        if (opcion.isRequiereEspecificacion()) {
            if (texto == null) {
                errores.add(new ErrorCampo(idPregunta, "Especifique su respuesta."));
            } else if (textoValido(idPregunta, texto, errores)) {
                salida.add(fila(idPregunta, idOpcion, texto));
            }
        } else if (texto != null) {
            errores.add(new ErrorCampo(idPregunta, "La opción elegida no admite especificación."));
        } else {
            salida.add(fila(idPregunta, idOpcion, null));
        }
    }

    private static boolean textoValido(Long idPregunta, String texto, List<ErrorCampo> errores) {
        if (texto.length() > MAX_TEXTO) {
            errores.add(new ErrorCampo(idPregunta, "El texto no puede superar " + MAX_TEXTO + " caracteres."));
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                errores.add(new ErrorCampo(idPregunta, "El texto contiene caracteres no permitidos."));
                return false;
            }
        }
        return true;
    }

    /** Recorta espacios; un texto vacio equivale a "sin respuesta". */
    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String recortado = texto.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    private static Map<String, Object> fila(Long idPregunta, Long idOpcion, String texto) {
        Map<String, Object> fila = new LinkedHashMap<String, Object>();
        fila.put("id_pregunta", idPregunta);
        fila.put("id_opcion", idOpcion);
        fila.put("texto_respuesta", texto);
        return fila;
    }
}
