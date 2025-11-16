# CollaboDraw (Spring Boot + Aiven MySQL)

CollaboDraw is a real‑time collaborative whiteboard built with Spring Boot and WebSockets. It uses Aiven MySQL for persistence and supports form login out of the box, with optional Google OAuth2 when credentials are provided.

## Highlights
- Real‑time collaboration (STOMP over SockJS)
- Form login (OAuth2 is optional and only enabled when configured)
- Env‑driven configuration (.env) with a Windows PowerShell helper script

## Tech stack
- Spring Boot 3.5.7, Spring Security 6
- Java 17 (from pom.xml)
- Thymeleaf templates + JS static assets
- Aiven MySQL over TLS (HikariCP)

## Project layout
```
src/main/java/com/example/collabodraw/
├─ CollaboDrawApplication.java
├─ config/
│  ├─ DatabaseConfig.java       # JdbcTemplate + connectivity check (non‑fatal)
│  ├─ WebConfig.java
│  └─ WebSocketConfig.java
├─ security/
│  ├─ SecurityConfig.java       # oauth2Login applied only when available
│  └─ MyUserDetailsService.java
├─ controller/                   # MVC + REST endpoints
├─ repository/                   # JdbcTemplate access
├─ service/                      # Business logic
└─ exception/                    # Error handling

src/main/resources/
├─ templates/                    # Thymeleaf pages (auth, home, mainscreen, etc.)
├─ static/                       # JS/CSS/Images (whiteboard.js, collab-socket.js,...)
└─ application.properties        # Uses env vars for DB + optional OAuth
```

## Prerequisites
- Windows PowerShell 5.1 or newer
- Java 17 (JDK)
- Internet egress to Aiven MySQL port (e.g., 17118)

## Configure environment (.env)
Use Option A (recommended): specify host/port/db and separate credentials. Do not embed credentials in the JDBC URL.

```
DB_HOST=collabodraw-pratishtha-400c.f.aivencloud.com
DB_PORT=17118
DB_NAME=defaultdb   # or your custom DB that exists in Aiven

DB_USER=<your_aiven_user>
DB_PASS=<your_aiven_password>

SSL_MODE=REQUIRED
```

Notes
- Alternatively, you can set AIVEN_HOST/AIVEN_PORT/AIVEN_DB; the run script maps them to DB_*.
- application.properties builds the JDBC URL like:
   jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=${SSL_MODE}

## Quick connectivity checks (optional)
PowerShell (replace values if different):

```
Resolve-DnsName collabodraw-pratishtha-400c.f.aivencloud.com
Test-NetConnection -ComputerName collabodraw-pratishtha-400c.f.aivencloud.com -Port 17118

# MySQL auth check (you will be prompted for password)
mysql -h collabodraw-pratishtha-400c.f.aivencloud.com -P 17118 -u <DB_USER> --ssl-mode=REQUIRED -D <DB_NAME> -e "SELECT 1;"
```

If the MySQL command says "Access denied", reset the password for that Aiven user or verify grants on the target database.

## Run locally (recommended)
Use the helper script which loads .env, builds, and runs the app. It also handles Windows quirks when passing the port.

```
Copy-Item .env.example .env
notepad .env   # fill your values

./scripts/run-aiven.ps1           # build (skip tests) + run on port 8080
./scripts/run-aiven.ps1 -Port 8081  # run on a different port
./scripts/run-aiven.ps1 -RunTests   # build WITH tests then run
./scripts/run-aiven.ps1 -VerboseEnv -SkipRun  # print resolved env only
```

Under the hood, the script exports env vars and sets SERVER_PORT for Spring Boot, then invokes mvnw spring-boot:run.

## Database initialization
This repo ships a schema: `src/main/resources/collaborative_workspace_mysql.sql`.

To load it once into your chosen DB:
```
mysql -h <DB_HOST> -P <DB_PORT> -u <DB_USER> --ssl-mode=REQUIRED -D <DB_NAME> < .\src\main\resources\collaborative_workspace_mysql.sql
```

## OAuth2 (optional)
Google OAuth is disabled unless credentials are set. To enable:
```
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```
SecurityConfig only applies oauth2Login when a ClientRegistrationRepository exists; otherwise form login is used.

## Testing
Run the existing tests:
```
./mvnw.cmd test
```
Current status on this machine:
- Build: PASS
- Tests: PASS (WhiteboardApplicationTests)

## Troubleshooting
- Access denied for user '<user>'@'<ip>'
   - Wrong password, missing grants, or DB name doesn’t exist. Verify user in Aiven console, reset password, and ensure grants on <DB_NAME>.
- UnknownHost / DNS failure
   - Check host in Aiven console; use Resolve-DnsName and Test-NetConnection.
- Port 8080 already in use
   - Use `./scripts/run-aiven.ps1 -Port 8081` or stop the conflicting process.
- Maven "Unknown lifecycle phase .run.arguments=..."
   - Use the helper script; it avoids Windows quoting issues by setting SERVER_PORT instead of passing -Drun arguments.
- SSL issues with local mysql client
   - Add `--ssl-mode=REQUIRED` (and optionally `--ssl-ca=ca.pem`). The app uses `sslMode=REQUIRED` already.

## License
MIT

---
CollaboDraw — collaborate visually in real time.
