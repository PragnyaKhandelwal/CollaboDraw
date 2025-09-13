package com.example.collabodraw.whiteboard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user@abc")
                .password("1234") // Plain text password (not recommended for prod)
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disabled CSRF for easier testing. Enable in production!
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/images/**", "/css/**", "/js/**", "/auth.js", "/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                    .loginPage("/auth")
                    .loginProcessingUrl("/auth/login")
                    .defaultSuccessUrl("/", true)
                    .permitAll())
            .oauth2Login(oauth2 -> oauth2
                    .loginPage("/auth")
                    .defaultSuccessUrl("/", true))
            .logout(logout -> logout.permitAll());
        return http.build();
    }

    @SuppressWarnings("deprecation")
    @Bean
    public static NoOpPasswordEncoder passwordEncoder() {
        return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance(); // For plain passwords
    }
}