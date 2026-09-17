# 🔗 Scalable URL Shortener

A distributed and horizontally scalable URL shortening service built with **Spring Boot, MySQL, Redis, Apache ZooKeeper, Nginx, Docker, and k6**.

The project focuses on the **system-design challenges behind a URL shortener**, including distributed ID generation, caching, database replication, horizontal scaling, load balancing, rate limiting, and asynchronous click analytics.

> 🚧 **Status:** Engineering / system-design project. The architecture is continuously being improved toward production readiness.

---

## 🚀 What This Project Does

The service converts long URLs into short, shareable links.

For example:

```text
https://www.example.com/very/long/url/path
                    ↓
             http://localhost:8081/aB91xZ
```

When a user accesses the short URL, the service resolves the short code and redirects them to the original URL.

The system is designed to handle the high-read traffic pattern normally associated with URL shorteners.

---

# ✨ Features

* 🔗 Short URL generation using **Base62 encoding**
* 🆔 Distributed ID allocation using **Apache ZooKeeper**
* ⚡ **Redis** caching for fast URL resolution
* 🚫 Negative caching for invalid short codes
* 🗄️ **MySQL** persistent storage
* 🔄 MySQL Primary + Read Replica architecture
* ⚖️ **Nginx** load balancing
* 📦 Multiple Spring Boot application instances
* 🚦 IP-based rate limiting for URL creation
* 📊 Asynchronous click-event processing architecture
* 🐳 Fully containerized development environment
* 🧪 **k6** load-testing setup
* ❤️ Spring Boot Actuator for monitoring
* 📄 Pagination for URL listing APIs
* ✅ Request and URL validation

---

# 🏗️ System Architecture

```text
                         ┌──────────────────┐
                         │      Client      │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │      Nginx       │
                         │  Load Balancer   │
                         └────────┬─────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
        ┌──────────┐        ┌──────────┐        ┌──────────┐
        │  App 1   │        │  App 2   │  ...   │  App 5   │
        │ Spring   │        │ Spring   │        │ Spring   │
        │  Boot    │        │  Boot    │        │  Boot    │
        └────┬─────┘        └────┬─────┘        └────┬─────┘
             │                   │                   │
             └───────────────────┼───────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
              ┌──────────┐             ┌─────────────┐
              │  Redis   │             │  ZooKeeper  │
              │  Cache   │             │ Coordination│
              └────┬─────┘             └─────────────┘
                   │
                   │ Cache Miss
                   ▼
            ┌──────────────┐
            │ MySQL Primary│
            │  READ/WRITE  │
            └───────┬──────┘
                    │
                Replication
                    │
                    ▼
            ┌──────────────┐
            │ MySQL Replica│
            │   READ ONLY  │
            └──────────────┘
```

---

# 🔄 URL Creation Flow

When a client creates a short URL:

```text
Client
   │
   │ POST /api/v1/url
   ▼
Nginx
   │
   ▼
Spring Boot
   │
   ├── Validate URL
   │
   ├── Check Rate Limit
   │
   ├── Request ID from ZooKeeper
   │
   ├── Base62 Encode ID
   │
   ├── Store URL in MySQL
   │
   ├── Store mapping in Redis
   │
   ▼
Return Short Code
```

Example:

```json
{
  "name": "Google",
  "url": "https://www.google.com"
}
```

Response:

```json
{
  "shortCode": "aB91xZ"
}
```

---

# ⚡ Redirect Flow

The redirect path is optimized around Redis caching.

```text
GET /{shortCode}
        │
        ▼
      Redis
      /   \
    HIT   MISS
     │      │
     │      ▼
     │    MySQL
     │      │
     │      ▼
     │    Redis
     │      │
     └──────┘
        │
        ▼
 Publish Click Event
        │
        ▼
 Redirect to Original URL
```

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
Redis
  │
  ▼
Original URL
  │
  ▼
HTTP Redirect
```

### Cache Miss

```text
Client
  │
  ▼
Spring Boot
  │
  ▼
Redis → MISS
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
HTTP Redirect
```

This keeps frequently accessed URLs away from the database and reduces database load.

---

# 🧠 Distributed ID Generation

Generating short codes using a local counter becomes problematic when multiple application instances are running.

For example:

```text
App 1 → ID 100
App 2 → ID 100   ❌ Collision
App 3 → ID 100   ❌ Collision
```

This project uses **Apache ZooKeeper** as a distributed coordination mechanism for ID allocation.

```text
                 ZooKeeper Cluster
                        │
              Distributed Coordination
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
        App 1         App 2         App 3
          │             │             │
          └─────────────┼─────────────┘
                        ▼
                    Unique ID
                        │
                        ▼
                   Base62 Encode
                        │
                        ▼
                   Short Code
```

The resulting ID is converted into a compact Base62 representation.

Example:

```text
123456
   ↓
Base62
   ↓
w7E
```

---

# ⚖️ Horizontal Scaling

The application layer runs multiple Spring Boot instances behind Nginx.

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

This allows the application layer to scale horizontally.

Instead of:

```text
Client → One Application Instance
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

Because the application instances are designed to be stateless, traffic can be distributed between them.

---

# 🗄️ Database Architecture

The Docker environment contains:

* MySQL Primary
* MySQL Read Replica
* Replication initialization container
* Persistent Docker volumes

```text
                 ┌────────────────────┐
                 │   MySQL Primary    │
                 │    READ / WRITE    │
                 └─────────┬──────────┘
                           │
                       Replication
                           │
                           ▼
                 ┌────────────────────┐
                 │   MySQL Replica    │
                 │     READ ONLY      │
                 └────────────────────┘
```

The application is configured with separate datasource URLs for the primary and replica databases.

This provides a foundation for separating read-heavy workloads from write operations.

---

# ⚡ Redis Caching

Redis acts as the first lookup layer for short-code resolution.

```text
                 Short Code
                      │
                      ▼
                   Redis
                  /     \
               HIT       MISS
                │          │
                ▼          ▼
          Original URL   MySQL
                           │
                           ▼
                         Redis
```

### Why Redis?

URL shorteners are typically **read-heavy systems**.

A popular short URL may be requested many more times than it is created.

Without caching:

```text
Request
   ↓
Application
   ↓
MySQL
```

With caching:

```text
Request
   ↓
Application
   ↓
Redis
   ↓
Original URL
```

This reduces repeated database queries.

---

# 🚫 Negative Caching

The system also supports caching failed lookups.

For example:

```text
GET /doesNotExist
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
      404
```

Without negative caching, a client repeatedly requesting the same invalid short code could continuously hit MySQL.

Negative caching helps protect the database from this type of traffic.

---

# 🚦 Rate Limiting

URL creation requests are rate-limited by client IP.

Current configuration:

```text
Maximum Requests: 10
Window:           60 seconds
```

Example:

```http
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
Retry-After: 42
```

This helps prevent excessive URL creation requests from a single client.

---

# 📊 Click Analytics

Redirect requests should remain lightweight.

Instead of synchronously updating the database on every redirect:

```text
Redirect
   │
   ├── Resolve URL
   ├── MySQL UPDATE
   └── Redirect
```

the architecture moves click processing toward asynchronous processing:

```text
Redirect Request
       │
       ▼
 Resolve Short Code
       │
       ▼
 Publish Click Event
       │
       ▼
 Async Consumer
       │
       ▼
 Update Analytics
```

This prevents analytics writes from unnecessarily increasing latency on the redirect path.

The architecture can later be extended using technologies such as:

```text
Kafka
Redis Streams
RabbitMQ
```

---

# 🌐 API

## Create Short URL

```http
POST /api/v1/url
Content-Type: application/json
```

Request:

```json
{
  "name": "Google",
  "url": "https://www.google.com"
}
```

Response:

```json
{
  "shortCode": "aB91xZ"
}
```

---

## Redirect

```http
GET /{shortCode}
```

Example:

```text
http://localhost:8081/aB91xZ
```

The server resolves the short code and redirects the client to the original URL.

---

## Get URLs

```http
GET /api/v1/url?page=0&size=50
```

Supports pagination.

Maximum page size:

```text
100
```

---

## Get URL by ID

```http
GET /api/v1/url/id/{id}
```

Example:

```http
GET /api/v1/url/id/100
```

---

## Delete URL

```http
DELETE /api/v1/url/delete/{id}
```

The corresponding cached mapping is removed along with the database record.

---

# 🧰 Tech Stack

| Technology               | Purpose                         |
| ------------------------ | ------------------------------- |
| **Java 25**              | Application runtime             |
| **Spring Boot**          | Backend framework               |
| **Spring Data JPA**      | Database persistence            |
| **MySQL**                | Persistent storage              |
| **Redis**                | Caching                         |
| **Apache ZooKeeper**     | Distributed coordination        |
| **Nginx**                | Reverse proxy / load balancing  |
| **Docker**               | Containerization                |
| **Docker Compose**       | Distributed local environment   |
| **k6**                   | Load testing                    |
| **Maven**                | Build and dependency management |
| **Spring Boot Actuator** | Monitoring / health endpoints   |

The project's Maven configuration includes Spring Boot, JPA, MySQL, Redis, ZooKeeper/Curator, and Actuator dependencies.

---

# 🐳 Running Locally

## Prerequisites

Install:

* Java 25
* Docker
* Docker Compose
* Git

---

## 1. Clone the Repository

```bash
git clone https://github.com/callmeSHREYAS/url_shortner.git
```

```bash
cd url_shortner
```

---

## 2. Start the Complete Stack

```bash
docker compose up --build
```

The Docker Compose environment starts:

```text
MySQL Primary
MySQL Replica
Redis
Spring Boot App 1
Spring Boot App 2
Spring Boot App 3
Spring Boot App 4
Spring Boot App 5
Nginx
ZooKeeper 1
ZooKeeper 2
ZooKeeper 3
```

The repository's Compose configuration defines the MySQL primary/replica, Redis, five application containers, Nginx, and a three-node ZooKeeper setup.

---

## 3. Check Running Containers

```bash
docker compose ps
```

---

## 4. View Logs

```bash
docker compose logs -f
```

For a particular service:

```bash
docker compose logs -f app1
```

---

## 5. Stop the Application

```bash
docker compose down
```

---

## 6. Remove Containers and Volumes

```bash
docker compose down -v
```

> ⚠️ This removes the persistent Docker volumes and therefore deletes the local database data.

---

# 🔍 Useful Docker Commands

Check all containers:

```bash
docker ps
```

Enter the Redis container:

```bash
docker exec -it redis-url-shortner redis-cli
```

Enter ZooKeeper:

```bash
docker exec -it zookeeper-1 bash
```

Check ZooKeeper containers:

```bash
docker ps | grep zookeeper
```

Inspect application logs:

```bash
docker logs -f url-shortner-1
```

---

# 🧪 Load Testing

The repository contains a `k6/` directory for load-testing scenarios.

Load testing can be used to measure:

* Requests per second
* Redirect latency
* Redis cache performance
* Database pressure
* CPU utilization
* Memory utilization
* Application scalability
* Behavior under traffic spikes
* Failure behavior

Example:

```bash
docker compose up --build
```

Then execute the appropriate k6 script from:

```text
k6/
```

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

# 🧠 Important System Design Concepts Demonstrated

This project is primarily an exploration of distributed backend architecture.

### 1. Caching

```text
Application → Redis → MySQL
```

Reduces database reads.

### 2. Horizontal Scaling

```text
Nginx
 ├── App 1
 ├── App 2
 ├── App 3
 ├── App 4
 └── App 5
```

Allows the application layer to scale independently.

### 3. Database Replication

```text
Primary
   │
   ▼
Replica
```

Provides a foundation for read scaling and redundancy.

### 4. Distributed Coordination

```text
Applications
      │
      ▼
  ZooKeeper
      │
      ▼
Unique IDs
```

Coordinates ID allocation across application instances.

### 5. Cache-Aside Pattern

```text
Read Redis
   │
   ├── HIT  → Return
   │
   └── MISS
         ↓
       MySQL
         ↓
      Redis SET
```

### 6. Asynchronous Processing

```text
Request
   ↓
Publish Event
   ↓
Async Consumer
   ↓
Analytics
```

### 7. Load Testing

```text
k6
 ↓
Nginx
 ↓
Multiple App Instances
 ↓
Redis / MySQL
```

---

# 📈 Scalability Roadmap

The current project demonstrates several distributed-system concepts, but there are additional improvements that can make the architecture more resilient.

## Infrastructure

* [ ] Redis Sentinel / Redis Cluster
* [ ] Highly available MySQL
* [ ] Database connection-pool tuning
* [ ] Database indexing review
* [ ] Automated backups
* [ ] Container orchestration with Kubernetes

## Event Processing

* [ ] Kafka / Redis Streams
* [ ] Durable click events
* [ ] Consumer groups
* [ ] Retry mechanism
* [ ] Dead-letter queue
* [ ] Dedicated analytics storage

## Reliability

* [ ] Circuit breakers
* [ ] Timeout policies
* [ ] Retry policies
* [ ] Graceful shutdown
* [ ] Health-aware load balancing
* [ ] Failure recovery

## Observability

* [ ] Prometheus
* [ ] Grafana
* [ ] Centralized logging
* [ ] OpenTelemetry
* [ ] Distributed tracing
* [ ] Application dashboards

## Security

* [ ] Move credentials to environment variables
* [ ] Secrets management
* [ ] HTTPS
* [ ] API authentication
* [ ] Better proxy-aware IP handling
* [ ] Stronger request validation

---

# 🔐 Security Note

The current Docker Compose configuration contains development credentials directly in the Compose file.

For example:

```yaml
MYSQL_ROOT_PASSWORD: myrootpassword
```

These credentials are suitable only for local development.

For production deployment, use:

```text
Environment Variables
        or
Docker Secrets
        or
Cloud Secret Manager
```

and never commit production credentials to Git.

---

# 📚 What I Learned

This project helped me understand how a simple URL shortener evolves into a distributed system.

Key concepts explored:

* REST API design
* Spring Boot
* JPA and MySQL
* Redis caching
* Cache-aside pattern
* Negative caching
* Base62 encoding
* Distributed ID generation
* Apache ZooKeeper
* MySQL replication
* Nginx load balancing
* Horizontal scaling
* Rate limiting
* Asynchronous processing
* Docker networking
* Docker Compose
* Load testing with k6
* System-design trade-offs

---

# 🎯 Future Goal

The long-term goal of this project is to evolve it from a locally distributed application into a more production-oriented system with:

```text
                 ┌─────────────────────┐
                 │       Clients       │
                 └──────────┬──────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │     Nginx     │
                    │ Load Balancer │
                    └───────┬───────┘
                            │
                ┌───────────┼───────────┐
                ▼           ▼           ▼
              App 1       App 2       App N
                │           │           │
                └───────────┼───────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
            Redis       ZooKeeper       MySQL
                                        /    \
                                   Primary   Replica
                                      │
                                      ▼
                                Event Stream
                                      │
                                      ▼
                                  Analytics
```

The focus is not simply making the API work, but understanding **how the system behaves when traffic, data, and application instances increase.**

---

# 👨‍💻 Author

**Shreyas Vartak**

GitHub: [@callmeSHREYAS](https://github.com/callmeSHREYAS)

Repository: [url_shortner](https://github.com/callmeSHREYAS/url_shortner)

---

## ⭐ If you found this project useful

Feel free to explore the repository, raise issues, or suggest improvements.

Built to learn **backend engineering + distributed systems + system design**.
