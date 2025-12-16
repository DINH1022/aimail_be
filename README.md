# AI Email Dashboard

A full-stack email dashboard application with React frontend and Spring Boot backend, featuring JWT authentication and responsive design.

## Setup and Run Instructions

### Prerequisites

**Frontend:**

- Node.js 20.19+ or 22.12+
- npm or yarn

**Backend:**

- Java 17+
- Maven 3.6+
- Database H2 for development

### Backend Setup

1. **Configure environment variables**

   Edit `src/main/resources/application.yaml`:

   ```yaml
   spring:
     datasource:
       url: jdbc:h2:mem:testdb
       username: sa
       password: password

   jwt:
     secret: your-super-secret-jwt-signing-key-change-this
     access-expiration: 15m
     refresh-expiration-days: 30
   ```

   Or use environment variables:

   ```bash
   export JWT_SECRET=your-secret-key
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/aimailbox
   export SPRING_DATASOURCE_USERNAME=dbuser
   export SPRING_DATASOURCE_PASSWORD=dbpassword
   ```

2. **Run the application**

   ```bash
   # Build
   mvn clean package

   # Run
   java -jar target/AImailbox-0.0.1-SNAPSHOT.jar

   # Or development mode
   mvn spring-boot:run
   ```

   Backend runs on `http://localhost:8080`

### Frontend Setup

1. **Install dependencies**

   ```bash
   cd frontend
   npm install
   ```

2. **Configure API endpoint** (optional)

   Update `src/api/apiClient.ts`:

   ```typescript
   const API_BASE_URL = "http://localhost:8080/api";
   ```

3. **Start development server**

   ```bash
   npm run dev
   ```

   Frontend runs on `http://localhost:5173`
   Backend runs on `http://localhost:8080`

---

## Sorting and Filtering

Simple sorting and filtering controls that apply to cards within Kanban columns.

### API Endpoint

`GET /api/emails`

### Parameters

| Parameter        | Type    | Description                                                                                              |
| :--------------- | :------ | :------------------------------------------------------------------------------------------------------- |
| `label`          | String  | **Recommended.** Filter by Gmail Label ID or Name (e.g., `INBOX`, `Label_36`). Supports dynamic columns. |
| `unreadOnly`     | Boolean | Filter for Unread emails. Pass `true` to show only unread items.                                         |
| `hasAttachments` | Boolean | Filter for emails with Attachments. Pass `true` to show only items with files attached.                  |
| `sort`           | String  | Sorting criteria: `newest`, `oldest`, `sender`. Default: `newest`.                                       |

### Usage Examples

- **Inbox (Newest first):**
  `GET /api/emails?label=INBOX&sort=newest`

- **ToDo Column (Sorted by Sender):**
  `GET /api/emails?label=Label_36&sort=sender`

- **Unread in Inbox:**
  `GET /api/emails?label=INBOX&unreadOnly=true`

- **Process Attachments (Filter by Label, hasAttachments & Sort):**
  `GET /api/emails?label=INBOX&hasAttachments=true&sort=oldest`

- **Complex Filter (Unread + Attachments + Sort by Sender):**
  `GET /api/emails?label=INBOX&unreadOnly=true&hasAttachments=true&sort=sender`

---

## Snooze / Deferral Feature

This project includes an email "snooze" (deferral) workflow that lets users postpone emails until a specified time. The workflow is implemented as a lightweight workflow DB that complements the Gmail/proxy data.

Overview

- Users choose a snooze time from the UI (quick options or a custom datetime). The backend stores a workflow record referencing the email's `threadId` with `status = SNOOZED` and `snoozedUntil` set to the chosen time.
- A scheduled task runs periodically (every 60 seconds in development) to restore snoozed emails whose `snoozedUntil` has passed. Restoring sets the workflow `status` back to `INBOX` (or a prior state) and clears `snoozedUntil`.

API Endpoints (backend)

- `POST /api/emails/thread/{threadId}/snooze` — Create or update a workflow record for the given `threadId` and set a snooze timestamp. Request body should include an ISO-8601 timestamp (e.g. `{"snoozeTime":"2025-12-10T08:00:00Z"}`).
- `POST /api/emails/{id}/unsnooze` — Remove snooze state from a workflow record (used when user unsnoozes manually).
- `GET /api/emails?status=SNOOZED` — List workflow records currently snoozed (used by the UI to build the Snoozed mailbox/column).
- `PATCH /api/emails/{id}/read` and `PATCH /api/emails/{id}/starred` — Update workflow-managed flags.

Behavior and Merge Rules

- The application merges two data sources when rendering lists and details:
  - Gmail/proxy data: authoritative for message content, labels, and timestamps.
  - Workflow DB: authoritative for workflow-managed fields (status, snoozedUntil, summary), but may be `null` for some flags.
- To avoid overwriting Gmail-derived states (read/starred) when no workflow record exists, the backend model uses nullable flags for `isRead` and `isStarred`. The merge logic uses `workflowValue ?? gmailValue` so Gmail values are preserved unless workflow explicitly overrides them.
- To prevent large numbers of 404s during list prefetch, the frontend performs a batch fetch of workflow records and merges them by `threadId` at the list level rather than calling the workflow endpoint per thread.

Implementation Notes (developer)

- The scheduled restore job runs every 60 seconds by default in development (`@Scheduled(fixedRate = 60000)`). Adjust the interval in production as needed.
- The backend will create a workflow record when a user snoozes an email or when UI code explicitly requests one. Consider adding a dedicated `POST /api/emails/thread/{threadId}/create` endpoint if you want a clean way to create workflow records without using the snooze endpoint as a workaround.
- The `snooze` endpoint accepts ISO timestamps in the request body; the server stores timestamps in UTC and computes comparisons using `Instant`.

---

## Token Storage and Security

### Token Architecture

**Backend - Dual Token System:**

- **Access Tokens:**

  - Type: Stateless JWT
  - Lifespan: 15 minutes (configurable)
  - Storage: Not stored server-side
  - Purpose: Authenticate API requests
  - Validation: JWT signature verification

- **Refresh Tokens:**
  - Type: UUID strings
  - Lifespan: 30 days (configurable)
  - Storage: Database table with user association
  - Purpose: Obtain new access tokens
  - Rotation: Old token deleted, new one created on each use

**Frontend - Cookie-Based Storage:**

```typescript
// Token storage configuration
{
  secure: true,           // HTTPS only (auto-detected)
  sameSite: 'Lax',       // CSRF protection
  path: '/',             // Application-wide
  expires: {
    accessToken: 1 day,
    refreshToken: 30 days
  }
}
```

### Security Features

**Backend Security:**

- ✅ HTTPS required in production
- ✅ Refresh token rotation on every use
- ✅ Token revocation via database deletion
- ✅ Replay attack detection
- ✅ BCrypt password encryption
- ✅ Strong JWT signing secrets (256-bit minimum)
- ✅ Automatic token expiration

**Frontend Security:**

- ✅ Secure cookies with automatic HTTPS detection
- ✅ SameSite attribute for CSRF protection
- ✅ No localStorage (prevents XSS attacks)
- ✅ URL encoding to prevent injection
- ✅ Automatic token cleanup on expiry
- ✅ Concurrent request handling during refresh

### Authentication Flow

```
1. Login → Receive access + refresh tokens → Store in cookies

2. API Request → Auto-attach access token in Authorization header

3. Token Expires → 401 Error → Auto-refresh → Retry request

4. Logout → Delete refresh token from DB → Clear cookies
```

**Token Refresh Mechanism:**

```
Request fails (401)
  ↓
Automatic refresh with refresh token
  ↓
Get new access token
  ↓
Retry original request
  ↓
If refresh fails → Clear tokens → Redirect to login
```

### Security Recommendations

**Production Checklist:**

- [ ] Use strong, randomly generated JWT secret (256-bit+)
- [ ] Hash refresh tokens before storing in database (SHA256/bcrypt)
- [ ] Enable HTTPS for all communications
- [ ] Configure CORS to allow only trusted origins
- [ ] Set short access token expiration (10-15 minutes)
- [ ] Implement rate limiting on auth endpoints
- [ ] Monitor for suspicious token reuse patterns
- [ ] Rotate JWT signing keys periodically
- [ ] Use secure cookies with HttpOnly flag in production

---

## Third-Party Services

### Google OAuth

**Current Status:** Mock implementation included for development

### Hosting Providers

**Render (Backend):**

**Vercel (Frontend):**

---

## Public Hosting URL and Deployment

### Live URLs

- **Frontend:** [https://ai-email-chi.vercel.app/](https://ai-email-chi.vercel.app/)
- **Backend API:** [https://aiemail-twfi.onrender.com/](https://aiemail-twfi.onrender.com/)

### Reproduce Deployment Locally

#### Docker Deployment (Backend)

1. **Create Dockerfile:**

   ```dockerfile
   FROM maven:3.9-eclipse-temurin-17 AS build
   WORKDIR /app
   COPY pom.xml .
   COPY src ./src
   RUN mvn clean package -DskipTests

   FROM eclipse-temurin:17-jre-alpine
   WORKDIR /app
   COPY --from=build /app/target/AImailbox-0.0.1-SNAPSHOT.jar app.jar
   EXPOSE 8080
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. **Build and run:**

   ```bash
   docker build -t aimailbox:latest .

   docker run -e JWT_SECRET=your-secret \
              -e SPRING_PROFILES_ACTIVE=prod \
              -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
              -p 8080:8080 aimailbox:latest
   ```

#### Deploy to Render (Backend)

1. Create Web Service on Render
2. Connect Git repository
3. Build command: `mvn clean package -DskipTests`
4. Start command: `java -jar target/AImailbox-0.0.1-SNAPSHOT.jar`
5. Add environment variables: `JWT_SECRET`, `SPRING_DATASOURCE_URL`, etc.

#### Deploy to Vercel (Frontend)

```bash
# Install Vercel CLI
npm i -g vercel

# Deploy
vercel --prod
```

Or connect GitHub repository in Vercel dashboard for automatic deployments.
