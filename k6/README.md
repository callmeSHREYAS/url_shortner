# k6 testing for url_shortner

These scripts test the real app flow:

- `POST /api/v1/url` creates a short URL.
- `GET /api/v1/url/{shortCode}` checks redirect behavior without following the redirect.
- `GET /api/v1/url` checks list performance.
- `/instance` is used as the redirect target so the tests do not depend on an external website.

## Start the app

```powershell
docker compose up --build
```

Wait until nginx is available at:

```text
http://localhost:8081
```

## Install k6

On Windows, the simplest option is:

```powershell
winget install k6.k6
```

Then restart the terminal and check:

```powershell
docker run --rm grafana/k6 version
```

## Run tests

Smoke test:

```powershell
docker run --rm -i -v "${PWD}:/work" grafana/k6 run /work/k6/smoke.js
```

Normal load test:

```powershell
docker run --rm -i -v "${PWD}:/work" grafana/k6 run /work/k6/load.js
```

Spike test:

```powershell
docker run --rm -i -v "${PWD}:/work" grafana/k6 run /work/k6/spike.js
```

If the app runs somewhere else:

```powershell
$env:BASE_URL="http://localhost:8081"
docker run --rm -i -v "${PWD}:/work" grafana/k6 run /work/k6/load.js
```

## What to watch

- `http_req_failed`: should stay low. If it ri ses, inspect app, nginx, MySQL, Redis, and ZooKeeper logs.
- `http_req_duration p(95)`: the slowest common user experience. Redirects should usually be faster than creates.
- `create_url_duration`: pressure on ZooKeeper counter plus MySQL writes.
- `redirect_duration`: pressure on Redis and MySQL fallback.
- `bad_redirects`: redirect endpoint returning something other than a valid redirect.

## Useful failure checks

Run these while k6 is running:

```powershell
docker compose logs -f nginx
docker compose logs -f app1 app2 app3 app4 app5
docker stats
```

Then check the database size. `GET /api/v1/url` returns every row, so this endpoint will get slower as test data grows.

## Likely problems in this app

- `GET /api/v1/url` returns all records without pagination. Under load and after many test inserts, this can become very slow.
- Redirect click count increments only on Redis cache miss. Cache hits do not increment `tot_Clicks`, so click metrics may be lower than real traffic.
- Missing input validation means invalid or empty URLs can be stored.
- Missing friendly error handling means nonexistent short codes can return `500` instead of `404`.
- Test data will accumulate in MySQL. Clean it periodically or add a dedicated cleanup endpoint/profile for test environments.
