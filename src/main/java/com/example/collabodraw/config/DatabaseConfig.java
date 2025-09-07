package com.example.collabodraw.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Database configuration class
 */
@Configuration
@Profile("!test")
public class DatabaseConfig {
    // Database-specific configurations can be added here
    // Currently using application.properties for database configuration
}
