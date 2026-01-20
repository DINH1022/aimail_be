# AIMail - Backend

**Team:** 22120376-22120433-22120434

A full-stack email dashboard application that transforms Gmail into a Kanban-style productivity tool. It features AI-powered summarization, semantic search, and a dual-token authentication system.

## Contributors

| Student ID   | Full Name       |
| :----------- | :-------------- |
| **22120376** | Nguyễn Đức Toàn |
| **22120433** | Lê Quang Vinh   |
| **22120434** | Lê Thành Vinh   |

---

## System Overview

This project transforms the traditional inbox into a workflow-centric Kanban board:

- **Gmail Integration**: Securely syncs with Gmail via OAuth2.
- **Kanban Workflow**: Drag-and-drop emails between columns (Inbox, To Do, Done).
- **AI Power**: Uses LLMs (Gemini) for email summarization and semantic search (e.g., searching for "invoice" finds emails about payments).
- **Snooze**: Temporarily hide emails and auto-return them to the board at a scheduled time.
- **Search**: Fuzzy search (typo tolerance) and Vector-based Semantic search.

---

## Technology Stack

- **Backend framework**: Java 21+ with Spring Boot
- **Database**: PostgreSQL (with `pgvector` for embeddings)
- **Authentication**: Google OAuth2 + JWT (Dual Token System)
- **AI/ML**: Google Gemini API (Summarization & Embeddings)
- **Email Provider**: Gmail REST API
- **Deployment**: Docker, Render (Backend)

---

## Setup and Run Instructions

### Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL

### 1. Environment Configuration

Create `src/main/resources/application.yaml` with the following variables:

```yaml
spring:
  application:
    name: AImailbox
  datasource:
    url: your_database_url # Must support vector extension
    username: your_database_username
    password: your_database_passsword
  jpa:
    hibernate:
      ddl-auto: update

jwt:
  secret: <your_256_bit_jwt_secret>
  access-expiration-minutes: 15
  refresh-expiration-days: 7
google:
  client-id: <your_google_client_id>
  client-secret: <your_google_client_secret>
  redirect-uri: http://localhost:8080/auth/google/callback
  generative-api-key: <your_gemini_api_key> # For AI features

frontend:
  callback-url: http://localhost:5174/auth/google/callback
cors:
  allowed-origins:http://localhost:5174
```

### 2. Google Cloud Setup

1. Enable **Gmail API**.
2. Enable **Generative Language API** (for Gemini).
3. Create OAuth Credentials with redirect URI: `http://localhost:8080/auth/google/callback`.

### 3. Running the Server

```bash
# Build
./mvnw clean package -DskipTests

# Run
java -jar target/aimailbox-0.0.1-SNAPSHOT.jar

# Or
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`.

---

## API Endpoints

### Authentication

| Endpoint                 | Method   | Description                             |
| :----------------------- | :------- | :-------------------------------------- |
| `/auth/login`            | POST     | Login with Username & Password          |
| `/auth/register`         | POST     | Register with Username & Password       |
| `/auth/me`               | GET      | Get current user                        |
| `/auth/google/authorize` | POST     | Redirect to Google Login                |
| `/auth/google/callback`  | GET/POST | Exchange code for Access/Refresh Tokens |
| `/auth/refresh`          | POST     | Refresh Application JWT                 |
| `/auth/logout`           | POST     | Clear tokens and session                |

### Email & Workflow

| Endpoint                                        | Method | Description                    |
| :---------------------------------------------- | :----- | :----------------------------- |
| `/mailboxes`                                    | GET    | List Gmail labels/folders      |
| `/mailboxes/{id}/emails`                        | GET    | List emails (paginated)        |
| `/mailboxes`                                    | POST   | Create label                   |
| `/mailboxes`                                    | PATCH  | Update label                   |
| `/mailboxes`                                    | DELETE | Delete label                   |
| `/emails/{id}`                                  | GET    | Get email details              |
| `/emails/send`                                  | POST   | Send new email                 |
| `/emails/:id/modify`                            | POST   | Mark read/unread, star, delete |
| `/emails/:messageId/attachments/:attachmentsId	` | GET    | Stream attachment              |
| `/emails/{id}/snooze`                           | POST   | Snooze email until timestamp   |
| `/emails/{messageId}/summary`                   | GET    | AI Summary of the email        |

### Search

| Endpoint                 | Method | Description                    |
| :----------------------- | :----- | :----------------------------- |
| `/emails/search/fuzzy`   | GET    | Fuzzy search (typo tolerant)   |
| `/emails/search-sematic` | GET    | Semantic search (vector based) |

### Kanban

| Endpoint              | Method | Description              |
| :-------------------- | :----- | :----------------------- |
| `/kanban/columns`     | GET    | Get column configuration |
| `/kanban/columns`     | POST   | Create/Update columns    |
| `/kanban/columns/:id` | DELETE | Delete column            |

---

## Security Architecture

This project uses a **Dual-Token System** to ensure security while maintaining a seamless user experience.

1. **Application Security (Frontend <-> Backend)**:
   - **Access Token**: Short-lived JWT (15 min) stored in HttpOnly cookies.
   - **Refresh Token**: Long-lived stored in DB, rotated on every use.
   - **Protection**: CRS handling via SameSite cookies, XSS protection via HttpOnly.

2. **Gmail Security (Backend <-> Google)**:
   - **OAuth2 Tokens**: Stored securely in the backend database.
   - **Auto-Refresh**: Backend automatically refreshes Google tokens when expired.
   - **Scope Strictness**: Only requests necessary Gmail scopes.

---

## Key User Flows

1. **Login**: User clicks "Login with Google" -> Redirects to Google -> Returns with Code -> Backend exchanges for JWT & Google Tokens.
2. **AI Summary**: User opens email -> Backend fetches content -> Sends to Gemini API -> Returns concise summary.
3. **Semantic Search**: User searches "receipts" -> Backend converts query to vector -> Compares with email embedding vectors -> Returns semantically related emails (e.g., invoices).
4. **Snooze**: User snoozes email -> Backend marks status as 'SNOOZED' -> Scheduled task checks periodically -> Restores email to Inbox when time expires.
