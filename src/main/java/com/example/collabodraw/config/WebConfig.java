package com.example.collabodraw.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is configured in a single place: {@link com.example.collabodraw.security.SecurityConfig}
 * (app.cors.allowed-origins). This class previously also registered its own, different
 * allowed-origin list via addCorsMappings(), which was redundant and could silently drift out
 * of sync with the security-layer config - removed rather than kept as a second source of truth.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Static resources
        if (!registry.hasMappingForPattern("/static/**")) {
            registry.addResourceHandler("/static/**")
                    .addResourceLocations("classpath:/static/")
                    .setCachePeriod(3600);
        }
        
        // Images
        if (!registry.hasMappingForPattern("/images/**")) {
            registry.addResourceHandler("/images/**")
                    .addResourceLocations("classpath:/static/images/")
                    .setCachePeriod(3600);
        }
        
        // Favicon
        if (!registry.hasMappingForPattern("/favicon.ico")) {
            registry.addResourceHandler("/favicon.ico")
                    .addResourceLocations("classpath:/static/")
                    .setCachePeriod(3600);
        }
        
        // JavaScript files
        if (!registry.hasMappingForPattern("/*.js")) {
            registry.addResourceHandler("/*.js")
                    .addResourceLocations("classpath:/static/")
                    .setCachePeriod(3600);
        }
    }
}