import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';

export const options = {
  vus: 1,
  iterations: 5,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    name: `k6-smoke-${unique}`,
    url: `${BASE_URL}/instance`,
  });

  const createRes = http.post(`${BASE_URL}/api/v1/url`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(createRes, {
    'create returns 200': (res) => res.status === 200,
    'short code returned': (res) => res.body && res.body.trim().length > 0,
  });

  const shortCode = createRes.body.trim();
  const redirectRes = http.get(`${BASE_URL}/api/v1/url/${shortCode}`, {
    redirects: 0,
  });

  check(redirectRes, {
    'redirect returns 302 or 303': (res) => res.status === 302 || res.status === 303,
    'redirect location is set': (res) => Boolean(res.headers.Location),
  });

  const listRes = http.get(`${BASE_URL}/api/v1/url`);
  check(listRes, {
    'list returns 200': (res) => res.status === 200,
    'list returns json': (res) =>
      String(res.headers['Content-Type'] || '').includes('application/json'),
  });

  sleep(1);
}
