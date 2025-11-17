# CollaboDraw

Real-time collaborative whiteboard built with Spring Boot, STOMP/WebSockets, Thymeleaf, and MySQL. Multiple users can draw together, see each other’s cursors, and collaborate on boards live. Authentication supports form login and Google OAuth2.

**Status:** Java 21 LTS, Spring Boot 3.5.7. Verified build and tests are green.

**Demo targets:** Local dev via H2 (dev profile) or Aiven MySQL for production.

**Key Features**
- Real-time collaboration using STOMP over SockJS.
- Board presence, cursor sharing, element sync, and versioning events.
- Form login; optional Google OAuth2 with env-configured credentials.
- Clean MVC + service + repository structure; HikariCP for MySQL.

**Tech Stack**
- Java 21 (LTS), Maven Wrapper
- Spring Boot 3.5.7, Spring Security 6
- Thymeleaf templates + static JS
- WebSocket/STOMP (`/ws`, `/app`, `/topic`)
- MySQL (Aiven) with TLS; H2 for dev profile

**Project Structure**
```
src/main/java/com/example/collabodraw/
├─ CollaboDrawApplication.java
├─ config/
│  ├─ DatabaseConfig.java       # DataSource/JdbcTemplate + non-fatal connectivity check
│  ├─ WebConfig.java            # Static resources + CORS
│  └─ WebSocketConfig.java      # STOMP endpoints + broker
├─ security/
│  ├─ SecurityConfig.java       # Auth rules, form login, OAuth2
│  └─ MyUserDetailsService.java
├─ controller/                   # MVC/REST endpoints (boards, auth, etc.)
├─ repository/                   # Persistence layer
├─ service/                      # Business logic
└─ exception/                    # Centralized exceptions

src/main/resources/
├─ templates/                    # auth, home, mainscreen, my-content, settings, ...
├─ static/                       # JS/CSS/Images (whiteboard.js, js/collab-socket.js)
├─ application.properties        # Default (MySQL via env)
└─ application-dev.properties    # Dev profile (H2, no external DB)
```

**WebSocket API**
- Endpoint: `/ws` (SockJS)
- App prefix: `/app`
- Broker destinations: `/topic`, `/queue`
- Sample channels used by the UI (see `static/js/collab-socket.js`):
  - Send: `/app/board/{id}/join|leave|heartbeat|cursor|version|element`
  - Subscribe:
    - `/topic/board.{id}.participants`
    - `/topic/board.{id}.cursors`
    - `/topic/board.{id}.versions`
    - `/topic/board.{id}.elements`

Example JS usage:
```js
CollaboSocket.connect(() => {
  const sub = CollaboSocket.subscribeElements(boardId, (evt, meta) => {
    // handle element updates
  });
  CollaboSocket.joinBoard(boardId);
});
```

**Routes**
- Pages: `/auth`, `/home`, `/board/{boardId}`, `/my-content`, `/settings`, `/templates`, `/shared`
- Legacy redirects: `/whiteboard`, `/whiteboard.html` → `/mainscreen`
- WebSocket handshake: `/ws`

---

## Prerequisites
- Windows PowerShell 5.1+
- Java 21 (JDK) on PATH (`java -version` shows 21)
- Internet access to your MySQL host/port (Aiven example: 17118)

---

## Configuration

The application reads database config from environment variables in `application.properties`:

- `DB_HOST` / `DB_PORT` / `DB_NAME` (or `AIVEN_HOST` / `AIVEN_PORT` / `AIVEN_DB`)
- `DB_USER` / `DB_PASS`
- `SSL_MODE` (default: `REQUIRED`)

This builds the JDBC URL as:
```
jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=${SSL_MODE}&serverTimezone=Asia/Kolkata&connectTimeout=5000&socketTimeout=10000&tcpKeepAlive=true
```

Google OAuth2 (optional but enabled by `SecurityConfig`):
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

If these OAuth variables are missing, startup may fail. The helper script enforces them to avoid silent misconfiguration.

Create a `.env` file for local runs (loaded by `scripts/run-aiven.ps1`):
```
DB_HOST=collabodraw-pratishtha-400c.f.aivencloud.com
DB_PORT=17118
DB_NAME=defaultdb
DB_USER=avnadmin
DB_PASS=replace_me
SSL_MODE=REQUIRED

GOOGLE_CLIENT_ID=replace_me
GOOGLE_CLIENT_SECRET=replace_me
```

---

## Running Locally

Two modes are supported:

1) Production-like (MySQL/Aiven)
- Build and run with the helper script (loads `.env`, builds, and runs):
```powershell
./scripts/run-aiven.ps1                # build (skip tests) + run on port 8080
./scripts/run-aiven.ps1 -Port 8081     # choose another port
./scripts/run-aiven.ps1 -RunTests      # run tests before starting
./scripts/run-aiven.ps1 -DevFallback   # auto-switch to H2 if DB unreachable
```

2) Dev profile (H2, no external DB)
- Start with the `dev` profile to use in-memory H2:
```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
& .\mvnw.cmd spring-boot:run
```

Open http://localhost:8080

---

## Build, Test, Package
```powershell
& .\mvnw.cmd -DskipTests package     # build jar
& .\mvnw.cmd test                    # run tests
java -jar .\target\whiteboard-0.0.1-SNAPSHOT.jar   # run packaged app
```

---

## Database Initialization
The repository includes a MySQL schema: `src/main/resources/collaborative_workspace_mysql.sql`

Load it into your database once (example):
```powershell
mysql -h $env:DB_HOST -P $env:DB_PORT -u $env:DB_USER --ssl-mode=REQUIRED -D $env:DB_NAME ^
  < .\src\main\resources\collaborative_workspace_mysql.sql
```

---

## Deployment (Render)
This repo includes a `render.yaml` blueprint. Ensure environment variables align with the app’s expectations:

Recommended for this codebase:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`, `SSL_MODE`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- Optional: `HIKARI_MAX`, `HIKARI_MIN`

Alternatively, you may set Spring canonical env names directly:
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

Start command in Render should run the shaded jar; port is provided by `$PORT`.

---

## Security Notes
- CSRF disabled for APIs; static resources permitted; authenticated routes guard collaboration pages.
- OAuth2 login is configured; provide Google credentials in production environments.
- CORS allows `http://localhost:8080` by default for local development.

---

## Contributing
- Fork and create a feature branch.
- Keep changes focused; add tests where helpful.
- Open a PR with a clear description and testing notes.

---

## License
MIT

CollaboDraw — collaborate visually in real time.
