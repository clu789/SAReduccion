const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const nodemailer = require('nodemailer');

const plantilla = fs.readFileSync(
  path.join(__dirname, '../../src/main/resources/mail/aviso-tramite.html'), 'utf8'
);
const eventos = {
  Autorizado: 'Tu solicitud fue aprobada.',
  Rechazado: 'Tu solicitud fue rechazada.',
  Aplicado: 'La deducción de tu solicitud fue aplicada.',
  Cancelado: 'La deducción de tu solicitud fue cancelada.',
};

class ErrorDemo extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function escapar(valor) {
  return String(valor).replace(/[&<>"']/g, caracter => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[caracter]);
}

function emailValido(valor) {
  return typeof valor === 'string' && /^[^\s<>@,;\r\n]+@[^\s<>@,;\r\n]+\.[^\s<>@,;\r\n]+$/.test(valor);
}

function configuracion(env) {
  const destinatarios = (env.DEMO_RECIPIENTS || '').split(',').map(valor => valor.trim().toLowerCase()).filter(Boolean);
  const from = (env.SAR_MAIL_FROM || '').trim();
  const puerto = Number(env.SPRING_MAIL_PORT || 587);
  const secure = env.SMTP_SSL_ENABLE === 'true';
  const starttls = env.SMTP_STARTTLS_ENABLE !== 'false';
  if (!env.SPRING_MAIL_USERNAME || !env.SPRING_MAIL_PASSWORD || !emailValido(from)
      || !env.DEMO_ACCESS_CODE || !destinatarios.length || !destinatarios.every(emailValido)
      || !Number.isInteger(puerto) || puerto < 1 || puerto > 65535 || secure === starttls) {
    throw new ErrorDemo(503, 'Completa la conexión de Brevo en las variables del despliegue.');
  }
  return {
    from,
    destinatarios,
    codigo: env.DEMO_ACCESS_CODE,
    transport: {
      host: env.SPRING_MAIL_HOST || 'smtp-relay.brevo.com',
      port: puerto,
      secure,
      requireTLS: starttls,
      auth: { user: env.SPRING_MAIL_USERNAME, pass: env.SPRING_MAIL_PASSWORD },
      connectionTimeout: 5000,
      greetingTimeout: 5000,
      socketTimeout: 10000,
      tls: { rejectUnauthorized: true, minVersion: 'TLSv1.2' },
    },
  };
}

function autorizado(recibido, esperado) {
  const hash = valor => crypto.createHash('sha256').update(String(valor || '')).digest();
  return crypto.timingSafeEqual(hash(recibido), hash(esperado));
}

function prepararMensaje(datos, config) {
  const evento = datos.evento;
  const solicitud = String(datos.solicitud || '').trim();
  const destinatario = String(datos.destinatario || '').trim().toLowerCase();
  const comentario = String(datos.comentario || '').trim();
  if (!Object.hasOwn(eventos, evento) || !/^[1-9]\d{0,9}$/.test(solicitud)
      || !emailValido(destinatario) || comentario.length > 2000) {
    throw new ErrorDemo(400, 'Revisa el estado, el folio, el destinatario y el comentario.');
  }
  if (evento === 'Rechazado' && !comentario) {
    throw new ErrorDemo(400, 'Indica el motivo del rechazo.');
  }
  if (!config.destinatarios.includes(destinatario)) {
    throw new ErrorDemo(403, 'Usa un destinatario habilitado para esta demostración.');
  }
  const bloque = comentario ? "<div style='padding:18px;background:#f2f6fa;border-left:4px solid #315e8c'>"
    + "<b>Comentario del administrador</b><p style='line-height:1.6'>"
    + escapar(comentario).replace(/\r?\n/g, '<br>') + '</p></div>' : '';
  const valores = {
    solicitud,
    estado: evento,
    color: evento === 'Rechazado' ? '#a32f35' : '#17665e',
    mensaje: eventos[evento],
    comentario: bloque,
  };
  const html = plantilla.replace(/\{\{(solicitud|estado|color|mensaje|comentario)\}\}/g,
    (_, clave) => valores[clave]);
  return {
    from: config.from,
    to: destinatario,
    subject: 'SAReducción | ' + evento + ' | Solicitud ' + solicitud,
    html,
    text: eventos[evento] + (comentario ? '\n\nComentario del administrador\n' + comentario : '')
      + '\n\nConsulta el estado y los detalles en el portal de SAReduccion.',
  };
}

async function procesarEnvio(datos, codigo, env, crearTransporte = nodemailer.createTransport) {
  const config = configuracion(env);
  if (!autorizado(codigo, config.codigo)) {
    throw new ErrorDemo(401, 'La clave de demostración es incorrecta.');
  }
  const mensaje = prepararMensaje(datos, config);
  const transporte = crearTransporte(config.transport);
  try {
    const resultado = await transporte.sendMail(mensaje);
    if (!resultado.accepted || !resultado.accepted.length) {
      throw new ErrorDemo(503, 'Brevo no aceptó el destinatario. Revisa el registro del proveedor.');
    }
    return { message: 'Brevo aceptó el mensaje. Revisa el buzón del destinatario.', messageId: resultado.messageId };
  } catch (error) {
    if (error instanceof ErrorDemo) throw error;
    throw new ErrorDemo(503, 'No se pudo completar el envío. Revisa las credenciales, el remitente y la conexión SMTP.');
  } finally {
    transporte.close();
  }
}

module.exports = { configuracion, prepararMensaje, procesarEnvio, ErrorDemo, eventos };
