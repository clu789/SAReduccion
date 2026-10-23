-- SAReduccion - Datos ficticios de desarrollo (Oracle XE 21c / XEPDB1)
-- Carga 9 estudiantes, 2 administradores, 8 aspirantes, convocatorias,
-- formularios, solicitudes, respuestas, historiales y deducciones de ejemplo.
-- Tipos de solicitud validos (catalogo cerrado):
--   900001 Hijos de trabajadores
--   900002 Hijos de homologados
-- NO crea cuentas Oracle, NO altera el esquema y NO borra filas.
-- Ejecutar SOLO como SA_REDUCCION en XEPDB1.
-- Se carga unicamente cuando SAR_LOAD_DEV_SEED esta activo (entorno de desarrollo).
-- IDs 900001+ reservados para estos datos; no se modifican las secuencias.
SET DEFINE OFF
SET SERVEROUTPUT ON
SET SQLBLANKLINES ON
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
  v_count NUMBER;
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'SA_REDUCCION'
     OR SYS_CONTEXT('USERENV','CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20960,'Conectarse como SA_REDUCCION a XEPDB1');
  END IF;
  -- Evitar colisiones con IDs reservados de otro origen.
  SELECT COUNT(*) INTO v_count FROM Solicitudes
   WHERE idSolicitud BETWEEN 900001 AND 900999
     AND NOT (idSolicitud BETWEEN 900001 AND 900020);
  IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20961,'Rango de seed ocupado'); END IF;
END;
/


PROMPT ===== ESTUDIANTES Y ADMINISTRADORES =====

INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT '21281181', 'Sofia', 'López', 'Hernández', 'Ingenieria en Sistemas Computacionales', 5 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = '21281181');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT 'C21281182', 'Juan', 'Hernandez', 'Cruz', 'Ingenieria Industrial', 6 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = 'C21281182');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT 'B21281183', 'Valeria', 'Lopez', 'Mendoza', 'Ingenieria en Tecnologias de la Informacion', 4 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = 'B21281183');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT 'BC21281184', 'Carlos Eduardo', 'Ramirez', 'Torres', 'Ingenieria Electromecanica', 8 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = 'BC21281184');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT '21281185', 'Fernanda', 'Castillo', 'Vega', 'Ingenieria Quimica', 3 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = '21281185');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT '21281186', 'Miguel Angel', 'Navarro', 'Ruiz', 'Ingenieria Quimica', 7 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = '21281186');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT '21281187', 'Daniela', 'Ortega', 'Flores', 'Ingenieria en Logistica', 2 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = '21281187');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT '21281188', 'Jorge Luis', 'Sanchez', 'Reyes', 'Ingenieria en Gestion Empresarial', 9 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = '21281188');


INSERT INTO Estudiantes (Numero_Control, Nombre, PrimerAppellido, SegundoApellido, Carrera, Semestre)
SELECT '21281189', 'Mariana Isabel', 'Cervantes', 'Silva', 'Ingenieria Ambiental', 5 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Estudiantes WHERE Numero_Control = '21281189');


INSERT INTO Administradores (idAdministrador, Nombre, PrimerApellido, SegundoApellido)
SELECT 'SA01', 'Laura Beatriz', 'Ramirez', 'Ortega' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Administradores WHERE idAdministrador = 'SA01');


INSERT INTO Administradores (idAdministrador, Nombre, PrimerApellido, SegundoApellido)
SELECT 'SA02', 'Mario Alberto', 'Salgado', 'Perez' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Administradores WHERE idAdministrador = 'SA02');


PROMPT ===== CATALOGOS COMPLEMENTARIOS =====

-- Catalogo cerrado: unicamente son validos estos dos tipos de solicitud.
INSERT INTO Tipo_Solicitudes (id_Tipo_Solicitud, Nombre, Descripcion)
SELECT 900001, 'Hijos de trabajadores', 'Apoyo a hijos de trabajadores' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Tipo_Solicitudes WHERE id_Tipo_Solicitud = 900001);


INSERT INTO Tipo_Solicitudes (id_Tipo_Solicitud, Nombre, Descripcion)
SELECT 900002, 'Hijos de homologados', 'Apoyo a hijos de personal homologado' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Tipo_Solicitudes WHERE id_Tipo_Solicitud = 900002);


INSERT INTO Monto_Inscripciones (id_monto_inscripcion, monto_pago)
SELECT 900001, 1750 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Monto_Inscripciones WHERE id_monto_inscripcion = 900001);


INSERT INTO Monto_Inscripciones (id_monto_inscripcion, monto_pago)
SELECT 900002, 1900 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Monto_Inscripciones WHERE id_monto_inscripcion = 900002);


INSERT INTO Monto_Reinscripciones (id_monto_reinscripcion, monto_pago)
SELECT 900001, 1450 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Monto_Reinscripciones WHERE id_monto_reinscripcion = 900001);


INSERT INTO Monto_Reinscripciones (id_monto_reinscripcion, monto_pago)
SELECT 900002, 1600 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Monto_Reinscripciones WHERE id_monto_reinscripcion = 900002);


INSERT INTO Convocatorias (id_convocatoria, fecha_inicio, fecha_fin, id_periodo, anio)
SELECT 900001, DATE '2026-01-12', DATE '2026-02-28', 2, 2026 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Convocatorias WHERE id_convocatoria = 900001);


INSERT INTO Convocatorias (id_convocatoria, fecha_inicio, fecha_fin, id_periodo, anio)
SELECT 900002, DATE '2026-08-10', DATE '2026-11-30', 1, 2026 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Convocatorias WHERE id_convocatoria = 900002);


INSERT INTO Convocatorias_TipoSolicitudes (id_convocatoria, id_Tipo_Solicitud, monto_deduccion, fecha_asignacion_montos)
SELECT 900001, 900001, 500, DATE '2026-01-05' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Convocatorias_TipoSolicitudes WHERE id_convocatoria = 900001 AND id_Tipo_Solicitud = 900001);


INSERT INTO Convocatorias_TipoSolicitudes (id_convocatoria, id_Tipo_Solicitud, monto_deduccion, fecha_asignacion_montos)
SELECT 900001, 900002, 650, DATE '2026-01-05' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Convocatorias_TipoSolicitudes WHERE id_convocatoria = 900001 AND id_Tipo_Solicitud = 900002);


INSERT INTO Convocatorias_TipoSolicitudes (id_convocatoria, id_Tipo_Solicitud, monto_deduccion, fecha_asignacion_montos)
SELECT 900002, 900001, 500, DATE '2026-08-01' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Convocatorias_TipoSolicitudes WHERE id_convocatoria = 900002 AND id_Tipo_Solicitud = 900001);


INSERT INTO Convocatorias_TipoSolicitudes (id_convocatoria, id_Tipo_Solicitud, monto_deduccion, fecha_asignacion_montos)
SELECT 900002, 900002, 650, DATE '2026-08-01' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Convocatorias_TipoSolicitudes WHERE id_convocatoria = 900002 AND id_Tipo_Solicitud = 900002);


INSERT INTO Formularios (idFormulario, Nombre, Descripcion, id_Tipo_Solicitud)
SELECT 900001, 'Solicitud para hijos de trabajadores', 'Formulario ficticio para pruebas', 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formularios WHERE idFormulario = 900001);


INSERT INTO Formularios (idFormulario, Nombre, Descripcion, id_Tipo_Solicitud)
SELECT 900002, 'Solicitud para hijos de homologados', 'Formulario ficticio para pruebas', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formularios WHERE idFormulario = 900002);


INSERT INTO Preguntas (idPregunta, texto_Pregunta, tipo_pregunta)
SELECT 900001, 'Describe brevemente el motivo de tu solicitud', 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Preguntas WHERE idPregunta = 900001);


INSERT INTO Preguntas (idPregunta, texto_Pregunta, tipo_pregunta)
SELECT 900002, '¿Cuentas con documentacion comprobatoria?', 2 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Preguntas WHERE idPregunta = 900002);


INSERT INTO Preguntas (idPregunta, texto_Pregunta, tipo_pregunta)
SELECT 900003, 'Indica tu situacion actual', 3 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Preguntas WHERE idPregunta = 900003);


INSERT INTO Preguntas (idPregunta, texto_Pregunta, tipo_pregunta)
SELECT 900004, 'Describe la relacion con el trabajador', 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Preguntas WHERE idPregunta = 900004);


INSERT INTO Preguntas (idPregunta, texto_Pregunta, tipo_pregunta)
SELECT 900005, '¿Deseas recibir notificaciones por correo?', 2 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Preguntas WHERE idPregunta = 900005);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900001, 900001, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900001 AND idPregunta = 900001);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900001, 900002, 2 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900001 AND idPregunta = 900002);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900001, 900003, 3 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900001 AND idPregunta = 900003);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900001, 900005, 4 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900001 AND idPregunta = 900005);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900002, 900001, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900002 AND idPregunta = 900001);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900002, 900002, 2 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900002 AND idPregunta = 900002);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900002, 900003, 3 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900002 AND idPregunta = 900003);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900002, 900005, 4 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900002 AND idPregunta = 900005);


INSERT INTO Formulario_Preguntas (idFormulario, idPregunta, orden)
SELECT 900001, 900004, 5 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Formulario_Preguntas WHERE idFormulario = 900001 AND idPregunta = 900004);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900001, 'Si', 1, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900001);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900002, 'No', 2, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900002);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900003, 'Estudio y trabajo', 1, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900003);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900004, 'Solo estudio', 2, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900004);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900005, 'Otro', 3, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900005);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900006, 'Si', 1, 900005 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900006);


INSERT INTO Opciones (id_opcion, texto_opcion, orden, id_pregunta)
SELECT 900007, 'No', 2, 900005 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Opciones WHERE id_opcion = 900007);


PROMPT ===== ASPIRANTES =====

INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026A001', 2, 2026, 'Andrea', 'Morales', 'Pineda', 1, 'MOPA080512MDFRNN01', 'andrea.morales.demo@gmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A001' AND periodo = 2 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026A002', 2, 2026,'Emilio', 'Rojas', 'Lozano', 1, 'ROLE080921HDFJZM02', 'emilio.rojas.demo@hotmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A002' AND periodo = 2 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026A003', 2, 2026, 'Paola', 'Vargas', 'Soto', 1, 'VASP080305MDFRTT03', 'paola.vargas.demo@outlook.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026A003' AND periodo = 2 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026B004', 1, 2026,'Diego', 'Fuentes', 'Lara', 1, 'FULD080617HDFRRG04', 'diego.fuentes.demo@live.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B004' AND periodo = 1 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026B005', 1, 2026, 'Lucia', 'Mendoza', 'Gil', 1, 'MEGL080827MDFNLC05', 'lucia.mendoza.demo@yahoo.com.mx' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B005' AND periodo = 1 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026B006', 1, 2026, 'Mateo', 'Serrano', 'Reyes', 1, 'SERM080106HDFRYY06', 'mateo.serrano.demo@yahoo.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B006' AND periodo = 1 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026B007', 1, 2026, 'Renata', 'Aguilar', 'Vega', 1, 'AUVR080214MDFGGT07', 'renata.aguilar.demo@icloud.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B007' AND periodo = 1 AND anio = 2026);


INSERT INTO Aspirantes (ficha, periodo, anio, Nombre, PrimerApellido, SegundoApellido, Semestre, Curp, correo)
SELECT 'F2026B008', 1, 2026, 'Hector', 'Campos', 'Nava', 1, 'CANH080725HDFMVT08', 'hector.campos.demo@gmail.com' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Aspirantes WHERE ficha = 'F2026B008' AND periodo = 1 AND anio = 2026);


PROMPT ===== SOLICITUDES =====

INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900001, 2, 900002, '21281181', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900001, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900001);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900002, 1, 900002, 'C21281182', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900002);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900003, 3, 900002, 'B21281183', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900003);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900004, 2, 900002, 'BC21281184', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900001, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900004);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900005, 1, 900002, '21281185', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900005);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900006, 2, 900002, '21281186', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900006);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900007, 1, 900002, '21281187', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900001, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900007);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900008, 3, 900002, '21281188', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900008);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900009, 2, 900002, '21281189', NULL, NULL, NULL, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900009);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900010, 2, 900001, NULL, 'F2026A001', 2, 2026, DATE '2026-02-10', DATE '2026-02-10', 900001, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900010);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900011, 1, 900001, NULL, 'F2026A002', 2, 2026, DATE '2026-02-10', DATE '2026-02-10', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900011);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900012, 3, 900001, NULL, 'F2026A003', 2, 2026, DATE '2026-02-10', DATE '2026-02-10', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900012);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900013, 2, 900002, NULL, 'F2026B004', 1, 2026, DATE '2026-10-01', DATE '2026-10-01', 900001, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900013);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900014, 2, 900002, NULL, 'F2026B005', 1, 2026, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900014);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900015, 1, 900002, NULL, 'F2026B006', 1, 2026, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900015);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900016, 2, 900002, NULL, 'F2026B007', 1, 2026, DATE '2026-10-01', DATE '2026-10-01', 900001, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900016);


INSERT INTO Solicitudes (idSolicitud, id_estatus_solicitud, id_convocatoria, num_control, ficha, periodo, anio, fecha_Solicitud, fecha_actualizacion, id_Tipo_Solicitud, idFormulario)
SELECT 900017, 3, 900002, NULL, 'F2026B008', 1, 2026, DATE '2026-10-01', DATE '2026-10-01', 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Solicitudes WHERE idSolicitud = 900017);


PROMPT ===== RESPUESTAS =====

INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000011, 'Solicito apoyo para continuar mis estudios', 900001, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000011);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000012, NULL, 900001, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000012);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000013, NULL, 900001, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000013);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000014, NULL, 900001, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000014);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000021, 'Solicito apoyo para continuar mis estudios', 900002, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000021);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000022, NULL, 900002, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000022);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000023, NULL, 900002, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000023);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000024, NULL, 900002, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000024);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000031, 'Solicito apoyo para continuar mis estudios', 900003, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000031);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000032, NULL, 900003, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000032);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000033, NULL, 900003, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000033);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000034, NULL, 900003, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000034);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000041, 'Solicito apoyo para continuar mis estudios', 900004, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000041);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000042, NULL, 900004, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000042);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000043, NULL, 900004, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000043);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000044, NULL, 900004, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000044);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000051, 'Solicito apoyo para continuar mis estudios', 900005, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000051);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000052, NULL, 900005, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000052);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000053, NULL, 900005, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000053);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000054, NULL, 900005, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000054);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000061, 'Solicito apoyo para continuar mis estudios', 900006, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000061);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000062, NULL, 900006, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000062);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000063, NULL, 900006, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000063);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000064, NULL, 900006, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000064);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000071, 'Solicito apoyo para continuar mis estudios', 900007, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000071);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000072, NULL, 900007, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000072);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000073, NULL, 900007, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000073);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000074, NULL, 900007, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000074);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000081, 'Solicito apoyo para continuar mis estudios', 900008, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000081);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000082, NULL, 900008, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000082);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000083, NULL, 900008, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000083);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000084, NULL, 900008, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000084);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000091, 'Solicito apoyo para continuar mis estudios', 900009, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000091);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000092, NULL, 900009, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000092);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000093, NULL, 900009, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000093);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000094, NULL, 900009, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000094);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000101, 'Solicito apoyo para continuar mis estudios', 900010, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000101);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000102, NULL, 900010, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000102);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000103, NULL, 900010, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000103);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000104, NULL, 900010, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000104);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000111, 'Solicito apoyo para continuar mis estudios', 900011, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000111);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000112, NULL, 900011, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000112);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000113, NULL, 900011, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000113);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000114, NULL, 900011, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000114);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000121, 'Solicito apoyo para continuar mis estudios', 900012, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000121);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000122, NULL, 900012, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000122);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000123, NULL, 900012, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000123);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000124, NULL, 900012, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000124);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000131, 'Solicito apoyo para continuar mis estudios', 900013, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000131);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000132, NULL, 900013, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000132);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000133, NULL, 900013, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000133);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000134, NULL, 900013, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000134);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000141, 'Solicito apoyo para continuar mis estudios', 900014, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000141);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000142, NULL, 900014, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000142);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000143, NULL, 900014, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000143);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000144, NULL, 900014, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000144);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000151, 'Solicito apoyo para continuar mis estudios', 900015, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000151);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000152, NULL, 900015, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000152);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000153, NULL, 900015, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000153);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000154, NULL, 900015, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000154);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000161, 'Solicito apoyo para continuar mis estudios', 900016, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000161);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000162, NULL, 900016, 900002, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000162);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000163, NULL, 900016, 900003, 900003 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000163);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000164, NULL, 900016, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000164);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000171, 'Solicito apoyo para continuar mis estudios', 900017, 900001, NULL FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000171);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000172, NULL, 900017, 900002, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000172);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000173, NULL, 900017, 900003, 900004 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000173);


INSERT INTO Respuestas (id_respuesta, texto_respuesta, id_solicitud, id_pregunta, id_opcion)
SELECT 9000174, NULL, 900017, 900005, 900006 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Respuestas WHERE id_respuesta = 9000174);


PROMPT ===== HISTORIALES DE SOLICITUD =====

INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000011, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900001, 1, NULL, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000011);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000012, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900001, 2, 'SA01', 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000012);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000021, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900002, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000021);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000031, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900003, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000031);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000032, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900003, 3, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000032);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000041, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900004, 1, NULL, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000041);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000042, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900004, 2, 'SA01', 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000042);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000051, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900005, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000051);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000061, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900006, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000061);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000062, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900006, 2, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000062);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000071, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900007, 1, NULL, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000071);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000081, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900008, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000081);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000082, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900008, 3, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000082);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000091, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900009, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000091);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000092, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900009, 2, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000092);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000101, 'Recepcion de solicitud de prueba', DATE '2026-02-10', 900010, 1, NULL, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000101);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000102, 'Resolucion de solicitud ficticia', DATE '2026-02-18', 900010, 2, 'SA01', 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000102);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000111, 'Recepcion de solicitud de prueba', DATE '2026-02-10', 900011, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000111);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000121, 'Recepcion de solicitud de prueba', DATE '2026-02-10', 900012, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000121);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000122, 'Resolucion de solicitud ficticia', DATE '2026-02-18', 900012, 3, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000122);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000131, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900013, 1, NULL, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000131);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000132, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900013, 2, 'SA01', 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000132);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000141, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900014, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000141);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000142, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900014, 2, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000142);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000151, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900015, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000151);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000161, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900016, 1, NULL, 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000161);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000162, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900016, 2, 'SA01', 900001 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000162);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000171, 'Recepcion de solicitud de prueba', DATE '2026-10-01', 900017, 1, NULL, 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000171);


INSERT INTO Historial_Solicitudes (id_historial, comentario, fecha_Registro, id_solicitud, id_estatus, id_administrador, id_Tipo_solicitud)
SELECT 9000172, 'Resolucion de solicitud ficticia', DATE '2026-10-08', 900017, 3, 'SA01', 900002 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Solicitudes WHERE id_historial = 9000172);


PROMPT ===== DEDUCCIONES AUTORIZADAS =====

INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900001, 2, 950, DATE '2026-10-09', 900001, 'SA01', DATE '2026-10-09', 900002, 900001, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', NULL, 900001, 1450, 500 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900001);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000011, 900001, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000011);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900004, 2, 950, DATE '2026-10-09', 900004, 'SA01', DATE '2026-10-09', 900002, 900001, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', NULL, 900001, 1450, 500 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900004);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000041, 900004, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000041);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900006, 2, 800, DATE '2026-10-09', 900006, 'SA01', DATE '2026-10-09', 900002, 900002, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', NULL, 900001, 1450, 650 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900006);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000061, 900006, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000061);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900009, 2, 800, DATE '2026-10-09', 900009, 'SA01', DATE '2026-10-09', 900002, 900002, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', NULL, 900001, 1450, 650 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900009);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000091, 900009, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000091);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900010, 2, 1250, DATE '2026-10-09', 900010, 'SA01', DATE '2026-10-09', 900001, 900001, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', 900001, NULL, 1750, 500 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900010);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000101, 900010, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000101);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900013, 2, 1250, DATE '2026-10-09', 900013, 'SA01', DATE '2026-10-09', 900002, 900001, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', 900001, NULL, 1750, 500 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900013);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000131, 900013, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000131);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900014, 2, 1100, DATE '2026-10-09', 900014, 'SA01', DATE '2026-10-09', 900002, 900002, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', 900001, NULL, 1750, 650 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900014);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000141, 900014, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000141);


INSERT INTO Deducciones_autorizadas (id_deduccion_autorizada, estatus_deduccion, monto_final, fecha_registro, id_solicitud, id_administrador, fecha_autorizacion, id_convocatoria, id_Tipo_Solicitud, fecha_actualizacion, comentario, id_monto_inscripcion, id_monto_reinscripcion, monto_pago, monto_deduccion)
SELECT 900016, 2, 1250, DATE '2026-10-09', 900016, 'SA01', DATE '2026-10-09', 900002, 900001, DATE '2026-10-09', 'Autorizacion ficticia para desarrollo', 900001, NULL, 1750, 500 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Deducciones_autorizadas WHERE id_deduccion_autorizada = 900016);


INSERT INTO Historial_Deducciones (id_Historial_Deduccion, id_deduccion_autorizada, id_estatus, id_administrador, comentario, fecha_registro)
SELECT 9000161, 900016, 2, 'SA01', 'Deduccion autorizada de prueba', DATE '2026-10-09' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM Historial_Deducciones WHERE id_Historial_Deduccion = 9000161);


-- Validaciones antes de confirmar: errores provocan rollback.
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM Solicitudes s
  JOIN Formularios f ON f.idFormulario=s.idFormulario
  WHERE s.idSolicitud BETWEEN 900001 AND 900017 AND s.id_Tipo_Solicitud<>f.id_Tipo_Solicitud;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20970,'Solicitudes con formulario incorrecto'); END IF;
  SELECT COUNT(*) INTO v_count FROM Deducciones_autorizadas d
  WHERE d.id_deduccion_autorizada BETWEEN 900001 AND 900017
    AND d.monto_final <> d.monto_pago-d.monto_deduccion;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20971,'Montos de deduccion inconsistentes'); END IF;
  -- Solo pueden existir los dos tipos de solicitud validos.
  SELECT COUNT(*) INTO v_count FROM Tipo_Solicitudes
  WHERE id_Tipo_Solicitud NOT IN (900001,900002);
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20972,'Tipos de solicitud no permitidos'); END IF;
  SELECT COUNT(*) INTO v_count FROM Solicitudes
  WHERE idSolicitud BETWEEN 900001 AND 900017
    AND id_Tipo_Solicitud NOT IN (900001,900002);
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20973,'Solicitudes con tipo no permitido'); END IF;
  -- Toda solicitud debe tener configurado su monto en la convocatoria.
  SELECT COUNT(*) INTO v_count FROM Solicitudes s
  WHERE s.idSolicitud BETWEEN 900001 AND 900017
    AND NOT EXISTS (
      SELECT 1 FROM Convocatorias_TipoSolicitudes c
      WHERE c.id_convocatoria = s.id_convocatoria
        AND c.id_Tipo_Solicitud = s.id_Tipo_Solicitud);
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20974,'Solicitudes sin configuracion de monto'); END IF;
  -- La deduccion debe coincidir con el monto configurado en la convocatoria.
  SELECT COUNT(*) INTO v_count FROM Deducciones_autorizadas d
  JOIN Convocatorias_TipoSolicitudes c
    ON c.id_convocatoria = d.id_convocatoria
   AND c.id_Tipo_Solicitud = d.id_Tipo_Solicitud
  WHERE d.id_deduccion_autorizada BETWEEN 900001 AND 900017
    AND d.monto_deduccion <> c.monto_deduccion;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20975,'Deducciones no coinciden con el monto configurado'); END IF;
  -- El historial debe conservar el tipo de solicitud vigente.
  SELECT COUNT(*) INTO v_count FROM Historial_Solicitudes h
  JOIN Solicitudes s ON s.idSolicitud = h.id_solicitud
  WHERE h.id_solicitud BETWEEN 900001 AND 900017
    AND h.id_Tipo_solicitud <> s.id_Tipo_Solicitud;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20976,'Historial con tipo de solicitud inconsistente'); END IF;
END;
/
COMMIT;
PROMPT ===== RESUMEN DEL SEED (EXISTENTES + NUEVOS) =====
SELECT 'Estudiantes' entidad, COUNT(*) total FROM Estudiantes WHERE Numero_Control IN
 ('21281181','C21281182','B21281183','BC21281184','21281185','21281186','21281187','21281188','21281189')
UNION ALL SELECT 'Administradores', COUNT(*) FROM Administradores WHERE idAdministrador IN ('SA01','SA02')
UNION ALL SELECT 'Tipos de solicitud', COUNT(*) FROM Tipo_Solicitudes WHERE id_Tipo_Solicitud IN (900001,900002)
UNION ALL SELECT 'Aspirantes', COUNT(*) FROM Aspirantes WHERE ficha LIKE 'F2026%'
UNION ALL SELECT 'Solicitudes', COUNT(*) FROM Solicitudes WHERE idSolicitud BETWEEN 900001 AND 900017
UNION ALL SELECT 'Respuestas', COUNT(*) FROM Respuestas WHERE id_solicitud BETWEEN 900001 AND 900017
UNION ALL SELECT 'Historial solicitudes', COUNT(*) FROM Historial_Solicitudes WHERE id_solicitud BETWEEN 900001 AND 900017
UNION ALL SELECT 'Deducciones', COUNT(*) FROM Deducciones_autorizadas WHERE id_solicitud BETWEEN 900001 AND 900017
UNION ALL SELECT 'Historial deducciones', COUNT(*) FROM Historial_Deducciones WHERE id_deduccion_autorizada BETWEEN 900001 AND 900017;
PROMPT Seed terminado. No se crearon cuentas Oracle.
EXIT SUCCESS

