'use strict';
const { spawnSync } = require('child_process');
const path = require('path');

const pruebas = [
  path.resolve(__dirname, 'formulario.test.js'),
  path.resolve(__dirname, 'panel-solicitante.test.js'),
  path.resolve(__dirname, '..', '..', 'modulo-administrativo', 'pruebas', 'panel-admin.test.js'),
];

let fallo = false;
for (const archivo of pruebas) {
  console.log('\n====================================================');
  console.log('Ejecutando: ' + path.relative(path.resolve(__dirname, '..', '..'), archivo));
  console.log('====================================================');
  const res = spawnSync(process.execPath, [archivo], { stdio: 'inherit' });
  if (res.status !== 0) fallo = true;
}

process.exit(fallo ? 1 : 0);
