package com.example.collabodraw.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLTransientConnectionException;
import java.util.Map;

/**
 * Global exception handler for the application.
 * - Returns JSON 503 for DB connectivity issues (to keep APIs predictable when MySQL is down).
 * - Preserves existing MVC error flows for validation and generic exceptions.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // JSON response for DB connectivity issues
    @ExceptionHandler({CannotGetJdbcConnectionException.class,
            SQLTransientConnectionException.class})
    public ResponseEntity<Map<String, Object>> handleDbConnectivity(Exception ex) {
        log.error("Database connectivity error", ex);
        String hint = "Database is unreachable. For local dev, run with profile 'dev' (H2): " +
                "mvn spring-boot:run -Dspring-boot.run.profiles=dev";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "error", "database_unavailable",
                        "message", "The database is temporarily unavailable. Please try again shortly.",
                        "hint", hint
                ));
    }

    // Existing MVC flows
    @ExceptionHandler(BindException.class)
    public String handleValidationException(BindException ex, Model model) {
        StringBuilder errors = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.append(error.getDefaultMessage()).append("; ")
        );
        model.addAttribute("error", errors.toString());
        return "auth";
    }

    // Missing routes/static files throw this; without a dedicated handler it falls into
    // the catch-all below and gets redirected to /auth or /home instead of rendering the
    // templates/error/404.html view. Rethrowing hands it back to Spring Boot's normal
    // error-view resolution, which picks that template.
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFound(NoResourceFoundException ex) throws NoResourceFoundException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request,
            RedirectAttributes redirectAttributes, Authentication authentication) {
        // Never echo ex.getMessage()/class back to the client here: for /api/** most routes
        // are reached before authentication is enforced, so any leaked detail (SQL text,
        // table/column names, stack internals) is handed to an anonymous caller. Full detail
        // goes to the server log only, keyed by a correlation id the client can reference.
        String correlationId = Long.toHexString(System.nanoTime());
        log.error("Unhandled exception [{}] on {} {}", correlationId, request.getMethod(), request.getRequestURI(), ex);

        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "internal_error",
                            "message", "Something went wrong on our end. Please try again.",
                            "reference", correlationId
                    ));
        }
        redirectAttributes.addFlashAttribute("error", "An unexpected error occurred. Reference: " + correlationId);
        // An authenticated user hitting a bug mid-session should land back in the app, not
        // get bounced to what looks like a logged-out screen; only anonymous users go to /auth.
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated();
        return "redirect:" + (isAuthenticated ? "/home" : "/auth");
    }
}
