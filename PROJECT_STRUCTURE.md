# CollaboDraw Project Structure

This document is a quick navigation guide for contributors.
It reflects the current repository layout and where to add changes.

## Top-Level

```
CollaboDraw/
├─ src/                         # Application source and tests
├─ scripts/                     # Local run helpers (PowerShell)
├─ .env.example                 # Environment template
├─ Dockerfile                   # Container build
├─ render.yaml                  # Render deployment blueprint
├─ pom.xml                      # Maven project definition
├─ mvnw / mvnw.cmd              # Maven wrapper scripts
├─ README.md                    # Main documentation
└─ PROJECT_STRUCTURE.md         # This file
```

## Backend (Spring Boot)

```
src/main/java/com/example/collabodraw/
├─ CollaboDrawApplication.java
├─ config/
│  ├─ DatabaseConfig.java
│  ├─ GlobalModelAttributes.java  # injects pageTheme etc. into every template render
│  ├─ WebConfig.java
│  ├─ WebSocketAuthorizationInterceptor.java  # gates STOMP subscriptions by board membership
│  └─ WebSocketConfig.java
├─ controller/                  # Web/MVC controllers, incl. WhiteboardController
├─ exception/                   # GlobalExceptionHandler + custom exceptions
├─ model/                       # Entities (model/entity) and DTOs (model/dto)
├─ realtime/                    # EventStore interface + InMemoryEventStore - see ARCHITECTURE.md
├─ repository/                  # Data access layer (raw JDBC, not JPA/Spring Data)
├─ security/                    # Security config and auth services
└─ service/                     # Business logic
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for why `realtime/` is a separate package and what
depending on `EventStore` (interface) instead of a concrete class buys you later.

## Frontend Assets and Views

```
src/main/resources/
├─ application.properties        # shared config; profile-specific: application-{aiven,dev}.properties
├─ collaborative_workspace_mysql.sql
├─ static/
│  ├─ auth.js, board-operations.js, sidebar-toggle.js
│  ├─ css/
│  │  ├─ theme.css                # shared :root design tokens - single source of truth
│  │  └─ theme-dark.css           # dark-mode overrides layered on top of theme.css
│  ├─ js/
│  │  ├─ collab-socket.js, csrf-fetch.js
│  │  └─ notification-bell.js, notification-service.js
│  ├─ modules/                    # mainscreen.html's editor, split by concern
│  │  ├─ canvas.js, drawing.js, elements.js, history.js
│  │  ├─ init.js, realtime.js, state.js, storage.js, ui.js
│  └─ images/
└─ templates/
   ├─ auth.html, home.html, mainscreen.html, my-content.html,
   │  settings.html, shared.html, templates.html
   ├─ error.html, error/404.html  # branded error pages, theme-aware
   └─ fragments/
      └─ icons.html               # shared SVG icon sprite (th:fragment="sprite")
```

## Tests

```
src/test/java/com/example/collabodraw/
├─ security/
├─ websocket/
└─ whiteboard/
```

## Where to Make Changes

- Add endpoint/controller logic: `src/main/java/com/example/collabodraw/controller/`
- Add business rules: `src/main/java/com/example/collabodraw/service/`
- Add DB queries/repositories: `src/main/java/com/example/collabodraw/repository/`
- Add websocket protocol changes: `src/main/java/com/example/collabodraw/config/WebSocketConfig.java` and `src/main/resources/static/js/collab-socket.js`
- Add/reuse an icon: `src/main/resources/templates/fragments/icons.html` (add a `<symbol>`, then reference it anywhere with `<svg class="icon"><use href="#icon-name"></use></svg>`)
- Add page UI and templates: `src/main/resources/templates/`
- Add static client behavior: `src/main/resources/static/`
- Add tests: `src/test/java/com/example/collabodraw/`

## Contributor Notes

- Keep layers separated: controller -> service -> repository.
- Avoid committing secrets (`.env`, credentials, keys).
- Run tests before opening a pull request.
- Keep pull requests focused and small where possible.