package com.example.collabodraw.controller;

import com.example.collabodraw.exception.UserAlreadyExistsException;
import com.example.collabodraw.model.dto.UserRegistrationDto;
import com.example.collabodraw.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for authentication-related endpoints
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/auth")
    public String loginPage(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password. Please try again.");
        }
        return "auth";
    }

    @PostMapping("/register")
    public String register(@Valid UserRegistrationDto user, Model model) {
        try {
            userService.registerUser(user);
            model.addAttribute("message", "Registration successful! Please log in.");
        } catch (UserAlreadyExistsException e) {
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "Registration failed: " + e.getMessage());
        }
        return "auth";
    }
}
