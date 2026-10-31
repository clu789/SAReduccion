-- SAReduccion | 06-seed-desarrollo.sql
-- Oracle 21c / XEPDB1 | datos de DEMOSTRACION, NO datos institucionales reales.
-- Ejecutar como SA_REDUCCION sobre esquema NUEVO, una sola vez.
-- Requiere 02-crear-estructura.sql y los paquetes de 05-crear-paquetes-permisos.sql.
-- Solo convocatorias usan IDs 900001 y 900002.
-- Las demas entidades conservan secuencias del script 02.
-- El 02 ya carga Tipo_Solicitudes (1,2), Formularios (1,2), Periodos,
-- Tipo_Preguntas y catalogos de estados: NO se duplican aqui.
-- Identidades obligatorias: estudiante 21281181, aspirante F2026A001
-- CURP MOPA080512MDFRNN01 (periodo 2, 2026), administrador SA01.
-- Las contrasenas de las cuentas Oracle se configuran fuera del seed.
-- Convocatoria 1: periodo 1/2026 cerrado; convocatoria 2: periodo 2/2026
-- abierta en la fecha de ejecucion (fechas relativas). Sin superposicion.
-- Todas las solicitudes y deducciones se crean y cambian con los paquetes de
-- 05-crear-paquetes-permisos.sql, nunca con DML directo.
SET SERVEROUTPUT ON SIZE UNLIMITED
SET SQLBLANKLINES ON
SET DEFINE OFF
WHENEVER OSERROR EXIT 1 ROLLBACK
WHENEVER SQLERROR EXIT 1 ROLLBACK

PROMPT === Validacion minima de ejecucion unica ===
DECLARE
 v_count NUMBER;
BEGIN
 IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'SA_REDUCCION'
    OR SYS_CONTEXT('USERENV','CON_NAME') <> 'XEPDB1' THEN
  RAISE_APPLICATION_ERROR(-20960,'Conectarse como SA_REDUCCION a XEPDB1');
 END IF;
 -- El seed exige un esquema recien inicializado: aborta si ya existen
 -- convocatorias, solicitudes o estudiantes.
 SELECT COUNT(*) INTO v_count FROM Convocatorias;
 IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20961,'Seed de una sola ejecucion: ya existen convocatorias'); END IF;
 SELECT COUNT(*) INTO v_count FROM Solicitudes;
 IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20962,'Seed de una sola ejecucion: ya existen solicitudes'); END IF;
 SELECT COUNT(*) INTO v_count FROM Estudiantes;
 IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20963,'Seed de una sola ejecucion: ya existen estudiantes'); END IF;
END;
/

-- Las convocatorias se insertan con ID explicito; el resto de tablas obtiene
-- sus identificadores de las secuencias creadas en 02. No se ejecuta
-- ALTER SEQUENCE.

PROMPT === Personas de demostracion ===
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT '21281181','Sofia','López','Hernández','Ingenieria en Sistemas Computacionales',5 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = '21281181');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT 'C21281182','Juan','Hernandez','Cruz','Ingenieria Industrial',6 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = 'C21281182');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT 'B21281183','Valeria','Lopez','Mendoza','Ingenieria en Tecnologias de la Informacion',4 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = 'B21281183');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT 'BC21281184','Carlos Eduardo','Ramirez','Torres','Ingenieria Electromecanica',8 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = 'BC21281184');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT '21281185','Fernanda','Castillo','Vega','Ingenieria Quimica',3 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = '21281185');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT '21281186','Miguel Angel','Navarro','Ruiz','Ingenieria Quimica',7 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = '21281186');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT '21281187','Daniela','Ortega','Flores','Ingenieria en Logistica',2 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = '21281187');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT '21281188','Jorge Luis','Sanchez','Reyes','Ingenieria en Gestion Empresarial',9 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = '21281188');
INSERT INTO Estudiantes(numero_control,nombre,primer_apellido,segundo_apellido,carrera,semestre)
SELECT '21281189','Mariana Isabel','Cervantes','Silva','Ingenieria Ambiental',5 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE numero_control = '21281189');
INSERT INTO Administradores(id_administrador,nombre,primer_apellido,segundo_apellido)
SELECT 'SA01','Juan','Ramirez','Ortega' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Administradores WHERE id_administrador = 'SA01');
INSERT INTO Administradores(id_administrador,nombre,primer_apellido,segundo_apellido)
SELECT 'SA02','Mario Alberto','Salgado','Perez' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Administradores WHERE id_administrador = 'SA02');

-- La MISMA ficha+CURP aparece en dos periodos del mismo anio (permitido).
-- El login aspirante debe elegir el registro 2/2026 por convocatoria abierta.

INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026A001',2,2026,'Fernando','Morales','Pineda',1,'MOPA080512MDFRNN01','fernando.morales.demo@gmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A001' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026A001',1,2026,'Fernando','Morales','Pineda',1,'MOPA080512MDFRNN01','fernando.morales.demo@gmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A001' AND periodo = 1 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026A002',2,2026,'Emilio','Rojas','Lozano',1,'ROLE080921HDFJZM02','emilio.rojas.demo@hotmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A002' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026A003',2,2026,'Paola','Vargas','Soto',1,'VASP080305MDFRTT03','paola.vargas.demo@outlook.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A003' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026B004',2,2026,'Diego','Fuentes','Lara',1,'FULD080617HDFRRG04','diego.fuentes.demo@live.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B004' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026B005',2,2026,'Lucia','Mendoza','Gil',1,'MEGL080827MDFNLC05','lucia.mendoza.demo@yahoo.com.mx' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B005' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026B006',2,2026,'Mateo','Serrano','Reyes',1,'SERM080106HDFRYY06','mateo.serrano.demo@yahoo.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B006' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026B007',2,2026,'Renata','Aguilar','Vega',1,'AUVR080214MDFGGT07','renata.aguilar.demo@icloud.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B007' AND periodo = 2 AND anio = 2026);
INSERT INTO Aspirantes(ficha,periodo,anio,nombre,primer_apellido,segundo_apellido,semestre,curp,correo)
SELECT 'F2026B008',2,2026,'Hector','Campos','Nava',1,'CANH080725HDFMVT08','hector.campos.demo@gmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B008' AND periodo = 2 AND anio = 2026);

PROMPT === Convocatorias, cuotas y deducciones oficiales ===
-- Periodo 1/2026 cerrado hace 10 dias (sin solicitudes historicas fabricadas).
INSERT INTO Convocatorias(id_convocatoria,fecha_inicio_solicitudes,fecha_fin_solicitudes,
 fecha_limite_autorizacion,fecha_limite_aplicacion,id_periodo,anio)
VALUES(900001,TRUNC(SYSDATE)-120,TRUNC(SYSDATE)-90,
 TRUNC(SYSDATE)-60,TRUNC(SYSDATE)-10,1,2026);
-- Periodo 2/2026 abierto hoy; periodo y anio son academicos, no SYSDATE.
INSERT INTO Convocatorias(id_convocatoria,fecha_inicio_solicitudes,fecha_fin_solicitudes,
 fecha_limite_autorizacion,fecha_limite_aplicacion,id_periodo,anio)
VALUES(900002,TRUNC(SYSDATE)-7,TRUNC(SYSDATE)+14,
 TRUNC(SYSDATE)+30,TRUNC(SYSDATE)+45,2,2026);
-- Las referencias a convocatorias se resuelven por periodo/anio; no dependen de las secuencias.
-- Los montos SOLO se configuran para la convocatoria abierta; la cerrada es inmutable.
INSERT INTO Convocatorias_TipoSolicitudes(id_convocatoria,id_tipo_solicitud,monto_deduccion)
SELECT id_convocatoria,1,500 FROM Convocatorias WHERE id_periodo=2 AND anio=2026;
INSERT INTO Convocatorias_TipoSolicitudes(id_convocatoria,id_tipo_solicitud,monto_deduccion)
SELECT id_convocatoria,2,650 FROM Convocatorias WHERE id_periodo=2 AND anio=2026;
INSERT INTO Monto_Inscripciones(id_monto_inscripcion,monto_pago)
VALUES(seq_monto_inscripciones.NEXTVAL,1750);
INSERT INTO Monto_Inscripciones(id_monto_inscripcion,monto_pago)
VALUES(seq_monto_inscripciones.NEXTVAL,1900);
INSERT INTO Monto_Reinscripciones(id_monto_reinscripcion,monto_pago)
VALUES(seq_monto_reinscripciones.NEXTVAL,1450);
INSERT INTO Monto_Reinscripciones(id_monto_reinscripcion,monto_pago)
VALUES(seq_monto_reinscripciones.NEXTVAL,1600);

PROMPT === Preguntas y opciones con IDs generados por secuencia ===
DECLARE
 p1 NUMBER;p2 NUMBER;p3 NUMBER;p4 NUMBER;
 p5 NUMBER;p6 NUMBER;p7 NUMBER;
 o1 NUMBER;o2 NUMBER;o3 NUMBER;o4 NUMBER;o5 NUMBER;o6 NUMBER;o7 NUMBER;o8 NUMBER;
BEGIN
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Nombre del trabajador con el que tiene parentesco','S',1) RETURNING id_pregunta INTO p1;
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Parentesco con el trabajador','S',3) RETURNING id_pregunta INTO p2;
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Modalidad de estudios','S',2) RETURNING id_pregunta INTO p3;
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Observaciones adicionales (opcional)','N',1) RETURNING id_pregunta INTO p4;
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Nombre del trabajador homologado','S',1) RETURNING id_pregunta INTO p5;
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Tipo de parentesco homologado','S',3) RETURNING id_pregunta INTO p6;
 INSERT INTO Preguntas(id_pregunta,texto_pregunta,obligatoria,tipo_pregunta)
 VALUES(seq_preguntas.NEXTVAL,'Cuenta con comprobante','S',2) RETURNING id_pregunta INTO p7;
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(1,p1,1);
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(1,p2,2);
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(1,p3,3);
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(1,p4,4);
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(2,p5,1);
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(2,p6,2);
 INSERT INTO Formulario_Preguntas(id_formulario,id_pregunta,orden) VALUES(2,p7,3);
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Hijo o hija',1,p2,0) RETURNING id_opcion INTO o1;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Otro (especifique)',2,p2,1) RETURNING id_opcion INTO o2;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Escolarizada',1,p3,0) RETURNING id_opcion INTO o3;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Mixta',2,p3,0) RETURNING id_opcion INTO o4;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Hijo o hija',1,p6,0) RETURNING id_opcion INTO o5;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Otro (especifique)',2,p6,1) RETURNING id_opcion INTO o6;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'Si',1,p7,0) RETURNING id_opcion INTO o7;
 INSERT INTO Opciones(id_opcion,texto_opcion,orden,id_pregunta,requiere_especificacion)
 VALUES(seq_opciones.NEXTVAL,'No',2,p7,0) RETURNING id_opcion INTO o8;
END;
/

PROMPT === Solicitudes creadas y cambiadas mediante paquetes PL/SQL ===
DECLARE
 v_conv NUMBER; v_mi NUMBER;v_mr NUMBER;
 v_p1 NUMBER;v_p2 NUMBER;v_p3 NUMBER;v_p5 NUMBER;v_p6 NUMBER;v_p7 NUMBER;
 v_o1 NUMBER;v_o2 NUMBER;v_o3 NUMBER;v_o4 NUMBER;v_o5 NUMBER;v_o6 NUMBER;v_o7 NUMBER;
 j1 CLOB;j1_otro CLOB;j2 CLOB;j2_otro CLOB;
 v_ficha VARCHAR2(18);v_periodo NUMBER;v_anio NUMBER;
 FUNCTION resp_abierta(p_p NUMBER,p_texto VARCHAR2) RETURN VARCHAR2 IS
 BEGIN RETURN '{"id_pregunta":'||p_p||',"texto_respuesta":"'||p_texto||'"}'; END;
 FUNCTION resp_opcion(p_p NUMBER,p_o NUMBER,p_espec VARCHAR2 DEFAULT NULL) RETURN VARCHAR2 IS
 BEGIN
  RETURN '{"id_pregunta":'||p_p||',"id_opcion":'||p_o||
   CASE WHEN p_espec IS NOT NULL THEN ',"texto_respuesta":"'||p_espec||'"' ELSE '' END||'}';
 END;
 PROCEDURE crear_estudiante(p_control VARCHAR2,p_tipo NUMBER,p_json CLOB,p_accion VARCHAR2) IS
  v_s NUMBER;v_d NUMBER;v_nuevo_tipo NUMBER;
 BEGIN
  PKG_ESTUDIANTE.crear_solicitud(p_control,v_conv,p_tipo,p_tipo,p_json,v_s);
  IF p_accion='RETIRADO' THEN
   PKG_ESTUDIANTE.retirar_solicitud(p_control,v_s);
  ELSIF p_accion='RECHAZADO' THEN
   PKG_ADMINISTRADOR.rechazar_solicitud('SA01',v_s,'Documentacion incompleta (desarrollo)');
  ELSIF p_accion IN ('AUTORIZADO','CANCELADO','APLICADO','REAUTORIZADO','REENVIADO') THEN
   PKG_ADMINISTRADOR.autorizar_deduccion('SA01',v_s,NULL,v_mr,'Autorizacion de desarrollo',v_d);
   IF p_accion IN ('CANCELADO','REAUTORIZADO','REENVIADO') THEN
    PKG_ADMINISTRADOR.cancelar_deduccion('SA02',v_d,'Cancelacion de desarrollo');
    IF p_accion='REAUTORIZADO' THEN
     PKG_ADMINISTRADOR.autorizar_deduccion('SA01',v_s,NULL,v_mr,'Reautorizacion directa',v_d);
    ELSIF p_accion='REENVIADO' THEN
     v_nuevo_tipo:=CASE WHEN p_tipo=1 THEN 2 ELSE 1 END;
     PKG_ESTUDIANTE.reenviar_solicitud(p_control,v_s,v_nuevo_tipo,v_nuevo_tipo,
      CASE WHEN v_nuevo_tipo=1 THEN j1 ELSE j2 END);
    END IF;
   ELSIF p_accion='APLICADO' THEN
    PKG_ADMINISTRADOR.aplicar_deduccion('SA02',v_d);
   END IF;
  END IF;
  DBMS_OUTPUT.PUT_LINE('ESTUDIANTE '||p_control||' | escenario='||p_accion||' | solicitud='||v_s);
 END;
 PROCEDURE crear_aspirante(p_ficha VARCHAR2,p_tipo NUMBER,p_json CLOB,p_accion VARCHAR2) IS
  v_s NUMBER;v_d NUMBER;
 BEGIN
  PKG_ASPIRANTE.crear_solicitud(p_ficha,2,2026,v_conv,p_tipo,p_tipo,p_json,v_s);
  IF p_accion='RETIRADO' THEN
   PKG_ASPIRANTE.retirar_solicitud(p_ficha,2,2026,v_s);
  ELSIF p_accion='RECHAZADO' THEN
   PKG_ADMINISTRADOR.rechazar_solicitud('SA01',v_s,'Documentacion incompleta (desarrollo)');
  ELSIF p_accion IN ('AUTORIZADO','CANCELADO','APLICADO') THEN
   PKG_ADMINISTRADOR.autorizar_deduccion('SA01',v_s,v_mi,NULL,'Autorizacion de desarrollo',v_d);
   IF p_accion='CANCELADO' THEN
    PKG_ADMINISTRADOR.cancelar_deduccion('SA02',v_d,'Cancelacion de desarrollo');
   ELSIF p_accion='APLICADO' THEN
    PKG_ADMINISTRADOR.aplicar_deduccion('SA02',v_d);
   END IF;
  END IF;
  DBMS_OUTPUT.PUT_LINE('ASPIRANTE '||p_ficha||' | escenario='||p_accion||' | solicitud='||v_s);
 END;
BEGIN
 SELECT id_convocatoria INTO v_conv FROM Convocatorias WHERE id_periodo=2 AND anio=2026;
 SELECT MIN(id_monto_inscripcion) INTO v_mi FROM Monto_Inscripciones;
 SELECT MIN(id_monto_reinscripcion) INTO v_mr FROM Monto_Reinscripciones;
 SELECT id_pregunta INTO v_p1 FROM Preguntas WHERE texto_pregunta='Nombre del trabajador con el que tiene parentesco';
 SELECT id_pregunta INTO v_p2 FROM Preguntas WHERE texto_pregunta='Parentesco con el trabajador';
 SELECT id_pregunta INTO v_p3 FROM Preguntas WHERE texto_pregunta='Modalidad de estudios';
 SELECT id_pregunta INTO v_p5 FROM Preguntas WHERE texto_pregunta='Nombre del trabajador homologado';
 SELECT id_pregunta INTO v_p6 FROM Preguntas WHERE texto_pregunta='Tipo de parentesco homologado';
 SELECT id_pregunta INTO v_p7 FROM Preguntas WHERE texto_pregunta='Cuenta con comprobante';
 SELECT id_opcion INTO v_o1 FROM Opciones WHERE id_pregunta=v_p2 AND orden=1;
 SELECT id_opcion INTO v_o2 FROM Opciones WHERE id_pregunta=v_p2 AND orden=2;
 SELECT id_opcion INTO v_o3 FROM Opciones WHERE id_pregunta=v_p3 AND orden=1;
 SELECT id_opcion INTO v_o4 FROM Opciones WHERE id_pregunta=v_p3 AND orden=2;
 SELECT id_opcion INTO v_o5 FROM Opciones WHERE id_pregunta=v_p6 AND orden=1;
 SELECT id_opcion INTO v_o6 FROM Opciones WHERE id_pregunta=v_p6 AND orden=2;
 SELECT id_opcion INTO v_o7 FROM Opciones WHERE id_pregunta=v_p7 AND orden=1;
 j1:='['||resp_abierta(v_p1,'Trabajador de ejemplo')||','||resp_opcion(v_p2,v_o1)||','||resp_opcion(v_p3,v_o3)||']';
 j1_otro:='['||resp_abierta(v_p1,'Trabajador de ejemplo')||','||resp_opcion(v_p2,v_o2,'Tutor legal')||','||resp_opcion(v_p3,v_o4)||']';
 j2:='['||resp_abierta(v_p5,'Trabajador homologado de ejemplo')||','||resp_opcion(v_p6,v_o5)||','||resp_opcion(v_p7,v_o7)||']';
 j2_otro:='['||resp_abierta(v_p5,'Trabajador homologado de ejemplo')||','||resp_opcion(v_p6,v_o6,'Tutor legal')||','||resp_opcion(v_p7,v_o7)||']';

 -- Debe identificar 2/2026, aunque la misma ficha+CURP exista en 1/2026.
 PKG_ASPIRANTE.validar_aspirante('F2026A001','MOPA080512MDFRNN01',v_ficha,v_periodo,v_anio);
 IF v_ficha<>'F2026A001' OR v_periodo<>2 OR v_anio<>2026 THEN
  RAISE_APPLICATION_ERROR(-20910,'Identidad de aspirante resuelta en periodo incorrecto');
 END IF;
 DBMS_OUTPUT.PUT_LINE('ASPIRANTE LOGIN | ficha='||v_ficha||' periodo='||v_periodo||' anio='||v_anio);

 -- Nueve estudiantes; cada uno llega a su estado final recorriendo las
 -- transiciones permitidas por el esquema.
 crear_estudiante('21281181',1,j1_otro,'PENDIENTE');
 crear_estudiante('C21281182',2,j2,'AUTORIZADO');
 crear_estudiante('B21281183',2,j2_otro,'RECHAZADO');
 crear_estudiante('BC21281184',1,j1,'RETIRADO');
 crear_estudiante('21281185',1,j1,'CANCELADO');
 crear_estudiante('21281186',2,j2,'APLICADO');
 crear_estudiante('21281187',1,j1,'REAUTORIZADO');
 crear_estudiante('21281188',1,j1,'REENVIADO');
 crear_estudiante('21281189',2,j2,'PENDIENTE');

 -- Ocho aspirantes con solicitud activa; el registro duplicado de 1/2026 se
 -- conserva sin solicitud asociada.
 crear_aspirante('F2026A001',1,j1,'PENDIENTE');
 crear_aspirante('F2026A002',2,j2,'AUTORIZADO');
 crear_aspirante('F2026A003',1,j1,'RETIRADO');
 crear_aspirante('F2026B004',2,j2,'RECHAZADO');
 crear_aspirante('F2026B005',1,j1,'CANCELADO');
 crear_aspirante('F2026B006',2,j2,'APLICADO');
 crear_aspirante('F2026B007',1,j1_otro,'PENDIENTE');
 crear_aspirante('F2026B008',2,j2,'PENDIENTE');
END;
/

PROMPT === Comprobaciones finales obligatorias ===
DECLARE
 n NUMBER;v_s NUMBER;v_d NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM Estudiantes;
 IF n<>9 THEN RAISE_APPLICATION_ERROR(-20920,'Se esperaban 9 estudiantes'); END IF;
 SELECT COUNT(*) INTO n FROM Aspirantes;
 IF n<>9 THEN RAISE_APPLICATION_ERROR(-20921,'Se esperaban 9 registros de aspirantes (8 fichas)'); END IF;
 SELECT COUNT(*) INTO n FROM Aspirantes WHERE ficha='F2026A001' AND curp='MOPA080512MDFRNN01' AND anio=2026;
 IF n<>2 THEN RAISE_APPLICATION_ERROR(-20922,'Falta duplicado academico entre periodos'); END IF;
 SELECT COUNT(*) INTO n FROM Solicitudes;
 IF n<>17 THEN RAISE_APPLICATION_ERROR(-20923,'Se esperaban 17 solicitudes'); END IF;
 SELECT COUNT(*) INTO n FROM Convocatorias_TipoSolicitudes;
 IF n<>2 THEN RAISE_APPLICATION_ERROR(-20931,'Se esperaban 2 configuraciones solo de convocatoria abierta'); END IF;
 SELECT COUNT(*) INTO n FROM Deducciones_autorizadas;
 IF n<>8 THEN RAISE_APPLICATION_ERROR(-20930,'Se esperaban 8 deducciones'); END IF;
 SELECT COUNT(*) INTO n FROM Solicitudes WHERE id_estatus_solicitud=6;
 IF n<2 THEN RAISE_APPLICATION_ERROR(-20924,'Faltan solicitudes RETIRADO'); END IF;
 SELECT COUNT(*) INTO n FROM Solicitudes WHERE id_estatus_solicitud=4;
 IF n<2 THEN RAISE_APPLICATION_ERROR(-20925,'Faltan solicitudes CANCELADO'); END IF;
 SELECT COUNT(*) INTO n FROM Solicitudes WHERE id_estatus_solicitud=5;
 IF n<2 THEN RAISE_APPLICATION_ERROR(-20926,'Faltan solicitudes APLICADO'); END IF;
 SELECT COUNT(*) INTO n FROM Solicitudes s JOIN Deducciones_autorizadas d ON d.id_solicitud=s.id_solicitud
  WHERE s.num_control='21281188' AND s.id_estatus_solicitud=1 AND s.id_tipo_solicitud=2
   AND d.estatus_deduccion=4 AND d.id_tipo_solicitud=1;
 IF n<>1 THEN RAISE_APPLICATION_ERROR(-20927,'Reenvio con deduccion cancelada no conserva tipo anterior'); END IF;
 SELECT COUNT(*) INTO n FROM Deducciones_autorizadas d WHERE NOT EXISTS
  (SELECT 1 FROM Historial_Deducciones h WHERE h.id_deduccion_autorizada=d.id_deduccion_autorizada);
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20928,'Deduccion sin historial inicial'); END IF;
 SELECT COUNT(*) INTO n FROM Solicitudes s JOIN Convocatorias c ON c.id_convocatoria=s.id_convocatoria
  WHERE s.ficha IS NOT NULL AND (s.periodo<>c.id_periodo OR s.anio<>c.anio);
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20929,'Aspirante asociado a convocatoria equivocada'); END IF;
 DBMS_OUTPUT.PUT_LINE('OK | 9 estudiantes, 9 registros aspirantes, 17 solicitudes, estados e historiales');
END;
/
COMMIT;
PROMPT === Resumen del seed ===
SELECT 'ESTUDIANTES' entidad,COUNT(*) total FROM Estudiantes
UNION ALL SELECT 'ASPIRANTES',COUNT(*) FROM Aspirantes
UNION ALL SELECT 'ADMINISTRADORES',COUNT(*) FROM Administradores
UNION ALL SELECT 'CONVOCATORIAS',COUNT(*) FROM Convocatorias
UNION ALL SELECT 'FORMULARIOS',COUNT(*) FROM Formularios
UNION ALL SELECT 'PREGUNTAS',COUNT(*) FROM Preguntas
UNION ALL SELECT 'OPCIONES',COUNT(*) FROM Opciones
UNION ALL SELECT 'SOLICITUDES',COUNT(*) FROM Solicitudes
UNION ALL SELECT 'DEDUCCIONES',COUNT(*) FROM Deducciones_autorizadas
UNION ALL SELECT 'HIST_SOLICITUDES',COUNT(*) FROM Historial_Solicitudes
UNION ALL SELECT 'HIST_DEDUCCIONES',COUNT(*) FROM Historial_Deducciones;
PROMPT === Estados de solicitudes ===
SELECT e.nombre,COUNT(*) total FROM Solicitudes s
 JOIN Estatus_Solicitudes e ON e.id_estatus_solicitud=s.id_estatus_solicitud
 GROUP BY e.nombre ORDER BY e.nombre;
PROMPT Seed de demostracion finalizado. Convocatorias con IDs 900001 y 900002.
EXIT SUCCESS
