package com.example.collabodraw.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
        log.error("Database connectivity error: {}", ex.getMessage());
        String hint = "Database is unreachable. For local dev, run with profile 'dev' (H2): " +
                "mvn spring-boot:run -Dspring-boot.run.profiles=dev";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "error", "database_unavailable",
                        "message", ex.getMessage(),
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

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "An unexpected error occurred: " + ex.getMessage());
        return "redirect:/auth";
    }
}
