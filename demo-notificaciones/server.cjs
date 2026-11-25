const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const handler = require('../api/notificaciones.js');
require('./build.cjs');

const tipos = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.png': 'image/png' };
const out = path.join(__dirname, 'dist');
const server = http.createServer(async (req, res) => {
  if (req.url.split('?')[0] === '/api/notificaciones') {
    res.status = code => { res.statusCode = code; return res; };
    res.json = value => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(value)); };
    const partes = [];
    let size = 0;
    for await (const parte of req) {
      size += parte.length;
      if (size > 8192) return res.status(413).json({ message: 'Mensaje demasiado largo.' });
      partes.push(parte);
    }
    if (partes.length) {
      try { req.body = JSON.parse(Buffer.concat(partes).toString('utf8')); }
      catch { return res.status(400).json({ message: 'JSON inválido.' }); }
    }
    return handler(req, res);
  }
  const nombre = req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0].slice(1);
  if (!['index.html','style.css','app.js','logo.png','plantilla.html'].includes(nombre)) {
    res.writeHead(404); return res.end('Recurso inexistente');
  }
  res.setHeader('Content-Type', tipos[path.extname(nombre)]);
  fs.createReadStream(path.join(out, nombre)).pipe(res);
});
server.listen(Number(process.env.DEMO_PORT || 18106), '127.0.0.1', () => {
  console.log('Demo local http://127.0.0.1:' + server.address().port);
});
