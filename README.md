# CollaboDraw
<img width="200" height="200" alt="image" src="https://github.com/user-attachments/assets/a2a7099a-569e-46b5-a146-10d973197b79" />



A real-time collaborative whiteboard built with Spring Boot, STOMP over WebSockets, Thymeleaf, and MySQL.
Multiple users can draw on the same board, see presence and cursor updates, and collaborate live.

## Features

- Real-time board events via STOMP channels (presence, cursor, element, version).
- Board collaboration flows (join/leave/heartbeat) with shared updates.
- Form login plus Google OAuth2 login.
- Clean MVC + service + repository backend structure.
- H2 dev profile for fast local testing without external DB.

## Application Screens
### Home
- Access boards
- Navigate to shared content
- Manage personal workspace
<img width="1918" height="1021" alt="image" src="https://github.com/user-attachments/assets/af50c2d2-849e-4e22-b7a6-8bb15ffb1684" />


### Whiteboard

- Draw shapes, lines, freehand sketches
- See other users’ cursors in real time
- Sync updates instantly across clients
<img width="1918" height="1026" alt="image" src="https://github.com/user-attachments/assets/f04cae0b-a34f-40dd-a98b-ad0def7c6843" />


 ### Live Collaboration 
- Track active users on board
- Real-time cursor movement
- Presence indicators

### Authentication (Login / OAuth)
- Secure login with Spring Security
- Google OAuth2 integration

<img width="1918" height="1008" alt="image" src="https://github.com/user-attachments/assets/f1314575-bb6b-4bda-98b0-17bfc4d17ac1" />

<img width="1918" height="1017" alt="image" src="https://github.com/user-attachments/assets/9f31c4ef-d82b-40dd-89f5-18050a8f8614" />

<img width="1754" height="717" alt="image" src="https://github.com/user-attachments/assets/a88447b9-a9a0-487b-9e1b-8f387dfcd905" />


## Tech Stack

- Java 21 (LTS), Maven Wrapper
- Spring Boot 3.5.7, Spring Security 6
- Thymeleaf + static JavaScript
- WebSocket/STOMP endpoints: `/ws`, `/app`, `/topic`, `/queue`
- MySQL (Aiven/hosted) and H2 (dev/test)

## Quick Start (First Run)

1. Clone the repo.
2. Ensure Java 21 is installed.
3. Copy `.env.example` to `.env` and fill your values.
4. Start using one of the run modes below.

### Prerequisites

- Windows PowerShell 5.1+ (for helper scripts)
- Java 21 on PATH (`java -version`)
- Maven is optional (wrapper included)

## Environment Variables

Database variables used by `application.properties`:

- `DB_HOST`, `DB_PORT`, `DB_NAME` (or `AIVEN_HOST`, `AIVEN_PORT`, `AIVEN_DB`)
- `DB_USER`, `DB_PASS`
- `SSL_MODE` (default `REQUIRED`)

OAuth variables:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

Important:

- OAuth is currently configured as mandatory at startup in this codebase.
- If OAuth values are missing, startup may fail.

## Local Run Modes

### Mode A: Production-like (MySQL / Aiven)

Use the helper script:

```powershell
./scripts/run-aiven.ps1
./scripts/run-aiven.ps1 -Port 8081
./scripts/run-aiven.ps1 -RunTests
./scripts/run-aiven.ps1 -DevFallback
```

### Mode B: Dev Profile (H2 in-memory)

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
& .\mvnw.cmd spring-boot:run
```

App URL: https://collabodraw-bdd2.onrender.com

## Build and Test

```powershell
& .\mvnw.cmd test
& .\mvnw.cmd -DskipTests package
java -jar .\target\whiteboard-0.0.1-SNAPSHOT.jar
```

## WebSocket Channels

- Handshake: `/ws`
- Send: `/app/board/{id}/join|leave|heartbeat|cursor|version|element`
- Subscribe:
  - `/topic/board.{id}.participants`
  - `/topic/board.{id}.cursors`
  - `/topic/board.{id}.versions`
  - `/topic/board.{id}.elements`

## Main Routes

- Public/Auth: `/auth`, `/login`, `/register`
- App pages: `/home`, `/board/{boardId}`, `/my-content`, `/settings`, `/templates`, `/shared`
- WebSocket: `/ws`

## Project Structure

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for the updated structure map.

## Deployment (Render)

This repo includes `render.yaml` (Docker service blueprint).

Set these environment variables in Render:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`, `SSL_MODE`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

Optional alternatives:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

## Contributing

New contributors are welcome.

1. Fork the repo.
2. Create a branch: `feature/<short-name>` or `fix/<short-name>`.
3. Keep PRs focused and include testing notes.
4. Run tests before opening a PR.
5. Open a Pull Request with screenshots/logs for UI or realtime behavior changes.

For collaboration ideas, check Issues or open a new issue with label suggestion:

- `good first issue`
- `help wanted`
- `enhancement`

## Open to Collaborate

If you are interested in collaborating on real-time systems, WebSockets, Spring Boot, or frontend whiteboard UX, feel free to open an issue or PR.

Maintainers are open to:

- feature collaboration
- bug-fix contributions
- performance and scalability improvements

## Security Notes

- Do not commit secrets (`.env`, DB credentials, OAuth keys).
- Use least-privilege DB users in hosted environments.
- Rotate credentials if exposed.

CollaboDraw: collaborate visually in real time.
