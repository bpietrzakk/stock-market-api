# Stock Market Simulator

Simplified stock exchange REST API with high availability. Built for Remitly internship recruitment task.

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
 nginx (port ${PORT})        ← user-defined, default 8080
  ├──▶ app-1:8080            ← internal port, fixed
  └──▶ app-2:8080            ← internal port, fixed
          │
          ▼
      PostgreSQL
```

---

## Requirements

- Docker 20.10+
- Docker Compose v2 (`docker compose`, not `docker-compose`)
- ~500MB free disk space (for images)
- Ports: configurable via PORT env var (default 8080)

---

## Quick Start

**Prerequisites:** Docker and Docker Compose must be installed.

```bash
git clone https://github.com/bpietrzakk/stock-market-simulation.git
cd stock-market-simulation

# Linux / macOS
./start.sh          # default port 8080
./start.sh 9090     # custom port

# Windows
start.bat           # default port 8080
start.bat 9090      # custom port
```

The application will be available at `http://localhost:<PORT>`.

### Cleanup

```bash
docker compose down -v   # stop and remove all data (volumes)
docker compose down      # stop but keep data
```

---

## API Endpoints

### Bank

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/stocks` | Set bank stock state |
| `GET` | `/stocks` | Get current bank state |

```bash
# set bank state
curl -X POST http://localhost:8080/stocks \
  -H "Content-Type: application/json" \
  -d '{"stocks": [{"name": "AAPL", "quantity": 100}, {"name": "GOOG", "quantity": 50}]}'

# get bank state
curl http://localhost:8080/stocks
# {"stocks":[{"name":"AAPL","quantity":100},{"name":"GOOG","quantity":50}]}
```

### Wallet

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallets/{wallet_id}/stocks/{stock_name}` | Buy or sell a stock |
| `GET` | `/wallets/{wallet_id}` | Get wallet state |
| `GET` | `/wallets/{wallet_id}/stocks/{stock_name}` | Get quantity of a specific stock |

```bash
# buy a stock (wallet is created automatically if it doesn't exist)
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "buy"}'

# sell a stock
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "sell"}'

# get wallet state
curl http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000
# {"id":"123e4567-e89b-12d3-a456-426614174000","stocks":[{"name":"AAPL","quantity":1}]}

# get quantity of specific stock
curl http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL
# 1
```

### Audit Log

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/log` | Get all transactions ordered by date |

```bash
curl http://localhost:8080/log
# {"log":[{"type":"BUY","walletId":"123e4567-...","stockName":"AAPL"}]}
```

### Error responses

> Examples assume bank state was set to `{"AAPL": 0}` for the second
> case (out of stock), and the wallet has never bought AAPL for the third.

```bash
# stock doesn't exist in bank → 404
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/UNKNOWN \
  -H "Content-Type: application/json" \
  -d '{"type": "buy"}'
# {"status":404,"message":"Stock not found: UNKNOWN"}

# bank has no stock left → 400
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "buy"}'
# {"status":400,"message":"Stock AAPL is out of stock"}

# trying to sell a stock you don't own → 400
curl -X POST http://localhost:8080/wallets/123e4567-e89b-12d3-a456-426614174000/stocks/AAPL \
  -H "Content-Type: application/json" \
  -d '{"type": "sell"}'
# {"status":400,"message":"Wallet doesn't have enough stocks: AAPL"}
```

### Chaos

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/chaos` | Kill current app instance (HA demo) |

```bash
curl -X POST http://localhost:8080/chaos
```
---

## High Availability

nginx is configured with `least_conn` load balancing and automatic failover:

```bash
# kill one instance (assuming default port)
curl -X POST http://localhost:8080/chaos

# next request still works — nginx routes to the second instance
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
