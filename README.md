# BizPilot AI

A smart back-office platform for freelancers and small businesses: customers, invoices, payments,
expenses, payment reminders, a 30/60/90-day cash-flow forecast, a what-if payment-delay simulator
and rule-based financial insights.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL 16, springdoc OpenAPI |
| Frontend | Angular 22 (standalone components, signals, reactive forms), SCSS |
| Tests    | JUnit 5, Mockito, MockMvc (backend) · Vitest (frontend) |

## Project structure

```
backend/bizpilot-backend   Spring Boot API (package com.bizpilot)
frontend/bizpilot          Angular single-page app
```

## Features

- **Auth** – registration and login with JWT; every record is scoped to the signed-in business.
- **Customers** – CRUD with search and pagination (archive instead of hard delete).
- **Invoices** – line items, tax and discount calculated on the server, draft → sent → paid lifecycle, cancel.
- **Payments** – partial and full payments with overpayment protection.
- **Expenses** – CRUD with category and date filters.
- **Dashboard** – totals, 12-month revenue vs expense chart, recent transactions.
- **Reminders** – drafted from fixed templates for overdue and upcoming invoices.
- **Cash-flow forecast** – 30/60/90 days, weekly or daily, confirmed vs expected cash.
- **What-if simulation** – read-only: see the impact of delayed customer payments.
- **Insights** – deterministic rules (overdue invoices, late payers, expense spikes, low projected cash…).

## Running locally

### Prerequisites
Java 21, Node.js 20+, PostgreSQL 16 with a database named `bizpilot_db`.

### Backend

Configuration comes from environment variables — no secrets are stored in the repository.

| Variable | Required | Default |
|----------|----------|---------|
| `DB_PASSWORD` | yes | – |
| `JWT_SECRET` | yes (32+ characters) | – |
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/bizpilot_db` |
| `DB_USERNAME` | no | `postgres` |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:4200` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | only for reminder emails | `smtp.zoho.in`, `587`, empty |

```powershell
cd backend/bizpilot-backend
.\mvnw.cmd spring-boot:run        # macOS/Linux: ./mvnw spring-boot:run
```

Flyway creates the schema on first start. API docs: http://localhost:8080/swagger-ui/index.html ·
health check: http://localhost:8080/api/health

Run the tests with `.\mvnw.cmd clean verify`.

### Frontend

```bash
cd frontend/bizpilot
npm install
npm start                          # http://localhost:4200
```

Production build: `npm run build` (output in `dist/bizpilot`). The API base URL is set in
`src/app/core/api.config.ts`.

## API overview

| Area | Base path |
|------|-----------|
| Auth | `/api/auth` (`register`, `login`, `me`) |
| Customers | `/api/customers` |
| Invoices | `/api/invoices` (+ `/{id}/send`, `/{id}/cancel`, `/{id}/payments`) |
| Payments | `/api/payments` |
| Expenses | `/api/expenses` |
| Dashboard | `/api/dashboard/summary`, `/recent-transactions`, `/revenue-chart`, `/expense-chart` |
| Reminders | `/api/reminders` (+ `/generate`, `/{id}/status`) |
| Cash flow | `/api/cashflow/forecast?days=30\|60\|90`, `POST /api/cashflow/what-if` |
| Insights | `/api/insights` |

All endpoints except register, login, health and the API docs require `Authorization: Bearer <token>`.
