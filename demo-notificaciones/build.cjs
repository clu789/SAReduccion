const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const out = path.join(__dirname, 'dist');
fs.mkdirSync(out, { recursive: true });
for (const nombre of ['index.html', 'app.js', 'style.css']) {
  fs.copyFileSync(path.join(__dirname, 'src', nombre), path.join(out, nombre));
}
fs.copyFileSync(path.join(root, 'modulo-administrativo/img/logo.png'), path.join(out, 'logo.png'));
fs.copyFileSync(path.join(root, 'src/main/resources/mail/aviso-tramite.html'), path.join(out, 'plantilla.html'));
console.log('Demo de notificaciones preparada.');
