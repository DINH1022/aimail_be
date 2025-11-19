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
   const API_BASE_URL = 'http://localhost:8080/api';
   ```

3. **Start development server**
   ```bash
   npm run dev
   ```

   Frontend runs on `http://localhost:5173`
   Backend runs on `http://localhost:8080`


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

## Third-Party Services

### Google OAuth

**Current Status:** Mock implementation included for development

### Hosting Providers

**Render (Backend):**

**Vercel (Frontend):**

---

