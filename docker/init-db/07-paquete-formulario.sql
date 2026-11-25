-- ============================================================================
-- SAReduccion | 07-paquete-formulario.sql | Oracle XE 21c / XEPDB1
-- Equipo 5 - Formularios de solicitud.
--
-- Paquete PKG_FORMULARIO: operaciones que necesita el formulario dinamico y
-- que no existian en los paquetes publicos. Tres son de SOLO LECTURA y una
-- envuelve el envio inicial para dejar el historial completo:
--   1) convocatorias_abiertas : convocatorias con plazo vigente, sus tipos de
--                               solicitud, el formulario de cada tipo y la
--                               solicitud previa del solicitante (si existe).
--   2) obtener_formulario     : instrucciones, preguntas y opciones de un
--                               formulario, validando convocatoria y plazo.
--   3) mis_respuestas         : datos y respuestas de una solicitud PROPIA
--                               (para consultar o precargar un reenvio).
--
--   4) crear_solicitud        : envio inicial. Llama a PKG_SOLICITUDES_CORE.crear
--                               (misma regla y mismo formato JSON que
--                               PKG_ESTUDIANTE / PKG_ASPIRANTE) y, solo si la
--                               solicitud no quedo con historial, registra el
--                               estado inicial PENDIENTE. Si el equipo de BD
--                               corrige crear() para registrarlo, este
--                               procedimiento no duplica el registro.
--
-- El reenvio NO se reimplementa: se usan PKG_ESTUDIANTE.reenviar_solicitud y
-- PKG_ASPIRANTE.reenviar_solicitud (ya registran el historial).
--
-- Requiere: scripts 01 a 05 (y 06 para datos de prueba).
-- Ejecutar como SA_REDUCCION en XEPDB1.
-- Si se ejecuta a mano, definir antes la variable del backend:
--     DEFINE ORACLE_BACKEND_USER=SAR_BACKEND
--
-- Errores propios (rango libre -20940..-20949):
--   -20940 Formulario no disponible para el tipo de solicitud
--   -20941 Solicitud no encontrada (inexistente o ajena)
--   -20942 Origen del solicitante invalido
--   -20943 Objetos INVALID tras compilar este paquete
-- Reutiliza los de PKG_SOLICITUDES_CORE: -20810 estudiante no registrado,
-- -20841 aspirante no registrado, -20812 convocatoria/tipo/formulario fuera de
-- plazo.
--
-- IMPORTANTE: igual que el resto de paquetes, estos parametros NO autentican.
-- El backend debe tomar la identidad de la sesion autenticada.
-- ============================================================================
SET SERVEROUTPUT ON SIZE UNLIMITED
SET VERIFY OFF
SET SQLBLANKLINES ON
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
PROMPT ===== VALIDACION DEL ESQUEMA =====
BEGIN
 IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'SA_REDUCCION' OR
    SYS_CONTEXT('USERENV','CON_NAME') <> 'XEPDB1' THEN
   RAISE_APPLICATION_ERROR(-20801,'Ejecutar como SA_REDUCCION en XEPDB1');
 END IF;
END;
/

CREATE OR REPLACE PACKAGE PKG_FORMULARIO AUTHID DEFINER AS
 -- Exactamente uno de los dos origenes debe venir informado:
 --   estudiante: p_control            (p_ficha, p_periodo y p_anio nulos)
 --   aspirante : p_ficha,p_periodo,p_anio (p_control nulo)
 PROCEDURE convocatorias_abiertas(p_control IN VARCHAR2,p_ficha IN VARCHAR2,
   p_periodo IN NUMBER,p_anio IN NUMBER,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE obtener_formulario(p_convocatoria IN NUMBER,p_tipo IN NUMBER,
   p_formulario OUT SYS_REFCURSOR,p_preguntas OUT SYS_REFCURSOR,p_opciones OUT SYS_REFCURSOR);
 PROCEDURE mis_respuestas(p_control IN VARCHAR2,p_ficha IN VARCHAR2,
   p_periodo IN NUMBER,p_anio IN NUMBER,p_solicitud IN NUMBER,
   p_datos OUT SYS_REFCURSOR,p_respuestas OUT SYS_REFCURSOR);
 -- Envio inicial. Mismo origen que los demas (control o ficha/periodo/anio).
 -- El cliente (backend) controla COMMIT/ROLLBACK.
 PROCEDURE crear_solicitud(p_control IN VARCHAR2,p_ficha IN VARCHAR2,
   p_periodo IN NUMBER,p_anio IN NUMBER,p_convocatoria IN NUMBER,p_tipo IN NUMBER,
   p_formulario IN NUMBER,p_respuestas IN CLOB,p_id OUT NUMBER);
END PKG_FORMULARIO;
/
CREATE OR REPLACE PACKAGE BODY PKG_FORMULARIO AS

 -- Valida que venga un solo origen y que ese solicitante exista.
 PROCEDURE validar_origen(p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER) IS
 BEGIN
  IF (p_control IS NULL AND p_ficha IS NULL) OR (p_control IS NOT NULL AND p_ficha IS NOT NULL) THEN
   RAISE_APPLICATION_ERROR(-20942,'Origen de solicitante invalido');
  END IF;
  IF p_control IS NOT NULL THEN
   PKG_SOLICITUDES_CORE.validar_estudiante(p_control);
  ELSE
   PKG_SOLICITUDES_CORE.validar_aspirante_identidad(p_ficha,p_periodo,p_anio);
  END IF;
 END;

 -- Cursor columnas:
 --  1 id_convocatoria  2 id_periodo  3 anio  4 fecha_inicio_solicitudes
 --  5 fecha_fin_solicitudes  6 id_tipo_solicitud  7 nombre_tipo
 --  8 id_formulario  9 id_solicitud (NULL si aun no tiene)  10 id_estatus_solicitud
 PROCEDURE convocatorias_abiertas(p_control IN VARCHAR2,p_ficha IN VARCHAR2,
   p_periodo IN NUMBER,p_anio IN NUMBER,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  validar_origen(p_control,p_ficha,p_periodo,p_anio);
  OPEN p_resultado FOR
   SELECT c.id_convocatoria,c.id_periodo,c.anio,
          c.fecha_inicio_solicitudes,c.fecha_fin_solicitudes,
          ct.id_tipo_solicitud,t.nombre AS nombre_tipo,f.id_formulario,
          s.id_solicitud,s.id_estatus_solicitud
     FROM Convocatorias c
     JOIN Convocatorias_TipoSolicitudes ct ON ct.id_convocatoria=c.id_convocatoria
     JOIN Tipo_Solicitudes t ON t.id_tipo_solicitud=ct.id_tipo_solicitud
     JOIN Formularios f ON f.id_tipo_solicitud=ct.id_tipo_solicitud
     LEFT JOIN Solicitudes s ON s.id_convocatoria=c.id_convocatoria
          AND ((p_control IS NOT NULL AND s.num_control=p_control AND s.ficha IS NULL)
            OR (p_ficha IS NOT NULL AND s.num_control IS NULL AND s.ficha=p_ficha
                AND s.periodo=p_periodo AND s.anio=p_anio))
    WHERE TRUNC(SYSDATE) BETWEEN TRUNC(c.fecha_inicio_solicitudes) AND TRUNC(c.fecha_fin_solicitudes)
      AND (p_control IS NOT NULL OR (c.id_periodo=p_periodo AND c.anio=p_anio))
    ORDER BY c.id_convocatoria,ct.id_tipo_solicitud;
 END;

 -- Cursor p_formulario: id_formulario,nombre,descripcion,instrucciones,id_tipo_solicitud
 -- Cursor p_preguntas : id_pregunta,texto_pregunta,obligatoria (S/N),
 --                      id_tipo_pregunta,tipo_nombre,orden
 -- Cursor p_opciones  : id_opcion,id_pregunta,texto_opcion,orden,requiere_especificacion
 PROCEDURE obtener_formulario(p_convocatoria IN NUMBER,p_tipo IN NUMBER,
   p_formulario OUT SYS_REFCURSOR,p_preguntas OUT SYS_REFCURSOR,p_opciones OUT SYS_REFCURSOR) IS
  v_form NUMBER;
 BEGIN
  BEGIN
   SELECT id_formulario INTO v_form FROM Formularios WHERE id_tipo_solicitud=p_tipo;
  EXCEPTION WHEN NO_DATA_FOUND THEN
   RAISE_APPLICATION_ERROR(-20940,'Formulario no disponible para el tipo de solicitud');
  END;
  -- Misma regla que usa el envio: convocatoria con plazo vigente y tipo configurado.
  PKG_SOLICITUDES_CORE.validar_convocatoria_formulario(p_convocatoria,p_tipo,v_form);
  OPEN p_formulario FOR
   SELECT f.id_formulario,f.nombre,f.descripcion,f.instrucciones,f.id_tipo_solicitud
     FROM Formularios f WHERE f.id_formulario=v_form;
  OPEN p_preguntas FOR
   SELECT p.id_pregunta,p.texto_pregunta,p.obligatoria,
          p.tipo_pregunta AS id_tipo_pregunta,tp.nombre AS tipo_nombre,fp.orden
     FROM Formulario_Preguntas fp
     JOIN Preguntas p ON p.id_pregunta=fp.id_pregunta
     JOIN Tipo_Preguntas tp ON tp.id_tipo_pregunta=p.tipo_pregunta
    WHERE fp.id_formulario=v_form
    ORDER BY fp.orden;
  OPEN p_opciones FOR
   SELECT o.id_opcion,o.id_pregunta,o.texto_opcion,o.orden,o.requiere_especificacion
     FROM Opciones o
     JOIN Formulario_Preguntas fp ON fp.id_pregunta=o.id_pregunta
    WHERE fp.id_formulario=v_form
    ORDER BY o.id_pregunta,o.orden;
 END;

 -- Cursor p_datos     : id_solicitud,id_convocatoria,id_tipo_solicitud,id_formulario,
 --                      id_estatus_solicitud,fecha_solicitud,fecha_actualizacion,
 --                      ultimo_comentario (motivo registrado por el administrador, si hay)
 -- Cursor p_respuestas: id_pregunta,id_opcion,texto_respuesta
 PROCEDURE mis_respuestas(p_control IN VARCHAR2,p_ficha IN VARCHAR2,
   p_periodo IN NUMBER,p_anio IN NUMBER,p_solicitud IN NUMBER,
   p_datos OUT SYS_REFCURSOR,p_respuestas OUT SYS_REFCURSOR) IS
  v_id NUMBER;
 BEGIN
  validar_origen(p_control,p_ficha,p_periodo,p_anio);
  BEGIN
   SELECT s.id_solicitud INTO v_id FROM Solicitudes s
    WHERE s.id_solicitud=p_solicitud
      AND ((p_control IS NOT NULL AND s.num_control=p_control AND s.ficha IS NULL)
        OR (p_ficha IS NOT NULL AND s.num_control IS NULL AND s.ficha=p_ficha
            AND s.periodo=p_periodo AND s.anio=p_anio));
  EXCEPTION WHEN NO_DATA_FOUND THEN
   -- Mismo error para inexistente y ajena: no revela que ids existen.
   RAISE_APPLICATION_ERROR(-20941,'Solicitud no encontrada');
  END;
  OPEN p_datos FOR
   SELECT s.id_solicitud,s.id_convocatoria,s.id_tipo_solicitud,s.id_formulario,
          s.id_estatus_solicitud,s.fecha_solicitud,s.fecha_actualizacion,
          (SELECT h.comentario FROM Historial_Solicitudes h
            WHERE h.id_solicitud=s.id_solicitud AND h.comentario IS NOT NULL
            ORDER BY h.fecha_registro DESC,h.id_historial DESC
            FETCH FIRST 1 ROW ONLY) AS ultimo_comentario
     FROM Solicitudes s WHERE s.id_solicitud=v_id;
  OPEN p_respuestas FOR
   SELECT r.id_pregunta,r.id_opcion,r.texto_respuesta
     FROM Respuestas r WHERE r.id_solicitud=v_id
    ORDER BY r.id_pregunta;
 END;

 -- Envio inicial atomico. Valida identidad, plazo, tipo/formulario y respuestas
 -- mediante PKG_SOLICITUDES_CORE.crear; despues asegura el historial inicial.
 PROCEDURE crear_solicitud(p_control IN VARCHAR2,p_ficha IN VARCHAR2,
   p_periodo IN NUMBER,p_anio IN NUMBER,p_convocatoria IN NUMBER,p_tipo IN NUMBER,
   p_formulario IN NUMBER,p_respuestas IN CLOB,p_id OUT NUMBER) IS
  n NUMBER;
 BEGIN
  SAVEPOINT sp_formulario_crear;
  PKG_SOLICITUDES_CORE.crear(p_control,p_ficha,p_periodo,p_anio,
    p_convocatoria,p_tipo,p_formulario,p_respuestas,p_id);
  SELECT COUNT(*) INTO n FROM Historial_Solicitudes WHERE id_solicitud=p_id;
  IF n=0 THEN
   PKG_SOLICITUDES_CORE.registrar_solicitud(p_id);
  END IF;
 EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO sp_formulario_crear;
  RAISE;
 END;
END PKG_FORMULARIO;
/

PROMPT ===== COMPROBAR COMPILACION =====
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM USER_OBJECTS
  WHERE OBJECT_NAME='PKG_FORMULARIO' AND STATUS<>'VALID';
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20943,'PKG_FORMULARIO INVALID: consultar USER_ERRORS'); END IF;
 SELECT COUNT(*) INTO n FROM USER_ERRORS WHERE NAME='PKG_FORMULARIO';
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20943,'Errores de compilacion: consultar USER_ERRORS'); END IF;
 DBMS_OUTPUT.PUT_LINE('OK: PKG_FORMULARIO compilado');
END;
/
PROMPT ===== CONCEDER EXECUTE SOLO A SAR_BACKEND =====
GRANT EXECUTE ON PKG_FORMULARIO TO &&ORACLE_BACKEND_USER;
COLUMN GRANTEE FORMAT A30
COLUMN TABLE_NAME FORMAT A30
COLUMN PRIVILEGE FORMAT A12
SELECT GRANTEE,TABLE_NAME,PRIVILEGE FROM USER_TAB_PRIVS_MADE
 WHERE TABLE_NAME='PKG_FORMULARIO' ORDER BY GRANTEE;
PROMPT ===== PKG_FORMULARIO LISTO =====
EXIT SUCCESS
