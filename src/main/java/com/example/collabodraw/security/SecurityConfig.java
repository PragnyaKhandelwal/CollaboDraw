package com.example.collabodraw.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final MyUserDetailsService myUserDetailsService;
    private final PreventLoginSwitchFilter preventLoginSwitchFilter;

    public SecurityConfig(MyUserDetailsService myUserDetailsService,
                          PreventLoginSwitchFilter preventLoginSwitchFilter) {
        this.myUserDetailsService = myUserDetailsService;
        this.preventLoginSwitchFilter = preventLoginSwitchFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(myUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        // We now ALWAYS enable oauth2Login; if client registrations are absent Spring will fail fast,
        // making misconfiguration visible instead of silently bypassing Google OAuth.
        boolean oauthAvailable = clientRegistrations.getIfAvailable() != null;
        http
            // Disable CSRF for REST API
            .csrf(csrf -> csrf.disable())
            
            // Authorization rules - ORDER MATTERS!
            .authorizeHttpRequests(authz -> authz
                // ✅ STATIC RESOURCES FIRST (Most Important!)
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/css/**").permitAll()
                .requestMatchers("/js/**").permitAll()
                .requestMatchers("/images/**").permitAll()
                .requestMatchers("/fonts/**").permitAll()
                .requestMatchers("/*.js").permitAll()
                .requestMatchers("/*.css").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/error").permitAll()
                
                // ✅ PUBLIC ENDPOINTS
                .requestMatchers("/auth", "/login", "/register").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // OAuth2 endpoints (safe even if disabled)
                
                // ✅ API ENDPOINTS
                .requestMatchers("/api/drawings/**", "/api/boards/**").permitAll()
                
                // ✅ PROTECTED ENDPOINTS
                .requestMatchers("/home", "/board/**", "/my-content", "/settings", "/templates", "/shared").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                    new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**")
                )
            )

            // ✅ FORM LOGIN
            .formLogin(form -> form
                .loginPage("/auth")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/home", true)
                .failureHandler((request, response, exception) -> {
                    String target = (exception instanceof AuthenticationServiceException ||
                            (exception.getCause() != null && exception.getCause() instanceof AuthenticationServiceException))
                            ? "/auth?error=dbUnavailable"
                            : "/auth?error=Invalid%20credentials";
                    try {
                        response.sendRedirect(target);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )

            // ✅ LOGOUT
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/auth")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            
            // ✅ REMEMBER ME
            .rememberMe(remember -> remember
                .key("collabodraw-secret-key")
                .tokenValiditySeconds(604800) // 7 days
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("collabodraw-remember-me")
            )
            
            // ✅ CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterBefore(preventLoginSwitchFilter, UsernamePasswordAuthenticationFilter.class);

        // Always configure OAuth2 login. If registrations are missing, startup will surface an error instead of silently bypassing.
        http.oauth2Login(oauth -> oauth
            .loginPage("/auth")
            .defaultSuccessUrl("/home", true)
            .failureUrl("/auth?error=OAuth2%20login%20failed")
        );
            
        return http.build();
    }

    /**
     * CORS Configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
