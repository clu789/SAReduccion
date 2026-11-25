const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { configuracion, prepararMensaje, procesarEnvio, eventos } = require('../lib/correo.cjs');

const env = {
  SPRING_MAIL_USERNAME: 'usuario-prueba',
  SPRING_MAIL_PASSWORD: 'credencial-de-prueba',
  SAR_MAIL_FROM: 'avisos@example.test',
  DEMO_ACCESS_CODE: 'acceso-de-prueba',
  DEMO_RECIPIENTS: 'equipo@example.test',
};
const base = { evento: 'Autorizado', solicitud: '42', destinatario: 'equipo@example.test', comentario: '' };

test('reutiliza los eventos de Spring y la plantilla compartida para los cuatro estados', () => {
  const java = fs.readFileSync(path.join(__dirname,
    '../../src/main/java/com/itt/sareduccion/service/NotificacionService.java'), 'utf8');
  const config = configuracion(env);
  for (const [evento, texto] of Object.entries(eventos)) {
    assert.ok(java.includes(texto), 'Mensaje distinto del backend Java');
    const message = prepararMensaje({ ...base, evento, comentario: '<script>alert(1)</script>\nRevisar' }, config);
    assert.ok(message.html.includes('&lt;script&gt;'));
    assert.ok(!message.html.includes('<script>'));
    assert.ok(message.html.includes('<br>'));
    assert.ok(!message.html.includes('{{'));
    assert.ok(message.subject.includes(evento));
    assert.ok(message.text.includes(texto));
  }
});

test('omite comentarios opcionales y exige el motivo del rechazo', () => {
  const config = configuracion(env);
  assert.ok(!prepararMensaje(base, config).html.includes('Comentario del administrador'));
  assert.throws(() => prepararMensaje({ ...base, evento: 'Rechazado' }, config), { status: 400 });
});

test('bloquea destinatarios no autorizados e inyección de cabeceras', () => {
  const config = configuracion(env);
  assert.throws(() => prepararMensaje({ ...base, destinatario: 'externo@example.test' }, config), { status: 403 });
  assert.throws(() => prepararMensaje({ ...base, destinatario: 'equipo@example.test\r\nBcc:otro@example.test' }, config), { status: 400 });
});

test('exige configuración y una clave válida antes de contactar SMTP', async () => {
  assert.throws(() => configuracion({}), { status: 503 });
  assert.throws(() => configuracion({ ...env, SMTP_SSL_ENABLE: 'true' }), { status: 503 });
  assert.equal(configuracion({ ...env, SMTP_SSL_ENABLE: 'true', SMTP_STARTTLS_ENABLE: 'false',
    SPRING_MAIL_PORT: '465' }).transport.secure, true);
  let conexiones = 0;
  await assert.rejects(procesarEnvio(base, 'incorrecto', env, () => { conexiones++; }), { status: 401 });
  assert.equal(conexiones, 0);
});

test('devuelve aceptación SMTP y cierra el transporte sin revelar credenciales', async () => {
  let closed = false;
  const result = await procesarEnvio(base, env.DEMO_ACCESS_CODE, env, () => ({
    async sendMail(mail) {
      assert.equal(mail.to, base.destinatario);
      return { accepted: [mail.to], messageId: 'prueba-smtp' };
    },
    close() { closed = true; },
  }));
  assert.equal(closed, true);
  assert.equal(result.messageId, 'prueba-smtp');
  assert.ok(!JSON.stringify(result).includes(env.SPRING_MAIL_PASSWORD));
});

test('convierte un fallo SMTP en error de servicio y cierra la conexión', async () => {
  let closed = false;
  await assert.rejects(procesarEnvio(base, env.DEMO_ACCESS_CODE, env, () => ({
    async sendMail() { throw new Error('password=valor-que-no-debe-salir'); },
    close() { closed = true; },
  })), error => error.status === 503 && !error.message.includes('valor-que-no-debe-salir'));
  assert.equal(closed, true);
});
