import http from 'node:http';
import https from 'node:https';
import { readFile } from 'node:fs/promises';

const port = Number(process.env.FRONTEND_PORT ?? 5173);
const backend = new URL(process.env.BOUNTY_API_ORIGIN ?? 'http://127.0.0.1:8080');
if (!['http:', 'https:'].includes(backend.protocol) || backend.username || backend.password ||
    backend.pathname !== '/' || backend.search || backend.hash) {
  throw new Error('BOUNTY_API_ORIGIN 必须是 HTTP(S) 服务地址，例如 http://127.0.0.1:8080');
}
const transport = backend.protocol === 'https:' ? https : http;
const staticFiles = new Map([
  ...['/', '/index.html', '/admin', '/admin/'].map(url => [url, ['index.html','text/html']]),
  ...['styles','home','student','marketplace','communication','admin'].map(name => [
    '/' + name + '.css', [name + '.css','text/css'],
  ]),
  ...['app','core','home','discovery','student','marketplace','communication','admin','admin-appeals'].map(name => [
    '/' + name + '.js', [name + '.js','text/javascript'],
  ]),
]);
const server = http.createServer(async (req, res) => {
  const path = req.url?.split('?')[0] ?? '/';
  if (path.startsWith('/api/') || path.startsWith('/uploads/')) {
    const request = transport.request(backend, {
      method: req.method,
      path: req.url,
      headers: { ...req.headers, host: backend.host },
    }, upstream => {
      res.writeHead(upstream.statusCode, upstream.headers);
      upstream.pipe(res);
    });
    request.on('error', () => {
      if (res.headersSent) return res.destroy();
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ code: 50200, message: '无法连接后端，请确认 Spring Boot 服务已启动。' }));
    });
    res.on('close', () => request.destroy());
    req.pipe(request);
    return;
  }

  const file = staticFiles.get(path);
  if (!file || !['GET', 'HEAD'].includes(req.method)) {
    res.writeHead(404).end();
    return;
  }
  try {
    const content = await readFile(new URL(file[0], import.meta.url));
    res.writeHead(200, {
      'Content-Type': file[1] + '; charset=utf-8',
      'Cache-Control': 'no-cache',
      'X-Content-Type-Options': 'nosniff',
    });
    res.end(req.method === 'HEAD' ? undefined : content);
  } catch {
    res.writeHead(404).end();
  }
});

server.on('upgrade', (req, client, head) => {
  if (req.url?.split('?')[0] !== '/ws/chat') {
    client.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
    return;
  }
  const request = transport.request(backend, {
    path: req.url,
    headers: { ...req.headers, host: backend.host },
  });
  request.on('upgrade', (reply, upstream, upstreamHead) => {
    upstream.on('error', () => client.destroy());
    client.on('error', () => upstream.destroy());
    client.on('close', () => upstream.destroy());
    upstream.on('close', () => client.destroy());
    if (client.destroyed) {
      upstream.destroy();
      return;
    }
    client.write('HTTP/1.1 101 Switching Protocols\r\n' +
      reply.rawHeaders.reduce((lines, value, index) =>
        lines + value + (index % 2 ? '\r\n' : ': '), '') + '\r\n');
    if (upstreamHead.length) client.write(upstreamHead);
    if (head.length) upstream.write(head);
    upstream.pipe(client).pipe(upstream);
  });
  request.on('response', reply => {
    reply.resume();
    client.end('HTTP/1.1 ' + reply.statusCode + ' WebSocket Upgrade Rejected\r\nConnection: close\r\n\r\n');
  });
  client.on('error', () => request.destroy());
  request.on('error', () => client.end('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n'));
  request.end();
});

server.listen(port, '127.0.0.1', () => {
  console.log('校园悬赏平台 http://127.0.0.1:' + server.address().port);
  console.log('后端代理 ' + backend.origin);
});
