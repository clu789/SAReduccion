-- ============================================================================
-- Pruebas de PKG_SEGURIDAD (Equipo 1). Ejecutar como SA_REDUCCION:
--   docker exec sareduccion-oracle bash /opt/sareduccion/init-db/aplicar-07-auditoria.sh --probar
-- No deja datos: borra los registros que inserta (correlation_id 'TEST-E1-%').
-- ============================================================================
SET VERIFY OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
PROMPT ===== PRUEBAS PKG_SEGURIDAD =====
DECLARE
 n NUMBER;
 v_perfil SEG_AUDITORIA_ACCESOS.tipo_perfil%TYPE;
 v_correo VARCHAR2(254);
 PROCEDURE ok(p_condicion BOOLEAN, p_nombre VARCHAR2) IS
 BEGIN
  IF p_condicion IS NULL OR NOT p_condicion THEN
   RAISE_APPLICATION_ERROR(-20859,'Prueba fallida: '||p_nombre);
  END IF;
  DBMS_OUTPUT.PUT_LINE('OK: '||p_nombre);
 END;
BEGIN
 DELETE FROM SEG_AUDITORIA_ACCESOS WHERE correlation_id LIKE 'TEST-E1-%';
 COMMIT;

 -- 1. Transaccion autonoma: el evento sobrevive al ROLLBACK de quien lo invoca.
 PKG_SEGURIDAD.registrar_evento('21281181','estudiante','10.0.0.5','LOGIN_FALLIDO','Prueba','TEST-E1-1');
 ROLLBACK;
 SELECT COUNT(*) INTO n FROM SEG_AUDITORIA_ACCESOS WHERE correlation_id='TEST-E1-1';
 ok(n=1,'el registro persiste tras ROLLBACK (transaccion autonoma)');

 -- 2. Normalizacion de perfil.
 SELECT tipo_perfil INTO v_perfil FROM SEG_AUDITORIA_ACCESOS WHERE correlation_id='TEST-E1-1';
 ok(v_perfil='ESTUDIANTE','el perfil se normaliza a mayusculas');
 PKG_SEGURIDAD.registrar_evento(NULL,'otro',NULL,'INTENTO_BLOQUEADO',NULL,'TEST-E1-2');
 SELECT tipo_perfil INTO v_perfil FROM SEG_AUDITORIA_ACCESOS WHERE correlation_id='TEST-E1-2';
 ok(v_perfil='DESCONOCIDO','un perfil no reconocido se guarda como DESCONOCIDO');

 -- 3. Valores largos se truncan en lugar de fallar.
 PKG_SEGURIDAD.registrar_evento(RPAD('X',200,'X'),'ADMINISTRADOR',RPAD('1',100,'1'),'LOGOUT',
   RPAD('D',1000,'D'),'TEST-E1-3');
 SELECT COUNT(*) INTO n FROM SEG_AUDITORIA_ACCESOS
  WHERE correlation_id='TEST-E1-3' AND LENGTH(identificador_usuario)=64 AND LENGTH(detalle)=400;
 ok(n=1,'los valores largos se truncan');

 -- 4. Un evento fuera del catalogo se rechaza.
 BEGIN
  PKG_SEGURIDAD.registrar_evento('X','ESTUDIANTE',NULL,'EVENTO_INVENTADO',NULL,'TEST-E1-4');
  ok(FALSE,'un evento no valido debe rechazarse');
 EXCEPTION WHEN OTHERS THEN
  ok(SQLCODE=-20851,'un evento no valido se rechaza con ORA-20851');
 END;

 -- 5. Correo de aspirante inexistente.
 v_correo := PKG_SEGURIDAD.correo_aspirante('NO-EXISTE',1,2000);
 ok(v_correo IS NULL,'correo_aspirante devuelve NULL si no existe el aspirante');

 DELETE FROM SEG_AUDITORIA_ACCESOS WHERE correlation_id LIKE 'TEST-E1-%';
 COMMIT;
 DBMS_OUTPUT.PUT_LINE('PRUEBAS PKG_SEGURIDAD: TODAS CORRECTAS');
END;
/
EXIT SUCCESS
