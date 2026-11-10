import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';

const pagePath = new URL('./index.html', import.meta.url);
const iconPath = new URL('./favicon.svg', import.meta.url);
const page = await readFile(pagePath);
const icon = await readFile(iconPath);
const port = Number.parseInt(process.env.PORT || '3000', 10);

const server = createServer((request, response) => {
  const method = request.method || 'GET';
  const path = new URL(request.url || '/', 'http://localhost').pathname;

  const securityHeaders = {
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
  };

  if (method !== 'GET' && method !== 'HEAD') {
    response.writeHead(405, { ...securityHeaders, Allow: 'GET, HEAD' });
    response.end();
    return;
  }

  if (path === '/health') {
    response.writeHead(200, { ...securityHeaders, 'Content-Type': 'text/plain; charset=utf-8' });
    response.end(method === 'HEAD' ? undefined : 'ok');
    return;
  }

  if (path === '/' || path === '/index.html') {
    response.writeHead(200, {
      ...securityHeaders,
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'public, max-age=300',
    });
    response.end(method === 'HEAD' ? undefined : page);
    return;
  }

  if (path === '/favicon.svg') {
    response.writeHead(200, {
      ...securityHeaders,
      'Content-Type': 'image/svg+xml; charset=utf-8',
      'Cache-Control': 'public, max-age=86400',
    });
    response.end(method === 'HEAD' ? undefined : icon);
    return;
  }

  response.writeHead(404, { ...securityHeaders, 'Content-Type': 'text/plain; charset=utf-8' });
  response.end(method === 'HEAD' ? undefined : 'Not found');
});

server.listen(port, '0.0.0.0', () => {
  console.log(`FrameBucket coming-soon site listening on port ${port}`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
