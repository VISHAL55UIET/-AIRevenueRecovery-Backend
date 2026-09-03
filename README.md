# AI Revenue Recovery Platform

> AI-powered payment recovery platform built with Java, Spring Boot, MySQL, Spring AI, Saga orchestration, idempotency, retry handling, Resilience4j, payment gateway integration, email communication, analytics, and a React-based operational dashboard..

---

## 📌 Project Overview

AI Revenue Recovery is a backend-focused payment recovery platform designed to handle failed payments intelligently instead of treating every payment failure as a terminal state.

The system analyzes payment failures, determines an appropriate recovery strategy, optionally uses AI to generate a recovery recommendation, creates and executes recovery workflows, persists recovery state, handles failures, supports Saga resume, tracks recovery attempts, and provides revenue recovery analytics.

The core idea is:

```text
Payment
   ↓
Payment Failure
   ↓
Failure Reason
   ↓
Recovery Decision
   ↓
AI Analysis
   ↓
Recovery Strategy
   ↓
Recovery Plan
   ↓
Saga Execution
   ↓
Recovery Attempt
   ↓
Success / Failure
   ↓
Retry / Resume
   ↓
Recovered Revenue
   ↓
Analytics
```

The project was designed around real backend engineering concerns such as reliability, idempotency, recoverability, state persistence, failure handling, external service resilience, auditability, and safe AI-assisted automation.

---

# 🎯 Problem Statement

Payment failures are common in real-world payment systems.

A payment can fail because of:

- Insufficient funds
- Card declined
- Network failure
- Expired card
- Bank error
- Fraud detection
- Unknown failure

A production payment recovery system should not apply the same strategy to every failure.

For example:

```text
INSUFFICIENT_FUNDS
        ↓
SEND_PAYMENT_REMINDER
        ↓
Retry Later
```

A transient network failure can be handled differently:

```text
NETWORK_ERROR
        ↓
RETRY_PAYMENT
```

An expired card may require a customer action:

```text
EXPIRED_CARD
        ↓
REQUEST_CARD_UPDATE
```

A suspicious payment should not be blindly retried:

```text
FRAUD_DETECTED
        ↓
BLOCK_RECOVERY
```

The platform therefore combines deterministic recovery rules with AI-assisted recommendations.

---

# 🏗️ High-Level Architecture

```text
                         ┌──────────────────────┐
                         │    React Frontend    │
                         │   Operational UI     │
                         └──────────┬───────────┘
                                    │
                                    │ REST / HTTP
                                    ▼
                         ┌──────────────────────┐
                         │   Spring Boot API    │
                         │     Controllers      │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │     Service Layer    │
                         │    Business Logic    │
                         └──────────┬───────────┘
                                    │
                  ┌─────────────────┼─────────────────┐
                  │                 │                 │
                  ▼                 ▼                 ▼
           Payment Services   Recovery Services    AI Services
                  │                 │                 │
                  │                 ▼                 ▼
                  │          Recovery Decision    AI Provider
                  │                 │
                  │                 ▼
                  │          Recovery Plan
                  │                 │
                  │                 ▼
                  │          Saga Orchestrator
                  │                 │
                  │          ┌──────┴──────┐
                  │          ▼             ▼
                  │       Success       Failure
                  │                         │
                  │                         ▼
                  │                    Retry / Resume
                  │
                  ▼
           Repository Layer
                  │
                  ▼
                MySQL
```

The backend follows a layered architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
MySQL
```

External dependencies are accessed through dedicated service boundaries.

---

# 🔄 Complete Payment Recovery Flow

```text
                       PAYMENT
                          │
                          ▼
                   Payment Failure
                          │
                          ▼
                   Failure Reason
                          │
                          ▼
              Recovery Decision Engine
                          │
              ┌───────────┴───────────┐
              │                       │
              ▼                       ▼
       Deterministic Rules            AI
              │                       │
              └───────────┬───────────┘
                          ▼
                  Recovery Decision
                          │
                          ▼
                   Recovery Plan
                          │
                          ▼
                Recovery Plan Step
                          │
                          ▼
                 Saga Orchestrator
                          │
                          ▼
                Recovery Execution
                          │
                 ┌────────┴────────┐
                 ▼                 ▼
              SUCCESS            FAILURE
                 │                 │
                 ▼                 ▼
        Payment Recovered      Retry / Resume
                 │                 │
                 └────────┬────────┘
                          ▼
                  Recovery Event
                          │
                          ▼
                 Recovery History
                          │
                          ▼
                    Analytics
                          │
                          ▼
                 Revenue Recovery
```

---

# 🤖 AI Recovery

The platform integrates an AI provider through the Spring AI / OpenAI-compatible interface.

The AI layer analyzes the failure reason and provides a recommendation that can be used by the recovery engine.

Example:

```text
Failure Reason
      ↓
INSUFFICIENT_FUNDS
      ↓
AI Analysis
      ↓
Recommendation
      ↓
SEND_PAYMENT_REMINDER
      ↓
Confidence Score
```

Example response:

```json
{
  "failureReason": "INSUFFICIENT_FUNDS",
  "action": "SEND_PAYMENT_REMINDER",
  "result": "REMINDER_ALREADY_SENT",
  "aiRecommendation": "Send a payment reminder because the failure reason is INSUFFICIENT_FUNDS.",
  "aiConfidence": 0.88
}
```

The AI layer is not treated as an unrestricted financial execution engine.

Instead:

```text
AI Recommendation
        +
Business Rules
        ↓
Recovery Decision
        ↓
Controlled Execution
```

This keeps business-critical execution under deterministic application control.

---

# 🧠 Recovery Decision Engine

The recovery decision layer maps payment failure reasons to appropriate recovery actions.

Typical strategy:

| Failure Reason | Recovery Strategy |
|---|---|
| `INSUFFICIENT_FUNDS` | Payment reminder / retry |
| `CARD_DECLINED` | Alternate payment method |
| `NETWORK_ERROR` | Retry payment |
| `EXPIRED_CARD` | Request card update |
| `BANK_ERROR` | Retry after delay |
| `FRAUD_DETECTED` | Block automated recovery |
| `UNKNOWN` | Manual review |

The exact recovery behavior is controlled by backend business logic.

The important architectural separation is:

```text
Decision
   ≠
Execution
```

The system first decides what should happen and then executes that decision.

---

# 🔄 Saga Pattern

The recovery workflow uses a Saga-style orchestration approach.

A recovery process can consist of multiple logical steps. Instead of relying on one large database transaction, the workflow state is persisted.

Example:

```text
PAYMENT_VALIDATION
        ↓
AI_DECISION
        ↓
AI_DECISION_COMPLETED
        ↓
RECOVERY_EXECUTION
        ↓
RECOVERY_EXECUTED
        ↓
COMPLETED
```

If an intermediate operation fails:

```text
Recovery Step
      ↓
   FAILURE
      ↓
Saga = FAILED
      ↓
Persist Failure State
```

The Saga stores workflow information such as:

- Saga ID
- Payment ID
- Current Step
- Status
- Action
- Failure Reason
- Error Message
- Started At
- Updated At
- Completed At

This makes the workflow inspectable and resumable.

---

# ♻️ Saga Resume

A major part of the recovery architecture is the ability to resume a failed Saga.

Example:

```text
Recovery Saga
      ↓
Current Step
      ↓
Execution
      ↓
FAILURE
      ↓
Saga State = FAILED
      ↓
Resume Request
      ↓
Continue Workflow
      ↓
SUCCESS
      ↓
COMPLETED
```

API:

```http
POST /api/ai-recovery/saga/{paymentId}/resume
```

The resume operation first checks the persisted Saga state.

If the Saga has already completed, the system does not execute it again.

Example:

```json
{
  "status": "ALREADY_COMPLETED",
  "message": "Recovery Saga already completed for this payment",
  "sagaId": "SAGA-...",
  "paymentId": 34
}
```

This protects against duplicate recovery execution.

---

# 🔐 Idempotency

Idempotency is important for payment systems because clients can retry requests when they experience:

- Network timeout
- Connection failure
- Gateway timeout
- Temporary server failure
- Client-side retry

Without idempotency:

```text
Request #1
    ↓
Payment Created
    ↓
Client Timeout
    ↓
Client Retries
    ↓
Request #2
    ↓
Duplicate Payment
```

With idempotency:

```text
Request
   ↓
Idempotency-Key
   ↓
Check Existing Request
   │
   ├── Existing → Return Existing Result
   │
   └── New      → Process Request
```

The same idempotency key represents the same logical operation.

```text
Same Key + Same Payload
        ↓
Same Logical Operation
```

While:

```text
Same Key + Different Payload
        ↓
Reject
```

Payment requests can therefore use:

```http
Idempotency-Key: <unique-key>
```

Database uniqueness is also important as an additional protection against concurrent duplicate requests.

---

# 🔁 Retry & Backoff

Recovery operations should not retry indefinitely.

Example:

```text
Attempt #1
    ↓
FAILURE
    ↓
Backoff
    ↓
Attempt #2
    ↓
FAILURE
    ↓
Backoff
    ↓
Attempt #3
    ↓
SUCCESS / FINAL FAILURE
```

Retry handling protects the system from:

- Infinite retries
- Duplicate attempts
- Retry storms
- Repeated immediate requests
- Exceeding recovery limits

---

# ⏰ Scheduled Recovery

Eligible failed payments can be processed automatically according to retry/recovery timing.

Conceptually:

```text
Database
    ↓
Failed / Retryable Payments
    ↓
nextRetryAt <= Current Time
    ↓
Recovery Scheduler
    ↓
Recovery Workflow
```

This allows the recovery engine to operate without requiring manual intervention for every payment.

---

# 🧩 Recovery Plans

Recovery decisions are separated from their execution.

```text
Recovery Decision
       ↓
Recovery Plan
       ↓
Recovery Plan Step
       ↓
Execution
```

A plan can contain actions such as:

```text
AUTOMATIC_RETRY
SEND_PAYMENT_REMINDER
REQUEST_ALTERNATE_PAYMENT
REQUEST_CARD_UPDATE
BLOCK_RECOVERY
```

This makes the recovery system extensible.

---

# 📝 Recovery Attempts

Every recovery operation can be tracked through recovery attempts.

A recovery attempt represents an individual execution attempt against a failed payment.

Important information includes:

- Payment
- Attempt number
- Recovery action
- Status
- Amount
- Failure reason
- Next retry time
- Created time
- Updated time

Example:

```text
Payment
   ↓
Attempt #1 → FAILED
   ↓
Attempt #2 → FAILED
   ↓
Attempt #3 → SUCCESS
```

This allows the system to distinguish between the original payment and subsequent recovery attempts.

---

# 📜 Recovery Events

Recovery events provide an audit trail of important workflow transitions.

Example:

```text
PAYMENT_FAILED
      ↓
RECOVERY_STARTED
      ↓
AI_DECISION_CREATED
      ↓
RECOVERY_STEP_STARTED
      ↓
RECOVERY_STEP_COMPLETED
      ↓
PAYMENT_RECOVERED
```

Events make it possible to understand:

- What happened?
- When did it happen?
- Why did it happen?
- Which action was selected?
- Which step failed?
- Was the Saga resumed?
- Was the payment recovered?

---

# 📚 Recovery History

Recovery history provides the complete recovery journey of a payment.

Example:

```text
Payment
   ↓
Recovery Attempt #1 → FAILED
   ↓
Recovery Attempt #2 → FAILED
   ↓
Recovery Attempt #3 → SUCCESS
```

This is useful for:

- Debugging
- Customer support
- Operational monitoring
- Analytics
- Recovery strategy analysis

---

# 💰 Revenue Recovery

The platform tracks revenue recovered through successful recovery operations.

```text
Failed Payment
      ↓
Recovery Attempt
      ↓
Successful Recovery
      ↓
Recovered Amount
      ↓
Revenue Analytics
```

Important metrics include:

- Total Revenue
- Recovered Revenue
- Recovery Attempts
- Successful Recoveries
- Failed Recoveries
- Recovery Success Rate

---

# 📊 Analytics

The system provides analytics around payment recovery.

Examples include:

```text
Total Recovery Attempts
Successful Attempts
Failed Attempts
Pending Attempts
Recovery Success Rate
Recovered Revenue
```

Revenue data can also be represented over time through the frontend dashboard.

The frontend contains a revenue recovery chart that consumes the analytics API.

Example endpoint:

```http
GET /api/analytics/revenue?period=30
```

---

# 📧 Email Communication

The recovery system can use email communication as part of the recovery workflow.

For example:

```text
INSUFFICIENT_FUNDS
        ↓
Recovery Decision
        ↓
SEND_PAYMENT_REMINDER
        ↓
Email Customer
```

Email functionality can therefore become one of the controlled recovery actions.

---

# 💳 Payment Gateway Integration

Payment gateway operations are isolated behind a dedicated payment gateway service.

Architecture:

```text
Recovery Service
       ↓
Payment Gateway Service
       ↓
External Payment Provider
```

This separation prevents payment-provider-specific implementation from leaking into the core recovery logic.

---

# 🛡️ Resilience4j

The project uses Resilience4j-based resilience patterns for external service reliability.

Relevant patterns include:

- Circuit Breaker
- Retry
- Bulkhead
- Rate Limiter
- Time Limiter

These patterns are particularly useful for external dependencies such as:

```text
AI Provider
Payment Gateway
Email Provider
```

Conceptually:

```text
Application
    ↓
External Service
    X
  Failure
    ↓
Resilience Layer
    ↓
Retry / Fallback / Controlled Failure
```

The goal is to prevent an external dependency failure from cascading through the recovery system.

---

# 🚦 API Rate Limiting
Kafka Event Processing
Transactional Outbox
Razorpay Order Creation
Razorpay Payment Verification
Recovery Intelligence
HTTP 429 Rate Limit Handling

The payment API is protected with Resilience4j RateLimiter to prevent excessive request bursts.

Current configuration:

```text
Payment API
    ↓
RateLimiter
    ↓
Allowed requests → Process
Exceeded limit  → HTTP 429
```

The `paymentApi` limiter is configured with a fixed request limit per refresh period and zero wait time for rejected requests. This provides controlled failure instead of allowing unlimited payment requests.

---

# 📨 Event-Driven Processing

Kafka is used for asynchronous event processing in the recovery workflow.

```text
Payment / Recovery Event
        ↓
     Kafka
        ↓
   Consumer
        ↓
Recovery Processing
```

Kafka producer reliability is configured with acknowledgements and producer retries. Consumer configuration uses a dedicated consumer group and starts from the earliest available offset.

---

# 📦 Transactional Outbox

The project includes an Outbox Publisher configuration for durable event publication.

```text
Database Transaction
        ↓
   Outbox Record
        ↓
 Outbox Publisher
        ↓
      Kafka
```

The outbox publisher processes records in batches and supports retry limits, helping reduce the risk of losing events between database state changes and asynchronous message publication.

---

# 💳 Razorpay Payment Flow

The application supports a Razorpay-based payment flow for recovery.

```text
Failed Payment
      ↓
Create Razorpay Order
      ↓
Razorpay Checkout
      ↓
Payment
      ↓
Backend Verification
      ↓
Recovered Payment
```

The backend exposes dedicated endpoints for creating Razorpay orders and verifying completed payments.

---

# 🤖 Recovery Intelligence

The recovery intelligence service analyzes a failed payment together with customer payment history and retry history.

The decision score considers factors such as:

- Failure reason
- Previous successful payments
- Previous failed payments
- Retry count
- Payment amount

The service produces:

```text
Payment
   ↓
Historical Analysis
   ↓
Recovery Score
   ↓
Priority
   ↓
Recommended Action
   ↓
Reason
```

Example actions include:

```text
RETRY_NOW
RETRY_SOON
SEND_REMINDER
MANUAL_REVIEW
```

This keeps the recovery recommendation explainable instead of returning only a raw AI response.

---

# 🚨 Rate Limit Error Handling

Rate-limit failures are returned as a proper HTTP `429 Too Many Requests` response so API clients and the frontend can distinguish throttling from other application errors.

Example response shape:

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded"
}
```

This provides a clean contract for frontend error handling and operational monitoring.

---

# 🛡️ Fraud Safety

Not every failed payment should be automatically recovered.

For example:

```text
FRAUD_DETECTED
      ↓
BLOCK_RECOVERY
```

This creates a safety boundary around automated recovery.

The system should not blindly retry a payment when the failure indicates a potentially suspicious transaction.

---

# 🔐 Security

The application contains security infrastructure including:

- JWT authentication
- Google OAuth 2.0
- Protected routes
- CORS configuration
- Environment-based credentials

Authentication and authorization are kept separate from the core payment recovery business logic.

---

# 🔑 Secret Management

Sensitive credentials should never be hardcoded into the source code.

Production configuration should use environment variables.

Example:

```properties
spring.application.name=AIRevenueRecovery

spring.datasource.url=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}
spring.datasource.username=${MYSQLUSER}
spring.datasource.password=${MYSQLPASSWORD}

spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.base-url=https://api.groq.com/openai/v1

jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}

razorpay.key-id=${RAZORPAY_KEY_ID}
razorpay.key-secret=${RAZORPAY_KEY_SECRET}

spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
```

Production environment variables include:

```text
MYSQLHOST
MYSQLPORT
MYSQLDATABASE
MYSQLUSER
MYSQLPASSWORD

GROQ_API_KEY

JWT_SECRET

MAIL_USERNAME
MAIL_PASSWORD

RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET

GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
```

Never commit the real values of:

```text
API Keys
Database Passwords
JWT Secrets
SMTP Passwords
Payment Gateway Secrets
OAuth Client Secrets
```

to GitHub.

---

# 🗃️ Database

The application uses MySQL for persistent state.

The database stores information required for:

- Customers
- Payments
- Recovery attempts
- Recovery decisions
- Recovery plans
- Recovery plan steps
- Recovery events
- Recovery tokens
- Idempotency requests
- Recovery Saga state
- Analytics data

The important architectural point is that recovery workflow state is persisted in the database.

This allows the system to recover from application restarts and resume persisted workflow state.

---

# 🧱 Backend Layering

The backend follows:

```text
HTTP Request
     ↓
Controller
     ↓
Service
     ↓
Repository
     ↓
MySQL
```

## Controller Layer

Responsible for:

- HTTP endpoints
- Path variables
- Request parameters
- Calling services
- Returning API responses

## Service Layer

Responsible for business logic such as:

- Payment processing
- Recovery decisions
- AI analysis
- Recovery execution
- Saga orchestration
- Retry scheduling
- Recovery attempts
- Recovery events
- Analytics
- Revenue recovery
- Email communication
- Idempotency

## Repository Layer

Responsible for persistence using:

- Spring Data JPA
- Hibernate
- MySQL

---

# 📂 Backend Structure

```text
src/main/java/com/AIRevenueRecovery

├── config/
│
├── controller/
│
├── dto/
│
├── entity/
│
├── exception/
│
├── repository/
│
└── service/
```

The project separates HTTP handling, business logic, persistence, domain models, DTOs, configuration, and exception handling.

---

# 🌐 API Endpoints

## Payment APIs

```http
POST   /api/payments
GET    /api/payments
GET    /api/payments/{id}
PUT    /api/payments/{id}
DELETE /api/payments/{id}
```

## Recovery APIs

```http
POST /api/recovery/{paymentId}
```

## AI Recovery APIs

```http
GET  /api/ai-recovery/analyze/{failureReason}

GET  /api/ai-recovery/payment/{paymentId}

POST /api/ai-recovery/execute/{paymentId}
```

## Saga APIs

Start/execute a recovery Saga:

```http
POST /api/ai-recovery/saga/{paymentId}
```

Resume a failed or persisted Saga:

```http
POST /api/ai-recovery/saga/{paymentId}/resume
```

Example:

```http
POST http://localhost:8080/api/ai-recovery/saga/34
```

Resume:

```http
POST http://localhost:8080/api/ai-recovery/saga/34/resume
```

## Recovery History

```http
GET /api/recovery-history/{paymentId}
```

## Revenue Analytics

```http
GET /api/analytics/revenue?period=30
```

---

# 🧪 Testing Strategy

The backend can be tested using Postman.

## Payment Testing

Test:

```text
Create Payment
Get Payment
Update Payment
Delete Payment
```

Also test:

```text
Duplicate Idempotency-Key
Same Key + Same Payload
Same Key + Different Payload
Invalid Payment
Invalid Customer
Missing Fields
```

## Recovery Testing

Test different failure reasons:

```text
INSUFFICIENT_FUNDS
CARD_DECLINED
NETWORK_ERROR
EXPIRED_CARD
BANK_ERROR
FRAUD_DETECTED
UNKNOWN
```

## Saga Testing

Test:

```text
New Saga
Successful Saga
Failed Saga
Persisted FAILED Saga
Resume Saga
Already Completed Saga
Unsupported / Invalid State
```

---

# 🧯 Failure Scenarios

## Duplicate Request

```text
Request
   ↓
Idempotency-Key
   ↓
Existing Request?
   │
   ├── YES → Return Existing Result
   │
   └── NO → Process Request
```

## AI Provider Failure

```text
Recovery
   ↓
AI Provider
   X
Failure
   ↓
Resilience Layer
   ↓
Retry / Fallback / Controlled Failure
```

## Saga Failure

```text
Recovery Step
      X
   Failure
      ↓
Saga = FAILED
      ↓
Error Persisted
      ↓
Resume / Retry
```

## Already Completed Saga

```text
Resume Request
      ↓
Check Saga State
      ↓
COMPLETED
      ↓
ALREADY_COMPLETED
      ↓
No Duplicate Execution
```

---

# 🧾 Exception Handling

The backend uses centralized exception handling.

Examples include exceptions for:

```text
Customer Not Found
Payment Not Found
Recovery Attempt Not Found
Maximum Retry
Maximum Retry Limit
```

A global exception handler converts application exceptions into structured API responses.

This keeps controllers focused on HTTP handling and avoids exposing raw internal exceptions.

---

# 🔍 Auditability

The platform is designed so that an operator can understand the lifecycle of a failed payment.

For example:

```text
What happened?
        ↓
Why did the payment fail?
        ↓
What recovery strategy was selected?
        ↓
What did the AI recommend?
        ↓
How many recovery attempts occurred?
        ↓
What was the Saga state?
        ↓
Was the Saga resumed?
        ↓
Was the payment recovered?
        ↓
How much revenue was recovered?
```

The information is distributed across payment, recovery attempt, event, plan, Saga, history, and analytics data.

---

# 🖥️ Frontend

The project includes a React/Vite frontend for operational visibility.

Main application areas include:

```text
Dashboard
Payments
Customers
Recovery
Analytics
AI Recovery
```

The frontend communicates with the Spring Boot backend using REST APIs.

The AI Recovery interface can display:

```text
Payment
Failure Reason
AI Recommendation
Confidence
Recovery Action
Recovery Result
Saga Status
Saga ID
Current Step
```

Example UI state:

```text
PAYMENT
PAY-SAGA-TEST-9876

FAILED
INSUFFICIENT FUNDS

RECOVERY SAGA
COMPLETED

Current Step:
COMPLETED

Action:
SEND PAYMENT REMINDER

Result:
COMPLETED
```

If a Saga has already been completed, the frontend can display the completed state rather than presenting the operation as if it still needs execution.

---

# 📈 Revenue Dashboard

The frontend includes revenue recovery visualization.

Example data flow:

```text
Spring Boot
    ↓
/api/analytics/revenue
    ↓
Revenue Analytics
    ↓
React
    ↓
Revenue Recovery Chart
```

The chart can display:

```text
Total Revenue
Recovered Revenue
Revenue Over Time
```

---

# 🧑‍💻 Local Development

## Requirements

Install:

```text
Java
Maven
MySQL
Node.js
npm
Git
```

---

# 🗄️ MySQL Setup

Create the database:

```sql
CREATE DATABASE ai_revenue_recovery;
```

Configure the local database connection using your local credentials.

Example:

```text
MYSQLHOST=localhost
MYSQLPORT=3306
MYSQLDATABASE=ai_revenue_recovery
MYSQLUSER=root
MYSQLPASSWORD=<your-local-password>
```

---

# ⚙️ Backend Configuration

Use environment variables for secrets.

Example:

```properties
spring.application.name=AIRevenueRecovery

spring.datasource.url=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}
spring.datasource.username=${MYSQLUSER}
spring.datasource.password=${MYSQLPASSWORD}

spring.jpa.hibernate.ddl-auto=update

spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.base-url=https://api.groq.com/openai/v1

jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}

razorpay.key-id=${RAZORPAY_KEY_ID}
razorpay.key-secret=${RAZORPAY_KEY_SECRET}

spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
```

---

# ▶️ Run Backend

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### Linux / macOS

```bash
./mvnw spring-boot:run
```

Backend:

```text
http://localhost:8080
```

---

# 📦 Build Backend

### Windows

```powershell
.\mvnw.cmd clean package
```

### Linux / macOS

```bash
./mvnw clean package
```

---

# ▶️ Run Frontend

From the frontend directory:

```bash
npm install
npm run dev
```

---

# ☁️ Deployment Architecture

The application can be deployed using separate services for the frontend, backend, and database.

```text
                     ┌──────────────────────┐
                     │        Vercel        │
                     │    React Frontend    │
                     └──────────┬───────────┘
                                │
                                │ HTTPS
                                ▼
                     ┌──────────────────────┐
                     │       Railway        │
                     │   Spring Boot API    │
                     └──────────┬───────────┘
                                │
                                │ MySQL
                                ▼
                     ┌──────────────────────┐
                     │     Railway MySQL    │
                     └──────────────────────┘
```

External integrations:

```text
Spring Boot
    │
    ├── AI Provider
    ├── Payment Gateway
    ├── SMTP / Email
    └── Google OAuth
```

---

# 🚀 Production Deployment

Deployment flow:

```text
GitHub
   ↓
Railway
   ↓
Spring Boot Backend
   ↓
Railway MySQL
```

Frontend:

```text
GitHub
   ↓
Vercel
   ↓
React Frontend
```

After deployment, configure:

- Database environment variables
- AI API key
- JWT secret
- Mail credentials
- Payment gateway credentials
- Google OAuth credentials
- Frontend API URL
- Backend CORS
- Production OAuth redirect URL

Production credentials must be stored as deployment environment variables.

---

# 🔒 Production Security Rules

Never commit real credentials to GitHub.

Do not commit:

```text
Database Password
AI API Key
JWT Secret
SMTP Password
Razorpay Secret
Google OAuth Client Secret
```

The repository should contain configuration placeholders only.

Example:

```properties
spring.datasource.password=${MYSQLPASSWORD}
```

instead of:

```properties
spring.datasource.password=real-password
```

---

# 🧠 Design Principles

The project follows backend engineering principles including:

- Separation of Concerns
- SOLID principles
- Dependency Injection
- Layered Architecture
- Repository Pattern
- Service Layer
- DTO-based API boundaries
- Centralized Exception Handling
- Idempotent API Design
- Transactional Processing
- Durable Workflow State
- Controlled Retry
- Failure Recovery
- External Service Resilience

---

# 🎓 Engineering Concepts Demonstrated

```text
Java
Spring Boot
Spring Data JPA
Hibernate
MySQL
REST APIs
Layered Architecture
Dependency Injection
SOLID Principles
Repository Pattern
DTOs
Transactions
Idempotency
Retry
Backoff
Saga Pattern
Saga Orchestration
Saga Resume
Workflow State Persistence
AI Integration
AI-assisted Decision Making
AI Guardrails
Payment Recovery
Payment Gateway Integration
Email Integration
Scheduled Recovery
Resilience4j
Circuit Breaker
Bulkhead
Rate Limiting
Exception Handling
Audit Events
Recovery History
Revenue Analytics
JWT
OAuth 2.0
CORS
Environment-based Configuration
Cloud Deployment
```

---

# 🧩 Why Saga Instead of One Large Transaction?

A payment recovery workflow may involve multiple operations and external systems.

A traditional database transaction cannot reliably control external operations such as:

```text
AI Provider
Payment Gateway
Email Provider
```

Therefore the workflow state is persisted and orchestrated.

Instead of:

```text
BEGIN TRANSACTION
     ↓
Everything
     ↓
COMMIT
```

the recovery workflow behaves more like:

```text
Step 1
  ↓
Persist State
  ↓
Step 2
  ↓
Persist State
  ↓
Step 3
  ↓
Persist State
  ↓
COMPLETED
```

If the application fails after a persisted step, the system can inspect the Saga state and resume the workflow.

This is one of the main reasons Saga orchestration is useful in the project.

---

# 🔥 Important Production Scenarios

## Scenario 1 — Client Retry

```text
Client
  ↓
Payment API
  ↓
Timeout
  ↓
Client Retry
  ↓
Same Idempotency-Key
  ↓
Existing Operation Detected
  ↓
No Duplicate Payment
```

## Scenario 2 — AI Failure

```text
Recovery
  ↓
AI Provider
  ↓
Failure
  ↓
Resilience4j
  ↓
Retry / Fallback
  ↓
Controlled Recovery Behavior
```

## Scenario 3 — Saga Failure

```text
Saga
  ↓
Step 1
  ↓
Step 2
  X
Failure
  ↓
Persist FAILED State
  ↓
Resume
  ↓
Continue Workflow
```

## Scenario 4 — Already Completed Saga

```text
Resume Request
      ↓
Load Saga
      ↓
Status = COMPLETED
      ↓
ALREADY_COMPLETED
      ↓
Do Not Execute Again
```

## Scenario 5 — Fraud

```text
Payment Failure
      ↓
FRAUD_DETECTED
      ↓
BLOCK_RECOVERY
```

---

# 🔮 Future Improvements

Potential future improvements that are not part of the current implementation:

```text
Distributed Saga
Redis-backed distributed idempotency
Distributed scheduler locking
Dead Letter Queue
Optimistic Locking
OpenTelemetry
Prometheus
Grafana
Structured JSON Logging
Centralized Log Aggregation
Role-Based Access Control
Refresh Token Rotation
Dedicated Secret Manager
Flyway / Liquibase
Testcontainers
Integration Testing
CI/CD Pipeline
Horizontal Scaling
Multi-Tenant Architecture
```

---

# 📌 Current Architecture Philosophy

The project is built around one central idea:

> A failed payment should become a recoverable business workflow rather than simply an error response.

The complete lifecycle is:

```text
Payment
   ↓
Failure
   ↓
Failure Analysis
   ↓
Recovery Decision
   ↓
AI Recommendation
   ↓
Recovery Strategy
   ↓
Recovery Plan
   ↓
Saga Execution
   ↓
Recovery Attempt
   ↓
Success / Failure
   ↓
Retry / Resume
   ↓
Recovery Event
   ↓
Recovery History
   ↓
Recovered Revenue
   ↓
Analytics
```

The architecture emphasizes:

```text
Reliability
Recoverability
Idempotency
Traceability
Controlled Automation
Safe AI Integration
```

---

# 👨‍💻 Author

## Vishal Singh

Backend-focused Java engineer with interest in:

- Java
- Spring Boot
- Backend Engineering
- Payment Systems
- System Design
- Distributed Systems
- AI Integration
- Reliability Engineering
- Data Structures & Algorithms

---

# 📄 License

This project is developed as a production-oriented engineering and portfolio project for learning, experimentation, system design practice, and demonstrating backend engineering capabilities.
