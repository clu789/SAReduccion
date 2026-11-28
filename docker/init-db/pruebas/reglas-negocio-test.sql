-- ============================================================================
-- Pruebas automatizadas de reglas de negocio PL/SQL (SAReduccion)
-- Ejecutar como SA_REDUCCION en XEPDB1:
--   sqlplus -L -s SA_REDUCCION/<pw>@//localhost:1521/XEPDB1 @reglas-negocio-test.sql
-- No deja datos residuales: crea un estudiante temporal dentro de la misma
-- transaccion y ejecuta ROLLBACK al finalizar (y ante cualquier SQLERROR).
-- ============================================================================
SET VERIFY OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
PROMPT ===== PRUEBAS REGLAS DE NEGOCIO PL/SQL =====

DECLARE
  v_conv NUMBER;
  v_mr NUMBER;
  v_p1 NUMBER; v_p2 NUMBER; v_p3 NUMBER;
  v_p5 NUMBER; v_p6 NUMBER; v_p7 NUMBER; v_p8 NUMBER;
  v_o1 NUMBER; v_o2 NUMBER; v_o3 NUMBER; v_o5 NUMBER;
  v_sol NUMBER;
  v_sol_dup NUMBER;
  v_ded NUMBER;
  v_hist NUMBER;
  v_est NUMBER;
  v_monto_pago NUMBER;
  v_monto_orig NUMBER;
  v_monto_nuevo NUMBER := 777.00;
  v_final NUMBER;
  v_admin_ded VARCHAR2(10);
  v_ficha VARCHAR2(18);
  v_per NUMBER;
  v_anio NUMBER;
  v_conv_tmp NUMBER;
  v_n NUMBER;
  v_json_f1 CLOB;
  v_json_f2 CLOB;

  PROCEDURE ok(p_cond BOOLEAN, p_nombre VARCHAR2) IS
  BEGIN
    IF p_cond IS NULL OR NOT p_cond THEN
      RAISE_APPLICATION_ERROR(-20990, 'Prueba fallida: ' || p_nombre);
    END IF;
    DBMS_OUTPUT.PUT_LINE('OK: ' || p_nombre);
  END;
BEGIN
  IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'SA_REDUCCION' OR
     SYS_CONTEXT('USERENV', 'CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20801, 'Ejecutar como SA_REDUCCION en XEPDB1');
  END IF;

  -- Buscar convocatoria abierta (900002 del seed) y catalogo de preguntas/montos.
  SELECT id_convocatoria INTO v_conv
    FROM Convocatorias
   WHERE TRUNC(SYSDATE) BETWEEN TRUNC(fecha_inicio_solicitudes) AND TRUNC(fecha_fin_solicitudes)
   FETCH FIRST 1 ROW ONLY;

  SELECT MAX(id_monto_reinscripcion) INTO v_mr FROM Monto_Reinscripciones;
  SELECT monto_pago INTO v_monto_pago FROM Monto_Reinscripciones WHERE id_monto_reinscripcion = v_mr;
  SELECT monto_deduccion INTO v_monto_orig
    FROM Convocatorias_TipoSolicitudes
   WHERE id_convocatoria = v_conv AND id_tipo_solicitud = 2;

  SELECT id_pregunta INTO v_p1 FROM Preguntas WHERE texto_pregunta = 'Clave del trabajador';
  SELECT id_pregunta INTO v_p2 FROM Preguntas WHERE texto_pregunta = 'Nombre completo del trabajador';
  SELECT id_pregunta INTO v_p3 FROM Preguntas WHERE texto_pregunta = 'Parentesco con el trabajador';
  SELECT id_pregunta INTO v_p5 FROM Preguntas WHERE texto_pregunta = 'Clave del trabajador homologado';
  SELECT id_pregunta INTO v_p6 FROM Preguntas WHERE texto_pregunta = 'Nombre completo del trabajador homologado';
  SELECT id_pregunta INTO v_p7 FROM Preguntas WHERE texto_pregunta = 'Parentesco con el trabajador homologado';
  SELECT id_pregunta INTO v_p8 FROM Preguntas WHERE texto_pregunta = 'Cuenta con comprobante de homologacion';

  SELECT id_opcion INTO v_o1 FROM Opciones WHERE id_pregunta = v_p3 AND orden = 1;
  SELECT id_opcion INTO v_o2 FROM Opciones WHERE id_pregunta = v_p3 AND orden = 2;
  SELECT id_opcion INTO v_o3 FROM Opciones WHERE id_pregunta = v_p7 AND orden = 1;
  SELECT id_opcion INTO v_o5 FROM Opciones WHERE id_pregunta = v_p8 AND orden = 1;

  v_json_f1 := '[{"id_pregunta":' || v_p1 || ',"texto_respuesta":"CLV-TEST"},' ||
               '{"id_pregunta":' || v_p2 || ',"texto_respuesta":"Trabajador Test"},' ||
               '{"id_pregunta":' || v_p3 || ',"id_opcion":' || v_o1 || '}]';

  v_json_f2 := '[{"id_pregunta":' || v_p5 || ',"texto_respuesta":"CLV-H-TEST"},' ||
               '{"id_pregunta":' || v_p6 || ',"texto_respuesta":"Homologado Test"},' ||
               '{"id_pregunta":' || v_p7 || ',"id_opcion":' || v_o3 || '},' ||
               '{"id_pregunta":' || v_p8 || ',"id_opcion":' || v_o5 || '}]';

  -- Estudiante temporal (se revertira con el ROLLBACK final).
  INSERT INTO Estudiantes(numero_control, nombre, primer_apellido, segundo_apellido, carrera, semestre)
  VALUES('T9999999', 'Prueba', 'Reglas', 'Negocio', 'Ingenieria en Sistemas', 5);

  -- 1. Crear solicitud inicial via PKG_FORMULARIO -> estado PENDIENTE e historial inicial.
  PKG_FORMULARIO.crear_solicitud('T9999999', NULL, NULL, NULL, v_conv, 1, 1, v_json_f1, v_sol);
  SELECT id_estatus_solicitud INTO v_est FROM Solicitudes WHERE id_solicitud = v_sol;
  SELECT COUNT(*) INTO v_hist FROM Historial_Solicitudes WHERE id_solicitud = v_sol AND id_estatus = 1;
  ok(v_est = 1 AND v_hist = 1, '1. crear_solicitud registra PENDIENTE con historial inicial');

  -- 2. Unicidad por convocatoria.
  BEGIN
    PKG_FORMULARIO.crear_solicitud('T9999999', NULL, NULL, NULL, v_conv, 1, 1, v_json_f1, v_sol_dup);
    ok(FALSE, '2. segunda solicitud en misma convocatoria debe fallar');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -1, '2. rechaza segunda solicitud en misma convocatoria (ORA-00001)');
  END;

  -- 3. Validaciones de formulario: obligatoria faltante, ajena y mixta sin especificacion.
  BEGIN
    PKG_ESTUDIANTE.reenviar_solicitud('T9999999', v_sol, 1, 1, '[{"id_pregunta":' || v_p1 || ',"texto_respuesta":"X"}]');
    ok(FALSE, '3a. debe rechazar pregunta obligatoria omitida');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20855 OR SQLCODE = -20862, '3a. valida obligatoriedad de preguntas');
  END;

  -- 4. Retiro de solicitud PENDIENTE y bloqueo de doble retiro.
  PKG_ESTUDIANTE.retirar_solicitud('T9999999', v_sol);
  SELECT id_estatus_solicitud INTO v_est FROM Solicitudes WHERE id_solicitud = v_sol;
  ok(v_est = 6, '4a. retirar_solicitud cambia estado a RETIRADO (6)');

  BEGIN
    PKG_ESTUDIANTE.retirar_solicitud('T9999999', v_sol);
    ok(FALSE, '4b. segundo retiro debe rechazarse');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20859, '4b. rechaza retiro de solicitud no pendiente (-20859)');
  END;

  -- 5. Validaciones de respuesta al reenviar (pregunta ajena y mixta sin especificacion).
  BEGIN
    PKG_ESTUDIANTE.reenviar_solicitud('T9999999', v_sol, 1, 1,
      '[{"id_pregunta":' || v_p5 || ',"texto_respuesta":"Ajena"}]');
    ok(FALSE, '5a. pregunta ajena al formulario debe rechazarse');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20854, '5a. rechaza pregunta ajena al formulario (-20854)');
  END;

  BEGIN
    PKG_ESTUDIANTE.reenviar_solicitud('T9999999', v_sol, 1, 1,
      '[{"id_pregunta":' || v_p1 || ',"texto_respuesta":"CLV"},' ||
      '{"id_pregunta":' || v_p2 || ',"texto_respuesta":"Nom"},' ||
      '{"id_pregunta":' || v_p3 || ',"id_opcion":' || v_o2 || '}]');
    ok(FALSE, '5b. opcion mixta sin especificacion debe rechazarse');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20017, '5b. trigger exige especificacion en opcion mixta (-20017)');
  END;

  -- 6. Reenvio valido cambiando tipo 1 -> 2 conserva el mismo ID y reemplaza respuestas.
  PKG_ESTUDIANTE.reenviar_solicitud('T9999999', v_sol, 2, 2, v_json_f2);
  SELECT id_estatus_solicitud INTO v_est FROM Solicitudes WHERE id_solicitud = v_sol;
  SELECT COUNT(*) INTO v_n FROM Respuestas WHERE id_solicitud = v_sol AND id_pregunta IN (v_p5, v_p6, v_p7, v_p8);
  ok(v_est = 1 AND v_n = 4, '6. reenvio cambia tipo 1->2, conserva ID, regresa a PENDIENTE y reemplaza respuestas');

  -- 7. Rechazo administrativo exige comentario y permite reenvio posterior.
  BEGIN
    PKG_ADMINISTRADOR.rechazar_solicitud('SA01', v_sol, '   ');
    ok(FALSE, '7a. rechazo sin comentario debe rechazarse');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20874, '7a. rechazo exige comentario (-20874)');
  END;

  PKG_ADMINISTRADOR.rechazar_solicitud('SA01', v_sol, 'Falta comprobante oficial');
  SELECT id_estatus_solicitud INTO v_est FROM Solicitudes WHERE id_solicitud = v_sol;
  ok(v_est = 3, '7b. rechazar_solicitud cambia a RECHAZADO (3)');

  PKG_ESTUDIANTE.reenviar_solicitud('T9999999', v_sol, 2, 2, v_json_f2);
  SELECT id_estatus_solicitud INTO v_est FROM Solicitudes WHERE id_solicitud = v_sol;
  ok(v_est = 1, '7c. reenvio desde RECHAZADO vuelve a PENDIENTE (1)');

  -- 8. Autorizar deduccion y prohibir retiro por estudiante.
  PKG_ADMINISTRADOR.autorizar_deduccion('SA01', v_sol, NULL, v_mr, NULL, v_ded);
  SELECT estatus_deduccion, monto_final, id_administrador
    INTO v_est, v_final, v_admin_ded
    FROM Deducciones_autorizadas
   WHERE id_deduccion_autorizada = v_ded;
  ok(v_est = 2 AND v_final = (v_monto_pago - v_monto_orig) AND v_admin_ded = 'SA01',
    '8a. autorizar_deduccion calcula monto_final y asigna autorizante SA01');

  BEGIN
    PKG_ESTUDIANTE.retirar_solicitud('T9999999', v_sol);
    ok(FALSE, '8b. estudiante no puede retirar solicitud AUTORIZADO');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20859, '8b. estudiante impedido de retirar solicitud AUTORIZADO (-20859)');
  END;

  -- 9. Cancelar deduccion por SA02 conserva a SA01 como autorizante y registra SA02 en historial.
  PKG_ADMINISTRADOR.cancelar_deduccion('SA02', v_ded, 'Revision de vigencia');
  SELECT estatus_deduccion, id_administrador INTO v_est, v_admin_ded
    FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = v_ded;
  SELECT COUNT(*) INTO v_n FROM Historial_Deducciones
   WHERE id_deduccion_autorizada = v_ded AND id_estatus = 4 AND id_administrador = 'SA02';
  ok(v_est = 4 AND v_admin_ded = 'SA01' AND v_n = 1,
    '9. cancelar_deduccion pasa a CANCELADO, conserva SA01 en deduccion y registra SA02 en historial');

  -- 10. Reautorizar, actualizar monto oficial y aplicar (recalculo e inmutabilidad).
  PKG_ADMINISTRADOR.autorizar_deduccion('SA01', v_sol, NULL, v_mr, 'Reautorizada', v_ded);
  PKG_ADMINISTRADOR.actualizar_monto_oficial('SA01', v_conv, 2, v_monto_nuevo);
  PKG_ADMINISTRADOR.aplicar_deduccion('SA02', v_ded);
  SELECT estatus_deduccion, monto_deduccion, monto_final, id_administrador
    INTO v_est, v_monto_orig, v_final, v_admin_ded
    FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = v_ded;
  ok(v_est = 5 AND v_monto_orig = v_monto_nuevo AND v_final = (v_monto_pago - v_monto_nuevo) AND v_admin_ded = 'SA01',
    '10a. aplicar_deduccion recalcula con monto oficial vigente y pasa a APLICADO (5)');

  BEGIN
    PKG_ADMINISTRADOR.cancelar_deduccion('SA02', v_ded, 'Intento sobre aplicada');
    ok(FALSE, '10b. deduccion APLICADO no puede cancelarse');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20880, '10b. deduccion APLICADO es inmutable ante cancelacion (-20880)');
  END;

  -- 11. Inmutabilidad de historiales.
  BEGIN
    UPDATE Historial_Solicitudes SET comentario = 'Alterado' WHERE id_solicitud = v_sol;
    ok(FALSE, '11a. UPDATE en Historial_Solicitudes debe fallar');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20070, '11a. Historial_Solicitudes bloquea UPDATE (-20070)');
  END;
  BEGIN
    DELETE FROM Historial_Deducciones WHERE id_deduccion_autorizada = v_ded;
    ok(FALSE, '11b. DELETE en Historial_Deducciones debe fallar');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20071, '11b. Historial_Deducciones bloquea DELETE (-20071)');
  END;

  -- 12. Convocatorias: fechas invalidas y traslape con convocatoria abierta.
  BEGIN
    PKG_ADMINISTRADOR.crear_convocatoria('SA01', 1, 2099,
      TRUNC(SYSDATE) + 10, TRUNC(SYSDATE) + 5, TRUNC(SYSDATE) + 20, TRUNC(SYSDATE) + 30, 500, 650, v_conv_tmp);
    ok(FALSE, '12a. orden de fechas invalido debe fallar');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20911, '12a. rechaza orden de fechas invalido (-20911)');
  END;
  BEGIN
    PKG_ADMINISTRADOR.crear_convocatoria('SA01', 1, 2099,
      TRUNC(SYSDATE), TRUNC(SYSDATE) + 10, TRUNC(SYSDATE) + 20, TRUNC(SYSDATE) + 30, 500, 650, v_conv_tmp);
    ok(FALSE, '12b. convocatoria traslapada debe fallar');
  EXCEPTION WHEN OTHERS THEN
    ok(SQLCODE = -20917, '12b. rechaza convocatoria con intervalo traslapado (-20917)');
  END;

  -- 13. Aspirante con doble periodo desambiguado a convocatoria abierta.
  PKG_ASPIRANTE.validar_aspirante('F2026A001', 'MOPA080512MDFRNN01', v_ficha, v_per, v_anio);
  ok(v_ficha = 'F2026A001' AND v_per = 2 AND v_anio = 2026,
    '13. validar_aspirante resuelve periodo abierto (2/2026)');

  ROLLBACK;
  SELECT COUNT(*) INTO v_n FROM Estudiantes WHERE numero_control = 'T9999999';
  ok(v_n = 0, '14. ROLLBACK final deja la base sin datos de prueba');
  DBMS_OUTPUT.PUT_LINE('PRUEBAS REGLAS DE NEGOCIO: TODAS CORRECTAS');
END;
/
EXIT SUCCESS
