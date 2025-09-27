# CollaboDraw - Real-Time Collaborative Whiteboard

CollaboDraw is a cloud-based, real-time collaborative whiteboard application built with **Spring Boot 3.5.6** and **Google Cloud SQL**. It enables multiple users to draw, interact, and brainstorm on a shared digital canvas simultaneously with seamless authentication and data persistence.

## 🚀 Features

- **Real-time collaboration** on shared whiteboards
- **Secure session management** with Spring Security

## 🏗️ Project Architecture

### **Layered Architecture**
```
src/main/java/com/example/collabodraw/
├── CollaboDrawApplication.java          # Main Spring Boot application
├── config/                              # Configuration classes
│   ├── DatabaseConfig.java             # Database configuration
│   └── WebConfig.java                  # Web MVC configuration
├── controller/                          # REST/Web controllers
│   ├── AuthController.java             # Authentication endpoints
│   ├── HomeController.java             # Home page dashboard
│   ├── MainScreenController.java       # Whiteboard interface
│   ├── BoardController.java            # Board operations (create, share, etc.)
│   ├── TemplateController.java         # Template operations (use, preview)
│   ├── TemplatesController.java        # Templates gallery page
│   ├── SettingsController.java         # User settings and profile
│   ├── MyContentController.java        # User's personal content
│   ├── SharedController.java           # Shared whiteboards
│   └── RootController.java             # Root redirect handler
├── service/                            # Business logic layer
│   ├── UserService.java                # User business logic
│   └── WhiteboardService.java          # Whiteboard business logic
├── repository/                         # Data access layer (JDBC)
│   ├── UserRepository.java             # User data operations

### Connecting to Cloud SQL Automatically (Local)
Use the helper script to start the Cloud SQL proxy and run the app:

PowerShell (Windows):
```
setx CLOUD_SQL_INSTANCE "<project>:<region>:<instance>"
$Env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\to\service-account.json"
$Env:DB_USER="yourdbuser"
$Env:DB_PASS="yourdbpass"
./scripts/start-local-with-cloudsql.ps1
```
The script will:
- Download the Cloud SQL Proxy binary if missing
│   ├── BoardRepository.java            # Board data operations
│   ├── BoardMembershipRepository.java  # Board membership operations
│   └── ElementRepository.java          # Board elements operations
- Start it on localhost:3307
- Export DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASS for Spring Boot
- Launch `mvn spring-boot:run`

Datasource is now parameterized via environment variables (see `application.properties`).

If you already have a tunnel or proxy running, you can simply run:
```
mvn spring-boot:run
```
│   ├── BoardRepository.java            # Board data operations
│   ├── BoardMembershipRepository.java  # Board membership operations
│   └── ElementRepository.java          # Board elements operations
├── model/                              # Data models and DTOs
│   ├── UserProfile.java                # User profile entity
│   └── dto/                            # Data Transfer Objects
│       ├── UserRegistrationDto.java    # Registration form DTO
│       ├── UserLoginDto.java           # Login form DTO
│       └── WhiteboardDto.java          # Whiteboard creation DTO
├── security/                           # Security configuration
│   ├── SecurityConfig.java             # Spring Security config
│   └── MyUserDetailsService.java       # Custom user details service
└── exception/                          # Exception handling
    ├── GlobalExceptionHandler.java     # Global exception handler
    └── UserAlreadyExistsException.java # Custom exceptions
```

### **Frontend Structure**
```
src/main/resources/
├── templates/                          # Thymeleaf templates
│   ├── auth.html                      # Authentication page
│   ├── home.html                      # Home page dashboard
│   ├── mainscreen.html                # Main whiteboard interface
│   ├── settings.html                  # User settings and profile
│   ├── my-content.html                # User's personal content
│   ├── shared.html                    # Shared whiteboards
│   └── templates.html                 # Templates gallery
├── static/                            # Static resources
│   ├── auth.js                        # Authentication JavaScript
│   ├── sidebar-toggle.js              # Sidebar functionality
│   ├── whiteboard.js                  # Whiteboard canvas logic
│   ├── board-operations.js            # Board navigation (simplified)
│   ├── favicon.ico                    # Site favicon
│   └── images/                        # UI icons and assets
└── application.properties             # Application configuration
```

## 🔧 Technology Stack

- **Backend**: Spring Boot 3.5.6, Spring Security, Spring JDBC
- **Database**: Google Cloud SQL (MySQL 8.0)
- **Authentication**: Google OAuth2, Form-based login
- **Frontend**: Thymeleaf, HTML5 Canvas, JavaScript, CSS3
- **Build Tool**: Maven
- **Java Version**: 21
- **Connection Pooling**: HikariCP

## 📊 Database Schema

### **Core Tables**
- **`users`**: User accounts and authentication data
- **`whiteboards`**: Collaborative whiteboard metadata
The application connects directly to Google Cloud SQL using the Cloud SQL Java Socket Factory (no manual proxy required for normal development):
- **Instance**: `test-e470b:asia-south2:collabodraw`
- **Database (example)**: `collaborative_workspace_db`
- **Local fallback**: Run a local MySQL or optional proxy on `127.0.0.1:3307` when `CLOUD_SQL_INSTANCE` is not set
- Whiteboards have multiple participants (1:N)

## ⚙️ Configuration

### **Cloud SQL Setup**
The application connects to Google Cloud SQL via Cloud SQL Proxy:
- **Instance**: `test-e470b:asia-south2:collabodraw`
- **Local Proxy Port**: 3307
- **Database**: `collabodraw`
- **Auto-create**: Enabled via `createDatabaseIfNotExist=true`

### **OAuth2 Configuration**
Google OAuth2 is configured for secure authentication:
- Client ID and Secret configured in `application.properties`
- Scope: `openid,profile,email`
- Redirect handling via Spring Security

### **Connection Pool**
HikariCP configuration optimized for Cloud SQL:
- Maximum pool size: 10 connections
- Minimum idle: 5 connections
- Connection timeout: 20 seconds
- Max lifetime: 20 minutes

## 🚀 Getting Started

### **Prerequisites**
- Java 21 or higher
- Maven 3.6+
- Google Cloud SQL Proxy running on port 3307
2. **Set required environment variables (Direct Cloud SQL)**
   ```bash
   # Bash / Linux / macOS
   export CLOUD_SQL_INSTANCE=test-e470b:asia-south2:collabodraw
   export DB_NAME=collaborative_workspace_db
   export DB_USER=your_db_user
   export DB_PASS=your_db_password
   ```
   PowerShell:
   ```powershell
   $env:CLOUD_SQL_INSTANCE="test-e470b:asia-south2:collabodraw"
   $env:DB_NAME="collaborative_workspace_db"
   $env:DB_USER="your_db_user"
   $env:DB_PASS="your_db_password"
   ```
1. **Clone the repository**
   ```bash
   git clone https://github.com/PragnyaKhandelwal/CollaboDraw.git
   cd CollaboDraw
   ```

2. **Configure Cloud SQL Proxy**
   ```bash
   cloud_sql_proxy -instances=test-e470b:asia-south2:collabodraw=tcp:3307
   ```

3. **Build the application**
   ```bash
   mvn clean compile
   ```

Automatic database creation has been removed. Ensure the schema & tables already exist in Cloud SQL (or manage via migrations). The app will fail fast if objects are missing.
   ```bash
## 🗄️ Database Connectivity Modes

| Mode | Description | Activate By |
|------|-------------|-------------|
| Direct Cloud SQL (default) | Secure socket-based connection (no local proxy) | Set `CLOUD_SQL_INSTANCE`, `DB_USER`, `DB_PASS`, `DB_NAME` |
| Local / Proxy | Use local MySQL or optional proxy | Omit `CLOUD_SQL_INSTANCE`; set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS` |
| Local Profile | Dedicated property overrides | `SPRING_PROFILES_ACTIVE=local` |

Example Direct (PowerShell):
```powershell
gcloud auth application-default login
$env:CLOUD_SQL_INSTANCE="test-e470b:asia-south2:collabodraw"
$env:DB_NAME="collaborative_workspace_db"
$env:DB_USER="your_db_user"
$env:DB_PASS="your_db_password"
./mvnw spring-boot:run
```

Example Local (own MySQL instance):
```powershell
Remove-Item Env:CLOUD_SQL_INSTANCE -ErrorAction SilentlyContinue
$env:DB_HOST="127.0.0.1"; $env:DB_PORT="3306"
$env:DB_NAME="collaborative_workspace_db"
$env:DB_USER="root"; $env:DB_PASS="local_dev_pass"
./mvnw spring-boot:run
```

Optional legacy proxy helper:
```powershell
./scripts/start-local-with-cloudsql.ps1
```

### Troubleshooting
| Symptom | Likely Cause | Action |
|---------|--------------|--------|
| Repeated "instance closed the connection" | Wrong password or mixing direct/proxy modes | Verify env vars; ensure `CLOUD_SQL_INSTANCE` usage matches intent |
| Access denied | Invalid credentials or grants | Check MySQL grants; reset password |
| Unknown database | DB name mismatch | Correct `DB_NAME` or create schema |
| Socket creation failure | ADC / IAM problem | Run `gcloud auth application-default login` or set service account key |
| Hikari pool timeout | Network / firewall / private IP config | Confirm Cloud SQL authorized networks or private service access |
   mvn spring-boot:run
   ```

5. **Access the application**
   - URL: `http://localhost:8080`
   - Authentication: `http://localhost:8080/auth`

### **Database Initialization**
The application automatically creates the `collabodraw` database and required tables on first startup using the configured Cloud SQL connection.

## 🎯 Key Features Implementation

### **Authentication Flow**
1. User visits `/auth` for login/signup
2. OAuth2 login via Google or form-based authentication
3. Successful authentication redirects to `/home`
4. Session management via Spring Security

### **Whiteboard Management**
1. Users can create new whiteboards from dashboard
2. Real-time collaboration on shared canvas
3. Participant management and sharing
4. Persistent storage in Cloud SQL

### **Security Features**
- CSRF protection enabled
- Secure session management
- Password encryption with BCrypt
- OAuth2 integration with Google
- Role-based access control

## 🔄 Development Workflow

### **Adding New Features**
1. **Model**: Create/update entity classes in `model/`
2. **Repository**: Add data access methods in `repository/`
3. **Service**: Implement business logic in `service/`
4. **Controller**: Create endpoints in `controller/`
5. **Frontend**: Update templates and static resources

### **Testing**
- Unit tests for service layer logic
- Integration tests for repository operations
- End-to-end testing for complete workflows

## 🚀 Deployment

### **Cloud Deployment Considerations**
- Cloud SQL connection via private IP
- Environment-specific configuration
- SSL/TLS certificates for production
- Container deployment with Docker
- Load balancing for high availability

## 🔮 Future Enhancements

### **Planned Features**
- Real-time drawing synchronization with WebSockets
- Advanced drawing tools (shapes, text, colors)
- File upload and image insertion
- Whiteboard templates and sharing
- User profiles and preferences
- Mobile-responsive design improvements
- API for third-party integrations

### **Technical Improvements**
- Microservices architecture migration
- Redis caching for session management
- Message queues for real-time updates
- Advanced monitoring and logging
- Performance optimization
- Automated testing pipeline

## 📝 API Documentation

### **Authentication Endpoints**
- `GET /auth` - Login/Signup page
- `POST /auth/signup` - User registration
- `POST /auth/signin` - User login
- `GET /auth/logout` - User logout

### **Application Endpoints**
- `GET /home` - Dashboard (authenticated)
- `GET /mainscreen` - Whiteboard interface
- `GET /settings` - User settings and profile
- `GET /my-content` - User's personal content
- `GET /shared` - Shared whiteboards
- `GET /templates` - Templates gallery

### **Board Management Endpoints**
- `POST /board/open` - Open a board
- `POST /board/share` - Share a board
- `POST /board/duplicate` - Duplicate a board
- `POST /board/delete` - Delete a board
- `POST /board/copy-shared` - Copy a shared board
- `POST /board/leave` - Leave a shared board

### **Template Endpoints**
- `POST /template/use` - Use a template
- `POST /template/preview` - Preview a template

### **Profile Management**
- `POST /settings/update-profile` - Update user profile

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Team

- **Developer**: Pragnya Khandelwal
- **Project**: 2nd Year SEM 3 PBL
- **Institution**: [Your Institution Name]

## 📞 Support

For support and questions:
- Create an issue in the GitHub repository
- Contact: [Your Email]

---

**CollaboDraw** - Bringing ideas to life through collaborative drawing! 🎨✨
