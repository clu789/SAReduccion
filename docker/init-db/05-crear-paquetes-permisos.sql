-- ============================================================================
-- SAReduccion | 05-crear-paquetes-permisos.sql | Oracle XE 21c / XEPDB1
-- Requiere: 01, 02-crear-estructura.sql, 03 y 04.
-- API de operaciones atomicas; el cliente controla COMMIT/ROLLBACK.
-- Las operaciones realizan SAVEPOINT y ROLLBACK TO SAVEPOINT ante errores.
-- IMPORTANTE: la cuenta SAR_BACKEND debe derivar los identificadores del
-- usuario de una sesion autenticada. Estos parametros NO autentican por si solos.
-- Formato de respuestas: JSON array de objetos:
--   [{"id_pregunta":1,"id_opcion":null,"texto_respuesta":"texto"},...]
-- El arreglo debe contener una respuesta por cada pregunta obligatoria.
-- La validacion de propiedad en Oracle no reemplaza la autenticacion del backend.
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

-- ============================================================================
-- PAQUETE INTERNO COMPARTIDO. SIN GRANT A SAR_BACKEND.
-- Encapsula reglas de solicitudes para estudiantes y aspirantes.
-- ============================================================================
CREATE OR REPLACE PACKAGE PKG_SOLICITUDES_CORE AUTHID DEFINER AS
 PROCEDURE validar_estudiante(p_control VARCHAR2);
 PROCEDURE validar_aspirante_identidad(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER);
 PROCEDURE validar_administrador(p_admin VARCHAR2);
 PROCEDURE validar_convocatoria_formulario(p_conv NUMBER,p_tipo NUMBER,p_form NUMBER);
 PROCEDURE guardar_respuestas_completas(p_solicitud NUMBER,p_form NUMBER,p_respuestas CLOB);
 PROCEDURE registrar_solicitud(p_solicitud NUMBER,p_admin VARCHAR2 DEFAULT NULL,p_comentario VARCHAR2 DEFAULT NULL);
 PROCEDURE registrar_deduccion(p_deduccion NUMBER,p_admin VARCHAR2);
 PROCEDURE crear(p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,
   p_conv NUMBER,p_tipo NUMBER,p_form NUMBER,p_respuestas CLOB,p_id OUT NUMBER);
 PROCEDURE retirar(p_solicitud NUMBER,p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER);
 PROCEDURE reenviar(p_solicitud NUMBER,p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,
   p_tipo NUMBER,p_form NUMBER,p_respuestas CLOB);
END PKG_SOLICITUDES_CORE;
/
CREATE OR REPLACE PACKAGE BODY PKG_SOLICITUDES_CORE AS
 PROCEDURE validar_estudiante(p_control VARCHAR2) IS n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Estudiantes WHERE numero_control=p_control;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20810,'Estudiante no registrado'); END IF;
 END;
 PROCEDURE validar_aspirante_identidad(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER) IS n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Aspirantes WHERE ficha=p_ficha AND periodo=p_periodo AND anio=p_anio;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20841,'Aspirante no registrado en el periodo indicado'); END IF;
 END;
 PROCEDURE validar_administrador(p_admin VARCHAR2) IS n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Administradores WHERE id_administrador=p_admin;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20820,'Administrador no registrado'); END IF;
 END;
 PROCEDURE validar_convocatoria_formulario(p_conv NUMBER,p_tipo NUMBER,p_form NUMBER) IS n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n
  FROM Convocatorias c JOIN Convocatorias_TipoSolicitudes ct ON ct.id_convocatoria=c.id_convocatoria
   JOIN Formularios f ON f.id_tipo_solicitud=ct.id_tipo_solicitud
  WHERE c.id_convocatoria=p_conv AND ct.id_tipo_solicitud=p_tipo AND f.id_formulario=p_form
    AND TRUNC(SYSDATE) BETWEEN TRUNC(c.fecha_inicio_solicitudes) AND TRUNC(c.fecha_fin_solicitudes);
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20812,'Convocatoria, tipo o formulario fuera de plazo'); END IF;
 END;
 PROCEDURE guardar_respuestas_completas(p_solicitud NUMBER,p_form NUMBER,p_respuestas CLOB) IS
  n NUMBER;
 BEGIN
  IF p_respuestas IS NULL THEN
   RAISE_APPLICATION_ERROR(-20850,'Respuestas deben ser JSON valido');
  END IF;
  SELECT COUNT(*) INTO n FROM dual WHERE p_respuestas IS JSON STRICT;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20850,'Respuestas deben ser JSON valido'); END IF;
  -- Validar que el documento raiz sea arreglo, no objeto.
  IF NOT REGEXP_LIKE(DBMS_LOB.SUBSTR(p_respuestas,4000,1),'^[[:space:]]*\[') THEN
   RAISE_APPLICATION_ERROR(-20851,'Respuestas deben ser un arreglo JSON');
  END IF;
  SELECT COUNT(*) INTO n FROM JSON_TABLE(p_respuestas,'$[*]'
    COLUMNS (id_pregunta NUMBER PATH '$.id_pregunta' ERROR ON ERROR));
  IF n=0 THEN
   SELECT COUNT(*) INTO n FROM Formulario_Preguntas fp JOIN Preguntas p ON p.id_pregunta=fp.id_pregunta
    WHERE fp.id_formulario=p_form AND p.obligatoria='S';
   IF n>0 THEN RAISE_APPLICATION_ERROR(-20852,'Faltan preguntas obligatorias'); END IF;
  END IF;
  -- Rechaza ids duplicados, nulos y preguntas ajenas al formulario.
  SELECT COUNT(*) INTO n FROM (
   SELECT j.id_pregunta FROM JSON_TABLE(p_respuestas,'$[*]'
     COLUMNS (id_pregunta NUMBER PATH '$.id_pregunta' NULL ON ERROR)) j
   GROUP BY j.id_pregunta HAVING COUNT(*)>1 OR j.id_pregunta IS NULL
  );
  IF n>0 THEN RAISE_APPLICATION_ERROR(-20853,'Preguntas repetidas o sin identificador'); END IF;
  SELECT COUNT(*) INTO n FROM JSON_TABLE(p_respuestas,'$[*]'
    COLUMNS (id_pregunta NUMBER PATH '$.id_pregunta' NULL ON ERROR)) j
   WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas fp
                     WHERE fp.id_formulario=p_form AND fp.id_pregunta=j.id_pregunta);
  IF n>0 THEN RAISE_APPLICATION_ERROR(-20854,'Respuesta a pregunta ajena al formulario'); END IF;
  SELECT COUNT(*) INTO n FROM Formulario_Preguntas fp JOIN Preguntas p ON p.id_pregunta=fp.id_pregunta
   WHERE fp.id_formulario=p_form AND p.obligatoria='S'
    AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(p_respuestas,'$[*]'
      COLUMNS (id_pregunta NUMBER PATH '$.id_pregunta')) j WHERE j.id_pregunta=p.id_pregunta);
  IF n>0 THEN RAISE_APPLICATION_ERROR(-20855,'No se respondieron todas las preguntas obligatorias'); END IF;
  -- El formato y la coherencia de cada respuesta (tipo de pregunta, opcion y
  -- especificacion) los valida trg_respuestas_validacion al insertar. Las
  -- respuestas previas se sustituyen dentro de la misma transaccion.
  DELETE FROM Respuestas WHERE id_solicitud=p_solicitud;
  INSERT INTO Respuestas(id_respuesta,id_solicitud,id_pregunta,id_opcion,texto_respuesta)
  SELECT seq_respuestas.NEXTVAL,p_solicitud,j.id_pregunta,j.id_opcion,j.texto_respuesta
  FROM JSON_TABLE(p_respuestas,'$[*]'
    COLUMNS (id_pregunta NUMBER PATH '$.id_pregunta' ERROR ON ERROR,
             id_opcion NUMBER PATH '$.id_opcion' NULL ON ERROR,
             texto_respuesta VARCHAR2(4000) PATH '$.texto_respuesta' NULL ON ERROR)) j;
 END;
 PROCEDURE registrar_solicitud(p_solicitud NUMBER,p_admin VARCHAR2 DEFAULT NULL,p_comentario VARCHAR2 DEFAULT NULL) IS
  s Solicitudes%ROWTYPE;
 BEGIN
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=p_solicitud;
  INSERT INTO Historial_Solicitudes(id_historial,id_solicitud,id_estatus,id_tipo_solicitud,id_administrador,comentario)
  VALUES(seq_historial_solicitudes.NEXTVAL,p_solicitud,s.id_estatus_solicitud,s.id_tipo_solicitud,p_admin,p_comentario);
 END;
 PROCEDURE registrar_deduccion(p_deduccion NUMBER,p_admin VARCHAR2) IS
  d Deducciones_autorizadas%ROWTYPE;
 BEGIN
  SELECT * INTO d FROM Deducciones_autorizadas WHERE id_deduccion_autorizada=p_deduccion;
  INSERT INTO Historial_Deducciones(id_historial_deduccion,id_deduccion_autorizada,id_estatus,
    id_administrador,id_tipo_solicitud,monto_pago,monto_deduccion,monto_final)
  VALUES(seq_historial_deducciones.NEXTVAL,p_deduccion,d.estatus_deduccion,p_admin,
    d.id_tipo_solicitud,d.monto_pago,d.monto_deduccion,d.monto_final);
 END;
 PROCEDURE crear(p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,
   p_conv NUMBER,p_tipo NUMBER,p_form NUMBER,p_respuestas CLOB,p_id OUT NUMBER) IS
  v_per NUMBER;v_anio NUMBER;
 BEGIN
  SAVEPOINT sp_crear_solicitud;
  IF (p_control IS NULL AND p_ficha IS NULL) OR (p_control IS NOT NULL AND p_ficha IS NOT NULL) THEN
   RAISE_APPLICATION_ERROR(-20856,'Origen de solicitud invalido');
  END IF;
  IF p_control IS NOT NULL THEN validar_estudiante(p_control);
  ELSE
   validar_aspirante_identidad(p_ficha,p_periodo,p_anio);
   SELECT id_periodo,anio INTO v_per,v_anio FROM Convocatorias WHERE id_convocatoria=p_conv;
   IF p_periodo<>v_per OR p_anio<>v_anio THEN RAISE_APPLICATION_ERROR(-20857,'Aspirante de otro periodo'); END IF;
  END IF;
  validar_convocatoria_formulario(p_conv,p_tipo,p_form);
  SELECT seq_solicitudes.NEXTVAL INTO p_id FROM dual;
  INSERT INTO Solicitudes(id_solicitud,id_estatus_solicitud,id_convocatoria,num_control,ficha,periodo,anio,id_tipo_solicitud,id_formulario)
  VALUES(p_id,1,p_conv,p_control,p_ficha,p_periodo,p_anio,p_tipo,p_form);
  guardar_respuestas_completas(p_id,p_form,p_respuestas);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_crear_solicitud; RAISE;
 END;
 PROCEDURE retirar(p_solicitud NUMBER,p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER) IS
  s Solicitudes%ROWTYPE;v_fin DATE;
 BEGIN
  SAVEPOINT sp_retirar_solicitud;
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=p_solicitud FOR UPDATE;
  IF NOT ((p_control IS NOT NULL AND s.num_control=p_control AND s.ficha IS NULL)
    OR (p_ficha IS NOT NULL AND s.num_control IS NULL AND s.ficha=p_ficha AND s.periodo=p_periodo AND s.anio=p_anio)) THEN
   RAISE_APPLICATION_ERROR(-20858,'Solicitud ajena');
  END IF;
  IF s.id_estatus_solicitud<>1 THEN RAISE_APPLICATION_ERROR(-20859,'Solo se retiran solicitudes pendientes'); END IF;
  SELECT fecha_fin_solicitudes INTO v_fin FROM Convocatorias WHERE id_convocatoria=s.id_convocatoria;
  IF TRUNC(SYSDATE)>TRUNC(v_fin) THEN RAISE_APPLICATION_ERROR(-20860,'Plazo de retiro cerrado'); END IF;
  UPDATE Solicitudes SET id_estatus_solicitud=6 WHERE id_solicitud=p_solicitud;
  registrar_solicitud(p_solicitud);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_retirar_solicitud; RAISE;
 END;
 PROCEDURE reenviar(p_solicitud NUMBER,p_control VARCHAR2,p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,
   p_tipo NUMBER,p_form NUMBER,p_respuestas CLOB) IS
  s Solicitudes%ROWTYPE;
 BEGIN
  SAVEPOINT sp_reenviar_solicitud;
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=p_solicitud FOR UPDATE;
  IF NOT ((p_control IS NOT NULL AND s.num_control=p_control AND s.ficha IS NULL)
    OR (p_ficha IS NOT NULL AND s.num_control IS NULL AND s.ficha=p_ficha AND s.periodo=p_periodo AND s.anio=p_anio)) THEN
   RAISE_APPLICATION_ERROR(-20861,'Solicitud ajena');
  END IF;
  IF s.id_estatus_solicitud NOT IN (3,4,6) THEN
   RAISE_APPLICATION_ERROR(-20862,'Solo se reenvian solicitudes retiradas, rechazadas o canceladas');
  END IF;
  validar_convocatoria_formulario(s.id_convocatoria,p_tipo,p_form);
  -- El reenvio puede cambiar el formulario, asi que se descartan las respuestas
  -- del formulario anterior antes de insertar las nuevas.
  DELETE FROM Respuestas WHERE id_solicitud=p_solicitud;
  UPDATE Solicitudes SET id_estatus_solicitud=1,id_tipo_solicitud=p_tipo,id_formulario=p_form
   WHERE id_solicitud=p_solicitud;
  guardar_respuestas_completas(p_solicitud,p_form,p_respuestas);
  registrar_solicitud(p_solicitud);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_reenviar_solicitud; RAISE;
 END;
END PKG_SOLICITUDES_CORE;
/

-- ============================================================================
-- API ESTUDIANTE
-- ============================================================================
CREATE OR REPLACE PACKAGE PKG_ESTUDIANTE AUTHID DEFINER AS
 PROCEDURE mis_datos(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_solicitudes(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_deducciones(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud(p_numero_control IN VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB,p_id OUT NUMBER);
 PROCEDURE retirar_solicitud(p_numero_control VARCHAR2,p_solicitud NUMBER);
 PROCEDURE reenviar_solicitud(p_numero_control VARCHAR2,p_solicitud NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB);
END PKG_ESTUDIANTE;
/
CREATE OR REPLACE PACKAGE BODY PKG_ESTUDIANTE AS
 PROCEDURE mis_datos(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_estudiante(p_numero_control);
  OPEN p_resultado FOR SELECT numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre
   FROM Estudiantes WHERE numero_control=p_numero_control;
 END;
 PROCEDURE mis_solicitudes(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_estudiante(p_numero_control);
  OPEN p_resultado FOR SELECT id_solicitud,id_convocatoria,id_tipo_solicitud,id_estatus_solicitud,fecha_solicitud,fecha_actualizacion
   FROM Solicitudes WHERE num_control=p_numero_control;
 END;
 PROCEDURE mis_deducciones(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_estudiante(p_numero_control);
  OPEN p_resultado FOR SELECT d.id_deduccion_autorizada,d.id_solicitud,d.estatus_deduccion,
   d.monto_pago,d.monto_deduccion,d.monto_final FROM Deducciones_autorizadas d
   JOIN Solicitudes s ON s.id_solicitud=d.id_solicitud WHERE s.num_control=p_numero_control;
 END;
 PROCEDURE crear_solicitud(p_numero_control IN VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB,p_id OUT NUMBER) IS
 BEGIN
  PKG_SOLICITUDES_CORE.crear(p_numero_control,NULL,NULL,NULL,p_convocatoria,p_tipo,p_formulario,p_respuestas,p_id);
 END;
 PROCEDURE retirar_solicitud(p_numero_control VARCHAR2,p_solicitud NUMBER) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_estudiante(p_numero_control);
  PKG_SOLICITUDES_CORE.retirar(p_solicitud,p_numero_control,NULL,NULL,NULL);
 END;
 PROCEDURE reenviar_solicitud(p_numero_control VARCHAR2,p_solicitud NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_estudiante(p_numero_control);
  PKG_SOLICITUDES_CORE.reenviar(p_solicitud,p_numero_control,NULL,NULL,NULL,p_tipo,p_formulario,p_respuestas);
 END;
END PKG_ESTUDIANTE;
/

-- ============================================================================
-- API ASPIRANTE: ficha + CURP identifica el registro de la convocatoria
-- actualmente abierta. Las demas operaciones reciben ficha/periodo/anio
-- autenticados por el backend; NO se debe confiar en parametros del navegador.
-- ============================================================================
CREATE OR REPLACE PACKAGE PKG_ASPIRANTE AUTHID DEFINER AS
 PROCEDURE validar_aspirante(p_ficha IN VARCHAR2,p_curp IN VARCHAR2,
   p_ficha_out OUT VARCHAR2,p_periodo OUT NUMBER,p_anio OUT NUMBER);
 PROCEDURE mis_solicitudes(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_deducciones(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_convocatoria NUMBER,
   p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB,p_id OUT NUMBER);
 PROCEDURE retirar_solicitud(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_solicitud NUMBER);
 PROCEDURE reenviar_solicitud(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_solicitud NUMBER,
   p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB);
END PKG_ASPIRANTE;
/
CREATE OR REPLACE PACKAGE BODY PKG_ASPIRANTE AS
 PROCEDURE validar_aspirante(p_ficha IN VARCHAR2,p_curp IN VARCHAR2,
   p_ficha_out OUT VARCHAR2,p_periodo OUT NUMBER,p_anio OUT NUMBER) IS
  n NUMBER;
 BEGIN
  IF p_ficha IS NULL OR p_curp IS NULL THEN RAISE_APPLICATION_ERROR(-20840,'No autorizado'); END IF;
  SELECT COUNT(*) INTO n FROM Aspirantes a JOIN Convocatorias c
    ON c.id_periodo=a.periodo AND c.anio=a.anio
   WHERE a.ficha=p_ficha AND UPPER(a.curp)=UPPER(p_curp)
     AND TRUNC(SYSDATE) BETWEEN TRUNC(c.fecha_inicio_solicitudes) AND TRUNC(c.fecha_fin_solicitudes);
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20841,'No autorizado para convocatoria abierta'); END IF;
  SELECT a.ficha,a.periodo,a.anio INTO p_ficha_out,p_periodo,p_anio
   FROM Aspirantes a JOIN Convocatorias c ON c.id_periodo=a.periodo AND c.anio=a.anio
   WHERE a.ficha=p_ficha AND UPPER(a.curp)=UPPER(p_curp)
     AND TRUNC(SYSDATE) BETWEEN TRUNC(c.fecha_inicio_solicitudes) AND TRUNC(c.fecha_fin_solicitudes);
 END;
 PROCEDURE mis_solicitudes(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_aspirante_identidad(p_ficha,p_periodo,p_anio);
  OPEN p_resultado FOR SELECT id_solicitud,id_convocatoria,id_tipo_solicitud,id_estatus_solicitud,fecha_solicitud,fecha_actualizacion
   FROM Solicitudes WHERE ficha=p_ficha AND periodo=p_periodo AND anio=p_anio;
 END;
 PROCEDURE mis_deducciones(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_aspirante_identidad(p_ficha,p_periodo,p_anio);
  OPEN p_resultado FOR SELECT d.id_deduccion_autorizada,d.id_solicitud,d.estatus_deduccion,
   d.monto_pago,d.monto_deduccion,d.monto_final FROM Deducciones_autorizadas d
   JOIN Solicitudes s ON s.id_solicitud=d.id_solicitud
   WHERE s.ficha=p_ficha AND s.periodo=p_periodo AND s.anio=p_anio;
 END;
 PROCEDURE crear_solicitud(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_convocatoria NUMBER,
   p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB,p_id OUT NUMBER) IS
 BEGIN
  PKG_SOLICITUDES_CORE.crear(NULL,p_ficha,p_periodo,p_anio,p_convocatoria,p_tipo,p_formulario,p_respuestas,p_id);
 END;
 PROCEDURE retirar_solicitud(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_solicitud NUMBER) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_aspirante_identidad(p_ficha,p_periodo,p_anio);
  PKG_SOLICITUDES_CORE.retirar(p_solicitud,NULL,p_ficha,p_periodo,p_anio);
 END;
 PROCEDURE reenviar_solicitud(p_ficha VARCHAR2,p_periodo NUMBER,p_anio NUMBER,p_solicitud NUMBER,
   p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_aspirante_identidad(p_ficha,p_periodo,p_anio);
  PKG_SOLICITUDES_CORE.reenviar(p_solicitud,NULL,p_ficha,p_periodo,p_anio,p_tipo,p_formulario,p_respuestas);
 END;
END PKG_ASPIRANTE;
/

-- ============================================================================
-- API ADMINISTRADOR
-- El backend debe pasar el id del administrador AUTENTICADO. Oracle solo
-- comprueba existencia: no puede inferir la identidad web de SAR_BACKEND.
-- ============================================================================
CREATE OR REPLACE PACKAGE PKG_ADMINISTRADOR AUTHID DEFINER AS
 PROCEDURE listar_estudiantes(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_solicitudes(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_convocatorias(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_respuestas(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_deducciones(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud_estudiante(p_id_administrador VARCHAR2,p_control VARCHAR2,p_convocatoria NUMBER,
   p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB,p_id OUT NUMBER);
 PROCEDURE rechazar_solicitud(p_id_administrador VARCHAR2,p_solicitud NUMBER,p_comentario VARCHAR2);
 PROCEDURE autorizar_deduccion(p_id_administrador VARCHAR2,p_solicitud NUMBER,
   p_id_monto_inscripcion NUMBER,p_id_monto_reinscripcion NUMBER,p_comentario VARCHAR2,p_id OUT NUMBER);
 PROCEDURE cancelar_deduccion(p_id_administrador VARCHAR2,p_deduccion NUMBER,p_motivo VARCHAR2);
 PROCEDURE aplicar_deduccion(p_id_administrador VARCHAR2,p_deduccion NUMBER);
 PROCEDURE actualizar_fechas_convocatoria(p_id_administrador VARCHAR2,p_convocatoria NUMBER,
   p_inicio DATE,p_fin DATE,p_limite_autorizacion DATE,p_limite_aplicacion DATE);
 PROCEDURE actualizar_monto_oficial(p_id_administrador VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_monto NUMBER);
END PKG_ADMINISTRADOR;
/
CREATE OR REPLACE PACKAGE BODY PKG_ADMINISTRADOR AS
 PROCEDURE comprobar_plazo(p_conv NUMBER,p_tipo VARCHAR2) IS
  c Convocatorias%ROWTYPE;v_lim DATE;
 BEGIN
  SELECT * INTO c FROM Convocatorias WHERE id_convocatoria=p_conv;
  IF p_tipo='AUTORIZAR' THEN v_lim:=c.fecha_limite_autorizacion;
  ELSE v_lim:=c.fecha_limite_aplicacion; END IF;
  IF TRUNC(SYSDATE)>TRUNC(v_lim) THEN RAISE_APPLICATION_ERROR(-20870,'Plazo administrativo cerrado'); END IF;
 END;
 PROCEDURE obtener_montos(p_conv NUMBER,p_tipo NUMBER,p_mi NUMBER,p_mr NUMBER,
  p_pago OUT NUMBER,p_descuento OUT NUMBER,p_final OUT NUMBER) IS
 BEGIN
  IF (p_mi IS NULL AND p_mr IS NULL) OR (p_mi IS NOT NULL AND p_mr IS NOT NULL) THEN
   RAISE_APPLICATION_ERROR(-20871,'Indicar exactamente un monto base');
  END IF;
  IF p_mi IS NOT NULL THEN
   SELECT monto_pago INTO p_pago FROM Monto_Inscripciones WHERE id_monto_inscripcion=p_mi;
  ELSE
   SELECT monto_pago INTO p_pago FROM Monto_Reinscripciones WHERE id_monto_reinscripcion=p_mr;
  END IF;
  SELECT monto_deduccion INTO p_descuento FROM Convocatorias_TipoSolicitudes
   WHERE id_convocatoria=p_conv AND id_tipo_solicitud=p_tipo;
  IF p_descuento>p_pago THEN RAISE_APPLICATION_ERROR(-20872,'Deduccion mayor que monto base'); END IF;
  p_final:=p_pago-p_descuento;
 END;
 PROCEDURE listar_estudiantes(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR SELECT numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre FROM Estudiantes;
 END;
 PROCEDURE listar_solicitudes(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR SELECT s.id_solicitud,s.num_control,s.ficha,s.periodo,s.anio,s.id_convocatoria,
    s.id_estatus_solicitud,s.id_tipo_solicitud,s.fecha_actualizacion,t.nombre
   FROM Solicitudes s LEFT JOIN Tipo_Solicitudes t ON t.id_tipo_solicitud=s.id_tipo_solicitud;
 END;
 PROCEDURE listar_convocatorias(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR SELECT id_convocatoria,fecha_inicio_solicitudes,fecha_fin_solicitudes,
   fecha_limite_autorizacion,fecha_limite_aplicacion,id_periodo,anio FROM Convocatorias
   ORDER BY anio DESC,id_periodo DESC,id_convocatoria DESC;
 END;
 PROCEDURE listar_respuestas(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR SELECT id_respuesta,id_solicitud,id_pregunta,id_opcion,texto_respuesta FROM Respuestas;
 END;
 PROCEDURE listar_deducciones(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR SELECT id_deduccion_autorizada,id_solicitud,estatus_deduccion,monto_pago,
   monto_deduccion,monto_final FROM Deducciones_autorizadas;
 END;
 PROCEDURE crear_solicitud_estudiante(p_id_administrador VARCHAR2,p_control VARCHAR2,p_convocatoria NUMBER,
   p_tipo NUMBER,p_formulario NUMBER,p_respuestas CLOB,p_id OUT NUMBER) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  PKG_SOLICITUDES_CORE.crear(p_control,NULL,NULL,NULL,p_convocatoria,p_tipo,p_formulario,p_respuestas,p_id);
 END;
 PROCEDURE rechazar_solicitud(p_id_administrador VARCHAR2,p_solicitud NUMBER,p_comentario VARCHAR2) IS
  s Solicitudes%ROWTYPE;
 BEGIN
  SAVEPOINT sp_rechazar;
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=p_solicitud FOR UPDATE;
  comprobar_plazo(s.id_convocatoria,'AUTORIZAR');
  IF s.id_estatus_solicitud<>1 THEN RAISE_APPLICATION_ERROR(-20873,'Solo se rechazan solicitudes pendientes'); END IF;
  IF TRIM(p_comentario) IS NULL THEN RAISE_APPLICATION_ERROR(-20874,'Indicar motivo del rechazo'); END IF;
  UPDATE Solicitudes SET id_estatus_solicitud=3 WHERE id_solicitud=p_solicitud;
  PKG_SOLICITUDES_CORE.registrar_solicitud(p_solicitud,p_id_administrador,p_comentario);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_rechazar; RAISE;
 END;
 PROCEDURE autorizar_deduccion(p_id_administrador VARCHAR2,p_solicitud NUMBER,
   p_id_monto_inscripcion NUMBER,p_id_monto_reinscripcion NUMBER,p_comentario VARCHAR2,p_id OUT NUMBER) IS
  s Solicitudes%ROWTYPE;v_est NUMBER;v_pago NUMBER;v_desc NUMBER;v_final NUMBER;v_ded NUMBER;
 BEGIN
  SAVEPOINT sp_autorizar;
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=p_solicitud FOR UPDATE;
  comprobar_plazo(s.id_convocatoria,'AUTORIZAR');
  IF s.id_estatus_solicitud NOT IN (1,4) THEN RAISE_APPLICATION_ERROR(-20875,'Solicitud no pendiente ni cancelada'); END IF;
  IF TRIM(p_comentario) IS NULL THEN RAISE_APPLICATION_ERROR(-20876,'Indicar comentario de autorizacion'); END IF;
  IF s.id_estatus_solicitud=1 THEN
   SELECT COUNT(*) INTO v_est FROM Deducciones_autorizadas WHERE id_solicitud=p_solicitud AND estatus_deduccion<>4;
   IF v_est>0 THEN RAISE_APPLICATION_ERROR(-20877,'Deduccion previa no cancelada'); END IF;
  END IF;
  obtener_montos(s.id_convocatoria,s.id_tipo_solicitud,p_id_monto_inscripcion,p_id_monto_reinscripcion,
   v_pago,v_desc,v_final);
  SELECT COUNT(*),MIN(id_deduccion_autorizada) INTO v_est,v_ded
    FROM Deducciones_autorizadas WHERE id_solicitud=p_solicitud;
  IF v_est=0 THEN
   SELECT seq_deducciones_autorizadas.NEXTVAL INTO p_id FROM dual;
   INSERT INTO Deducciones_autorizadas(id_deduccion_autorizada,estatus_deduccion,monto_final,
    id_solicitud,id_administrador,id_convocatoria,id_tipo_solicitud,id_monto_inscripcion,
    id_monto_reinscripcion,monto_pago,monto_deduccion)
   VALUES(p_id,2,v_final,p_solicitud,p_id_administrador,s.id_convocatoria,s.id_tipo_solicitud,
    p_id_monto_inscripcion,p_id_monto_reinscripcion,v_pago,v_desc);
  ELSE
   p_id:=v_ded;
   UPDATE Deducciones_autorizadas SET estatus_deduccion=2,id_administrador=p_id_administrador,
    id_tipo_solicitud=s.id_tipo_solicitud,id_monto_inscripcion=p_id_monto_inscripcion,
    id_monto_reinscripcion=p_id_monto_reinscripcion,monto_pago=v_pago,monto_deduccion=v_desc,monto_final=v_final
   WHERE id_deduccion_autorizada=p_id AND estatus_deduccion=4;
   IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20878,'Deduccion no cancelada'); END IF;
  END IF;
  UPDATE Solicitudes SET id_estatus_solicitud=2 WHERE id_solicitud=p_solicitud;
  PKG_SOLICITUDES_CORE.registrar_solicitud(p_solicitud,p_id_administrador,p_comentario);
  PKG_SOLICITUDES_CORE.registrar_deduccion(p_id,p_id_administrador);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_autorizar; RAISE;
 END;
 PROCEDURE cancelar_deduccion(p_id_administrador VARCHAR2,p_deduccion NUMBER,p_motivo VARCHAR2) IS
  d Deducciones_autorizadas%ROWTYPE;s Solicitudes%ROWTYPE;
 BEGIN
  SAVEPOINT sp_cancelar;
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  IF TRIM(p_motivo) IS NULL THEN RAISE_APPLICATION_ERROR(-20879,'Motivo de cancelacion obligatorio'); END IF;
  -- Orden de bloqueos consistente: primero solicitud y despues deduccion.
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=(SELECT id_solicitud FROM Deducciones_autorizadas
   WHERE id_deduccion_autorizada=p_deduccion) FOR UPDATE;
  SELECT * INTO d FROM Deducciones_autorizadas WHERE id_deduccion_autorizada=p_deduccion FOR UPDATE;
  comprobar_plazo(s.id_convocatoria,'APLICAR');
  IF d.estatus_deduccion<>2 OR s.id_estatus_solicitud<>2 THEN
   RAISE_APPLICATION_ERROR(-20880,'Solo se cancela una deduccion autorizada');
  END IF;
  UPDATE Deducciones_autorizadas SET estatus_deduccion=4,id_administrador=p_id_administrador
   WHERE id_deduccion_autorizada=p_deduccion;
  UPDATE Solicitudes SET id_estatus_solicitud=4 WHERE id_solicitud=d.id_solicitud;
  PKG_SOLICITUDES_CORE.registrar_solicitud(d.id_solicitud,p_id_administrador,p_motivo);
  PKG_SOLICITUDES_CORE.registrar_deduccion(p_deduccion,p_id_administrador);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_cancelar; RAISE;
 END;
 PROCEDURE aplicar_deduccion(p_id_administrador VARCHAR2,p_deduccion NUMBER) IS
  d Deducciones_autorizadas%ROWTYPE;s Solicitudes%ROWTYPE;
  v_pago NUMBER;v_desc NUMBER;v_final NUMBER;
 BEGIN
  SAVEPOINT sp_aplicar;
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  -- Orden de bloqueos consistente: primero solicitud y despues deduccion.
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=(SELECT id_solicitud FROM Deducciones_autorizadas
   WHERE id_deduccion_autorizada=p_deduccion) FOR UPDATE;
  SELECT * INTO d FROM Deducciones_autorizadas WHERE id_deduccion_autorizada=p_deduccion FOR UPDATE;
  comprobar_plazo(s.id_convocatoria,'APLICAR');
  IF d.estatus_deduccion<>2 OR s.id_estatus_solicitud<>2 THEN
   RAISE_APPLICATION_ERROR(-20881,'Solo se aplica una deduccion autorizada');
  END IF;
  obtener_montos(s.id_convocatoria,s.id_tipo_solicitud,d.id_monto_inscripcion,d.id_monto_reinscripcion,
   v_pago,v_desc,v_final);
  UPDATE Deducciones_autorizadas SET estatus_deduccion=5,id_administrador=p_id_administrador,
   monto_pago=v_pago,monto_deduccion=v_desc,monto_final=v_final WHERE id_deduccion_autorizada=p_deduccion;
  UPDATE Solicitudes SET id_estatus_solicitud=5 WHERE id_solicitud=d.id_solicitud;
  PKG_SOLICITUDES_CORE.registrar_solicitud(d.id_solicitud,p_id_administrador,'Deduccion aplicada');
  PKG_SOLICITUDES_CORE.registrar_deduccion(p_deduccion,p_id_administrador);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_aplicar; RAISE;
 END;
 PROCEDURE actualizar_fechas_convocatoria(p_id_administrador VARCHAR2,p_convocatoria NUMBER,
   p_inicio DATE,p_fin DATE,p_limite_autorizacion DATE,p_limite_aplicacion DATE) IS
  c Convocatorias%ROWTYPE;v_n NUMBER;
 BEGIN
  SAVEPOINT sp_fechas;
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  SELECT * INTO c FROM Convocatorias WHERE id_convocatoria=p_convocatoria FOR UPDATE;
  IF TRUNC(SYSDATE)>TRUNC(c.fecha_limite_aplicacion) THEN
   RAISE_APPLICATION_ERROR(-20882,'Convocatoria cerrada definitivamente');
  END IF;
  IF TRUNC(p_inicio)>TRUNC(p_fin) OR TRUNC(p_fin)>TRUNC(p_limite_autorizacion)
     OR TRUNC(p_limite_autorizacion)>TRUNC(p_limite_aplicacion) THEN
   RAISE_APPLICATION_ERROR(-20883,'Orden de fechas invalido');
  END IF;
  -- No desplazar fechas para excluir movimientos ya realizados.
  SELECT COUNT(*) INTO v_n FROM Solicitudes WHERE id_convocatoria=p_convocatoria
   AND TRUNC(fecha_solicitud) NOT BETWEEN TRUNC(p_inicio) AND TRUNC(p_fin);
  IF v_n>0 THEN RAISE_APPLICATION_ERROR(-20884,'Fechas excluyen solicitudes ya enviadas'); END IF;
  SELECT COUNT(*) INTO v_n FROM Historial_Solicitudes h JOIN Solicitudes s ON s.id_solicitud=h.id_solicitud
   WHERE s.id_convocatoria=p_convocatoria
    AND ((h.id_estatus IN (1,6) AND TRUNC(h.fecha_registro)>TRUNC(p_fin))
      OR (h.id_estatus IN (2,3) AND TRUNC(h.fecha_registro)>TRUNC(p_limite_autorizacion))
      OR (h.id_estatus IN (4,5) AND TRUNC(h.fecha_registro)>TRUNC(p_limite_aplicacion)));
  IF v_n>0 THEN RAISE_APPLICATION_ERROR(-20885,'Fechas excluyen movimientos historicos'); END IF;
  UPDATE Convocatorias SET fecha_inicio_solicitudes=p_inicio,fecha_fin_solicitudes=p_fin,
   fecha_limite_autorizacion=p_limite_autorizacion,fecha_limite_aplicacion=p_limite_aplicacion
   WHERE id_convocatoria=p_convocatoria;
  INSERT INTO Historial_Convocatorias(id_historial_convocatoria,id_convocatoria,id_administrador,
   inicio_anterior,fin_anterior,autorizacion_anterior,aplicacion_anterior,
   inicio_nuevo,fin_nuevo,autorizacion_nueva,aplicacion_nueva)
  VALUES(seq_historial_convocatorias.NEXTVAL,p_convocatoria,p_id_administrador,
   c.fecha_inicio_solicitudes,c.fecha_fin_solicitudes,c.fecha_limite_autorizacion,c.fecha_limite_aplicacion,
   p_inicio,p_fin,p_limite_autorizacion,p_limite_aplicacion);
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_fechas; RAISE;
 END;
 PROCEDURE actualizar_monto_oficial(p_id_administrador VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_monto NUMBER) IS
 BEGIN
  SAVEPOINT sp_monto;
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  comprobar_plazo(p_convocatoria,'AUTORIZAR');
  IF p_monto IS NULL OR p_monto<0 THEN RAISE_APPLICATION_ERROR(-20886,'Monto invalido'); END IF;
  UPDATE Convocatorias_TipoSolicitudes SET monto_deduccion=p_monto
   WHERE id_convocatoria=p_convocatoria AND id_tipo_solicitud=p_tipo;
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20887,'Tipo no configurado en convocatoria'); END IF;
 EXCEPTION WHEN OTHERS THEN ROLLBACK TO sp_monto; RAISE;
 END;
END PKG_ADMINISTRADOR;
/

-- ============================================================================
-- COMPROBACION Y PERMISOS (solo los tres paquetes publicos)
-- ============================================================================
PROMPT ===== COMPROBAR COMPILACION =====
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM USER_OBJECTS
  WHERE OBJECT_NAME IN ('PKG_SOLICITUDES_CORE','PKG_ESTUDIANTE','PKG_ASPIRANTE','PKG_ADMINISTRADOR')
    AND STATUS<>'VALID';
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20830,'Objetos INVALID: consultar USER_ERRORS'); END IF;
 SELECT COUNT(*) INTO n FROM USER_ERRORS
  WHERE NAME IN ('PKG_SOLICITUDES_CORE','PKG_ESTUDIANTE','PKG_ASPIRANTE','PKG_ADMINISTRADOR');
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20831,'Errores de compilacion: consultar USER_ERRORS'); END IF;
 DBMS_OUTPUT.PUT_LINE('OK: cuatro paquetes compilados');
END;
/
PROMPT ===== CONCEDER EXECUTE SOLO A SAR_BACKEND =====
GRANT EXECUTE ON PKG_ESTUDIANTE TO &&ORACLE_BACKEND_USER;
GRANT EXECUTE ON PKG_ADMINISTRADOR TO &&ORACLE_BACKEND_USER;
GRANT EXECUTE ON PKG_ASPIRANTE TO &&ORACLE_BACKEND_USER;
COLUMN GRANTEE FORMAT A30
COLUMN TABLE_NAME FORMAT A30
COLUMN PRIVILEGE FORMAT A12
SELECT GRANTEE,TABLE_NAME,PRIVILEGE FROM USER_TAB_PRIVS_MADE
 WHERE TABLE_NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR','PKG_ASPIRANTE','PKG_SOLICITUDES_CORE')
 ORDER BY GRANTEE,TABLE_NAME;
PROMPT ===== PAQUETES Y PERMISOS CONFIGURADOS =====
EXIT SUCCESS
