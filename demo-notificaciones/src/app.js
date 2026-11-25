'use strict';

const form = document.getElementById('send-form');
const agregar = document.getElementById('add-comment');
const comentario = document.getElementById('comentario');
const estadoConexion = document.getElementById('connection');
const resultado = document.getElementById('send-status');
const boton = document.getElementById('send-button');
const textos = {
  Autorizado: 'Tu solicitud fue aprobada.',
  Rechazado: 'Tu solicitud fue rechazada.',
  Aplicado: 'La deducción de tu solicitud fue aplicada.',
  Cancelado: 'La deducción de tu solicitud fue cancelada.',
};
let plantilla = '';
let configurado = false;
let enviando = false;

function escapar(valor) {
  return valor.replace(/[&<>"']/g, caracter => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[caracter]);
}

function actualizar() {
  if (!enviando) {
    resultado.textContent = '';
    resultado.dataset.state = '';
  }
  const evento = form.elements.evento.value;
  const motivoObligatorio = evento === 'Rechazado';
  if (motivoObligatorio) agregar.checked = true;
  agregar.disabled = motivoObligatorio;
  comentario.required = motivoObligatorio;
  document.getElementById('comment-field').hidden = !agregar.checked;
  document.getElementById('comment-hint').textContent = motivoObligatorio
    ? 'El motivo del rechazo es obligatorio.' : 'Se incluirá dentro del correo.';
  const observacion = agregar.checked ? comentario.value.trim() : '';
  const bloque = observacion ? "<div style='padding:18px;background:#f2f6fa;border-left:4px solid #315e8c'>"
    + "<b>Comentario del administrador</b><p style='line-height:1.6'>"
    + escapar(observacion).replace(/\r?\n/g, '<br>') + '</p></div>' : '';
  const valores = {
    solicitud: escapar(form.elements.solicitud.value || '…'),
    estado: evento,
    color: motivoObligatorio ? '#a32f35' : '#17665e',
    mensaje: textos[evento],
    comentario: bloque,
  };
  document.getElementById('email-preview').srcdoc = plantilla.replace(
    /\{\{(solicitud|estado|color|mensaje|comentario)\}\}/g, (_, clave) => valores[clave]);
  document.getElementById('subject').textContent =
    'SAReducción | ' + evento + ' | Solicitud ' + (form.elements.solicitud.value || '…');
  const etiqueta = document.getElementById('preview-state');
  etiqueta.textContent = evento;
  etiqueta.classList.toggle('rejected', motivoObligatorio);
}

form.addEventListener('input', actualizar);
form.addEventListener('change', () => {
  if (!agregar.checked) comentario.value = '';
  actualizar();
});
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (enviando || !configurado || !form.reportValidity()) return;
  enviando = true;
  boton.disabled = true;
  boton.textContent = 'Enviando con Brevo…';
  resultado.dataset.state = '';
  resultado.textContent = 'Preparando el correo y conectando con el servidor.';
  try {
    const response = await fetch('/api/notificaciones', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Demo-Code': document.getElementById('demo-code').value,
      },
      body: JSON.stringify({
        evento: form.elements.evento.value,
        solicitud: form.elements.solicitud.value,
        destinatario: form.elements.destinatario.value,
        comentario: agregar.checked ? comentario.value : '',
      }),
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'El envío no se completó.');
    resultado.dataset.state = 'success';
    resultado.textContent = data.message;
  } catch (error) {
    resultado.dataset.state = 'error';
    resultado.textContent = error.message || 'Revisa la conexión e intenta de nuevo.';
  } finally {
    enviando = false;
    boton.disabled = !configurado;
    boton.textContent = 'Enviar correo de prueba';
  }
});

async function iniciar() {
  try {
    const [templateResponse, stateResponse] = await Promise.all([
      fetch('/plantilla.html'), fetch('/api/notificaciones'),
    ]);
    if (!templateResponse.ok || !stateResponse.ok) throw new Error('carga');
    plantilla = await templateResponse.text();
    const estado = await stateResponse.json();
    configurado = estado.configurado;
    estadoConexion.textContent = configurado ? 'Brevo · configuración cargada'
      : 'Falta conectar Brevo en el despliegue';
    estadoConexion.classList.toggle('ready', configurado);
    boton.disabled = !configurado;
    actualizar();
  } catch {
    estadoConexion.textContent = 'No se pudo cargar la demo. Recarga la página.';
  }
}
iniciar();
