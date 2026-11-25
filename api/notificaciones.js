const { configuracion, procesarEnvio, ErrorDemo } = require('../demo-notificaciones/lib/correo.cjs');

module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'GET') {
    let conectado = true;
    try { configuracion(process.env); } catch { conectado = false; }
    return res.status(200).json({ configurado: conectado });
  }
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'GET, POST');
    return res.status(405).json({ message: 'Método no disponible.' });
  }
  if (!String(req.headers['content-type'] || '').startsWith('application/json')) {
    return res.status(415).json({ message: 'El envío requiere un cuerpo JSON.' });
  }
  const datos = req.body;
  if (!datos || typeof datos !== 'object' || Array.isArray(datos)
      || Buffer.byteLength(JSON.stringify(datos)) > 8192) {
    return res.status(400).json({ message: 'La solicitud de envío es inválida.' });
  }
  try {
    const resultado = await procesarEnvio(datos, req.headers['x-demo-code'], process.env);
    return res.status(200).json(resultado);
  } catch (error) {
    const status = error instanceof ErrorDemo ? error.status : 500;
    const message = error instanceof ErrorDemo ? error.message : 'Ocurrió un error al preparar el mensaje.';
    return res.status(status).json({ message });
  }
};
