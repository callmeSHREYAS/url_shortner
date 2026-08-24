import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';

const createLatency = new Trend('create_url_duration');
const redirectLatency = new Trend('redirect_duration');
const badRedirects = new Rate('bad_redirects');

export const options = {
  scenarios: {
    create_urls: {
      executor: 'ramping-vus',
      exec: 'createUrls',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 25 },
        { duration: '30s', target: 0 },
      ],
    },
    redirect_urls: {
      executor: 'constant-vus',
      exec: 'redirectUrls',
      vus: 30,
      duration: '2m',
      startTime: '10s',
    },
    list_urls: {
      executor: 'constant-vus',
      exec: 'listUrls',
      vus: 3,
      duration: '2m',
      startTime: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    create_url_duration: ['p(95)<1000'],
    redirect_duration: ['p(95)<500'],
    bad_redirects: ['rate<0.01'],
  },
};

export function setup() {
  const codes = [];

  for (let i = 0; i < 20; i += 1) {
    const unique = `${Date.now()}-setup-${i}`;
    const res = http.post(
      `${BASE_URL}/api/v1/url`,
      JSON.stringify({
        name: `k6-seed-${unique}`,
        url: `${BASE_URL}/instance`,
      }),
      { headers: { 'Content-Type': 'application/json' } },
    );

    if (res.status === 200 && res.body.trim()) {
      codes.push(res.body.trim());
    }
  }

  return { codes };
}

export function createUrls() {
  group('create short url', () => {
    const unique = `${Date.now()}-${__VU}-${__ITER}`;
    const res = http.post(
      `${BASE_URL}/api/v1/url`,
      JSON.stringify({
        name: `k6-load-${unique}`,
        url: `${BASE_URL}/instance`,
      }),
      { headers: { 'Content-Type': 'application/json' } },
    );

    createLatency.add(res.timings.duration);

    const ok = check(res, {
      'create status is 200': (r) => r.status === 200,
      'create body has short code': (r) => r.body && r.body.trim().length > 0,
    });

    badRedirects.add(!ok);
  });

  sleep(0.2);
}

export function redirectUrls(data) {
  group('redirect short url', () => {
    const code = data.codes[Math.floor(Math.random() * data.codes.length)];

    if (!code) {
      sleep(0.5);
      return;
    }

    const res = http.get(`${BASE_URL}/api/v1/url/${code}`, { redirects: 0 });
    redirectLatency.add(res.timings.duration);

    const ok = check(res, {
      'redirect status is 302 or 303': (r) => r.status === 302 || r.status === 303,
      'redirect has location': (r) => Boolean(r.headers.Location),
    });

    badRedirects.add(!ok);
  });

  sleep(0.1);
}

export function listUrls() {
  group('list urls', () => {
    const res = http.get(`${BASE_URL}/api/v1/url`);

    check(res, {
      'list status is 200': (r) => r.status === 200,
      'list response is json': (r) =>
        String(r.headers['Content-Type'] || '').includes('application/json'),
    });
  });

  sleep(1);
}
