package com.itt.sareduccion.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.RespuestaSolicitud;
import com.itt.sareduccion.model.SolicitudAdmin;
import com.itt.sareduccion.repository.AdministradorRepository;

/**
 * Logica de negocio del panel administrativo. Toda operacion recibe el id del
 * administrador ya validado por la sesion (nunca del cliente) y delega en
 * {@code PKG_ADMINISTRADOR}. Los errores de Oracle se traducen a mensajes
 * genericos antes de salir del servicio.
 */
@Service
public class SolicitudAdminService {

    private static final int ESTATUS_PENDIENTE = 1;
    private static final int ESTATUS_AUTORIZADO = 2;
    private static final int ESTATUS_RECHAZADO = 3;
    private static final int ESTATUS_DEDUCCION_AUTORIZADO = 2;
    private static final int ESTATUS_DEDUCCION_CANCELADO = 4;

    private static final Map<Integer, String> ESTATUS_NOMBRES;
    private static final Map<Integer, String> TIPO_SOLICITUD_NOMBRES;

    static {
        Map<Integer, String> estatus = new LinkedHashMap<Integer, String>();
        estatus.put(1, "PENDIENTE");
        estatus.put(2, "AUTORIZADO");
        estatus.put(3, "RECHAZADO");
        estatus.put(4, "CANCELADO");
        estatus.put(5, "APLICADO");
        ESTATUS_NOMBRES = estatus;

        Map<Integer, String> tipos = new LinkedHashMap<Integer, String>();
        tipos.put(1, "Beca");
        tipos.put(2, "Credito");
        tipos.put(3, "Deduccion de inscripcion");
        TIPO_SOLICITUD_NOMBRES = tipos;
    }

    private final AdministradorRepository administradorRepository;

    public SolicitudAdminService(AdministradorRepository administradorRepository) {
        this.administradorRepository = administradorRepository;
    }

    public List<SolicitudAdmin> listar(String idAdministrador, Integer estatus, Long convocatoria,
            String solicitante) {
        List<SolicitudAdmin> solicitudes = administradorRepository.listarSolicitudes(idAdministrador);
        String filtro = solicitante == null ? null : solicitante.trim().toUpperCase(Locale.ROOT);
        List<SolicitudAdmin> resultado = new ArrayList<SolicitudAdmin>();
        for (SolicitudAdmin solicitud : solicitudes) {
            if (estatus != null && !estatus.equals(solicitud.getIdEstatusSolicitud())) {
                continue;
            }
            if (convocatoria != null && !convocatoria.equals(solicitud.getIdConvocatoria())) {
                continue;
            }
            if (filtro != null && !filtro.isEmpty()) {
                String identificador = solicitud.getSolicitante();
                boolean coincideFolio = String.valueOf(solicitud.getIdSolicitud()).contains(filtro);
                boolean coincideSolicitante = identificador != null
                        && identificador.toUpperCase(Locale.ROOT).contains(filtro);
                if (!coincideFolio && !coincideSolicitante) {
                    continue;
                }
            }
            resultado.add(solicitud);
        }
        return resultado;
    }

    public Map<String, Object> estadisticas(String idAdministrador) {
        List<SolicitudAdmin> solicitudes = administradorRepository.listarSolicitudes(idAdministrador);
        Map<Integer, Integer> conteos = new LinkedHashMap<Integer, Integer>();
        for (Integer key : ESTATUS_NOMBRES.keySet()) {
            conteos.put(key, 0);
        }
        for (SolicitudAdmin solicitud : solicitudes) {
            Integer idEstatus = solicitud.getIdEstatusSolicitud();
            if (idEstatus != null && conteos.containsKey(idEstatus)) {
                conteos.put(idEstatus, conteos.get(idEstatus) + 1);
            }
        }

        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("total", solicitudes.size());
        respuesta.put("pendiente", conteos.get(1));
        respuesta.put("autorizado", conteos.get(2));
        respuesta.put("rechazado", conteos.get(3));
        respuesta.put("cancelado", conteos.get(4));
        respuesta.put("aplicado", conteos.get(5));
        // Alias de compatibilidad con el panel actual (vocabulario previo).
        respuesta.put("aprobado", conteos.get(2));
        respuesta.put("correccion", conteos.get(3));
        return respuesta;
    }

    public DetalleSolicitud detalle(String idAdministrador, long idSolicitud) {
        SolicitudAdmin solicitud = buscar(idAdministrador, idSolicitud);
        if (solicitud == null) {
            throw new ReglaNegocioException("Solicitud no encontrada");
        }

        List<RespuestaSolicitud> respuestas = new ArrayList<RespuestaSolicitud>();
        for (RespuestaSolicitud respuesta : administradorRepository.listarRespuestas(idAdministrador)) {
            if (respuesta.getIdSolicitud() != null && respuesta.getIdSolicitud().longValue() == idSolicitud) {
                respuestas.add(respuesta);
            }
        }

        DeduccionAdmin deduccion = null;
        for (DeduccionAdmin candidata : administradorRepository.listarDeducciones(idAdministrador)) {
            if (candidata.getIdSolicitud() != null && candidata.getIdSolicitud().longValue() == idSolicitud) {
                deduccion = candidata;
                break;
            }
        }
        return new DetalleSolicitud(solicitud, respuestas, deduccion);
    }

    /**
     * Aprueba una solicitud pendiente y, en la misma transaccion, autoriza su
     * deduccion (la base exige que la solicitud ya este AUTORIZADA). El monto
     * se elige segun el tipo de solicitante; si el cliente no lo indica se usa
     * el id de la convocatoria como valor por defecto del entorno de desarrollo.
     */
    public void aprobar(String idAdministrador, long idSolicitud, Long idMontoInscripcion,
            Long idMontoReinscripcion, String comentario) {
        SolicitudAdmin solicitud = buscar(idAdministrador, idSolicitud);
        if (solicitud == null) {
            throw new ReglaNegocioException("Solicitud no encontrada");
        }
        if (!Integer.valueOf(ESTATUS_PENDIENTE).equals(solicitud.getIdEstatusSolicitud())) {
            throw new ReglaNegocioException("Solo se pueden aprobar solicitudes pendientes");
        }

        Long inscripcion = idMontoInscripcion;
        Long reinscripcion = idMontoReinscripcion;
        if (inscripcion == null && reinscripcion == null) {
            if (solicitud.esEstudiante()) {
                reinscripcion = solicitud.getIdConvocatoria();
            } else {
                inscripcion = solicitud.getIdConvocatoria();
            }
        }

        try {
            administradorRepository.cambiarEstatusSolicitud(idAdministrador, idSolicitud, ESTATUS_AUTORIZADO);
            administradorRepository.autorizarDeduccion(idAdministrador, idSolicitud, inscripcion,
                    reinscripcion, comentario);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "No se pudo aprobar la solicitud");
        }
    }

    public void rechazar(String idAdministrador, long idSolicitud, String comentario) {
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new ReglaNegocioException("El comentario es obligatorio para rechazar o solicitar correccion");
        }
        try {
            administradorRepository.cambiarEstatusSolicitud(idAdministrador, idSolicitud, ESTATUS_RECHAZADO);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "No se pudo rechazar la solicitud");
        }
    }

    public void cancelarDeduccion(String idAdministrador, long idDeduccion, String comentario) {
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new ReglaNegocioException("El comentario es obligatorio para cancelar una deduccion");
        }
        DeduccionAdmin deduccion = null;
        for (DeduccionAdmin candidata : administradorRepository.listarDeducciones(idAdministrador)) {
            if (candidata.getIdDeduccion() != null && candidata.getIdDeduccion().longValue() == idDeduccion) {
                deduccion = candidata;
                break;
            }
        }
        if (deduccion == null) {
            throw new ReglaNegocioException("Deduccion no encontrada");
        }
        if (!Integer.valueOf(ESTATUS_DEDUCCION_AUTORIZADO).equals(deduccion.getEstatusDeduccion())) {
            throw new ReglaNegocioException("Solo se pueden cancelar deducciones autorizadas");
        }

        try {
            administradorRepository.cambiarEstatusDeduccion(idAdministrador, idDeduccion,
                    ESTATUS_DEDUCCION_CANCELADO, comentario);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "No se pudo cancelar la deduccion");
        }
    }

    public List<DeduccionAdmin> listarDeducciones(String idAdministrador, Integer estatus) {
        List<DeduccionAdmin> deducciones = administradorRepository.listarDeducciones(idAdministrador);
        if (estatus == null) {
            return deducciones;
        }
        List<DeduccionAdmin> resultado = new ArrayList<DeduccionAdmin>();
        for (DeduccionAdmin deduccion : deducciones) {
            if (estatus.equals(deduccion.getEstatusDeduccion())) {
                resultado.add(deduccion);
            }
        }
        return resultado;
    }

    /** Nombre legible del estatus de solicitud (1..5). */
    public String nombreEstatus(Integer idEstatus) {
        String nombre = ESTATUS_NOMBRES.get(idEstatus);
        return nombre == null ? "DESCONOCIDO" : nombre;
    }

    /** Nombre legible del tipo de solicitud; cae a "Tipo <id>" si no se conoce. */
    public String nombreTipo(Integer idTipo) {
        if (idTipo == null) {
            return null;
        }
        String nombre = TIPO_SOLICITUD_NOMBRES.get(idTipo);
        return nombre == null ? "Tipo " + idTipo : nombre;
    }

    private SolicitudAdmin buscar(String idAdministrador, long idSolicitud) {
        for (SolicitudAdmin solicitud : administradorRepository.listarSolicitudes(idAdministrador)) {
            if (solicitud.getIdSolicitud() != null && solicitud.getIdSolicitud().longValue() == idSolicitud) {
                return solicitud;
            }
        }
        return null;
    }

    private ReglaNegocioException traducirError(DataAccessException exception, String mensajeGenerico) {
        if (hasOracleError(exception, 20823, 20824, 20825, 20826, 20827, 20019, 20020, 20021, 20022,
                20023, 20024, 20025, 20026, 20027)) {
            return new ReglaNegocioException(mensajeGenerico);
        }
        throw exception;
    }

    private boolean hasOracleError(DataAccessException exception, int... codigos) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException) {
                int codigo = ((SQLException) current).getErrorCode();
                for (int esperado : codigos) {
                    if (codigo == esperado) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Detalle completo de una solicitud: datos, respuestas y deduccion (si existe). */
    public static final class DetalleSolicitud {
        private final SolicitudAdmin solicitud;
        private final List<RespuestaSolicitud> respuestas;
        private final DeduccionAdmin deduccion;

        public DetalleSolicitud(SolicitudAdmin solicitud, List<RespuestaSolicitud> respuestas,
                DeduccionAdmin deduccion) {
            this.solicitud = solicitud;
            this.respuestas = respuestas;
            this.deduccion = deduccion;
        }

        public SolicitudAdmin getSolicitud() {
            return solicitud;
        }

        public List<RespuestaSolicitud> getRespuestas() {
            return respuestas;
        }

        public DeduccionAdmin getDeduccion() {
            return deduccion;
        }
    }
}
