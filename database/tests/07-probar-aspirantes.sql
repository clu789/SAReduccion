-- ============================================================================
-- 07-probar-aspirantes.sql  (PRUEBAS, no modifica datos de forma permanente)
-- Ejecutar como SA_REDUCCION en XEPDB1.
--
-- Comprueba:
--   A) PKG_ASPIRANTE: casos validos (periodo 1 y 2), ficha/CURP incorrectas,
--      anio anterior, y coincidencia ambigua mismo anio/distinto periodo.
--   B) ck_aspirantes_correo: dominios permitidos, mayusculas, y rechazos.
-- Todos los INSERT viven en una transaccion que termina en ROLLBACK: no dejan
-- registros ni imprimen CURP, correos ni otros datos personales.
-- ============================================================================
SET ECHO OFF
SET VERIFY OFF
SET SERVEROUTPUT ON
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
  PROCEDURE probar_auth(p_ficha VARCHAR2,p_curp VARCHAR2,p_ok BOOLEAN,p_code NUMBER,p_label VARCHAR2) IS
    vf VARCHAR2(18); vp NUMBER; va NUMBER;
  BEGIN
    BEGIN
      PKG_ASPIRANTE.validar_aspirante(p_ficha,p_curp,vf,vp,va);
      IF NOT p_ok THEN RAISE_APPLICATION_ERROR(-20980,'Se esperaba rechazo: '||p_label); END IF;
      DBMS_OUTPUT.PUT_LINE('OK aceptado: '||p_label);
    EXCEPTION
      WHEN OTHERS THEN
        IF p_ok THEN RAISE; END IF;
        IF SQLCODE <> p_code THEN
          RAISE_APPLICATION_ERROR(-20981,'Codigo inesperado en '||p_label||': '||SQLCODE);
        END IF;
        DBMS_OUTPUT.PUT_LINE('OK rechazado ('||SQLCODE||'): '||p_label);
    END;
  END;

  PROCEDURE probar_correo(p_seq NUMBER,p_correo VARCHAR2,p_valido BOOLEAN,p_label VARCHAR2) IS
    v_ficha VARCHAR2(18) := 'CTEST'||LPAD(p_seq,3,'0');
    v_curp  VARCHAR2(18) := 'DDDD000000HDFRRR'||LPAD(p_seq,2,'0');
  BEGIN
    BEGIN
      INSERT INTO Aspirantes (ficha,periodo,anio,Nombre,PrimerApellido,Curp,correo)
      VALUES (v_ficha,1,2026,'Prueba','Correo',v_curp,p_correo);
      IF NOT p_valido THEN RAISE_APPLICATION_ERROR(-20985,'Se esperaba rechazo de correo: '||p_label); END IF;
      DBMS_OUTPUT.PUT_LINE('OK correo aceptado: '||p_label);
    EXCEPTION
      WHEN OTHERS THEN
        IF p_valido THEN RAISE; END IF;
        IF SQLCODE <> -2290 THEN
          RAISE_APPLICATION_ERROR(-20986,'Codigo inesperado en '||p_label||': '||SQLCODE);
        END IF;
        DBMS_OUTPUT.PUT_LINE('OK correo rechazado ('||SQLCODE||'): '||p_label);
    END;
  END;
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'SA_REDUCCION'
     OR SYS_CONTEXT('USERENV','CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20879,'Ejecutar como SA_REDUCCION en XEPDB1');
  END IF;

  DBMS_OUTPUT.PUT_LINE('===== A) AUTENTICACION DE ASPIRANTE =====');
  -- Validos: un registro de periodo 2 y uno de periodo 1 (ambos del anio actual).
  probar_auth('F2026A001','MOPA080512MDFRNN01',TRUE,NULL,'aspirante valido periodo 2');
  probar_auth('F2026B004','FULD080617HDFRRG04',TRUE,NULL,'aspirante valido periodo 1');
  -- Ficha y CURP incorrectas.
  probar_auth('FNOEXISTE1','MOPA080512MDFRNN01',FALSE,-20841,'ficha incorrecta');
  probar_auth('F2026A001','CURPNOEXISTE000001',FALSE,-20841,'curp incorrecta');
  -- Registro de un anio anterior al actual.
  INSERT INTO Aspirantes (ficha,periodo,anio,Nombre,PrimerApellido,Curp,correo)
  VALUES ('TSTANIO1',1,2025,'Prueba','Anio','AAAA000000HDFRRR01','test.anio@gmail.com');
  probar_auth('TSTANIO1','AAAA000000HDFRRR01',FALSE,-20841,'aspirante de anio anterior');
  -- Coincidencia ambigua: misma ficha+CURP+anio en periodo 1 y 2.
  INSERT INTO Aspirantes (ficha,periodo,anio,Nombre,PrimerApellido,Curp,correo)
  VALUES ('TSTAMB01',1,2026,'Prueba','Ambig','BBBB000000HDFRRR02','test.ambig@gmail.com');
  INSERT INTO Aspirantes (ficha,periodo,anio,Nombre,PrimerApellido,Curp,correo)
  VALUES ('TSTAMB01',2,2026,'Prueba','Ambig','BBBB000000HDFRRR02','test.ambig@gmail.com');
  probar_auth('TSTAMB01','BBBB000000HDFRRR02',FALSE,-20842,'coincidencia ambigua mismo anio');

  DBMS_OUTPUT.PUT_LINE('===== B) VALIDACION DE CORREO =====');
  probar_correo(1,'usuario.uno@gmail.com',TRUE,'gmail');
  probar_correo(2,'usuario.dos@hotmail.com',TRUE,'hotmail');
  probar_correo(3,'usuario.tres@outlook.com',TRUE,'outlook');
  probar_correo(4,'usuario.cuatro@live.com',TRUE,'live');
  probar_correo(5,'usuario.cinco@yahoo.com.mx',TRUE,'yahoo.com.mx');
  probar_correo(6,'usuario.seis@yahoo.com',TRUE,'yahoo.com');
  probar_correo(7,'usuario.siete@icloud.com',TRUE,'icloud');
  probar_correo(8,'usuario.ocho@GMAIL.COM',TRUE,'dominio en mayusculas');
  probar_correo(9,'usuario.nueve@example.invalid',FALSE,'dominio no autorizado');
  probar_correo(10,'usuario diez@gmail.com',FALSE,'correo con espacio');
  probar_correo(11,'usuario..once@gmail.com',FALSE,'formato incorrecto (punto doble)');
  probar_correo(12,'usuario.doce@gmail.com.mx',FALSE,'extension no permitida');

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('OK: pruebas de aspirante finalizadas; transaccion revertida (sin datos permanentes)');
END;
/
EXIT SUCCESS
