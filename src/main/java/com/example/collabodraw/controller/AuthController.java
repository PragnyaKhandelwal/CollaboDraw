package com.example.collabodraw.controller;

import com.example.collabodraw.exception.UserAlreadyExistsException;
import com.example.collabodraw.model.dto.UserRegistrationDto;
import com.example.collabodraw.service.DatabaseHealthService;
import com.example.collabodraw.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

/**
 * Controller for authentication-related endpoints
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final DatabaseHealthService databaseHealthService;

    public AuthController(UserService userService, DatabaseHealthService databaseHealthService) {
        this.userService = userService;
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping("/auth")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "currentUser", required = false) String currentUser,
            @RequestParam(value = "tab", required = false) String tab,
            Authentication authentication,
            Model model) {
        boolean databaseAvailable = databaseHealthService.refresh();
        model.addAttribute("databaseAvailable", databaseAvailable);
        model.addAttribute("databaseMessage", databaseHealthService.getLastStatusMessage());
        model.addAttribute("databaseReason", databaseHealthService.getLastFailureReason());
        model.addAttribute("activeTab", (tab == null || tab.isBlank()) ? "login" : tab);

        if (!databaseAvailable && error == null) {
            model.addAttribute("error", "Aiven/MySQL is currently unavailable. " + databaseHealthService.getLastFailureReason());
        }

        if (message != null && !message.isBlank()) {
            model.addAttribute("message", message);
        }

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            model.addAttribute("message", "You are already logged in as " + authentication.getName() + ". Logout first to use another account in this browser.");
        }

        if (error != null) {
            if ("dbUnavailable".equals(error)) {
                model.addAttribute("error", "Aiven/MySQL is currently unavailable. " + databaseHealthService.getLastFailureReason());
            } else if ("alreadyLoggedIn".equals(error)) {
                String user = (currentUser != null && !currentUser.isBlank()) ? currentUser : "another account";
                model.addAttribute("error", "Already logged in as " + user + " in this browser session. Please logout first to switch users.");
            } else {
                model.addAttribute("error", "Invalid username or password. Please try again.");
            }
        }
        return "auth";
    }

    @PostMapping("/register")
    public String register(@Valid UserRegistrationDto user, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addAttribute("error", "Please check the registration form and try again.");
            redirectAttributes.addAttribute("tab", "signup");
            return "redirect:/auth";
        }

        try {
            userService.registerUser(user);
            redirectAttributes.addAttribute("message", "Registration successful! Please log in.");
            redirectAttributes.addAttribute("tab", "login");
            return "redirect:/auth";
        } catch (UserAlreadyExistsException e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("tab", "signup");
            return "redirect:/auth";
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", "Registration failed: " + e.getMessage());
            redirectAttributes.addAttribute("tab", "signup");
            return "redirect:/auth";
        }
    }
}
