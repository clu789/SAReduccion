# Asignación de equipos — Sistema SAReduccion

**Proyecto:** Sistema de reducción de pagos de inscripción y reinscripción  

**Organización:** Desarrollo por funcionalidades completas (frontend, backend y base de datos)  

**Alcance:** Módulo de solicitantes, módulo administrativo y servicios compartidos  

**Etapa:** Desarrollo e integración de una versión de prueba

## 1. Distribución general

El proyecto se divide en dos módulos principales: **solicitantes** (estudiantes y aspirantes) y **administración**. Además, cuenta con servicios compartidos de autenticación, seguridad, notificaciones y gestión de la base de datos.

Cada equipo funcional desarrolla su módulo de extremo a extremo: interfaz, validaciones, endpoints e integración con la lógica de negocio existente. Los paquetes PL/SQL, restricciones y scripts Oracle oficiales constituyen la base común del proyecto. Si un equipo necesita modificaciones, debe justificarlas, desarrollarlas y probarlas en su propia rama; el **equipo 7** revisa, valida e integra los cambios aprobados en la versión oficial.

| Equipo | Área | Funcionalidad asignada |
| --- | --- | --- |
| **1** | Compartido | Autenticación de estudiantes, aspirantes y administradores; seguridad, sesiones, navegación compartida y lineamientos de logs. |
| **2** | Administración | Panel administrativo: revisión y resolución de solicitudes, comentarios y deducciones. |
| **3** | Solicitantes | Panel adaptable a teléfonos y computadoras: perfil, solicitudes, seguimiento, retiro y reenvío. |
| **4** | Administración | Gestión de convocatorias: creación, fechas, periodos, año y montos de reducción. |
| **5** | Solicitantes | Formularios: preguntas, opciones, captura, validación y almacenamiento de respuestas. |
| **6** | Compartido | Notificaciones por correo sobre eventos y cambios de estado del trámite. |
| **7** | Base de datos | Revisión, validación e integración oficial de estructura, paquetes PL/SQL, permisos y scripts Oracle. |

### Reglas funcionales acordadas

- La cantidad de reducción se configura por convocatoria y tipo de solicitud: **Hijo de trabajador** o **Hijo de homologado**. El administrador no necesita seleccionar por separado inscripción o reinscripción para definir esta reducción; el cálculo de la deducción utiliza el monto base que corresponda.
- Cada convocatoria contempla cuatro fechas: **inicio de solicitudes, fin de solicitudes, límite de autorización y límite de aplicación**. Deben respetar ese orden y no superponerse con el ciclo completo de otra convocatoria. Los límites se consideran por día calendario.
- Se permite **una sola solicitud por solicitante y convocatoria**. Corregir y reenviar no crea otra solicitud: conserva el mismo registro y sustituye las respuestas confirmadas.
- El solicitante envía la solicitud y sus respuestas completas en una sola operación. No se contempla guardar borradores. Las preguntas obligatorias deben responderse; las preguntas mixtas exigen una especificación cuando se elige la opción que la requiere.
- Una solicitud **PENDIENTE** no puede editarse directamente. El solicitante puede **retirarla**, pasando a **RETIRADO**, mientras siga abierto el periodo de recepción. Después puede reenviarla, con o sin cambios, dentro del mismo plazo.
- Las solicitudes **RECHAZADO**, **RETIRADO** o **CANCELADO** pueden reenviarse a **PENDIENTE** dentro del periodo de recepción. Se conserva la misma solicitud y se reemplazan el tipo y las respuestas al confirmar el reenvío.
- El administrador puede aprobar o rechazar solicitudes **PENDIENTE** hasta el límite de autorización. También puede cancelar una deducción autorizada, registrando el motivo y actualizando conjuntamente la solicitud y la deducción a **CANCELADO**.
- Una solicitud **CANCELADO** puede ser reautorizada directamente por el administrador, dentro del plazo de autorización, o reenviada por el solicitante dentro del plazo de recepción. En este último caso, la deducción cancelada permanece como tal hasta una nueva aprobación.
- Las deducciones **AUTORIZADO** pueden cancelarse o aplicarse hasta el límite de aplicación. Al aplicar se recalculan los importes oficiales vigentes; **APLICADO** es un estado terminal de solicitud y deducción.
- Los movimientos relevantes generan historiales mediante los procedimientos de Oracle. Los registros históricos no se editan y las operaciones administrativas deben quedar asociadas al administrador responsable.
- La identificación de aspirantes utiliza ficha, CURP y el **periodo y año académico de la convocatoria**, no solamente el año calendario. La misma ficha y CURP pueden existir en periodos académicos diferentes.
- La autenticación de aspirantes y las notificaciones pueden utilizar infraestructura común de correo, pero tienen responsables funcionales diferentes.
- Los controles de autenticación y autorización se aplican en el servidor. Ocultar botones o secciones en la interfaz no sustituye las validaciones del backend ni de Oracle.
- Cada equipo integra logs técnicos de sus operaciones de acuerdo con el estándar común definido por el equipo 1, sin registrar contraseñas, tokens, CURP completos u otros datos sensibles innecesarios.
- Las pantallas de **solicitantes** deben funcionar correctamente en teléfonos móviles y computadoras. Se priorizarán soluciones sencillas compatibles con el frontend existente y Spring Boot, evitando incorporar tecnologías adicionales sin necesidad.

## 2. Responsabilidades por equipo

### Equipo 1 — Autenticación y seguridad del sistema

**Integrantes:**

- Andrea Arroyo Durán
- Nelson Nathaniel Noriega Castillo
- Diego Salazar Reyes
- Juan Pablo Castillo Ángeles
- Cesar Adan de la Cruz Moctezuma

**Responsabilidades:**

- Revisar, completar e integrar el inicio de sesión de **estudiante, aspirante y administrador**, aprovechando la implementación existente. El equipo 1 es responsable tanto de la autenticación como de la interfaz de acceso administrativo.
- Encargarse de la navegación compartida entre las vistas de la aplicación y, cuando resulte necesario, de un menú desplegable para acceder a ellas. El equipo definirá la solución más sencilla y adecuada, coordinándose con los responsables de cada pantalla.
- Mantener la validación de sesión en cada petición protegida, el cierre de sesión, la expiración por inactividad y las redirecciones por rol. Aprovechar la sesión existente y su identificador seguro; no agregar un segundo sistema de tokens sin una necesidad justificada.
- Incorporar **captcha en los tres inicios de sesión**, con una alternativa accesible cuando corresponda.
- Completar la verificación de identidad de aspirantes mediante correo electrónico y códigos temporales, en coordinación con el equipo 6.
- Extender el **rate limiting** a los accesos de estudiante y administrador, además del aspirante; incluir controles temporales por usuario y por origen cuando sea viable, sin crear un módulo de desbloqueo administrativo.
- Implementar un **interceptor o mecanismo central de autorización por rol** para proteger los endpoints de negocio. Cada equipo funcional conserva la responsabilidad de validar las reglas específicas de acceso a sus propios datos y operaciones.
- Extender la **protección CSRF** a las operaciones de negocio que modifican información (`POST`, `PUT`, `PATCH` y `DELETE`, según corresponda). Proporcionar el mecanismo común para que los demás equipos lo utilicen.
- Implementar la **auditoría de intentos de acceso y eventos relevantes de sesión en Oracle**, desarrollando y probando los cambios SQL/PLSQL necesarios en su rama para revisión del equipo 7.
- Definir el **estándar común de logs técnicos**: formato, niveles, identificador de correlación, manejo de errores, enmascaramiento de datos y criterios razonables de rotación y retención. Configurar la infraestructura compartida y comunicar el estándar a los demás equipos; cada equipo instrumenta su módulo.
- Configurar correctamente la **IP real del cliente detrás de Nginx**, confiando únicamente en cabeceras reenviadas por el proxy autorizado.
- Revisar la configuración de **cookies y cabeceras de seguridad** para el despliegue correspondiente, incluyendo `HttpOnly`, `SameSite`, `Secure` bajo HTTPS y las políticas aplicables de CSP/HSTS.
- Definir una respuesta uniforme ante **sesión expirada o acceso no autorizado** (`401`/`403`) y un mecanismo reutilizable para que cada equipo implemente el aviso y la redirección en sus pantallas.
- Realizar pruebas de seguridad de los accesos: intentos repetidos, captcha, fijación de sesión, expiración, autorización por rol y CSRF. Corregir fallos detectados en el mecanismo común.

**Entregable:** Autenticación funcional de los tres perfiles, controles de seguridad compartidos y documentados, auditoría de accesos, configuración segura de sesiones y lineamientos de logs que los demás equipos puedan integrar.

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
- Autorizar deducciones, cancelar solicitudes y deducciones autorizadas con motivo, y reautorizar cancelaciones cuando corresponda, respetando las fechas límite y los estados permitidos.
- Consultar el historial de decisiones y cambios relevantes.
- Integrar la aplicación de deducciones mediante el procedimiento existente, con recálculo de montos oficiales vigentes y estado terminal **APLICADO**. Coordinar con el proceso institucional de comprobantes o pagos cuando se integre, sin dar por implementada esa integración.
- Implementar frontend, endpoints, lógica y procedimientos PL/SQL necesarios en su rama.
- Integrar la información real de Oracle en el panel administrativo.
- Aplicar las restricciones por rol y los mecanismos compartidos de sesión y CSRF definidos por el equipo 1; validar permisos y transiciones de estado también en el backend.
- Registrar logs técnicos de revisiones, decisiones, cambios de estado y errores, sin exponer respuestas sensibles ni datos personales innecesarios.
- Documentar sus endpoints y probar las operaciones autorizadas y no autorizadas.

**Entregable:** Panel administrativo funcional para consultar, revisar y resolver solicitudes y administrar las deducciones autorizadas, con controles de acceso, registros técnicos y pruebas.

### Equipo 3 — Panel del estudiante y aspirante

**Integrantes:**

- Colín Gerardo Vanessa Fernanda
- Gómez Gómez Joana Nataly
- Yadhira Anadanely Benítez Millan
- Pamela Espinoza Montiel
- Sergio Yahir Hernández Guerrero
- Lugo Chavira Ricardo

**Responsabilidades:**

- Mostrar los datos de perfil correspondientes al estudiante o aspirante autenticado.
- Consultar convocatorias disponibles y permitir iniciar el trámite correspondiente.
- Mostrar las solicitudes registradas, sus estados, los comentarios administrativos y las deducciones asociadas.
- Permitir reenviar solicitudes **RETIRADO**, **RECHAZADO** o **CANCELADO** dentro del periodo de recepción, conservando el mismo registro y coordinando la actualización del tipo y las respuestas con el equipo 5.
- Permitir al estudiante o aspirante **retirar** únicamente sus solicitudes **PENDIENTE**, mientras siga abierto el periodo de recepción. Una solicitud **AUTORIZADO** solo puede ser cancelada por un administrador; la interfaz no debe ofrecer esa acción al solicitante.
- Integrar los paneles con endpoints reales y procedimientos PL/SQL, sustituyendo los datos simulados.
- Adaptar las vistas del solicitante para teléfonos móviles y computadoras, cuidando la navegación, los formularios y la visualización del seguimiento. Coordinar la navegación compartida con el equipo 1.
- Desarrollar y probar en su rama los cambios de backend y base de datos que requiera su funcionalidad.
- Implementar en sus pantallas el manejo de **sesión expirada**, avisos de inactividad y respuestas `401`/`403`, de acuerdo con el mecanismo común del equipo 1.
- Validar en el backend que cada solicitante solamente pueda consultar o modificar sus propios datos y solicitudes; integrar CSRF en las operaciones de cambio.
- Registrar logs técnicos de consultas relevantes, edición, cancelación y errores, siguiendo el estándar común, y documentar sus endpoints.

**Entregable:** Paneles de estudiante y aspirante funcionales, con información real de perfil y solicitudes, seguimiento, retiro y reenvío conforme a las reglas del sistema, con interfaces adaptables a teléfonos móviles, y manejo adecuado de sesión expirada.

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
- Establecer las cuatro fechas de la convocatoria: inicio y fin de solicitudes, límite de autorización y límite de aplicación, además del periodo y año académico.
- Asociar los tipos de solicitud y demás condiciones necesarias para su vigencia.
- Asignar la **cantidad de reducción** para personal **interno** y **homologado** por convocatoria, sin exigir al administrador que diferencie inscripción de reinscripción para esta asignación.
- Validar el orden de las fechas, la ausencia de convocatorias superpuestas, los importes y las relaciones. No permitir cambios una vez superado el límite de aplicación.
- Permitir modificar fechas mientras corresponda, registrando las anteriores y nuevas en el historial de convocatorias; permitir actualizar los montos oficiales hasta el límite de autorización, sin alterar retroactivamente las deducciones ya autorizadas.
- Implementar las interfaces, endpoints y propuestas SQL/PLSQL que sean necesarias para esta funcionalidad.
- Coordinar la disponibilidad de convocatorias con los equipos 2, 3 y 5.
- Restringir las operaciones administrativas a usuarios autorizados y aplicar los controles comunes de sesión y CSRF.
- Registrar logs técnicos de creación, modificación y errores de convocatorias y montos; documentar endpoints y probar las validaciones del módulo.

**Entregable:** Módulo administrativo funcional para configurar convocatorias, su vigencia y los montos de reducción por categoría de personal, con validaciones y registros técnicos.

### Equipo 5 — Formularios de solicitud

**Integrantes:**

- Damaris Lizet Álvarez García
- Ramón Castillo Terrón
- Ulises Bertín Terrón Mejía
- Roberto Carlos Acuña Tabaco
- Jesús Fernando López González

**Responsabilidades:**

- Mostrar el formulario asociado al tipo de solicitud permitido por la convocatoria, incluyendo sus instrucciones y los requisitos documentales indicados.
- Obtener preguntas y opciones desde la base de datos.
- Implementar preguntas **ABIERTA**, **MÚLTIPLE** (una sola opción) y **MIXTA** (una sola opción y especificación adicional cuando la opción seleccionada la requiera), según el modelo existente.
- Validar campos obligatorios y formatos de respuesta.
- Guardar y recuperar respuestas mediante el backend y los procedimientos PL/SQL. El envío inicial de solicitud y respuestas debe ser completo y atómico, sin borradores.
- Permitir preparar y confirmar el reenvío de solicitudes **RETIRADO**, **RECHAZADO** o **CANCELADO** dentro del plazo de recepción. La actualización de tipo y respuestas se realiza al reenviar, sin crear otra solicitud; una solicitud **PENDIENTE** no se edita directamente.
- Coordinar la integración del formulario con el panel del equipo 3.
- Desarrollar y probar en su rama los cambios técnicos necesarios.
- Verificar en el backend la identidad del solicitante, la pertenencia de la solicitud y el estado permitido antes de registrar o modificar respuestas.
- Integrar los mecanismos comunes de sesión, autorización y CSRF; gestionar respuestas `401`/`403` en las pantallas del formulario.
- Registrar logs técnicos de creación, envío, edición y errores del formulario, sin guardar las respuestas sensibles en los logs; documentar endpoints y probar sus validaciones.

**Entregable:** Formulario dinámico reutilizable para estudiantes y aspirantes, conectado a Oracle, con registro, consulta y edición controlada de respuestas y validaciones de seguridad.

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
- Coordinar la configuración técnica de correo con el **equipo 1**, manteniendo separadas las notificaciones del trámite y los códigos de autenticación. El equipo 1 conserva la responsabilidad de verificar la identidad de aspirantes.
- Probar los envíos con un entorno de desarrollo o servicio de pruebas, sin depender de datos personales reales.
- Aplicar las reglas de acceso que correspondan a sus endpoints, evitar exponer códigos de verificación o credenciales en logs y documentar las interfaces del servicio.

**Entregable:** Servicio de notificaciones por correo integrado con los eventos del sistema, con plantillas, manejo de errores, logs técnicos y pruebas de envío.

### Equipo 7 — Base de datos e integración oficial

**Integrantes:**

- Iñaki Iturriaga Rabanal
- Samuel Alcantara Fernandez
- Brayan Antonio Segundo de Jesus
- Emiliano Arista Rodriguez
- Milton Florencio Arzate
- Jesus Alberto Arroyo Lugo

**Responsabilidades:**

- Mantener la estructura oficial de Oracle, sus relaciones, restricciones, índices, secuencias y triggers, así como los paquetes existentes de estudiantes, aspirantes, administradores y operaciones internas de solicitudes.
- Revisar las propuestas SQL/PLSQL que cada equipo desarrolle y pruebe en su propia rama.
- Validar la integridad de los datos, la seguridad, los privilegios y la compatibilidad con los demás módulos.
- Consolidar los cambios aprobados en los scripts oficiales de inicialización y paquetes PL/SQL.
- Resolver incompatibilidades entre propuestas y ejecutar pruebas de integración de la base de datos.
- Mantener documentación, procedimientos de respaldo y recuperación y pruebas de instalación.
- Conservar separados el seed de desarrollo con datos ficticios y las configuraciones institucionales reales. Revisar las pruebas de reglas de negocio y que la inicialización falle de forma visible cuando Oracle reporte errores.
- **Revisar que los logs y auditorías de los equipos no expongan datos personales, contraseñas, credenciales ni tokens.** Cada equipo conserva la responsabilidad de corregir sus propios registros; el equipo 7 revisa este aspecto durante la integración.
- Revisar e integrar las estructuras de auditoría Oracle que proponga y pruebe el equipo 1, siguiendo el mismo procedimiento que para los demás cambios de base de datos.

**Entregable:** Base de datos oficial consistente y validada, con cambios de los módulos integrados, scripts actualizados, permisos controlados, revisión de privacidad en logs y pruebas documentadas.

**Criterio de colaboración:** El equipo 7 **no desarrolla por defecto todos los procedimientos solicitados por los demás equipos**. Cada equipo funcional prepara sus propios cambios de SQL/PLSQL y los prueba en su rama; el equipo 7 los revisa, solicita ajustes cuando proceda y los incorpora a la versión oficial.

## 3. Acuerdos compartidos de seguridad e integración

- **Autenticación y seguridad central:** el equipo 1 mantiene el mecanismo común de inicio de sesión, sesiones, captcha, rate limiting, autorización por rol, CSRF y auditoría de accesos.
- **Autorización de negocio:** cada equipo verifica en su backend los permisos, la pertenencia de registros y las transiciones de estado correspondientes a su módulo.
- **Sesión expirada:** el equipo 1 define las respuestas y el mecanismo común; los equipos con interfaces implementan los avisos, redirecciones y manejo de `401`/`403` en sus propias pantallas.
- **Navegación compartida:** el equipo 1 coordina el acceso entre vistas y, si hace falta, el menú desplegable. Cada equipo funcional integra sus propias pantallas sin duplicar la navegación común.
- **Adaptación a móviles:** los equipos 3 y 5 aseguran que los paneles y formularios de solicitantes funcionen en teléfonos y computadoras, con una implementación sencilla y compatible con el proyecto Spring Boot.
- **Logs:** el equipo 1 define el estándar técnico y la configuración compartida; **todos los equipos** agregan los logs de sus funcionalidades. El equipo 7 revisa que los registros y auditorías no expongan información sensible.
- **Datos personales:** usar solamente la información necesaria para cada operación, evitar exponer CURP y otros datos personales en logs, y establecer reglas básicas de acceso y tratamiento de datos. Las pruebas deben usar preferentemente información ficticia.
- **Pruebas:** cada equipo verifica el funcionamiento normal, los errores y los accesos no autorizados de sus endpoints. El equipo 1 prueba los mecanismos compartidos de seguridad.
- **Contratos de API:** cada equipo documenta sus endpoints, parámetros, respuestas y códigos HTTP. Puede hacerlo en Markdown; **OpenAPI/Swagger no es obligatorio** para esta etapa.
- **SQL/PLSQL:** se reutilizan primero los procedimientos, triggers y restricciones ya existentes. Cada equipo desarrolla y prueba únicamente los cambios que necesite; el equipo 7 los valida e integra en los scripts oficiales.
- **Integración entre equipos:** acordar los contratos de los endpoints y eventos antes de depender de la implementación final de otro módulo, para reducir bloqueos.

### Actividades fuera del alcance obligatorio actual

No se asignan como funcionalidades de esta etapa: cambio, recuperación o desbloqueo de contraseñas de cuentas Oracle; gestión avanzada de sesiones concurrentes o cierre de todas las sesiones; revalidación periódica del perfil Oracle durante sesiones ya iniciadas; WAF avanzado en Nginx; almacén compartido de sesiones para múltiples instancias del backend; ni un panel de desbloqueo administrativo de usuarios. Se podrán reconsiderar si las condiciones de despliegue lo requieren.

**No se incluye la migración de Oracle XE 21c a Oracle 12c como responsabilidad de ningún equipo.** El desarrollo actual corresponde a una versión de prueba. Cuando se entreguen los objetos oficiales, se evaluarán por separado su compatibilidad, integración y permisos en el entorno de destino.

## 4. Flujo funcional de integración

1. **Configuración de convocatoria — Equipo 4.** El administrador crea la convocatoria, establece las cuatro fechas, el periodo y año académico, y define las cantidades de reducción para hijos de trabajadores y homologados.

2. **Autenticación — Equipo 1.** El estudiante o aspirante inicia sesión y accede a la interfaz correspondiente; los administradores ingresan a su propio módulo. El equipo 1 proporciona también la navegación compartida y el menú desplegable cuando sea necesario.

3. **Panel del solicitante — Equipo 3.** El usuario consulta su perfil, las convocatorias disponibles y sus solicitudes, e inicia el trámite permitido. Las vistas deben poder utilizarse desde teléfonos y computadoras.

4. **Formulario — Equipo 5.** El solicitante responde las preguntas del tipo de solicitud elegido y envía conjuntamente la solicitud y todas las respuestas obligatorias. No se guardan borradores. Si la solicitud está **RETIRADO**, **RECHAZADO** o **CANCELADO**, puede corregir y reenviar el mismo registro dentro del periodo de recepción.

5. **Revisión administrativa — Equipo 2.** El administrador consulta la solicitud y sus respuestas, aprueba o rechaza dentro del plazo, y administra las deducciones. Puede cancelar una autorización con motivo, reautorizar una cancelación cuando corresponda y aplicar la deducción mediante el procedimiento existente.

6. **Notificación — Equipo 6.** Se envían los avisos pertinentes por correo ante los eventos configurados, sin confundir los avisos del trámite con los códigos de verificación de identidad.

7. **Seguimiento — Equipo 3.** El estudiante o aspirante consulta el estado actualizado, los comentarios y las deducciones. Puede retirar una solicitud **PENDIENTE** durante la recepción y reenviar solicitudes **RETIRADO**, **RECHAZADO** o **CANCELADO** dentro de ese mismo plazo. La cancelación de una solicitud autorizada corresponde al administrador.

**Participación transversal del equipo 1:** Proporciona autenticación, navegación compartida, controles de seguridad y lineamientos de logs para que los demás equipos los integren sin transferirle sus reglas particulares de negocio.

**Participación transversal del equipo 7:** Durante todo el flujo, revisa y consolida los cambios oficiales de Oracle propuestos por los equipos funcionales. Los paquetes y validaciones existentes deben reutilizarse antes de proponer modificaciones.

**Pendiente de integración:** Coordinar con el sistema institucional el momento de aplicación de una deducción y la generación del comprobante correspondiente. La base de datos ya contempla el procedimiento de aplicación; esta asignación no supone que la integración institucional esté terminada.
