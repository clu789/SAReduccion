package com.itt.sareduccion.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.model.ConvocatoriaAdmin;
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
    private static final int ESTATUS_CANCELADO = 4;
    private static final int ESTATUS_DEDUCCION_AUTORIZADO = 2;

    private static final Map<Integer, String> ESTATUS_NOMBRES;
    private static final Map<Integer, String> TIPO_SOLICITUD_NOMBRES;

    static {
        Map<Integer, String> estatus = new LinkedHashMap<Integer, String>();
        estatus.put(1, "PENDIENTE");
        estatus.put(2, "AUTORIZADO");
        estatus.put(3, "RECHAZADO");
        estatus.put(4, "CANCELADO");
        estatus.put(5, "APLICADO");
        estatus.put(6, "RETIRADO");
        ESTATUS_NOMBRES = estatus;

        Map<Integer, String> tipos = new LinkedHashMap<Integer, String>();
        tipos.put(1, "Hijo de trabajador");
        tipos.put(2, "Hijo de homologado");
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

    public List<ConvocatoriaAdmin> listarConvocatorias(String idAdministrador) {
        return administradorRepository.listarConvocatorias(idAdministrador);
    }

    public Map<String, Object> estadisticas(String idAdministrador) {
        return estadisticas(idAdministrador, null);
    }

    public Map<String, Object> estadisticas(String idAdministrador, Long convocatoria) {
        List<SolicitudAdmin> todas = administradorRepository.listarSolicitudes(idAdministrador);
        List<SolicitudAdmin> solicitudes = new ArrayList<SolicitudAdmin>();
        for (SolicitudAdmin solicitud : todas) {
            if (convocatoria == null || convocatoria.equals(solicitud.getIdConvocatoria())) {
                solicitudes.add(solicitud);
            }
        }
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
        respuesta.put("retirado", conteos.get(6));
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
     * Aprueba (o reautoriza) una solicitud y, en la misma transaccion, autoriza
     * su deduccion. Admite solicitudes PENDIENTE o CANCELADO, igual que
     * {@code autorizar_deduccion} (el panel expone esto con dos botones
     * distintos — "Aprobar" y "Reautorizar" — pero ambos llaman este metodo).
     * El monto se elige segun el tipo de solicitante.
     */
    public void aprobar(String idAdministrador, long idSolicitud, Long idMontoInscripcion,
            Long idMontoReinscripcion, String comentario) {
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new ReglaNegocioException("El comentario es obligatorio para autorizar la solicitud");
        }
        SolicitudAdmin solicitud = buscar(idAdministrador, idSolicitud);
        if (solicitud == null) {
            throw new ReglaNegocioException("Solicitud no encontrada");
        }
        Integer estatusActual = solicitud.getIdEstatusSolicitud();
        if (!Integer.valueOf(ESTATUS_PENDIENTE).equals(estatusActual)
                && !Integer.valueOf(ESTATUS_CANCELADO).equals(estatusActual)) {
            throw new ReglaNegocioException("Solo se pueden aprobar solicitudes pendientes o canceladas");
        }

        // TODO(#4 - decidir con el equipo de BD): no existe un catalogo consultable
        // de ids de monto. autorizar_deduccion exige exactamente uno de
        // p_id_monto_inscripcion/p_id_monto_reinscripcion (si no, ORA-20871).
        // Usar el id de convocatoria como id de monto (como se hacia antes del
        // merge) produce NO_DATA_FOUND en obtener_montos. Opciones a acordar:
        //   a) Agregar PKG_ADMINISTRADOR.listar_montos(p_admin, p_resultado) que
        //      devuelva ambos catalogos (Monto_Inscripciones / Monto_Reinscripciones)
        //      y que este servicio elija MIN(id_monto_reinscripcion) para estudiante
        //      / MIN(id_monto_inscripcion) para aspirante (igual que hace el seed).
        //   b) Que el frontend obtenga el id de algun catalogo ya expuesto y lo
        //      envie explicito en AprobarRequest (requeriria exponer un endpoint
        //      de consulta de montos).
        // No se implementa ninguna opcion aqui: requiere acuerdo con el equipo de BD.
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
            // autorizar_deduccion realiza por si sola la transicion de estatus
            // de la solicitud (1 o 4 -> 2); no se llama ningun cambio de estatus aparte.
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
            administradorRepository.rechazarSolicitud(idAdministrador, idSolicitud, comentario);
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
            administradorRepository.cancelarDeduccion(idAdministrador, idDeduccion, comentario);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "No se pudo cancelar la deduccion");
        }
    }

    /** Aplica una deduccion autorizada: estado definitivo e inmutable. */
    public void aplicarDeduccion(String idAdministrador, long idDeduccion) {
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
            throw new ReglaNegocioException("Solo se pueden aplicar deducciones autorizadas");
        }

        try {
            administradorRepository.aplicarDeduccion(idAdministrador, idDeduccion);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "No se pudo aplicar la deduccion");
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

    /** Nombre legible del estatus de solicitud (1..6). */
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

    /** ORA-01403 (NO_DATA_FOUND), estandar de Oracle. */
    private static final int ORACLE_NO_DATA_FOUND = 1403;
    /** PKG_ADMINISTRADOR.comprobar_plazo: "Plazo administrativo cerrado". */
    private static final int ORACLE_PLAZO_CERRADO = -20870;

    /**
     * Traduce errores de negocio lanzados por los paquetes/triggers de Oracle
     * (rango -20801..-20887 del paquete, -20001..-20075 de los triggers) y el
     * NO_DATA_FOUND estandar a un {@link ReglaNegocioException} con un mensaje
     * amigable. Cualquier otro error (conexion, SQL mal formado, etc.) se
     * relanza tal cual.
     */
    private ReglaNegocioException traducirError(DataAccessException exception, String mensajeGenerico) {
        Integer codigo = codigoOracle(exception);
        if (codigo == null) {
            throw exception;
        }
        if (codigo == ORACLE_PLAZO_CERRADO) {
            return new ReglaNegocioException(
                    mensajeGenerico + ": se vencio el plazo de la convocatoria para esta operacion.");
        }
        if (esErrorDePaquete(codigo) || esErrorDeTrigger(codigo) || codigo == ORACLE_NO_DATA_FOUND) {
            return new ReglaNegocioException(mensajeGenerico);
        }
        throw exception;
    }

    private boolean esErrorDePaquete(int codigo) {
        return codigo >= -20887 && codigo <= -20801;
    }

    private boolean esErrorDeTrigger(int codigo) {
        return codigo >= -20075 && codigo <= -20001;
    }

    /** Primer codigo de error de Oracle presente en la cadena de causas, o null si no hay ninguno. */
    private Integer codigoOracle(DataAccessException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException) {
                return Integer.valueOf(((SQLException) current).getErrorCode());
            }
        }
        return null;
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
