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
-- AUTHID DEFINER: el paquete opera con los privilegios del propietario.
-- El backend pasa el numero de control autenticado y el paquete comprueba
-- que pertenezca a ESTUDIANTES. Solo SAR_BACKEND recibe EXECUTE.
-- Consultas: datos propios, solicitudes propias y deducciones propias.
-- Escrituras: crear una solicitud y registrar/actualizar sus respuestas.
PROMPT ===== 02 PAQUETE ESTUDIANTE =====
CREATE OR REPLACE PACKAGE PKG_ESTUDIANTE AUTHID DEFINER AS
 PROCEDURE mis_datos(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_solicitudes(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE mis_deducciones(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud(p_numero_control IN VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER);
 PROCEDURE guardar_respuesta(p_numero_control IN VARCHAR2,p_solicitud NUMBER,p_pregunta NUMBER,p_texto VARCHAR2,p_opcion NUMBER DEFAULT NULL);
END PKG_ESTUDIANTE;
/
CREATE OR REPLACE PACKAGE BODY PKG_ESTUDIANTE AS
 -- Valida que la identidad proporcionada corresponda a un estudiante registrado.
 FUNCTION validar_estudiante(p_numero_control VARCHAR2) RETURN VARCHAR2 IS n NUMBER;
 BEGIN
  IF p_numero_control IS NULL THEN RAISE_APPLICATION_ERROR(-20810,'Se requiere numero de control'); END IF;
  SELECT COUNT(*) INTO n FROM Estudiantes WHERE Numero_Control=p_numero_control;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20811,'Estudiante no registrado'); END IF;
  RETURN p_numero_control;
 END;
 PROCEDURE mis_datos(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS c VARCHAR2(50):=validar_estudiante(p_numero_control); BEGIN
  OPEN p_resultado FOR SELECT Numero_Control,Nombre,PrimerAppellido,SegundoApellido,Carrera,Semestre FROM Estudiantes WHERE Numero_Control=c;
 END;
 PROCEDURE mis_solicitudes(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS c VARCHAR2(50):=validar_estudiante(p_numero_control); BEGIN
  OPEN p_resultado FOR SELECT idSolicitud,id_convocatoria,id_Tipo_Solicitud,id_estatus_solicitud,fecha_Solicitud FROM Solicitudes WHERE num_control=c;
 END;
 PROCEDURE mis_deducciones(p_numero_control IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS c VARCHAR2(50):=validar_estudiante(p_numero_control); BEGIN
  OPEN p_resultado FOR SELECT d.id_deduccion_autorizada,d.id_solicitud,d.estatus_deduccion,d.monto_pago,d.monto_deduccion,d.monto_final
  FROM Deducciones_autorizadas d JOIN Solicitudes s ON s.idSolicitud=d.id_solicitud WHERE s.num_control=c;
 END;
 -- Solo permite solicitar durante la vigencia de la convocatoria y cuando
 -- la combinacion convocatoria/tipo/formulario esta registrada.
 PROCEDURE crear_solicitud(p_numero_control IN VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER) IS
  c VARCHAR2(50):=validar_estudiante(p_numero_control); n NUMBER;
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
 PROCEDURE guardar_respuesta(p_numero_control IN VARCHAR2,p_solicitud NUMBER,p_pregunta NUMBER,p_texto VARCHAR2,p_opcion NUMBER DEFAULT NULL) IS
  c VARCHAR2(50):=validar_estudiante(p_numero_control); n NUMBER; rid NUMBER;
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
-- El backend pasa el id autenticado y el paquete valida ADMINISTRADORES.
-- Solo SAR_BACKEND recibe EXECUTE.
-- Puede consultar registros y gestionar solicitudes/deducciones.
-- Las respuestas son de solo lectura para este perfil.
PROMPT ===== 03 PAQUETE ADMINISTRADOR =====
CREATE OR REPLACE PACKAGE PKG_ADMINISTRADOR AUTHID DEFINER AS
 PROCEDURE listar_estudiantes(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_solicitudes(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_respuestas(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_deducciones(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE crear_solicitud_estudiante(p_id_administrador IN VARCHAR2,p_control VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER);
 PROCEDURE cambiar_estatus_solicitud(p_id_administrador IN VARCHAR2,p_solicitud NUMBER,p_estatus NUMBER);
 PROCEDURE autorizar_deduccion(p_id_administrador IN VARCHAR2,p_solicitud NUMBER,p_id_monto_inscripcion NUMBER,p_id_monto_reinscripcion NUMBER,p_comentario VARCHAR2,p_id OUT NUMBER);
 PROCEDURE cambiar_estatus_deduccion(p_id_administrador IN VARCHAR2,p_deduccion NUMBER,p_estatus NUMBER,p_comentario VARCHAR2 DEFAULT NULL);
END PKG_ADMINISTRADOR;
/
CREATE OR REPLACE PACKAGE BODY PKG_ADMINISTRADOR AS
 -- Valida que la identidad proporcionada corresponda a un administrador registrado.
 FUNCTION validar_administrador(p_id_administrador VARCHAR2) RETURN VARCHAR2 IS n NUMBER;
 BEGIN
  IF p_id_administrador IS NULL THEN RAISE_APPLICATION_ERROR(-20820,'Se requiere administrador'); END IF;
  SELECT COUNT(*) INTO n FROM Administradores WHERE idAdministrador=p_id_administrador;
  IF n<>1 THEN RAISE_APPLICATION_ERROR(-20820,'No es administrador registrado'); END IF;
  RETURN p_id_administrador;
 END;
 PROCEDURE listar_estudiantes(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(50):=validar_administrador(p_id_administrador); BEGIN
  OPEN p_resultado FOR SELECT Numero_Control,Nombre,PrimerAppellido,SegundoApellido,Carrera,Semestre FROM Estudiantes;
 END;
 PROCEDURE listar_solicitudes(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(50):=validar_administrador(p_id_administrador); BEGIN
  OPEN p_resultado FOR SELECT idSolicitud,num_control,ficha,periodo,anio,id_convocatoria,id_estatus_solicitud,id_Tipo_Solicitud FROM Solicitudes;
 END;
 PROCEDURE listar_respuestas(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(50):=validar_administrador(p_id_administrador); BEGIN
  OPEN p_resultado FOR SELECT id_respuesta,id_solicitud,id_pregunta,id_opcion,texto_respuesta FROM Respuestas;
 END;
 PROCEDURE listar_deducciones(p_id_administrador IN VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS u VARCHAR2(50):=validar_administrador(p_id_administrador); BEGIN
  OPEN p_resultado FOR SELECT id_deduccion_autorizada,id_solicitud,estatus_deduccion,monto_pago,monto_deduccion,monto_final FROM Deducciones_autorizadas;
 END;
 PROCEDURE crear_solicitud_estudiante(p_id_administrador IN VARCHAR2,p_control VARCHAR2,p_convocatoria NUMBER,p_tipo NUMBER,p_formulario NUMBER,p_id OUT NUMBER) IS
  u VARCHAR2(50):=validar_administrador(p_id_administrador); n NUMBER;
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
 PROCEDURE cambiar_estatus_solicitud(p_id_administrador IN VARCHAR2,p_solicitud NUMBER,p_estatus NUMBER) IS u VARCHAR2(50):=validar_administrador(p_id_administrador); n NUMBER;
 BEGIN
  IF p_estatus NOT IN (2,3) OR p_estatus IS NULL THEN RAISE_APPLICATION_ERROR(-20823,'Solo autorizar (2) o rechazar (3) solicitudes'); END IF;
  UPDATE Solicitudes SET id_estatus_solicitud=p_estatus WHERE idSolicitud=p_solicitud AND id_estatus_solicitud=1;
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20824,'Solicitud inexistente o no pendiente'); END IF;
 END;
 -- Solo autoriza deducciones para solicitudes previamente aprobadas.
 PROCEDURE autorizar_deduccion(p_id_administrador IN VARCHAR2,p_solicitud NUMBER,p_id_monto_inscripcion NUMBER,p_id_monto_reinscripcion NUMBER,p_comentario VARCHAR2,p_id OUT NUMBER) IS
  u VARCHAR2(50):=validar_administrador(p_id_administrador); v_conv NUMBER; v_tipo NUMBER; v_est NUMBER;
 BEGIN
  SELECT id_convocatoria,id_Tipo_Solicitud,id_estatus_solicitud INTO v_conv,v_tipo,v_est FROM Solicitudes WHERE idSolicitud=p_solicitud;
  IF v_est<>2 THEN RAISE_APPLICATION_ERROR(-20825,'Solicitud no autorizada'); END IF;
  SELECT seq_deducciones_autorizadas.NEXTVAL INTO p_id FROM dual;
  INSERT INTO Deducciones_autorizadas(id_deduccion_autorizada,estatus_deduccion,monto_final,id_solicitud,id_administrador,
    fecha_autorizacion,id_convocatoria,id_Tipo_Solicitud,comentario,id_monto_inscripcion,id_monto_reinscripcion,monto_pago,monto_deduccion)
  VALUES(p_id,2,0,p_solicitud,u,SYSDATE,v_conv,v_tipo,p_comentario,p_id_monto_inscripcion,p_id_monto_reinscripcion,0,0);
 END;
 PROCEDURE cambiar_estatus_deduccion(p_id_administrador IN VARCHAR2,p_deduccion NUMBER,p_estatus NUMBER,p_comentario VARCHAR2 DEFAULT NULL) IS u VARCHAR2(50):=validar_administrador(p_id_administrador);
 BEGIN
  IF p_estatus NOT IN (2,4,5) OR p_estatus IS NULL THEN RAISE_APPLICATION_ERROR(-20826,'Estatus invalido'); END IF;
  UPDATE Deducciones_autorizadas SET estatus_deduccion=p_estatus,id_administrador=u,comentario=p_comentario
   WHERE id_deduccion_autorizada=p_deduccion AND estatus_deduccion<>p_estatus;
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20827,'Deduccion inexistente o estatus sin cambios'); END IF;
 END;
END PKG_ADMINISTRADOR;
/
-- BLOQUE 3b. API del aspirante (autenticacion provisional de desarrollo).
-- GET valida ficha + CURP contra el anio actual de Oracle y devuelve la
-- identidad del registro (ficha, periodo, anio). No expone datos personales
-- y no usa SESSION_USER. Solo SAR_BACKEND recibe EXECUTE.
PROMPT ===== 03b PAQUETE ASPIRANTE =====
CREATE OR REPLACE PACKAGE PKG_ASPIRANTE AUTHID DEFINER AS
 PROCEDURE validar_aspirante(p_ficha IN VARCHAR2,p_curp IN VARCHAR2,
  p_ficha_out OUT VARCHAR2,p_periodo OUT NUMBER,p_anio OUT NUMBER);
END PKG_ASPIRANTE;
/
CREATE OR REPLACE PACKAGE BODY PKG_ASPIRANTE AS
 -- Acepta solo si existe exactamente un registro con la misma ficha y CURP
 -- (sin distinguir mayusculas) en el anio actual de Oracle. Si hay varias
 -- coincidencias (mismo anio, distinto periodo) rechaza sin elegir una.
 PROCEDURE validar_aspirante(p_ficha IN VARCHAR2,p_curp IN VARCHAR2,
  p_ficha_out OUT VARCHAR2,p_periodo OUT NUMBER,p_anio OUT NUMBER) IS
  n NUMBER;
  v_anio NUMBER := EXTRACT(YEAR FROM SYSDATE);
 BEGIN
  IF p_ficha IS NULL OR p_curp IS NULL THEN RAISE_APPLICATION_ERROR(-20840,'No autorizado'); END IF;
  SELECT COUNT(*) INTO n FROM Aspirantes
   WHERE ficha=p_ficha AND UPPER(Curp)=UPPER(p_curp) AND anio=v_anio;
  IF n=0 THEN RAISE_APPLICATION_ERROR(-20841,'No autorizado'); END IF;
  IF n>1 THEN RAISE_APPLICATION_ERROR(-20842,'No autorizado'); END IF;
  SELECT ficha,periodo,anio INTO p_ficha_out,p_periodo,p_anio FROM Aspirantes
   WHERE ficha=p_ficha AND UPPER(Curp)=UPPER(p_curp) AND anio=v_anio;
 END;
END PKG_ASPIRANTE;
/
-- BLOQUE 4. Verificar que especificaciones y cuerpos compilaron sin errores.
-- En caso de fallo, consultar USER_ERRORS con SA_REDUCCION.
PROMPT ===== 04 COMPROBAR COMPILACION =====
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM USER_OBJECTS WHERE OBJECT_NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR','PKG_ASPIRANTE') AND STATUS<>'VALID';
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20830,'Objetos INVALID: consultar USER_ERRORS'); END IF;
 SELECT COUNT(*) INTO n FROM USER_ERRORS WHERE NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR','PKG_ASPIRANTE');
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20831,'Errores de compilacion: consultar USER_ERRORS'); END IF;
 DBMS_OUTPUT.PUT_LINE('OK: los tres paquetes VALID');
END;
/
-- BLOQUE 5. Solo la cuenta backend ejecuta los paquetes.
PROMPT ===== 05 PRIVILEGIOS DE PAQUETES =====
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME='PKG_ESTUDIANTE' AND GRANTEE='ROL_SAR_ESTUDIANTE_SIM' AND PRIVILEGE='EXECUTE';
 IF n>0 THEN EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_ESTUDIANTE FROM ROL_SAR_ESTUDIANTE_SIM'; END IF;
 SELECT COUNT(*) INTO n FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME='PKG_ESTUDIANTE' AND GRANTEE='ROL_SAR_ADMIN_SIM' AND PRIVILEGE='EXECUTE';
 IF n>0 THEN EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_ESTUDIANTE FROM ROL_SAR_ADMIN_SIM'; END IF;
 SELECT COUNT(*) INTO n FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME='PKG_ADMINISTRADOR' AND GRANTEE='ROL_SAR_ESTUDIANTE_SIM' AND PRIVILEGE='EXECUTE';
 IF n>0 THEN EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_ADMINISTRADOR FROM ROL_SAR_ESTUDIANTE_SIM'; END IF;
 SELECT COUNT(*) INTO n FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME='PKG_ADMINISTRADOR' AND GRANTEE='ROL_SAR_ADMIN_SIM' AND PRIVILEGE='EXECUTE';
 IF n>0 THEN EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_ADMINISTRADOR FROM ROL_SAR_ADMIN_SIM'; END IF;
 SELECT COUNT(*) INTO n FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME='PKG_ASPIRANTE' AND GRANTEE IN ('ROL_SAR_ESTUDIANTE_SIM','ROL_SAR_ADMIN_SIM') AND PRIVILEGE='EXECUTE';
 IF n>0 THEN EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_ASPIRANTE FROM ROL_SAR_ESTUDIANTE_SIM'; END IF;
 SELECT COUNT(*) INTO n FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME='PKG_ASPIRANTE' AND GRANTEE='ROL_SAR_ADMIN_SIM' AND PRIVILEGE='EXECUTE';
 IF n>0 THEN EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_ASPIRANTE FROM ROL_SAR_ADMIN_SIM'; END IF;
END;
/
GRANT EXECUTE ON PKG_ESTUDIANTE TO &&ORACLE_BACKEND_USER;
GRANT EXECUTE ON PKG_ADMINISTRADOR TO &&ORACLE_BACKEND_USER;
GRANT EXECUTE ON PKG_ASPIRANTE TO &&ORACLE_BACKEND_USER;
-- BLOQUE 6. Auditoria rapida de permisos concedidos.
COLUMN GRANTEE FORMAT A30
COLUMN TABLE_NAME FORMAT A25
COLUMN PRIVILEGE FORMAT A12
SELECT GRANTEE, TABLE_NAME, PRIVILEGE
  FROM USER_TAB_PRIVS_MADE
 WHERE TABLE_NAME IN ('PKG_ESTUDIANTE','PKG_ADMINISTRADOR','PKG_ASPIRANTE')
  AND GRANTEE = '&&ORACLE_BACKEND_USER'
 ORDER BY GRANTEE, TABLE_NAME;
PROMPT ===== LISTO: PAQUETES CRUD Y PERMISOS CONFIGURADOS =====
-- DDL y GRANT hacen COMMIT implicito en Oracle. Las operaciones CRUD de
-- los procedimientos no hacen COMMIT: el cliente controla la transaccion.
EXIT SUCCESS
