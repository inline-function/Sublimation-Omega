// backend-js 执行器：node 直跑生成的 JS，捕获 stdout（V1 = 能直接跑的验收形态）
// 用法：node src/run.mjs <generated.js> [timeoutMs]
import { spawnSync } from 'node:child_process';
const args = process.argv.slice(2);
const target = args[0];
const timeout = parseInt(args[1] || '10000', 10);
const cwd = process.cwd();
const r = spawnSync(process.execPath, ['--no-warnings', target], {
  cwd: cwd, stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8', timeout: timeout
});
const stdout = r.stdout || '';
const stderr = r.stderr || '';
const out = {
  rc: r.status, timedOut: r.error && r.error.code === 'ETIMEDOUT' ? true : false,
  stdout: stdout, stderr: stderr.split('\n').slice(0, 6).join(' | ')
};
console.log(JSON.stringify(out));
