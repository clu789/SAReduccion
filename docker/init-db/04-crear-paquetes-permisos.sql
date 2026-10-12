-- ============================================================================
-- SAReduccion | 04-crear-paquetes-permisos.sql
-- Objetivo: definir la API PL/SQL para estudiantes y administradores.
-- Dependencias:
--   01-configurar-esquema.sql    (usuario propietario y privilegios)
--   02-crear-estructura.sql     (tablas y secuencias)
--   03-crear-identidades-roles.sql (roles Oracle)
-- Ejecutar con SQL*Plus o SQLcl como SA_REDUCCION conectado a XEPDB1.
-- NO ejecutar como SYS: los paquetes deben pertenecer a SA_REDUCCION.
--
-- Reejecutable: CREATE OR REPLACE conserva datos y recrea el codigo de paquetes.
-- No elimina usuarios, roles, tablas ni registros; no inserta datos de prueba.
-- No concede permisos CRUD directos sobre tablas a los roles.
-- ATENCION: reemplazar paquetes puede afectar sesiones que los usan.
-- ============================================================================
SET SERVEROUTPUT ON SIZE UNLIMITED
SET VERIFY OFF
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
-- BLOQUE 1. Seguridad: impedir creacion accidental en otro esquema/PDB.
PROMPT ===== 01 VALIDAR IDENTIDAD =====
BEGIN
 IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'SA_REDUCCION' OR SYS_CONTEXT('USERENV','CON_NAME') <> 'XEPDB1' THEN
  RAISE_APPLICATION_ERROR(-20801,'Ejecutar como SA_REDUCCION en XEPDB1');
 END IF;
 DBMS_OUTPUT.PUT_LINE('OK: esquema correcto');
END;
/
-- BLOQUE 2. API del estudiante.
-- AUTHID DEFINER: el paquete opera con los privilegios del propietario,
-- pero verifica SESSION_USER para limitar el acceso a la cuenta del alumno.
-- Consultas: datos propios, solicitudes propias y deducciones propias.
-- Escrituras: crear una solicitud y registrar/actualizar sus respuestas.
PROMPT ===== 02 PAQUETE ESTUDIANTE =====
CREATE OR REPLACE PACKAGE PKG_ESTUDIANTE AUTHID DEFINER AS
 PROCEDURE mis_datos(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_solicitudes(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_deducciones(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud(p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER);
 PROCEDURE guardar_respuesta(p_solicitud NUMBER,p_pregunta NUMBER,p_texto VARCHAR2,p_opcion NUMBER DEFAULT NULL);
END PKG_ESTUDIANTE;
/
CREATE OR REPLACE PACKAGE BODY PKG_ESTUDIANTE AS
 -- Comprueba el prefijo ALU_ y la existencia del numero de control.
 -- Se usa en todas las operaciones para evitar consultar datos ajenos.
 FUNCTION control_actual RETURN VARCHAR2 IS
  u VARCHAR2(128) := SYS_CONTEXT('USERENV','SESSION_USER'); n NUMBER; c VARCHAR2(50);
 BEGIN
  IF SUBSTR(u,1,4)<>'ALU_' THEN RAISE_APPLICATION_ERROR(-20810,'Se requiere estudiante'); END IF;
  c:=SUBSTR(u,5);
  SELECT COUNT(*) INTO n FROM Estudiantes WHERE Numero_Control=c;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20811,'Estudiante no registrado'); END IF;
  RETURN c;
 END;
 PROCEDURE mis_datos(p_resultado OUT SYS_REFCURSOR) IS c VARCHAR2(50):=control_actual; BEGIN
  OPEN p_resultado FOR SELECT Numero_Control,Nombre,PrimerAppellido,SegundoApellido,Carrera,Semestre FROM Estudiantes WHERE Numero_Control=c;
 END;
 PROCEDURE mis_solicitudes(p_resultado OUT SYS_REFCURSOR) IS c VARCHAR2(50):=control_actual; BEGIN
  OPEN p_resultado FOR SELECT idSolicitud,id_convocatoria,id_Tipo_Solicitud,id_estatus_solicitud,fecha_Solicitud FROM Solicitudes WHERE num_control=c;
 END;
 PROCEDURE mis_deducciones(p_resultado OUT SYS_REFCURSOR) IS c VARCHAR2(50):=control_actual; BEGIN
  OPEN p_resultado FOR SELECT d.id_deduccion_autorizada,d.id_solicitud,d.estatus_deduccion,d.monto_pago,d.monto_deduccion,d.monto_final
  FROM Deducciones_autorizadas d JOIN Solicitudes s ON s.idSolicitud=d.id_solicitud WHERE s.num_control=c;
 END;
 -- Solo permite solicitar durante la vigencia de la convocatoria y cuando
 -- la combinacion convocatoria/tipo/formulario esta registrada.
 PROCEDURE crear_solicitud(p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER) IS
  c VARCHAR2(50):=control_actual; n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Convocatorias v JOIN Convocatorias_TipoSolicitudes ct ON ct.id_convocatoria=v.id_convocatoria
   JOIN Formularios f ON f.id_Tipo_Solicitud=ct.id_Tipo_Solicitud
   WHERE v.id_convocatoria=p_convocatoria AND ct.id_Tipo_Solicitud=p_tipo AND f.idFormulario=p_formulario
   AND TRUNC(SYSDATE) BETWEEN TRUNC(v.fecha_inicio) AND TRUNC(v.fecha_fin);
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20812,'Convocatoria, tipo o formulario no disponible'); END IF;
  SELECT seq_solicitudes.NEXTVAL INTO p_id FROM dual;
  INSERT INTO Solicitudes(idSolicitud,id_estatus_solicitud,id_convocatoria,num_control,id_Tipo_Solicitud,idFormulario)
   VALUES(p_id,1,p_convocatoria,c,p_tipo,p_formulario);
 END;
 -- Inserta o actualiza una respuesta de una solicitud propia y pendiente.
 -- El llamador decide COMMIT/ROLLBACK de la transaccion.
 PROCEDURE guardar_respuesta(p_solicitud NUMBER,p_pregunta NUMBER,p_texto VARCHAR2,p_opcion NUMBER DEFAULT NULL) IS
  c VARCHAR2(50):=control_actual; n NUMBER; rid NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Solicitudes s JOIN Convocatorias v ON v.id_convocatoria=s.id_convocatoria
   WHERE s.idSolicitud=p_solicitud AND s.num_control=c AND s.id_estatus_solicitud=1
   AND TRUNC(SYSDATE) BETWEEN TRUNC(v.fecha_inicio) AND TRUNC(v.fecha_fin);
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20813,'Solicitud no propia, cerrada o fuera de plazo'); END IF;
  SELECT MIN(id_respuesta) INTO rid FROM Respuestas WHERE id_solicitud=p_solicitud AND id_pregunta=p_pregunta;
  IF rid IS NULL THEN
   INSERT INTO Respuestas(id_respuesta,texto_respuesta,id_solicitud,id_pregunta,id_opcion)
   VALUES(seq_respuestas.NEXTVAL,p_texto,p_solicitud,p_pregunta,p_opcion);
  ELSE
   UPDATE Respuestas SET texto_respuesta=p_texto,id_opcion=p_opcion WHERE id_respuesta=rid;
  END IF;
 END;
END PKG_ESTUDIANTE;
/
-- BLOQUE 3. API del administrador.
-- Exige que SESSION_USER corresponda a un registro de Administradores.
-- Puede consultar registros y gestionar solicitudes/deducciones.
-- Las respuestas son de solo lectura para este perfil.
PROMPT ===== 03 PAQUETE ADMINISTRADOR =====
CREATE OR REPLACE PACKAGE PKG_ADMINISTRADOR AUTHID DEFINER AS
 PROCEDURE listar_estudiantes(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_solicitudes(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_respuestas(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_deducciones(p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud_estudiante(p_control VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER);
 PROCEDURE cambiar_estatus_solicitud(p_solicitud NUMBER,p_estatus NUMBER);
 PROCEDURE autorizar_deduccion(p_solicitud NUMBER,p_id_monto_inscripcion NUMBER,p_id_monto_reinscripcion NUMBER,p_comentario VARCHAR2,p_id OUT NUMBER);
 PROCEDURE cambiar_estatus_deduccion(p_deduccion NUMBER,p_estatus NUMBER,p_comentario VARCHAR2 DEFAULT NULL);
END PKG_ADMINISTRADOR;
/
CREATE OR REPLACE PACKAGE BODY PKG_ADMINISTRADOR AS
 -- Valida que el usuario de Oracle sea un administrador registrado.
 FUNCTION admin_actual RETURN VARCHAR2 IS u VARCHAR2(128):=SYS_CONTEXT('USERENV','SESSION_USER'); n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Administradores WHERE idAdministrador=u;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20820,'No es administrador registrado'); END IF;
  RETURN u;
 END;
 PROCEDURE listar_estudiantes(p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(128):=admin_actual; BEGIN
  OPEN p_resultado FOR SELECT Numero_Control,Nombre,PrimerAppellido,SegundoApellido,Carrera,Semestre FROM Estudiantes;
 END;
 PROCEDURE listar_solicitudes(p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(128):=admin_actual; BEGIN
  OPEN p_resultado FOR SELECT idSolicitud,num_control,ficha,periodo,anio,id_convocatoria,id_estatus_solicitud,id_Tipo_Solicitud FROM Solicitudes;
 END;
 PROCEDURE listar_respuestas(p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(128):=admin_actual; BEGIN
  OPEN p_resultado FOR SELECT id_respuesta,id_solicitud,id_pregunta,id_opcion,texto_respuesta FROM Respuestas;
 END;
 PROCEDURE listar_deducciones(p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(128):=admin_actual; BEGIN
  OPEN p_resultado FOR SELECT id_deduccion_autorizada,id_solicitud,estatus_deduccion,monto_pago,monto_deduccion,monto_final FROM Deducciones_autorizadas;
 END;
 PROCEDURE crear_solicitud_estudiante(p_control VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER) IS
  u VARCHAR2(128):=admin_actual; n NUMBER;
 BEGIN
  SELECT COUNT(*) INTO n FROM Estudiantes WHERE Numero_Control=p_control;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20821,'Estudiante inexistente'); END IF;
  SELECT COUNT(*) INTO n FROM Convocatorias v JOIN Convocatorias_TipoSolicitudes ct ON ct.id_convocatoria=v.id_convocatoria
  JOIN Formularios f ON f.id_Tipo_Solicitud=ct.id_Tipo_Solicitud
  WHERE v.id_convocatoria=p_convocatoria AND ct.id_Tipo_Solicitud=p_tipo AND f.idFormulario=p_formulario
  AND TRUNC(SYSDATE) BETWEEN TRUNC(v.fecha_inicio) AND TRUNC(v.fecha_fin);
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20822,'Convocatoria o formulario no disponible'); END IF;
  SELECT seq_solicitudes.NEXTVAL INTO p_id FROM dual;
  INSERT INTO Solicitudes(idSolicitud,id_estatus_solicitud,id_convocatoria,num_control,id_Tipo_Solicitud,idFormulario)
  VALUES(p_id,1,p_convocatoria,p_control,p_tipo,p_formulario);
 END;
 -- Transiciones permitidas para una solicitud pendiente: 2 o 3.
 PROCEDURE cambiar_estatus_solicitud(p_solicitud NUMBER,p_estatus NUMBER) IS u VARCHAR2(128):=admin_actual; n NUMBER;
 BEGIN
  IF p_estatus NOT IN (2,3) OR p_estatus IS NULL THEN RAISE_APPLICATION_ERROR(-20823,'Solo autorizar (2) o rechazar (3) solicitudes'); END IF;
  UPDATE Solicitudes SET id_estatus_solicitud=p_estatus WHERE idSolicitud=p_solicitud AND id_estatus_solicitud=1;
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20824,'Solicitud inexistente o no pendiente'); END IF;
 END;
 -- Solo autoriza deducciones para solicitudes previamente aprobadas.
 PROCEDURE autorizar_deduccion(p_solicitud NUMBER,p_id_monto_inscripcion NUMBER,p_id_monto_reinscripcion NUMBER,p_comentario VARCHAR2,p_id OUT NUMBER) IS
  u VARCHAR2(128):=admin_actual; v_conv NUMBER; v_tipo NUMBER; v_est NUMBER;
 BEGIN
  SELECT id_convocatoria,id_Tipo_Solicitud,id_estatus_solicitud INTO v_conv,v_tipo,v_est FROM Solicitudes WHERE idSolicitud=p_solicitud;
  IF v_est<>2 THEN RAISE_APPLICATION_ERROR(-20825,'Solicitud no autorizada'); END IF;
  SELECT seq_deducciones_autorizadas.NEXTVAL INTO p_id FROM dual;
  INSERT INTO Deducciones_autorizadas(id_deduccion_autorizada,estatus_deduccion,monto_final,id_solicitud,id_administrador,
    fecha_autorizacion,id_convocatoria,id_Tipo_Solicitud,comentario,id_monto_inscripcion,id_monto_reinscripcion,monto_pago,monto_deduccion)
  VALUES(p_id,2,0,p_solicitud,u,SYSDATE,v_conv,v_tipo,p_comentario,p_id_monto_inscripcion,p_id_monto_reinscripcion,0,0);
 END;
 PROCEDURE cambiar_estatus_deduccion(p_deduccion NUMBER,p_estatus NUMBER,p_comentario VARCHAR2 DEFAULT NULL) IS u VARCHAR2(128):=admin_actual;
 BEGIN
  IF p_estatus NOT IN (2,4,5) OR p_estatus IS NULL THEN RAISE_APPLICATION_ERROR(-20826,'Estatus invalido'); END IF;
  UPDATE Deducciones_autorizadas SET estatus_deduccion=p_estatus,id_administrador=u,comentario=p_comentario
   WHERE id_deduccion_autorizada=p_deduccion AND estatus_deduccion<>p_estatus;
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20827,'Deduccion inexistente o estatus sin cambios'); END IF;
 END;
END PKG_ADMINISTRADOR;
/
-- BLOQUE 4. Verificar que especificaciones y cuerpos compilaron sin errores.
-- En caso de fallo, consultar USER_ERRORS con SA_REDUCCION.
PROMPT ===== 04 COMPROBAR COMPILACION =====
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM USER_OBJECTS WHERE OBJECT_NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR') AND STATUS<>'VALID';
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20830,'Objetos INVALID: consultar USER_ERRORS'); END IF;
 SELECT COUNT(*) INTO n FROM USER_ERRORS WHERE NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR');
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20831,'Errores de compilacion: consultar USER_ERRORS'); END IF;
 DBMS_OUTPUT.PUT_LINE('OK: ambos paquetes VALID');
END;
/
-- BLOQUE 5. Acceso por rol a los paquetes, no a las tablas.
-- Requiere que los roles existan (script 03).
-- El estudiante no recibe permisos del paquete de administracion.
PROMPT ===== 05 GRANT EXECUTE =====
GRANT EXECUTE ON PKG_ESTUDIANTE TO ROL_SAR_ESTUDIANTE_SIM;
GRANT EXECUTE ON PKG_ADMINISTRADOR TO ROL_SAR_ADMIN_SIM;
-- BLOQUE 6. Auditoria rapida de permisos concedidos.
COLUMN GRANTEE FORMAT A30
COLUMN TABLE_NAME FORMAT A25
COLUMN PRIVILEGE FORMAT A12
SELECT GRANTEE, TABLE_NAME, PRIVILEGE
  FROM USER_TAB_PRIVS_MADE
 WHERE TABLE_NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR')
   AND GRANTEE IN ('ROL_SAR_ESTUDIANTE_SIM','ROL_SAR_ADMIN_SIM')
 ORDER BY GRANTEE, TABLE_NAME;
PROMPT ===== LISTO: PAQUETES CRUD Y PERMISOS CONFIGURADOS =====
-- DDL y GRANT hacen COMMIT implicito en Oracle. Las operaciones CRUD de
-- los procedimientos no hacen COMMIT: el cliente controla la transaccion.
EXIT SUCCESS
