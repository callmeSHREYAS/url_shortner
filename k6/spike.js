import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';


export const options = {
  stages: [
    { duration: '15s', target: 10 },
    { duration: '20s', target: 100 },
    { duration: '30s', target: 100 },
    { duration: '20s', target: 10 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
  },
};

export default function () {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;

  const createRes = http.post(
    `${BASE_URL}/api/v1/url`,
    JSON.stringify({
      name: `k6-spike-${unique}`,
      url: `${BASE_URL}/instance`,
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(createRes, {
    'create survives spike': (res) => res.status === 200,
  });

  if (createRes.status === 200 && createRes.body.trim()) {
    const redirectRes = http.get(`${BASE_URL}/api/v1/url/${createRes.body.trim()}`, {
      redirects: 0,
    });

    check(redirectRes, {
      'redirect survives spike': (res) => res.status === 302 || res.status === 303,
    });
  }

  sleep(0.1);
}
