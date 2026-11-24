// Dev-server proxy to the deployed UAT backend: `npm run start:uat`.
// Override the target with DINEIN_API_TARGET, e.g. DINEIN_API_TARGET=http://localhost:9030 npm run start:uat.
//
// The backend only accepts its configured public origin (CORS + WebSocket origin check), so the browser's
// `Origin: http://localhost:4200` is rewritten to the backend's own origin. Spring then treats every request as
// same-origin. Cookies still land on localhost:4200 because the browser only ever talks to the dev server.
const target = (process.env.DINEIN_API_TARGET || 'http://35.154.15.238:9030').replace(/\/$/, '');

const sameOriginAsTarget = (proxy) => {
  proxy.on('proxyReq', (req) => req.setHeader('origin', target));
  proxy.on('proxyReqWs', (req) => req.setHeader('origin', target));
};

export default {
  '/api': {
    target,
    secure: false,
    changeOrigin: true,
    configure: sameOriginAsTarget,
  },
  '/ws': {
    target,
    ws: true,
    secure: false,
    changeOrigin: true,
    configure: sameOriginAsTarget,
  },
};
