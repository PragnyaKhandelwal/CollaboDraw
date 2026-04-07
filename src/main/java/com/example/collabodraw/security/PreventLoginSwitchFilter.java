package com.example.collabodraw.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class PreventLoginSwitchFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isLoginPost = "POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath());
        if (!isLoginPost) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean alreadyAuthenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (!alreadyAuthenticated) {
            filterChain.doFilter(request, response);
            return;
        }

        String currentUser = authentication.getName();
        String attemptedUser = request.getParameter("username");

        // Block account switching in the same browser session to avoid cross-tab identity confusion.
        if (attemptedUser != null && !attemptedUser.isBlank()
                && currentUser != null
                && !currentUser.equalsIgnoreCase(attemptedUser.trim())) {
            String currentEncoded = URLEncoder.encode(currentUser, StandardCharsets.UTF_8);
            response.sendRedirect("/auth?error=alreadyLoggedIn&currentUser=" + currentEncoded);
            return;
        }

        // If the same user submits login again while already authenticated, just keep current session.
        response.sendRedirect("/home");
    }
}
