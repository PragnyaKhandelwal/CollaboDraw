package com.example.collabodraw.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root controller for handling the main entry point
 */
@Controller
public class RootController {

    @GetMapping("/")
    public String root(Authentication authentication) {
        // If user is authenticated, redirect to home
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/home";
        }
        // Otherwise, redirect to auth page
        return "redirect:/auth";
    }
}