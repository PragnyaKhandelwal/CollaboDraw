package com.example.collabodraw.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String DEFAULT_REMEMBER_ME_KEY = "collabodraw-dev-only-change-me";

    private final PreventLoginSwitchFilter preventLoginSwitchFilter;

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:3000,http://127.0.0.1:8080}")
    private String allowedOriginsProperty;

    @Value("${app.remember-me.key:" + DEFAULT_REMEMBER_ME_KEY + "}")
    private String rememberMeKey;

    public SecurityConfig(PreventLoginSwitchFilter preventLoginSwitchFilter) {
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
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        if (DEFAULT_REMEMBER_ME_KEY.equals(rememberMeKey)) {
            log.warn("app.remember-me.key is not set; using an insecure built-in default. " +
                    "Set app.remember-me.key (or REMEMBER_ME_KEY env var) to a random secret before deploying.");
        }

        // Spring Security 6's default CsrfTokenRequestHandler (XorCsrfTokenRequestAttributeHandler)
        // BREACH-protects the token by XOR-masking the value it hands to Thymeleaf forms - a
        // raw cookie value read by JS and sent back verbatim as a header will NOT match what
        // that handler expects, and every fetch() POST/PUT/PATCH/DELETE gets rejected with 403
        // even with a "correct" token (confirmed by hand while verifying this fix end to end).
        // The plain CsrfTokenRequestAttributeHandler skips that masking, which is the
        // documented approach when a cookie-delivered token also needs to work as a raw header
        // value for JS-driven requests.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
            // CSRF is enabled with a JS-readable cookie (XSRF-TOKEN) so the frontend can read
            // it and send it back as the X-XSRF-TOKEN header on state-changing fetch() calls.
            // /ws/** is exempted: STOMP/SockJS fallback transports make frequent XHR requests
            // of their own and are not classic browser form/navigation targets; the WebSocket
            // handshake's allowed-origin check (see WebSocketConfig) is the relevant defense there.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfRequestHandler)
                .ignoringRequestMatchers("/ws/**")
            )

            // Authorization rules - ORDER MATTERS!
            .authorizeHttpRequests(authz -> authz
                // STATIC RESOURCES FIRST (Most Important!)
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/css/**").permitAll()
                .requestMatchers("/js/**").permitAll()
                .requestMatchers("/images/**").permitAll()
                .requestMatchers("/fonts/**").permitAll()
                .requestMatchers("/*.js").permitAll()
                .requestMatchers("/*.css").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/manifest.json").permitAll()
                .requestMatchers("/error").permitAll()

                // PUBLIC ENDPOINTS
                .requestMatchers("/auth", "/login", "/register").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // OAuth2 endpoints (safe even if disabled)

                // Every /api/** route requires authentication; each controller additionally
                // checks board membership/role before touching board data. There is
                // intentionally no permitAll() carve-out under /api/** - a prior version of
                // this file exempted /api/drawings/** and /api/boards/**, which let anyone on
                // the internet read or overwrite any board's canvas with no login at all.

                // PROTECTED ENDPOINTS
                .requestMatchers("/home", "/board/**", "/my-content", "/settings", "/templates", "/shared").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                )
            )

            // FORM LOGIN
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

            // LOGOUT
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/auth")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .permitAll()
            )

            // REMEMBER ME
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .tokenValiditySeconds(604800) // 7 days
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("collabodraw-remember-me")
            )

            // CORS
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
     * CORS configuration. Allowed origins come from a single property (app.cors.allowed-origins,
     * comma-separated) instead of being duplicated across SecurityConfig and WebConfig with
     * different, conflicting values. Set ALLOWED_ORIGINS in the deployment environment to
     * include the production URL (e.g. the Render app URL) alongside local dev origins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
