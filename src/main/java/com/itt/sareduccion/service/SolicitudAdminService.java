package com.itt.sareduccion.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.model.ConvocatoriaAdmin;
import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.HistorialDeduccionAdmin;
import com.itt.sareduccion.model.HistorialSolicitudAdmin;
import com.itt.sareduccion.model.MontoAdmin;
import com.itt.sareduccion.model.RespuestaSolicitud;
import com.itt.sareduccion.model.SolicitudAdmin;
import com.itt.sareduccion.repository.AdministradorRepository;
import com.itt.sareduccion.security.logging.SensitiveDataMasker;

/**
 * Logica de negocio del panel administrativo. Toda operacion recibe el id del
 * administrador ya validado por la sesion (nunca del cliente) y delega en
 * {@code PKG_ADMINISTRADOR}. Los errores de Oracle se traducen a mensajes
 * genericos antes de salir del servicio.
 *
 * <p>Logs tecnicos (estandar de SEGURIDAD.md §6): cada revision, decision y
 * cambio de estado se registra con un evento en mayusculas, los ids de la
 * operacion y el administrador enmascarado. Nunca se registran comentarios,
 * respuestas del formulario ni datos personales del solicitante.
 */
@Service
public class SolicitudAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(SolicitudAdminService.class);

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

    private final NotificacionService notificacionService;

    public SolicitudAdminService(AdministradorRepository administradorRepository,
            NotificacionService notificacionService) {
        this.administradorRepository = administradorRepository;
        this.notificacionService = notificacionService;
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
                String nombre = solicitud.getNombreCompleto();
                boolean coincideFolio = String.valueOf(solicitud.getIdSolicitud()).contains(filtro);
                boolean coincideSolicitante = identificador != null
                        && identificador.toUpperCase(Locale.ROOT).contains(filtro);
                boolean coincideNombre = nombre != null
                        && nombre.toUpperCase(Locale.ROOT).contains(filtro);
                if (!coincideFolio && !coincideSolicitante && !coincideNombre) {
                    continue;
                }
            }
            resultado.add(solicitud);
        }
        LOG.debug("SOLICITUDES_CONSULTADAS admin={} estatus={} convocatoria={} filtroSolicitante={} total={}",
                admin(idAdministrador), estatus, convocatoria, filtro != null && !filtro.isEmpty(), resultado.size());
        return resultado;
    }

    public List<ConvocatoriaAdmin> listarConvocatorias(String idAdministrador) {
        return administradorRepository.listarConvocatorias(idAdministrador);
    }

    public List<MontoAdmin> listarMontos(String idAdministrador) {
        return administradorRepository.listarMontos(idAdministrador);
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
        AdministradorRepository.DetalleSolicitudData datos;
        try {
            datos = administradorRepository.consultarDetalle(idAdministrador, idSolicitud);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "detalle_solicitud",
                    "No se pudo consultar el detalle de la solicitud");
        }
        if (datos == null) {
            throw rechazo("DETALLE", idSolicitud, idAdministrador, "Solicitud no encontrada");
        }
        LOG.info("SOLICITUD_REVISADA idSolicitud={} admin={} respuestas={}", idSolicitud, admin(idAdministrador),
                datos.getRespuestas() == null ? 0 : datos.getRespuestas().size());
        return new DetalleSolicitud(
                datos.getSolicitud(),
                datos.getRespuestas(),
                datos.getDeduccion(),
                datos.getHistorialSolicitud(),
                datos.getHistorialDeduccion());
    }

    /**
     * Aprueba (o reautoriza) una solicitud y, en la misma transaccion, autoriza
     * su deduccion. Admite solicitudes PENDIENTE o CANCELADO, igual que
     * {@code autorizar_deduccion} (el panel expone esto con dos botones
     * distintos — "Aprobar" y "Reautorizar" — pero ambos llaman este metodo).
     * El monto base se selecciona automaticamente segun el tipo de solicitante:
     * aspirante usa inscripcion y estudiante usa reinscripcion. Cuando existan
     * varios valores del tipo, se usa el registro con el identificador mas alto.
     * El comentario es opcional: si no se captura, el historial queda sin motivo.
     */
    public void aprobar(String idAdministrador, long idSolicitud, Long idMontoInscripcion,
            Long idMontoReinscripcion, String comentario) {
        String motivo = comentario == null || comentario.trim().isEmpty() ? null : comentario.trim();
        SolicitudAdmin solicitud;
        try {
            solicitud = administradorRepository.buscarSolicitud(idAdministrador, idSolicitud);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "buscar_solicitud", "No se pudo consultar la solicitud");
        }
        if (solicitud == null) {
            throw rechazo("APROBAR", idSolicitud, idAdministrador, "Solicitud no encontrada");
        }
        Integer estatusActual = solicitud.getIdEstatusSolicitud();
        if (!Integer.valueOf(ESTATUS_PENDIENTE).equals(estatusActual)
                && !Integer.valueOf(ESTATUS_CANCELADO).equals(estatusActual)) {
            throw rechazo("APROBAR", idSolicitud, idAdministrador,
                    "Solo se pueden aprobar solicitudes pendientes o canceladas");
        }

        String tipoMonto = solicitud.esEstudiante() ? "REINSCRIPCION" : "INSCRIPCION";
        MontoAdmin montoBase = montoBaseMasReciente(idAdministrador, tipoMonto);
        if (solicitud.esEstudiante()) {
            idMontoInscripcion = null;
            idMontoReinscripcion = montoBase.getIdMonto();
        } else {
            idMontoInscripcion = montoBase.getIdMonto();
            idMontoReinscripcion = null;
        }

        try {
            // autorizar_deduccion realiza por si sola la transicion de estatus
            // de la solicitud (1 o 4 -> 2); no se llama ningun cambio de estatus aparte.
            administradorRepository.autorizarDeduccion(idAdministrador, idSolicitud, idMontoInscripcion,
                    idMontoReinscripcion, motivo);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "aprobar_solicitud", "No se pudo aprobar la solicitud");
        }
        LOG.info("{} idSolicitud={} admin={} estatus={}->AUTORIZADO tipoMonto={} conComentario={}",
                Integer.valueOf(ESTATUS_CANCELADO).equals(estatusActual) ? "SOLICITUD_REAUTORIZADA" : "SOLICITUD_APROBADA",
                idSolicitud, admin(idAdministrador), nombreEstatus(estatusActual), tipoMonto, motivo != null);
        notificacionService.avisarCambio(idAdministrador, idSolicitud, NotificacionService.Evento.APROBACION, motivo);
    }

    public void rechazar(String idAdministrador, long idSolicitud, String comentario) {
        if (comentario == null || comentario.trim().isEmpty()) {
            throw rechazo("RECHAZAR", idSolicitud, idAdministrador,
                    "El comentario es obligatorio para rechazar o solicitar correccion");
        }
        try {
            administradorRepository.rechazarSolicitud(idAdministrador, idSolicitud, comentario);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "rechazar_solicitud", "No se pudo rechazar la solicitud");
        }
        LOG.info("SOLICITUD_RECHAZADA idSolicitud={} admin={} estatus=RECHAZADO", idSolicitud, admin(idAdministrador));
        notificacionService.avisarCambio(idAdministrador, idSolicitud, NotificacionService.Evento.RECHAZO, comentario);
    }

    /**
     * Cancela una deduccion autorizada y sincroniza la solicitud a CANCELADO.
     * El comentario es opcional: si se captura, queda en el historial.
     */
    public void cancelarDeduccion(String idAdministrador, long idDeduccion, String comentario) {
        final String operacion = "CANCELAR_DEDUCCION";
        String motivo = comentario == null || comentario.trim().isEmpty() ? null : comentario.trim();
        DeduccionAdmin deduccion;
        try {
            deduccion = administradorRepository.buscarDeduccion(idAdministrador, idDeduccion);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "buscar_deduccion", "No se pudo consultar la deduccion");
        }
        if (deduccion == null) {
            throw rechazo(operacion, idDeduccion, idAdministrador, "Deduccion no encontrada");
        }
        if (!Integer.valueOf(ESTATUS_DEDUCCION_AUTORIZADO).equals(deduccion.getEstatusDeduccion())) {
            throw rechazo(operacion, idDeduccion, idAdministrador, "Solo se pueden cancelar deducciones autorizadas");
        }

        try {
            administradorRepository.cancelarDeduccion(idAdministrador, idDeduccion, motivo);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "cancelar_deduccion", "No se pudo cancelar la deduccion");
        }
        LOG.info("DEDUCCION_CANCELADA idDeduccion={} idSolicitud={} admin={} estatus=AUTORIZADO->CANCELADO conComentario={}",
                idDeduccion, deduccion.getIdSolicitud(), admin(idAdministrador), motivo != null);
        notificacionService.avisarCambio(idAdministrador, deduccion.getIdSolicitud(), NotificacionService.Evento.CANCELACION, motivo);
    }

    /** Aplica una deduccion autorizada: estado definitivo e inmutable. */
    public void aplicarDeduccion(String idAdministrador, long idDeduccion) {
        aplicarDeduccion(idAdministrador, idDeduccion, null);
    }

    public void aplicarDeduccion(String idAdministrador, long idDeduccion, String comentario) {
        final String operacion = "APLICAR_DEDUCCION";
        DeduccionAdmin deduccion;
        try {
            deduccion = administradorRepository.buscarDeduccion(idAdministrador, idDeduccion);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "buscar_deduccion", "No se pudo consultar la deduccion");
        }
        if (deduccion == null) {
            throw rechazo(operacion, idDeduccion, idAdministrador, "Deduccion no encontrada");
        }
        if (!Integer.valueOf(ESTATUS_DEDUCCION_AUTORIZADO).equals(deduccion.getEstatusDeduccion())) {
            throw rechazo(operacion, idDeduccion, idAdministrador, "Solo se pueden aplicar deducciones autorizadas");
        }

        try {
            administradorRepository.aplicarDeduccion(idAdministrador, idDeduccion);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "aplicar_deduccion", "No se pudo aplicar la deduccion");
        }
        LOG.info("DEDUCCION_APLICADA idDeduccion={} idSolicitud={} admin={} estatus=AUTORIZADO->APLICADO",
                idDeduccion, deduccion.getIdSolicitud(), admin(idAdministrador));
        notificacionService.avisarCambio(idAdministrador, deduccion.getIdSolicitud(),
                NotificacionService.Evento.APLICACION, comentario);
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

    private MontoAdmin montoBaseMasReciente(String idAdministrador, String tipoMonto) {
        final List<MontoAdmin> montos;
        try {
            montos = administradorRepository.listarMontos(idAdministrador);
        } catch (DataAccessException exception) {
            throw traducirError(exception, "listar_montos", "No se pudo consultar el monto base");
        }

        MontoAdmin elegido = null;
        if (montos != null) {
            for (MontoAdmin monto : montos) {
                if (monto != null && tipoMonto.equals(monto.getTipoMonto())
                        && (elegido == null
                                || (monto.getIdMonto() != null
                                        && elegido.getIdMonto() != null
                                        && monto.getIdMonto().longValue() > elegido.getIdMonto().longValue()))) {
                    elegido = monto;
                }
            }
        }
        if (elegido == null || elegido.getIdMonto() == null) {
            String descripcion = "INSCRIPCION".equals(tipoMonto) ? "inscripcion" : "reinscripcion";
            LOG.warn("MONTO_BASE_NO_CONFIGURADO tipoMonto={}", tipoMonto);
            throw new ReglaNegocioException("No existe un monto base de " + descripcion + " configurado");
        }
        return elegido;
    }

    /** ORA-01403 (NO_DATA_FOUND), estandar de Oracle. */
    private static final int ORACLE_NO_DATA_FOUND = 1403;
    /** PKG_ADMINISTRADOR.comprobar_plazo: "Plazo administrativo cerrado". */
    private static final int ORACLE_PLAZO_CERRADO = 20870;

    /**
     * Traduce errores de negocio lanzados por los paquetes/triggers de Oracle
     * (rango -20801..-20887 del paquete, -20001..-20075 de los triggers) y el
     * NO_DATA_FOUND estandar a un {@link ReglaNegocioException} con un mensaje
     * amigable. Cualquier otro error (conexion, SQL mal formado, etc.) se
     * relanza tal cual.
     */
    private ReglaNegocioException traducirError(DataAccessException exception, String operacion,
            String mensajeGenerico) {
        Integer codigo = codigoOracle(exception);
        if (codigo == null) {
            throw exception;
        }
        int ora = Math.abs(codigo.intValue());
        if (ora == ORACLE_PLAZO_CERRADO) {
            LOG.warn("ORACLE_RECHAZO administrador operacion={} codigoOracle={} respuesta=NEGOCIO_TRADUCIDO",
                    operacion, ora);
            return new ReglaNegocioException(
                    mensajeGenerico + ": se vencio el plazo de la convocatoria para esta operacion.");
        }
        if (esErrorDePaquete(ora) || esErrorDeTrigger(ora) || ora == ORACLE_NO_DATA_FOUND) {
            LOG.warn("ORACLE_RECHAZO administrador operacion={} codigoOracle={} respuesta=NEGOCIO_TRADUCIDO",
                    operacion, ora);
            return new ReglaNegocioException(mensajeGenerico);
        }
        throw exception;
    }

    /** Registra una operacion rechazada por regla de negocio y devuelve la excepcion a lanzar. */
    private ReglaNegocioException rechazo(String operacion, long id, String idAdministrador, String mensaje) {
        LOG.warn("OPERACION_RECHAZADA operacion={} id={} admin={} motivo=\"{}\"", operacion, id,
                admin(idAdministrador), mensaje);
        return new ReglaNegocioException(mensaje);
    }

    private static String admin(String idAdministrador) {
        return SensitiveDataMasker.identifier(idAdministrador);
    }

    private boolean esErrorDePaquete(int ora) {
        return ora >= 20801 && ora <= 20887;
    }

    private boolean esErrorDeTrigger(int ora) {
        return ora >= 20001 && ora <= 20075;
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

    /** Detalle completo de una solicitud: datos, respuestas, deduccion e historiales. */
    public static final class DetalleSolicitud {
        private final SolicitudAdmin solicitud;
        private final List<RespuestaSolicitud> respuestas;
        private final DeduccionAdmin deduccion;
        private final List<HistorialSolicitudAdmin> historial;
        private final List<HistorialDeduccionAdmin> historialDeduccion;

        public DetalleSolicitud(SolicitudAdmin solicitud, List<RespuestaSolicitud> respuestas,
                DeduccionAdmin deduccion, List<HistorialSolicitudAdmin> historial,
                List<HistorialDeduccionAdmin> historialDeduccion) {
            this.solicitud = solicitud;
            this.respuestas = respuestas;
            this.deduccion = deduccion;
            this.historial = historial;
            this.historialDeduccion = historialDeduccion;
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

        public List<HistorialSolicitudAdmin> getHistorial() {
            return historial;
        }

        public List<HistorialDeduccionAdmin> getHistorialDeduccion() {
            return historialDeduccion;
        }
    }
}
