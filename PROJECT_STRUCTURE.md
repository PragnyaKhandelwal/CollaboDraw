# CollaboDraw - Project Structure

This document outlines the current project structure after cleanup and Java backend migration.

## 📁 Current Directory Structure

```
CollaboDraw/
├── src/
│   ├── main/
│   │   ├── java/com/example/collabodraw/
│   │   │   ├── CollaboDrawApplication.java          # Main Spring Boot application
│   │   │   ├── config/
│   │   │   │   ├── DatabaseConfig.java             # Database configuration
│   │   │   │   └── WebConfig.java                  # Web MVC configuration
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java             # Authentication endpoints
│   │   │   │   ├── BoardController.java            # Board operations (create, share, duplicate, delete)
│   │   │   │   ├── HomeController.java             # Home page dashboard
│   │   │   │   ├── MainScreenController.java       # Whiteboard interface
│   │   │   │   ├── MyContentController.java        # User's personal content
│   │   │   │   ├── RootController.java             # Root redirect handler
│   │   │   │   ├── SettingsController.java         # User settings and profile management
│   │   │   │   ├── SharedController.java           # Shared whiteboards
│   │   │   │   ├── TemplateController.java         # Template operations (use, preview)
│   │   │   │   └── TemplatesController.java        # Templates gallery page
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java     # Global exception handler
│   │   │   │   └── UserAlreadyExistsException.java # Custom user exceptions
│   │   │   ├── model/
│   │   │   │   ├── UserProfile.java                # User profile entity
│   │   │   │   └── dto/
│   │   │   │       ├── UserLoginDto.java           # Login form DTO
│   │   │   │       ├── UserRegistrationDto.java    # Registration form DTO
│   │   │   │       └── WhiteboardDto.java          # Whiteboard creation DTO
│   │   │   ├── repository/
│   │   │   │   ├── BoardMembershipRepository.java  # Board membership operations
│   │   │   │   ├── BoardRepository.java            # Board data operations
│   │   │   │   ├── ElementRepository.java          # Board elements operations
│   │   │   │   └── UserRepository.java             # User data operations
│   │   │   ├── security/
│   │   │   │   ├── MyUserDetailsService.java       # Custom user details service
│   │   │   │   └── SecurityConfig.java             # Spring Security configuration
│   │   │   └── service/
│   │   │       ├── UserService.java                # User business logic
│   │   │       └── WhiteboardService.java          # Whiteboard business logic
│   │   └── resources/
│   │       ├── application.properties               # Application configuration
│   │       ├── static/
│   │       │   ├── auth.js                         # Authentication JavaScript
│   │       │   ├── board-operations.js             # Board navigation (simplified)
│   │       │   ├── favicon.ico                     # Site favicon
│   │       │   ├── sidebar-toggle.js               # Sidebar functionality
│   │       │   ├── whiteboard.js                   # Whiteboard canvas logic
│   │       │   └── images/                         # UI icons and assets
│   │       │       ├── apps.png
│   │       │       ├── arrow-up-right.png
│   │       │       ├── circle (1).png
│   │       │       ├── clone.png
│   │       │       ├── duplicate.png
│   │       │       ├── file-edit.png
│   │       │       ├── file-export.png
│   │       │       ├── file.png
│   │       │       ├── hand-paper.png
│   │       │       ├── house-chimney.png
│   │       │       ├── interrogation.png
│   │       │       ├── link-alt.png
│   │       │       ├── minus-small.png
│   │       │       ├── move.png
│   │       │       ├── pen-nib.png
│   │       │       ├── pencil.png
│   │       │       ├── plus.png
│   │       │       ├── Preview.png
│   │       │       ├── rectangle-horizontal (1).png
│   │       │       ├── redo-alt.png
│   │       │       ├── refer-arrow.png
│   │       │       ├── search.png
│   │       │       ├── settings.png
│   │       │       ├── slash (2).png
│   │       │       ├── text (1).png
│   │       │       ├── undo-alt.png
│   │       │       └── user.png
│   │       └── templates/
│   │           ├── auth.html                       # Authentication page
│   │           ├── home.html                       # Home page dashboard
│   │           ├── mainscreen.html                 # Main whiteboard interface
│   │           ├── my-content.html                 # User's personal content
│   │           ├── settings.html                   # User settings and profile
│   │           ├── shared.html                     # Shared whiteboards
│   │           └── templates.html                  # Templates gallery
│   └── test/
│       └── java/com/example/collabodraw/
│           └── whiteboard/
│               └── WhiteboardApplicationTests.java # Spring Boot tests
├── target/                                         # Maven build output (excluded from source control)
├── .gitignore                                      # Git ignore rules
├── mvnw                                            # Maven wrapper (Unix)
├── mvnw.cmd                                        # Maven wrapper (Windows)
├── pom.xml                                         # Maven project configuration
├── README.md                                       # Project documentation
├── ENDPOINT_IMPLEMENTATION_SUMMARY.md             # Endpoint implementation history
└── JAVA_BACKEND_MIGRATION_SUMMARY.md              # Backend migration history
```

## 🚀 Architecture Overview

### **Clean Java Backend Architecture**
- **Pure Java Business Logic**: All business operations moved from JavaScript to Java controllers
- **Proper MVC Pattern**: Clear separation between controllers, services, and repositories
- **Spring Boot Best Practices**: Dependency injection, configuration management, and security
- **Simplified Frontend**: JavaScript reduced to navigation and UI interactions only

### **Key Components**

#### **Controllers (Web Layer)**
- **AuthController**: Handles user authentication (login/signup)
- **HomeController**: Main dashboard with user boards and recent activity
- **BoardController**: Board CRUD operations (create, open, share, duplicate, delete)
- **TemplateController**: Template operations (use, preview)
- **TemplatesController**: Templates gallery page
- **SettingsController**: User profile and settings management
- **MyContentController**: User's personal content management
- **SharedController**: Shared whiteboards management
- **RootController**: Root URL redirection

#### **Services (Business Layer)**
- **UserService**: User management, authentication, profile operations
- **WhiteboardService**: Board creation, management, and collaboration logic

#### **Repositories (Data Layer)**
- **UserRepository**: User data persistence
- **BoardRepository**: Board data operations
- **BoardMembershipRepository**: Board sharing and collaboration
- **ElementRepository**: Board content and drawing elements

#### **Security**
- **SecurityConfig**: Spring Security configuration for authentication and authorization
- **MyUserDetailsService**: Custom user authentication service

#### **Models & DTOs**
- **UserProfile**: User entity for profile management
- **DTOs**: Data transfer objects for form handling and API communication

## 🗑️ Cleaned Up Components

### **Removed Unused Classes**
- ~~`ModelImports.java`~~ - Utility class not referenced anywhere
- ~~`DatabaseProcedureService.java`~~ - Service not used by any component
- ~~`SessionRepository.java`~~ - Repository not referenced by any service
- ~~`CursorRepository.java`~~ - Repository not used in current implementation
- ~~`ActivityLogRepository.java`~~ - Repository not integrated with business logic
- ~~`util/` package~~ - Entire package removed as it contained only unused utilities

### **Cleaned Files**
- ~~`hs_err_pid14328.log`~~ - JVM error log removed from source control
- **target/** directory properly managed (build artifacts not tracked)

## 🔄 Migration Summary

### **Before Cleanup**
- Mixed JavaScript/Java business logic
- Multiple unused repository classes
- Utility classes without references
- Error logs in source control
- Complex frontend with business logic

### **After Cleanup**
- **Pure Java backend** with Spring Boot best practices
- **Simplified JavaScript** for navigation only
- **Clean architecture** with proper layer separation
- **No unused components** cluttering the codebase
- **Proper separation of concerns** between frontend and backend

## 🎯 Current Status

### **✅ Completed**
- Complete endpoint coverage for all user operations
- Profile management with initials display and database integration
- Template navigation fixed with proper user authentication
- Clean Java backend architecture with business logic properly separated
- Project cleanup with removal of all unused components

### **🚀 Ready for Development**
- Proper foundation for adding new features
- Clean, maintainable codebase
- Well-structured Spring Boot application
- Documentation updated to reflect current state

## 📋 Development Guidelines

### **Adding New Features**
1. **Controller**: Add endpoint in appropriate controller class
2. **Service**: Implement business logic in service layer
3. **Repository**: Add data operations if needed
4. **Frontend**: Update templates and add minimal JavaScript for navigation
5. **Testing**: Add unit tests for service layer logic

### **Code Organization**
- Keep business logic in Java services
- Use JavaScript only for UI interactions and navigation
- Follow established patterns for authentication and error handling
- Maintain proper separation between layers

---

This structure represents a clean, maintainable Spring Boot application following Java best practices with a clear separation between backend business logic and frontend presentation.