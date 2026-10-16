# Asignación de equipos — Sistema SAReduccion

**Proyecto:** Sistema de reducción de pagos de inscripción y reinscripción  
**Organización:** Desarrollo por funcionalidades completas (frontend, backend y base de datos)  
**Alcance:** Módulo de solicitantes, módulo administrativo y servicios compartidos

## 1. Distribución general

El proyecto se divide en dos módulos principales: **solicitantes** (estudiantes y aspirantes) y **administración**. Además, cuenta con servicios compartidos de autenticación, notificaciones y gestión de la base de datos.

Cada equipo funcional desarrolla su módulo de extremo a extremo: interfaz, validaciones, endpoints, lógica de negocio y los cambios SQL/PL/SQL que necesite. Estos cambios se desarrollan y prueban en su propia rama. El **equipo 7** revisa, valida e integra las modificaciones definitivas de la base de datos en la versión oficial del proyecto.

| Equipo | Área | Funcionalidad asignada |
| --- | --- | --- |
| **1** | Solicitantes | Panel del estudiante y aspirante: perfil, solicitudes, seguimiento, edición y cancelación. |
| **2** | Administración | Panel administrativo: revisión y resolución de solicitudes, comentarios y deducciones. |
| **3** | Solicitantes | Formularios: preguntas, opciones, captura, validación y almacenamiento de respuestas. |
| **4** | Administración | Gestión de convocatorias: creación, fechas, periodos, año y montos de reducción. |
| **5** | Compartido | Autenticación de estudiantes, aspirantes y administradores; posteriormente, apoyo a la migración Oracle 21c → 12c. |
| **6** | Compartido | Notificaciones por correo sobre eventos y cambios de estado del trámite. |
| **7** | Base de datos | Revisión, validación e integración oficial de estructura, paquetes PL/SQL, permisos y scripts Oracle. |

### Reglas funcionales acordadas

- La cantidad de reducción se configura por convocatoria según la categoría **personal interno** o **personal homologado**. Para asignarla, el administrador no necesita distinguir entre inscripción y reinscripción.
- Se permite **una sola solicitud por solicitante y convocatoria**. Corregir respuestas no debe generar una solicitud adicional.
- El solicitante puede **modificar respuestas** únicamente cuando la solicitud está **RECHAZADA** o **CANCELADA**.
- El estudiante o aspirante puede **cancelar su solicitud junto con el formulario únicamente cuando está PENDIENTE**. Si la solicitud está **AUTORIZADA**, solo un administrador puede cancelarla. Estas restricciones deben validarse en el backend y en la lógica de base de datos, no únicamente en la interfaz.
- No se contempla reutilizar una solicitud anterior para crear otra.
- **Pendiente de definición:** el estado al que regresa una solicitud rechazada o cancelada después de corregir y reenviar sus respuestas.
- La autenticación de aspirantes y las notificaciones pueden utilizar infraestructura común de correo, pero tienen responsables funcionales diferentes.

## 2. Responsabilidades por equipo

### Equipo 1 — Panel del estudiante y aspirante

**Integrantes:**
- Andrea Arroyo Durán
- Nelson Nathaniel Noriega Castillo
- Diego Salazar Reyes
- Juan Pablo Castillo Ángeles
- Cesar Adan de la Cruz Moctezuma

**Responsabilidades:**
- Mostrar los datos de perfil correspondientes al estudiante o aspirante autenticado.
- Consultar convocatorias disponibles y permitir iniciar el trámite correspondiente.
- Mostrar las solicitudes registradas, sus estados, los comentarios administrativos y las deducciones asociadas.
- Permitir modificar las respuestas de una solicitud **RECHAZADA** o **CANCELADA**, reutilizando el mismo registro de solicitud.
- Permitir al estudiante o aspirante cancelar únicamente sus solicitudes **PENDIENTES**. Una solicitud **AUTORIZADA** solo puede ser cancelada por un administrador; la interfaz no debe ofrecer esa acción al solicitante.
- Integrar los paneles con endpoints reales y procedimientos PL/SQL, sustituyendo los datos simulados.
- Desarrollar y probar en su rama los cambios de backend y base de datos que requiera su funcionalidad.

**Entregable:** Paneles de estudiante y aspirante funcionales, con información real de perfil y solicitudes, seguimiento, edición y cancelación conforme a las reglas del sistema.

### Equipo 2 — Panel administrativo y resolución de solicitudes

**Integrantes:**
- Emmanuel Reyes González
- Franco Maximiliano Domínguez Bombela
- Tania Isabel Cortés Guzmán
- Luis Romero Corral
- Brisa Yaremi Hernández Reyes
- Gerardo Pérez Sánchez
- Alan Ruiz Juárez

**Responsabilidades:**
- Listar y filtrar solicitudes por convocatoria, solicitante y estado.
- Consultar datos de cada solicitud y las respuestas del formulario.
- Aprobar o rechazar solicitudes, registrando los comentarios correspondientes.
- Autorizar deducciones y gestionar cancelaciones o cambios de estado conforme a las reglas establecidas. Solo el administrador puede cancelar una solicitud **AUTORIZADA**.
- Consultar el historial de decisiones y cambios relevantes.
- Implementar frontend, endpoints, lógica y procedimientos PL/SQL necesarios en su rama.
- Integrar la información real de Oracle en el panel administrativo.

**Entregable:** Panel administrativo funcional para consultar, revisar y resolver solicitudes y administrar las deducciones autorizadas.

### Equipo 3 — Formularios de solicitud

**Integrantes:**
- Colín Gerardo Vanessa Fernanda
- Gómez Gómez Joana Nataly
- Yadhira Anadanely Benítez Millan
- Pamela Espinoza Montiel
- Sergio Yahir Hernández Guerrero
- Lugo Chavira Ricardo

**Responsabilidades:**
- Mostrar el formulario asociado a la convocatoria y al tipo de solicitud.
- Obtener preguntas y opciones desde la base de datos.
- Implementar preguntas abiertas, de opción única y mixtas, según el modelo existente.
- Validar campos obligatorios y formatos de respuesta.
- Guardar y recuperar respuestas mediante el backend y los procedimientos PL/SQL.
- Habilitar la edición de respuestas solamente cuando la solicitud esté **RECHAZADA** o **CANCELADA**.
- Coordinar la integración del formulario con el panel del equipo 1.
- Desarrollar y probar en su rama los cambios técnicos necesarios.

**Entregable:** Formulario dinámico reutilizable para estudiantes y aspirantes, conectado a Oracle, con registro, consulta y edición controlada de respuestas.

### Equipo 4 — Gestión de convocatorias y montos de reducción

**Integrantes:**
- Diego Andrade Rojas
- Donovann Haziel López Alcántara
- Ana Itzel Santana Aniceto
- Jesús Fidel Vaca Vilchiz
- Alejandro Neri González
- Pablo Reyes Álvarez

**Responsabilidades:**
- Crear, consultar y administrar convocatorias.
- Establecer fechas de inicio y fin, periodo y año.
- Asociar los tipos de solicitud y demás condiciones necesarias para su vigencia.
- Asignar la **cantidad de reducción** para personal **interno** y **homologado** por convocatoria, sin exigir al administrador que diferencie inscripción de reinscripción para esta asignación.
- Validar fechas, importes, relaciones y duplicidades.
- Implementar las interfaces, endpoints y propuestas SQL/PLSQL que sean necesarias para esta funcionalidad.
- Coordinar la disponibilidad de convocatorias con los equipos 1, 2 y 3.

**Entregable:** Módulo administrativo funcional para configurar convocatorias, su vigencia y los montos de reducción por categoría de personal.

### Equipo 5 — Autenticación y gestión de acceso

**Integrantes:**
- Damaris Lizet Álvarez García
- Ramón Castillo Terrón
- Ulises Bertín Terrón Mejía
- Roberto Carlos Acuña Tabaco
- Jesús Fernando López González

**Responsabilidades:**
- Revisar, completar e integrar el inicio de sesión de **estudiante, aspirante y administrador**, aprovechando la implementación existente.
- Mantener sesiones, cierre de sesión, redirecciones por rol y validaciones de acceso en el backend.
- Completar la verificación de identidad de aspirantes mediante correo electrónico y códigos temporales, sustituyendo el mecanismo provisional cuando esté listo.
- Coordinar con el equipo 6 el uso compartido de infraestructura de correo, sin transferirle la responsabilidad de autenticación.
- Realizar pruebas funcionales y de seguridad del acceso para los tres perfiles.
- **Al finalizar sus actividades principales**, apoyar en la adaptación y validación de compatibilidad del sistema y los scripts con **Oracle 12c**, partiendo de la implementación actual en Oracle XE 21c y en coordinación con el equipo 7.

**Entregable:** Autenticación completa de los tres perfiles y, en una segunda etapa, apoyo documentado y probado para la transición de Oracle 21c a Oracle 12c.

### Equipo 6 — Notificaciones por correo

**Integrantes:**
- Nazly Huicochea Morales
- Omar Martínez Rodríguez
- Fabian Molina Villalva
  
**Responsabilidades:**
- Desarrollar el servicio backend para enviar notificaciones por correo.
- Preparar plantillas para cambios de estado, aprobación, rechazo y cancelación de solicitudes o deducciones, según los eventos definidos.
- Integrar los avisos con las operaciones del módulo administrativo y el seguimiento del solicitante.
- Manejar fallos de envío y registrar resultados para diagnóstico.
- Coordinar la configuración técnica de correo con el equipo 5, manteniendo separadas las notificaciones del trámite y los códigos de autenticación.
- Probar los envíos con un entorno de desarrollo o servicio de pruebas, sin depender de datos personales reales.

**Entregable:** Servicio de notificaciones por correo integrado con los eventos del sistema, con plantillas, manejo de errores y pruebas de envío.

### Equipo 7 — Base de datos e integración oficial

**Integrantes:**
- Iñaki Iturriaga Rabanal
- Samuel Alcantara Fernandez
- Brayan Antonio Segundo de Jesus
- Emiliano Arista Rodriguez
- Milton Florencio Arzate
- Jesus Alberto Arroyo Lugo

**Responsabilidades:**
- Mantener la estructura oficial de Oracle, sus relaciones, restricciones, índices, secuencias y triggers.
- Revisar las propuestas SQL/PLSQL que cada equipo desarrolle y pruebe en su propia rama.
- Validar la integridad de los datos, la seguridad, los privilegios y la compatibilidad con los demás módulos.
- Consolidar los cambios aprobados en los scripts oficiales de inicialización y paquetes PL/SQL.
- Resolver incompatibilidades entre propuestas y ejecutar pruebas de integración de la base de datos.
- Mantener documentación, procedimientos de respaldo y recuperación y pruebas de instalación.
- Coordinar con el equipo 5 las pruebas y ajustes de compatibilidad para Oracle 12c.

**Entregable:** Base de datos oficial consistente y validada, con cambios de los módulos integrados, scripts actualizados, permisos controlados y pruebas documentadas.

**Criterio de colaboración:** El equipo 7 **no desarrolla por defecto todos los procedimientos solicitados por los demás equipos**. Cada equipo funcional prepara sus propios cambios de SQL/PLSQL y los prueba en su rama; el equipo 7 los revisa, solicita ajustes cuando proceda y los incorpora a la versión oficial.

## 3. Flujo funcional de integración

1. **Configuración de convocatoria — Equipo 4.** El administrador crea la convocatoria, establece fechas, periodo y año, y define las cantidades de reducción para personal interno y homologado.
2. **Autenticación — Equipo 5.** El estudiante o aspirante inicia sesión y accede a la interfaz correspondiente; los administradores ingresan a su propio módulo.
3. **Panel del solicitante — Equipo 1.** El usuario consulta su perfil, las convocatorias disponibles y sus solicitudes, e inicia el trámite permitido.
4. **Formulario — Equipo 3.** El solicitante responde las preguntas asociadas y se registran sus respuestas y solicitud. Si la solicitud está rechazada o cancelada, puede corregir las respuestas del registro existente.
5. **Revisión administrativa — Equipo 2.** El administrador consulta la solicitud y sus respuestas, registra comentarios y resuelve la aprobación, rechazo o deducción correspondiente.
6. **Notificación — Equipo 6.** Se envían los avisos pertinentes por correo ante los eventos configurados.
7. **Seguimiento — Equipo 1.** El estudiante o aspirante consulta el estado actualizado, los comentarios y las deducciones; puede cancelar una solicitud **PENDIENTE** o corregir respuestas de una solicitud **RECHAZADA** o **CANCELADA**. La cancelación de una solicitud **AUTORIZADA** corresponde exclusivamente al administrador.

**Participación transversal del equipo 7:** Durante todo el flujo, revisa y consolida los cambios oficiales de Oracle propuestos por los equipos funcionales. Cada equipo desarrolla y prueba sus cambios en su propia rama para no depender de que el equipo 7 programe sus operaciones.

**Etapa posterior:** Una vez concluidas las funciones de autenticación, el equipo 5 apoya al equipo 7 en la adaptación y validación de Oracle 21c a Oracle 12c.
