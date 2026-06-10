# Stock Market Simulator

Simplified stock exchange REST API with high availability.

---

## Tech Stack

- **Java 21** + **Spring Boot 3.5.13**
- **PostgreSQL 16**
- **Docker + Docker Compose**
- **nginx** — reverse proxy / load balancer

---

## Architecture

Two application instances behind an nginx load balancer. If one instance crashes, nginx automatically routes traffic to the other.

```
  Client
    │
    ▼
 nginx (host port ${PORT}, default 8080)
  ├──▶ app-1 (container port 8080)
  └──▶ app-2 (container port 8080)
              │
              ▼
       PostgreSQL (container port 5432)
```

---

## Prerequisites

- **Git**
- **Docker** 20.10+ — Docker Desktop 3.x+ includes Docker Compose v2 as a built-in plugin (`docker compose`), no separate install needed
- **JDK 21** — only required to run tests locally (`./mvnw test`); the application itself runs entirely inside Docker

### Tested platforms

- macOS arm64 (Apple Silicon, M3)
- Windows x64

Docker handles cross-platform builds automatically — the official `eclipse-temurin:21` and `postgres:16` images are multi-arch (arm64 + x64).

---

## Quick Start

Clone the repository:

```bash
git clone https://github.com/bpietrzakk/stock-market-simulation.git
```

```bash
cd stock-market-simulation
```

**Linux / macOS** — default port (8080):

```bash
./start.sh
```

**Linux / macOS** — custom port:

```bash
./start.sh 9090
```

**Windows** — default port (8080):

```bat
.\start.bat
```

**Windows** — custom port:

```bat
.\start.bat 9090
```

The application will be available at `http://localhost:<PORT>`.

### Cleanup

Stop and remove all data (volumes):

```bash
docker compose down -v
```

Stop but keep data:

```bash
docker compose down
```

---

## API Endpoints

All examples use port 8080. Replace with your configured port if different.

`curl` works on Linux, macOS, and Windows CMD (Windows 10+). On Windows PowerShell, `curl` is an alias for `Invoke-WebRequest` — use `curl.exe` instead (ships with Windows 10+, same syntax as curl).

### Bank

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/stocks` | Set bank stock state |
| `GET` | `/stocks` | Get current bank state |

#### `POST /stocks` — Set bank state

Linux / macOS:
```bash
curl -X POST http://localhost:8080/stocks \
  -H "Content-Type: application/json" \
  -d '{"stocks": [{"name": "AAPL", "quantity": 100}, {"name": "GOOG", "quantity": 50}]}'
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/stocks -H "Content-Type: application/json" -d '{\"stocks\": [{\"name\": \"AAPL\", \"quantity\": 100}, {\"name\": \"GOOG\", \"quantity\": 50}]}'
```

Returns 200 OK.

#### `GET /stocks` — Get bank state

Linux / macOS:
```bash
curl http://localhost:8080/stocks
```

PowerShell:
```powershell
curl.exe http://localhost:8080/stocks
```

Response:
```json
{"stocks":[{"name":"AAPL","quantity":100},{"name":"GOOG","quantity":50}]}
```

---

### Wallet

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallets/{wallet_id}/stocks/{stock_name}` | Buy or sell a stock |
| `GET` | `/wallets/{wallet_id}` | Get wallet state |
| `GET` | `/wallets/{wallet_id}/stocks/{stock_name}` | Get quantity of a specific stock |

#### `POST /wallets/{wallet_id}/stocks/{stock_name}` — Buy a stock

Wallet is created automatically if it doesn't exist.

Linux / macOS:
```bash
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "buy"}'
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL -H "Content-Type: application/json" -d '{\"type\": \"buy\"}'
```

Returns 200 OK.

#### `POST /wallets/{wallet_id}/stocks/{stock_name}` — Sell a stock

Linux / macOS:
```bash
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "sell"}'
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL -H "Content-Type: application/json" -d '{\"type\": \"sell\"}'
```

Returns 200 OK.

#### `GET /wallets/{wallet_id}` — Get wallet state

Linux / macOS:
```bash
curl http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000
```

PowerShell:
```powershell
curl.exe http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000
```

Response:
```json
{"id":"123e4567-e89b-12d3-a456-426614174000","stocks":[{"name":"AAPL","quantity":1}]}
```

#### `GET /wallets/{wallet_id}/stocks/{stock_name}` — Get quantity of a specific stock

Linux / macOS:
```bash
curl http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL
```

PowerShell:
```powershell
curl.exe http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL
```

Response:
```
1
```

---

### Audit Log

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/log` | Get all transactions ordered by date |

#### `GET /log` — Get audit log

Linux / macOS:
```bash
curl http://localhost:8080/log
```

PowerShell:
```powershell
curl.exe http://localhost:8080/log
```

Response:
```json
{"log":[{"type":"buy","wallet_id":"123e4567-e89b-12d3-a456-426614174000","stock_name":"AAPL"}]}
```

---

### Error responses

Examples below assume bank state was set with AAPL at quantity 0 for the "out of stock" case, and the wallet has never bought AAPL for the "insufficient wallet stock" case.

#### Stock doesn't exist in bank — `404`

Linux / macOS:
```bash
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/UNKNOWN \
  -H "Content-Type: application/json" \
  -d '{"type": "buy"}'
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/UNKNOWN -H "Content-Type: application/json" -d '{\"type\": \"buy\"}'
```

Response:
```json
{"status":404,"message":"Stock not found: UNKNOWN"}
```

#### Bank has no stock left — `400`

Linux / macOS:
```bash
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "buy"}'
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL -H "Content-Type: application/json" -d '{\"type\": \"buy\"}'
```

Response:
```json
{"status":400,"message":"Stock AAPL is out of stock"}
```

#### Selling a stock you don't own — `400`

Linux / macOS:
```bash
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "sell"}'
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL -H "Content-Type: application/json" -d '{\"type\": \"sell\"}'
```

Response:
```json
{"status":400,"message":"Wallet doesn't have enough stocks: AAPL"}
```

---

### Chaos

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/chaos` | Kill current app instance (HA demo) |

#### `POST /chaos` — Kill current instance

Linux / macOS:
```bash
curl -X POST http://localhost:8080/chaos
```

PowerShell:
```powershell
curl.exe -X POST http://localhost:8080/chaos
```

---

## High Availability

nginx is configured with `least_conn` load balancing and automatic failover.

Kill one instance:

```bash
curl -X POST http://localhost:8080/chaos
```

Next request still works — nginx routes to the second instance:

```bash
curl http://localhost:8080/stocks
```

`proxy_next_upstream` in nginx.conf ensures that if an instance returns 502/503/504 or times out, the request is automatically retried on the other instance.

---

## Design Decisions

- **Two named services instead of replicas** — Docker Compose doesn't support scaling with a named nginx upstream, so `app-1` and `app-2` are defined explicitly. Simpler and more transparent for a recruitment task.
- **Healthcheck on Postgres** — app instances wait for Postgres to be ready before starting, avoiding connection errors on startup.
- **Client-generated UUID for wallet** — wallet is identified by a UUID provided by the client in the URL. No need for a separate "create wallet" endpoint — the wallet is created automatically on first buy.
- **Controller → Service → Repository** — standard layered architecture. Controllers handle HTTP, services handle business logic, repositories handle data access. Makes testing easier and keeps concerns separated.
- **Custom domain exceptions** — `NotFoundException` and `InvalidOperationException` with a global `@ControllerAdvice` handler instead of `ResponseStatusException`. Cleaner error responses and a single place to manage error handling.
- **Pessimistic lock on `BankStock`** — `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `findByName` prevents race conditions when multiple clients buy the same stock simultaneously.
- **Pessimistic locking over optimistic** — for high-contention writes (concurrent buys on the same popular stock), pessimistic locking avoids retry storms. Optimistic locking would be a better fit for low-contention scenarios where conflicts are rare and retries are cheap.

---

## Project Structure

```
src/main/java/com/bpietrzak/stockmarket/
├── controller/       # REST controllers
├── service/          # Business logic
├── repository/       # Spring Data JPA repositories
├── model/            # JPA entities
├── dto/              # Request/response objects
└── exception/        # Custom exceptions + global error handler
```

---

## Running Tests

```bash
./mvnw test
```

> **Note:** integration and concurrency tests use Testcontainers, which requires Docker to be running.

Tests include:
- **Unit tests** — service layer with Mockito (happy path, edge cases, exceptions)
- **Integration test** — end-to-end HTTP test with Testcontainers + real PostgreSQL
- **Concurrency test** — see below

### Concurrency test

`StockTradingConcurrencyTest` simulates 10 simultaneous buy requests
on a stock with `quantity=5`. Without proper synchronization, this would
result in negative bank balance (race condition).

The test verifies that:
- Exactly 5 requests succeed (HTTP 200)
- Exactly 5 requests fail with `InvalidOperationException` (HTTP 400)
- Bank quantity ends at 0, never negative
- All 5 successful operations are recorded in the audit log

This is enforced by `@Lock(LockModeType.PESSIMISTIC_WRITE)` on
`BankStockRepository.findByName()`, combined with `@Transactional`
on the service method — the transaction must wrap the entire read-modify-write
cycle, otherwise the lock is released too early.

---

## Production Considerations

In a production environment, the following would be added:

- **Flyway/Liquibase** — currently using `ddl-auto=update` for simplicity. Production needs versioned schema migrations.
- **Metrics/Prometheus** — no observability beyond audit log. Would add Micrometer + Grafana dashboard.
- **Rate limiting** — nginx doesn't limit requests per client. Would add `limit_req` to prevent abuse.
- **Authentication** — any client can read/modify any wallet. Would add JWT or API key auth.
- **Quantity in trade request** — currently buy/sell is always 1 unit. Would add `quantity` field to `TradeRequest`.
