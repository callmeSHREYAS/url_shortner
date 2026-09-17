# 🔗 Scalable URL Shortener

A production-oriented URL shortening service built with **Spring Boot**, **MySQL**, **Redis**, **Apache ZooKeeper**, **Nginx**, and **Docker**.

The project is designed to explore the system-design problems behind a URL shortener: fast redirects, caching, unique short-code generation, horizontal application scaling, database replication, rate limiting, asynchronous click analytics, and load testing.

> 🚧 This is an engineering/system-design project and is still being improved toward a more production-ready architecture.

---

## ✨ Features

* 🔗 Create short URLs using a Base62-style short-code generation strategy
* ⚡ Redis caching for fast redirects
* 🗄️ MySQL as the persistent data store
* 📖 MySQL primary + read-replica setup
* 🧩 Apache ZooKeeper for distributed coordination / ID allocation
* ⚖️ Nginx load balancing across multiple Spring Boot instances
* 📈 Horizontally scalable application containers
* 🚦 Rate limiting for URL creation
* 🛡️ HTTP/HTTPS URL validation and request validation
* 🚫 Negative caching for non-existent short codes
* 📊 Asynchronous click-event processing architecture
* 🐳 Docker Compose environment for the complete stack
* 🧪 k6 load-testing setup
* ❤️ Spring Boot Actuator for application monitoring

---

## 🏗️ Architecture

```text
                         ┌─────────────────┐
                         │     Client      │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │      Nginx      │
                         │ Load Balancer   │
                         └────────┬────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                │                 │                 │
                ▼                 ▼                 ▼
           ┌─────────┐       ┌─────────┐       ┌─────────┐
           │  App 1  │       │  App 2  │  ...  │  App 5  │
           │ Spring  │       │ Spring  │       │ Spring  │
           │  Boot   │       │  Boot   │       │  Boot   │
           └────┬────┘       └────┬────┘       └────┬────┘
                │                 │                 │
                └─────────────────┼─────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
              ┌──────────┐               ┌──────────────┐
              │  Redis   │               │  ZooKeeper   │
              │  Cache   │               │ Coordination │
              └──────────┘               └──────────────┘
                    │
                    │ cache miss / writes
                    ▼
              ┌──────────┐      replication      ┌──────────────┐
              │  MySQL   │ ───────────────────► │ MySQL Replica│
              │ Primary  │                       │ Read Replica │
              └──────────┘                       └──────────────┘
```

### Redirect Flow

```text
GET /{shortCode}
       │
       ▼
    Redis?
    /    \
  HIT    MISS
   │        │
   │        ▼
   │      MySQL
   │        │
   │        ▼
   │      Redis SET
   │        │
   └────┬───┘
        ▼
 Publish click event
        │
        ▼
 Redirect to original URL
```

The redirect path checks Redis first. On a cache miss, the service reads MySQL, populates Redis, publishes a click event, and redirects the client.

---

## 🧱 Tech Stack

| Technology           | Purpose                                  |
| -------------------- | ---------------------------------------- |
| Java 25              | Application runtime                      |
| Spring Boot          | REST API and application framework       |
| Spring Data JPA      | Persistence layer                        |
| MySQL                | Primary persistent database              |
| Redis                | URL cache and fast lookup layer          |
| Apache ZooKeeper     | Distributed coordination / ID allocation |
| Nginx                | Load balancing                           |
| Docker               | Containerization                         |
| Docker Compose       | Local distributed environment            |
| k6                   | Load testing                             |
| Maven                | Build and dependency management          |
| Spring Boot Actuator | Application monitoring                   |

---

# 🚀 API

## 1. Create a Short URL

```http
POST /api/v1/url
Content-Type: application/json
```

### Request

```json
{
  "name": "Google",
  "url": "https://www.google.com"
}
```

### Response

```json
{
  "shortCode": "aB91xZ"
}
```

The create endpoint:

1. Validates the request
2. Applies rate limiting
3. Generates a unique short code
4. Stores the URL in MySQL
5. Stores the mapping in Redis
6. Returns the generated short code

---

## 2. Redirect

```http
GET /{shortCode}
```

Example:

```text
http://localhost:8081/aB91xZ
```

The service resolves the short code and redirects the client to the original URL.

### Cache Hit

```text
Client
  │
  ▼
Nginx
  │
  ▼
Spring Boot
  │
  ▼
Redis ──────► Original URL
  │
  ▼
Redirect
```

### Cache Miss

```text
Client
  │
  ▼
Spring Boot
  │
  ▼
Redis ──────► MISS
  │
  ▼
MySQL
  │
  ▼
Redis SET
  │
  ▼
Publish Click Event
  │
  ▼
Redirect
```

---

## 3. Get URLs

```http
GET /api/v1/url?page=0&size=50
```

Supports pagination.

Maximum page size:

```text
100
```

---

## 4. Get URL by ID

```http
GET /api/v1/url/id/{id}
```

Example:

```http
GET /api/v1/url/id/100
```

---

## 5. Delete URL

```http
DELETE /api/v1/url/delete/{id}
```

The delete operation removes the corresponding Redis entry and then deletes the database record.

---

# ⚡ Caching Strategy

Redis is used as the first lookup layer for redirects.

```text
              Short Code
                   │
                   ▼
                Redis
               /     \
            HIT       MISS
             │          │
             ▼          ▼
         Original     MySQL
            URL          │
                         ▼
                       Redis
                         │
                         ▼
                  Original URL
```

This reduces repeated database queries for frequently accessed URLs.

### Negative Caching

The application also caches information about unknown short codes.

```text
GET /invalidCode
       │
       ▼
    Redis
       │
     MISS
       │
       ▼
    MySQL
       │
    NOT FOUND
       │
       ▼
 Cache negative result
       │
       ▼
    404 Response
```

This prevents repeated requests for the same invalid short code from continuously reaching MySQL.

---

# 🆔 Distributed Short-Code Generation

ZooKeeper is used as the distributed coordination layer for ID allocation across multiple application instances.

Conceptually:

```text
                ZooKeeper Cluster
                       │
             Distributed Coordination
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
        App 1        App 2        App 3
          │            │            │
          └────────────┼────────────┘
                       ▼
                 Unique ID
                       │
                       ▼
                  Base62 Encode
                       │
                       ▼
                  Short Code
```

This prevents multiple application instances from relying on independent in-memory counters.

---

# ⚖️ Horizontal Scaling

The application currently runs **five Spring Boot instances** behind Nginx.

```text
                     Nginx
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
      App 1          App 2          App 3
        │              │              │
        └──────────────┼──────────────┘
                       │
                 App 4 / App 5
```

Nginx distributes incoming requests across the application instances.

### Why this matters

Instead of:

```text
Client → One Spring Boot Instance
```

the architecture becomes:

```text
Client
   │
   ▼
Load Balancer
   │
   ├── App 1
   ├── App 2
   ├── App 3
   ├── App 4
   └── App 5
```

This allows the application layer to scale horizontally.

---

# 🗄️ Database Architecture

The Docker environment contains:

* MySQL Primary
* MySQL Read Replica
* Replication initialization container
* Persistent Docker volumes

Architecture:

```text
                    ┌──────────────────┐
                    │  MySQL Primary   │
                    │   READ / WRITE   │
                    └────────┬─────────┘
                             │
                         Replication
                             │
                             ▼
                    ┌──────────────────┐
                    │  MySQL Replica   │
                    │    READ ONLY     │
                    └──────────────────┘
```

The Spring Boot application is configured with separate primary and replica datasource URLs.

---

# 🚦 Rate Limiting

URL creation is rate-limited by client IP.

Current configuration:

```properties
rate-limit.create.max-requests=10
rate-limit.create.window-seconds=60
```

Therefore, the current configuration allows:

```text
10 URL creation requests
per client
per 60 seconds
```

The API also exposes rate-limit headers and returns `Retry-After` when the limit is exceeded.

Example:

```http
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
Retry-After: 42
```

---

# 📊 Click Analytics

Redirect requests publish click events instead of synchronously updating the database for every request.

```text
                  Redirect Request
                         │
                         ▼
                    Resolve URL
                         │
                         ▼
                  Publish Event
                         │
                         ▼
                  Async Worker
                         │
                         ▼
                  Update Analytics
```

This keeps the redirect path lightweight.

Instead of:

```text
Redirect
   │
   ├── Redis
   ├── MySQL
   └── MySQL UPDATE clicks
```

the target architecture is closer to:

```text
Redirect
   │
   ├── Redis
   └── Event Queue / Stream
              │
              ▼
        Async Processing
              │
              ▼
          Analytics DB
```

---

# 🐳 Running Locally

## Prerequisites

Install:

* Java 25
* Docker Desktop / Docker Engine
* Docker Compose
* Git

---

## Clone Repository

```bash
git clone https://github.com/callmeSHREYAS/url_shortner.git
```

```bash
cd url_shortner
```

---

## Start the Complete Stack

```bash
docker compose up --build
```

The Docker Compose environment starts:

```text
MySQL
MySQL Replica
Redis
App 1
App 2
App 3
App 4
App 5
Nginx
ZooKeeper 1
ZooKeeper 2
ZooKeeper 3
```

---

## Check Containers

```bash
docker compose ps
```

---

## Stop Containers

```bash
docker compose down
```

---

## Stop and Remove Volumes

```bash
docker compose down -v
```

> ⚠️ The current Docker Compose configuration contains development credentials. These should be moved to environment variables or a proper secrets manager before production deployment.

---

# 🧪 Load Testing

The project contains a `k6` directory for load testing.

The purpose of load testing is to evaluate:

* Requests per second
* Redirect latency
* Cache-hit performance
* Database pressure
* CPU utilization
* Memory utilization
* Application scalability
* Behavior under traffic spikes
* Failure behavior

Example workflow:

```bash
docker compose up --build
```

Then execute the relevant k6 script from the `k6/` directory.

---

# 📁 Project Structure

```text
url_shortner/
│
├── .mvn/
│
├── docker/
│   └── mysql/
│       └── configure-replication.sh
│
├── k6/
│   └── load-testing scripts
│
├── nginx/
│   └── nginx.conf
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/shreyas/url_shortner/
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│
├── Dockerfile
├── docker-compose.yml
├── docker-compose.loadtest.yml
├── pom.xml
├── mvnw
└── mvnw.cmd
```

---

# 🔍 Design Decisions

## Why Redis?

URL shorteners are generally read-heavy systems.

A popular short URL may be requested thousands or millions of times.

Without caching:

```text
Request → Application → MySQL
```

With Redis:

```text
Request → Application → Redis
                            │
                           HIT
```

This reduces database load and improves redirect latency.

---

## Why Nginx?

Nginx acts as the reverse proxy and load balancer.

```text
             Nginx
               │
     ┌─────────┼─────────┐
     ▼         ▼         ▼
   App 1     App 2     App 3
```

This allows multiple stateless application instances to process requests.

---

## Why ZooKeeper?

Multiple application instances need a coordinated way to allocate IDs.

Using an independent counter inside each application can produce collisions.

ZooKeeper provides distributed coordination so ID allocation can be coordinated across instances.

---

## Why Asynchronous Click Events?

A redirect should primarily:

1. Resolve the short code
2. Return the destination URL

It should not perform an expensive database write on every request.

Therefore:

```text
Redirect
   │
   ▼
Publish Click Event
   │
   ▼
Async Processing
```

This separates the high-volume redirect path from analytics processing.

---

# 📈 Scalability Roadmap

The project currently demonstrates several distributed-system concepts, but there are still important steps before calling it production-ready.

### Infrastructure

* [ ] Redis Sentinel / Redis Cluster
* [ ] Highly available MySQL
* [ ] Database connection-pool tuning
* [ ] Database indexing review
* [ ] Automated database backups

### Event Processing

* [ ] Kafka or Redis Streams
* [ ] Durable click events
* [ ] Consumer groups
* [ ] Retry / dead-letter handling
* [ ] Separate analytics storage

### Reliability

* [ ] Circuit breakers
* [ ] Retry policies
* [ ] Timeout policies
* [ ] Graceful shutdown
* [ ] Health-aware load balancing
* [ ] Failure recovery

### Observability

* [ ] Prometheus
* [ ] Grafana
* [ ] Centralized logging
* [ ] OpenTelemetry
* [ ] Distributed tracing
* [ ] Application dashboards

### Security

* [ ] Move secrets out of Docker Compose
* [ ] Environment-based configuration
* [ ] API authentication
* [ ] HTTPS
* [ ] Better IP/rate-limit handling behind proxies
* [ ] Input validation hardening

### Deployment

* [ ] GitHub Actions CI/CD
* [ ] Automated tests
* [ ] Docker image publishing
* [ ] Kubernetes deployment
* [ ] Kubernetes HPA
* [ ] Rolling deployments

---

# 🧠 What This Project Demonstrates

This project goes beyond a basic CRUD URL shortener and explores real backend/system-design concepts.

### Backend

* REST API design
* Spring Boot
* Spring Data JPA
* MySQL
* Maven
* Request validation

### Distributed Systems

* Horizontal scaling
* Distributed ID generation
* ZooKeeper coordination
* Database replication
* Load balancing

### Performance

* Redis caching
* Cache-aside pattern
* Negative caching
* Asynchronous processing
* Rate limiting

### Infrastructure

* Docker
* Docker Compose
* Nginx
* Multi-container architecture

### Testing & Monitoring

* k6 load testing
* Spring Boot Actuator
* Performance analysis

---

# ⚠️ Current Limitations

This project should **not yet be considered a fully production-ready URL-shortening platform**.

The distributed infrastructure is currently primarily intended to demonstrate and experiment with scalability and system-design concepts.

Important areas still requiring hardening include:

* High availability of infrastructure components
* Production-grade Redis architecture
* Production-grade MySQL architecture
* Durable event processing
* Observability
* Secret management
* Failure recovery
* Automated CI/CD
* Security hardening

---

# 🎯 Learning Goals

The main goal of this project is to understand how a simple URL shortener can evolve from:

```text
Simple CRUD Application
```

into:

```text
                 ┌─────────────┐
                 │    Client   │
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │    Nginx    │
                 └──────┬──────┘
                        │
             ┌──────────┼──────────┐
             ▼          ▼          ▼
           App 1      App 2      App 3
             │          │          │
             └──────────┼──────────┘
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
       Redis        ZooKeeper       MySQL
                                      │
                                      ▼
                                  Replica
```

The project is therefore mainly an exploration of **backend scalability, distributed systems, caching, database architecture, and system design**.

---

# 👨‍💻 Author

**Shreyas Vartak**

GitHub: [@callmeSHREYAS](https://github.com/callmeSHREYAS)

---

⭐ If you find the project useful for learning backend engineering and system design, consider giving the repository a star.
