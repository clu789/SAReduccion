-- SAReduccion / Equipo 4 - propuesta de ampliacion de PKG_ADMINISTRADOR
-- SOLO REVISION: no ejecutar sin aprobacion del Equipo 7.
-- Requiere ejecutar conectado como SA_REDUCCION en XEPDB1.
-- Conserva todos los procedimientos existentes y no hace COMMIT.
-- ADVERTENCIA: CREATE OR REPLACE PACKAGE puede invalidar dependencias
-- hasta recompilar el PACKAGE BODY. Desplegar coordinadamente.

CREATE OR REPLACE PACKAGE PKG_ADMINISTRADOR AUTHID DEFINER AS
 PROCEDURE listar_estudiantes(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_solicitudes(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_convocatorias(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_respuestas(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_deducciones(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_montos(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_historial_solicitud(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
 PROCEDURE listar_historial_deduccion(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR);
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
  -- Equipo 4: gestion de convocatorias (ampliacion aditiva)
  PROCEDURE crear_convocatoria(p_id_administrador VARCHAR2,p_periodo NUMBER,p_anio NUMBER,
    p_inicio DATE,p_fin DATE,p_limite_autorizacion DATE,p_limite_aplicacion DATE,
    p_monto_trabajador NUMBER,p_monto_homologado NUMBER,p_id OUT NUMBER);
  PROCEDURE consultar_montos_convocatoria(p_id_administrador VARCHAR2,p_convocatoria NUMBER,
    p_resultado OUT SYS_REFCURSOR);
  PROCEDURE listar_historial_convocatoria(p_id_administrador VARCHAR2,p_convocatoria NUMBER,
    p_resultado OUT SYS_REFCURSOR);
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
    s.id_estatus_solicitud,s.id_tipo_solicitud,s.fecha_actualizacion,t.nombre,
    TRIM(COALESCE(e.nombre,a.nombre)||' '||COALESCE(e.primer_apellido,a.primer_apellido)||
      CASE WHEN COALESCE(e.segundo_apellido,a.segundo_apellido) IS NOT NULL
           THEN ' '||COALESCE(e.segundo_apellido,a.segundo_apellido) ELSE '' END) AS nombre_solicitante
   FROM Solicitudes s LEFT JOIN Tipo_Solicitudes t ON t.id_tipo_solicitud=s.id_tipo_solicitud
    LEFT JOIN Estudiantes e ON e.numero_control=s.num_control
    LEFT JOIN Aspirantes a ON a.ficha=s.ficha AND a.periodo=s.periodo AND a.anio=s.anio;
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
 PROCEDURE listar_montos(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR
   SELECT id_monto_inscripcion AS id_monto,monto_pago,'INSCRIPCION' AS tipo_monto
     FROM Monto_Inscripciones
   UNION ALL
   SELECT id_monto_reinscripcion,monto_pago,'REINSCRIPCION'
     FROM Monto_Reinscripciones
   ORDER BY 3,1;
 END;
 PROCEDURE listar_historial_solicitud(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR
   SELECT h.id_historial,h.id_solicitud,h.id_estatus,es.nombre AS estatus_nombre,
    h.id_administrador,
    CASE WHEN h.id_administrador IS NULL THEN NULL
         ELSE TRIM(adm.nombre||' '||adm.primer_apellido||
              CASE WHEN adm.segundo_apellido IS NOT NULL THEN ' '||adm.segundo_apellido ELSE '' END)
    END AS administrador_nombre,
    h.comentario,h.fecha_registro
   FROM Historial_Solicitudes h
    JOIN Estatus_Solicitudes es ON es.id_estatus_solicitud=h.id_estatus
    LEFT JOIN Administradores adm ON adm.id_administrador=h.id_administrador
   ORDER BY h.id_solicitud,h.fecha_registro,h.id_historial;
 END;
 PROCEDURE listar_historial_deduccion(p_id_administrador VARCHAR2,p_resultado OUT SYS_REFCURSOR) IS
 BEGIN
  PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
  OPEN p_resultado FOR
   SELECT h.id_historial_deduccion,h.id_deduccion_autorizada,h.id_estatus,ed.nombre AS estatus_nombre,
    h.id_administrador,
    TRIM(adm.nombre||' '||adm.primer_apellido||
      CASE WHEN adm.segundo_apellido IS NOT NULL THEN ' '||adm.segundo_apellido ELSE '' END) AS administrador_nombre,
    h.monto_pago,h.monto_deduccion,h.monto_final,h.fecha_registro
   FROM Historial_Deducciones h
    JOIN Estatus_Deducciones ed ON ed.id_estatus_deducciones=h.id_estatus
    LEFT JOIN Administradores adm ON adm.id_administrador=h.id_administrador
   ORDER BY h.id_deduccion_autorizada,h.fecha_registro,h.id_historial_deduccion;
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
  -- El comentario de autorizacion es opcional: se guarda en el historial solo
  -- cuando el administrador lo captura. Rechazo y cancelacion siguen
  -- exigiendo motivo.
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
  -- El motivo de cancelacion es opcional: se registra en el historial solo
  -- cuando el administrador lo captura. El rechazo sigue exigiendolo.
  -- Orden de bloqueos consistente: primero solicitud y despues deduccion.
  SELECT * INTO s FROM Solicitudes WHERE id_solicitud=(SELECT id_solicitud FROM Deducciones_autorizadas
   WHERE id_deduccion_autorizada=p_deduccion) FOR UPDATE;
  SELECT * INTO d FROM Deducciones_autorizadas WHERE id_deduccion_autorizada=p_deduccion FOR UPDATE;
  comprobar_plazo(s.id_convocatoria,'APLICAR');
  IF d.estatus_deduccion<>2 OR s.id_estatus_solicitud<>2 THEN
   RAISE_APPLICATION_ERROR(-20880,'Solo se cancela una deduccion autorizada');
  END IF;
  -- No se reemplaza id_administrador: esa columna conserva a quien autorizo
  -- y el administrador que cancela queda en Historial_Deducciones.
  UPDATE Deducciones_autorizadas SET estatus_deduccion=4
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
  -- Aplicar no cambia al administrador que autorizo: la aplicacion queda
  -- registrada en Historial_Deducciones con el administrador que la ejecuta.
  UPDATE Deducciones_autorizadas SET estatus_deduccion=5,
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
  -- Equipo 4: crea una convocatoria y sus dos montos en una sola transaccion.
  -- El llamador hace COMMIT/ROLLBACK. Los IDs de tipo 1 y 2 son los del catalogo oficial.
  PROCEDURE crear_convocatoria(p_id_administrador VARCHAR2,p_periodo NUMBER,p_anio NUMBER,
    p_inicio DATE,p_fin DATE,p_limite_autorizacion DATE,p_limite_aplicacion DATE,
    p_monto_trabajador NUMBER,p_monto_homologado NUMBER,p_id OUT NUMBER) IS
    v_n NUMBER;
  BEGIN
    SAVEPOINT sp_crear_conv_e4;
    PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
    IF p_periodo IS NULL OR p_anio IS NULL OR p_anio<>TRUNC(p_anio)
       OR p_anio NOT BETWEEN 2000 AND 2200 THEN
      RAISE_APPLICATION_ERROR(-20910,'Periodo o anio invalido');
    END IF;
    SELECT COUNT(*) INTO v_n FROM Periodos WHERE id_periodo=p_periodo;
    IF v_n<>1 THEN RAISE_APPLICATION_ERROR(-20910,'Periodo no registrado'); END IF;
    IF p_inicio IS NULL OR p_fin IS NULL OR p_limite_autorizacion IS NULL
       OR p_limite_aplicacion IS NULL OR
       TRUNC(p_inicio)>TRUNC(p_fin) OR
       TRUNC(p_fin)>TRUNC(p_limite_autorizacion) OR
       TRUNC(p_limite_autorizacion)>TRUNC(p_limite_aplicacion) THEN
      RAISE_APPLICATION_ERROR(-20911,'Fechas de convocatoria invalidas');
    END IF;
    -- No crear convocatorias cuya configuracion de montos ya este cerrada.
    IF TRUNC(p_limite_autorizacion)<TRUNC(SYSDATE) THEN
      RAISE_APPLICATION_ERROR(-20912,'Plazo de autorizacion ya vencido');
    END IF;
    IF p_monto_trabajador IS NULL OR p_monto_homologado IS NULL
       OR p_monto_trabajador<0 OR p_monto_homologado<0
       OR p_monto_trabajador<>ROUND(p_monto_trabajador,2)
       OR p_monto_homologado<>ROUND(p_monto_homologado,2)
       OR p_monto_trabajador>=100000000 OR p_monto_homologado>=100000000 THEN
      RAISE_APPLICATION_ERROR(-20913,'Montos de deduccion invalidos');
    END IF;
    SELECT COUNT(*) INTO v_n FROM Tipo_Solicitudes WHERE id_tipo_solicitud IN (1,2);
    IF v_n<>2 THEN RAISE_APPLICATION_ERROR(-20914,'Catalogo de tipos incompleto'); END IF;
    -- Validaciones preventivas; restricciones y trigger siguen protegiendo
    -- frente a operaciones concurrentes.
    SELECT COUNT(*) INTO v_n FROM Convocatorias
     WHERE id_periodo=p_periodo AND anio=p_anio;
    IF v_n>0 THEN RAISE_APPLICATION_ERROR(-20916,'Ya existe convocatoria para ese periodo y anio'); END IF;
    -- Regla de solapamiento inclusivo equivalente a trg_convocatorias_intervalo.
    SELECT COUNT(*) INTO v_n FROM Convocatorias
     WHERE TRUNC(p_inicio)<=TRUNC(fecha_limite_aplicacion)
       AND TRUNC(p_limite_aplicacion)>=TRUNC(fecha_inicio_solicitudes);
    IF v_n>0 THEN RAISE_APPLICATION_ERROR(-20917,'Se superpone con otra convocatoria'); END IF;
    INSERT INTO Convocatorias(fecha_inicio_solicitudes,fecha_fin_solicitudes,
      fecha_limite_autorizacion,fecha_limite_aplicacion,id_periodo,anio)
    VALUES(p_inicio,p_fin,p_limite_autorizacion,p_limite_aplicacion,p_periodo,p_anio)
    RETURNING id_convocatoria INTO p_id;
    INSERT INTO Convocatorias_TipoSolicitudes(id_convocatoria,id_tipo_solicitud,monto_deduccion)
      VALUES(p_id,1,p_monto_trabajador);
    INSERT INTO Convocatorias_TipoSolicitudes(id_convocatoria,id_tipo_solicitud,monto_deduccion)
      VALUES(p_id,2,p_monto_homologado);
  EXCEPTION WHEN OTHERS THEN
    ROLLBACK TO sp_crear_conv_e4;
    RAISE;
  END;

  PROCEDURE consultar_montos_convocatoria(p_id_administrador VARCHAR2,p_convocatoria NUMBER,
    p_resultado OUT SYS_REFCURSOR) IS
    v_n NUMBER;
  BEGIN
    PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
    SELECT COUNT(*) INTO v_n FROM Convocatorias WHERE id_convocatoria=p_convocatoria;
    IF v_n<>1 THEN RAISE_APPLICATION_ERROR(-20915,'Convocatoria no registrada'); END IF;
    OPEN p_resultado FOR
      SELECT ct.id_convocatoria,ct.id_tipo_solicitud,t.nombre,
             ct.monto_deduccion,ct.fecha_asignacion_montos
      FROM Convocatorias_TipoSolicitudes ct
      JOIN Tipo_Solicitudes t ON t.id_tipo_solicitud=ct.id_tipo_solicitud
      WHERE ct.id_convocatoria=p_convocatoria
      ORDER BY ct.id_tipo_solicitud;
  END;

  PROCEDURE listar_historial_convocatoria(p_id_administrador VARCHAR2,p_convocatoria NUMBER,
    p_resultado OUT SYS_REFCURSOR) IS
    v_n NUMBER;
  BEGIN
    PKG_SOLICITUDES_CORE.validar_administrador(p_id_administrador);
    SELECT COUNT(*) INTO v_n FROM Convocatorias WHERE id_convocatoria=p_convocatoria;
    IF v_n<>1 THEN RAISE_APPLICATION_ERROR(-20915,'Convocatoria no registrada'); END IF;
    OPEN p_resultado FOR
      SELECT h.id_historial_convocatoria,h.id_convocatoria,h.id_administrador,
        h.fecha_registro,h.inicio_anterior,h.fin_anterior,
        h.autorizacion_anterior,h.aplicacion_anterior,
        h.inicio_nuevo,h.fin_nuevo,h.autorizacion_nueva,h.aplicacion_nueva
      FROM Historial_Convocatorias h
      WHERE h.id_convocatoria=p_convocatoria
      ORDER BY h.fecha_registro DESC,h.id_historial_convocatoria DESC;
  END;
END PKG_ADMINISTRADOR;
/
