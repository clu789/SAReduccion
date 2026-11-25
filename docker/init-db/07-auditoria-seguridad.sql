-- ============================================================================
-- 07-auditoria-seguridad.sql
-- Equipo 1 (Autenticacion y seguridad), integrado con el Equipo 7.
--
-- Auditoria de intentos de acceso y eventos de sesion:
--   * Tabla      SEG_AUDITORIA_ACCESOS
--   * Secuencia  SEQ_SEG_AUDITORIA_ACCESOS
--   * Paquete    PKG_SEGURIDAD (EXECUTE para la cuenta tecnica del backend)
--   * Procedim.  PRC_SEG_DEPURAR_AUDITORIA (retencion; sin GRANT al backend)
--   * Job        JOB_SEG_DEPURAR_AUDITORIA (DBMS_SCHEDULER diario a las 03:00)
--
-- Se ejecuta como SA_REDUCCION, despues de 05-crear-paquetes-permisos.sql.
-- Es re-ejecutable: si la tabla o la secuencia ya existen, las conserva. Para
-- aplicarlo sobre una base ya inicializada usar aplicar-07-auditoria.sh.
--
-- La cuenta tecnica NO recibe permisos sobre la tabla: solo puede insertar
-- por medio de PKG_SEGURIDAD.registrar_evento y no puede leer ni modificar
-- la bitacora.
-- ============================================================================
SET VERIFY OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON
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
-- TABLA Y SECUENCIA
-- ============================================================================
PROMPT ===== TABLA SEG_AUDITORIA_ACCESOS =====
DECLARE
 e_ya_existe EXCEPTION;
 PRAGMA EXCEPTION_INIT(e_ya_existe, -955);
BEGIN
 EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_seg_auditoria_accesos START WITH 1 INCREMENT BY 1';
EXCEPTION WHEN e_ya_existe THEN
 DBMS_OUTPUT.PUT_LINE('Secuencia existente: se conserva');
END;
/
DECLARE
 e_ya_existe EXCEPTION;
 PRAGMA EXCEPTION_INIT(e_ya_existe, -955);
BEGIN
 EXECUTE IMMEDIATE q'[
  CREATE TABLE SEG_AUDITORIA_ACCESOS (
    id_auditoria          NUMBER(19)    NOT NULL,
    fecha_evento          TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    identificador_usuario VARCHAR2(64),
    tipo_perfil           VARCHAR2(15)  NOT NULL,
    direccion_ip          VARCHAR2(45),
    evento                VARCHAR2(30)  NOT NULL,
    detalle               VARCHAR2(400),
    correlation_id        VARCHAR2(64),
    CONSTRAINT pk_seg_auditoria_accesos PRIMARY KEY (id_auditoria),
    CONSTRAINT ck_seg_aud_perfil CHECK (tipo_perfil IN
      ('ESTUDIANTE','ASPIRANTE','ADMINISTRADOR','DESCONOCIDO')),
    CONSTRAINT ck_seg_aud_evento CHECK (evento IN
      ('LOGIN_EXITOSO','LOGIN_FALLIDO','LOGOUT','INTENTO_BLOQUEADO','SESION_EXPIRADA',
       'CAPTCHA_FALLIDO','OTP_ENVIADO','OTP_FALLIDO','ACCESO_DENEGADO'))
  )]';
 EXECUTE IMMEDIATE 'CREATE INDEX ix_seg_aud_fecha ON SEG_AUDITORIA_ACCESOS (fecha_evento)';
 EXECUTE IMMEDIATE 'CREATE INDEX ix_seg_aud_usuario ON SEG_AUDITORIA_ACCESOS (identificador_usuario, fecha_evento)';
 EXECUTE IMMEDIATE 'CREATE INDEX ix_seg_aud_ip ON SEG_AUDITORIA_ACCESOS (direccion_ip, fecha_evento)';
EXCEPTION WHEN e_ya_existe THEN
 DBMS_OUTPUT.PUT_LINE('Tabla existente: se conserva');
END;
/
COMMENT ON TABLE SEG_AUDITORIA_ACCESOS IS 'Bitacora de intentos de acceso y eventos de sesion (Equipo 1)';
COMMENT ON COLUMN SEG_AUDITORIA_ACCESOS.identificador_usuario IS 'Cuenta, numero de control o ficha. Nunca CURP, correo ni contrasena';
COMMENT ON COLUMN SEG_AUDITORIA_ACCESOS.direccion_ip IS 'IP del cliente resuelta por el backend detras del proxy autorizado (IPv4 o IPv6)';
COMMENT ON COLUMN SEG_AUDITORIA_ACCESOS.detalle IS 'Motivo tecnico breve, sin datos personales ni credenciales';
COMMENT ON COLUMN SEG_AUDITORIA_ACCESOS.correlation_id IS 'Mismo identificador que X-Correlation-ID y que el log tecnico';

-- ============================================================================
-- PAQUETE PUBLICO PARA LA CUENTA TECNICA
-- ============================================================================
PROMPT ===== PAQUETE PKG_SEGURIDAD =====
CREATE OR REPLACE PACKAGE PKG_SEGURIDAD AUTHID DEFINER AS
 -- Registra un evento en una transaccion autonoma: se confirma aunque la
 -- transaccion de negocio que lo invoca termine en ROLLBACK, y nunca la confirma.
 PROCEDURE registrar_evento(p_identificador IN VARCHAR2,p_tipo_perfil IN VARCHAR2,
   p_direccion_ip IN VARCHAR2,p_evento IN VARCHAR2,p_detalle IN VARCHAR2,p_correlation_id IN VARCHAR2);
 -- Correo registrado de un aspirante ya validado, para enviarle el codigo temporal.
 FUNCTION correo_aspirante(p_ficha IN VARCHAR2,p_periodo IN NUMBER,p_anio IN NUMBER) RETURN VARCHAR2;
END PKG_SEGURIDAD;
/
CREATE OR REPLACE PACKAGE BODY PKG_SEGURIDAD AS
 PROCEDURE registrar_evento(p_identificador IN VARCHAR2,p_tipo_perfil IN VARCHAR2,
   p_direccion_ip IN VARCHAR2,p_evento IN VARCHAR2,p_detalle IN VARCHAR2,p_correlation_id IN VARCHAR2) IS
  PRAGMA AUTONOMOUS_TRANSACTION;
  v_perfil VARCHAR2(200);
  v_evento VARCHAR2(200);
 BEGIN
  v_perfil := UPPER(TRIM(SUBSTR(p_tipo_perfil,1,200)));
  v_evento := UPPER(TRIM(SUBSTR(p_evento,1,200)));
  IF v_evento IS NULL OR v_evento NOT IN
     ('LOGIN_EXITOSO','LOGIN_FALLIDO','LOGOUT','INTENTO_BLOQUEADO','SESION_EXPIRADA',
      'CAPTCHA_FALLIDO','OTP_ENVIADO','OTP_FALLIDO','ACCESO_DENEGADO') THEN
   RAISE_APPLICATION_ERROR(-20851,'Evento de seguridad no valido');
  END IF;
  IF v_perfil IS NULL OR v_perfil NOT IN ('ESTUDIANTE','ASPIRANTE','ADMINISTRADOR') THEN
   v_perfil := 'DESCONOCIDO';
  END IF;
  INSERT INTO SEG_AUDITORIA_ACCESOS
   (id_auditoria,fecha_evento,identificador_usuario,tipo_perfil,direccion_ip,evento,detalle,correlation_id)
  VALUES
   (seq_seg_auditoria_accesos.NEXTVAL,SYSTIMESTAMP,SUBSTR(TRIM(p_identificador),1,64),v_perfil,
    SUBSTR(TRIM(p_direccion_ip),1,45),v_evento,SUBSTR(p_detalle,1,400),SUBSTR(TRIM(p_correlation_id),1,64));
  COMMIT;
 EXCEPTION WHEN OTHERS THEN
  ROLLBACK;
  RAISE;
 END;
 FUNCTION correo_aspirante(p_ficha IN VARCHAR2,p_periodo IN NUMBER,p_anio IN NUMBER) RETURN VARCHAR2 IS
  v_correo Aspirantes.correo%TYPE;
 BEGIN
  SELECT correo INTO v_correo FROM Aspirantes
   WHERE ficha=p_ficha AND periodo=p_periodo AND anio=p_anio;
  RETURN v_correo;
 EXCEPTION WHEN NO_DATA_FOUND THEN
  RETURN NULL;
 END;
END PKG_SEGURIDAD;
/

-- ============================================================================
-- RETENCION. Uso administrativo (DBA o job programado); sin GRANT al backend.
-- ============================================================================
CREATE OR REPLACE PROCEDURE PRC_SEG_DEPURAR_AUDITORIA(p_dias_retencion IN NUMBER DEFAULT 365) AS
BEGIN
 IF p_dias_retencion IS NULL OR p_dias_retencion < 30 THEN
  RAISE_APPLICATION_ERROR(-20852,'La retencion minima es de 30 dias');
 END IF;
 DELETE FROM SEG_AUDITORIA_ACCESOS
  WHERE fecha_evento < SYSTIMESTAMP - NUMTODSINTERVAL(p_dias_retencion,'DAY');
 DBMS_OUTPUT.PUT_LINE('Registros depurados: '||SQL%ROWCOUNT);
 COMMIT;
END;
/

PROMPT ===== JOB PROGRAMADO DE DEPURACION =====
DECLARE
 e_job_no_existe EXCEPTION;
 PRAGMA EXCEPTION_INIT(e_job_no_existe, -27475);
BEGIN
 BEGIN
  DBMS_SCHEDULER.DROP_JOB(job_name => 'JOB_SEG_DEPURAR_AUDITORIA', force => TRUE);
 EXCEPTION WHEN e_job_no_existe THEN
  NULL;
 END;
 DBMS_SCHEDULER.CREATE_JOB(
  job_name        => 'JOB_SEG_DEPURAR_AUDITORIA',
  job_type        => 'PLSQL_BLOCK',
  job_action      => 'BEGIN PRC_SEG_DEPURAR_AUDITORIA(365); END;',
  start_date      => SYSTIMESTAMP,
  repeat_interval => 'FREQ=DAILY;BYHOUR=3;BYMINUTE=0;BYSECOND=0',
  enabled         => TRUE,
  auto_drop       => FALSE,
  comments        => 'Depuracion periodica diaria de SEG_AUDITORIA_ACCESOS (retencion minima 365 dias)'
 );
END;
/

-- ============================================================================
-- COMPROBACION Y PERMISOS
-- ============================================================================
PROMPT ===== COMPROBAR COMPILACION =====
DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM USER_OBJECTS
  WHERE OBJECT_NAME IN ('PKG_SEGURIDAD','PRC_SEG_DEPURAR_AUDITORIA') AND STATUS<>'VALID';
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20853,'Objetos INVALID: consultar USER_ERRORS'); END IF;
 SELECT COUNT(*) INTO n FROM USER_SCHEDULER_JOBS
  WHERE JOB_NAME = 'JOB_SEG_DEPURAR_AUDITORIA' AND ENABLED = 'TRUE';
 IF n<>1 THEN RAISE_APPLICATION_ERROR(-20854,'Job JOB_SEG_DEPURAR_AUDITORIA ausente o deshabilitado'); END IF;
 DBMS_OUTPUT.PUT_LINE('OK: PKG_SEGURIDAD, PRC_SEG_DEPURAR_AUDITORIA y JOB_SEG_DEPURAR_AUDITORIA validos');
END;
/
PROMPT ===== CONCEDER EXECUTE A LA CUENTA TECNICA =====
GRANT EXECUTE ON PKG_SEGURIDAD TO &&ORACLE_BACKEND_USER;
PROMPT ===== AUDITORIA DE SEGURIDAD CONFIGURADA =====
EXIT SUCCESS
