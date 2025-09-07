package com.example.collabodraw.service;

import com.example.collabodraw.exception.UserAlreadyExistsException;
import com.example.collabodraw.model.dto.UserRegistrationDto;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for User-related business logic
 */
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(UserRegistrationDto registrationDto) {
        // Check if user already exists
        if (userRepository.findByUsername(registrationDto.getUsername()) != null) {
            throw new UserAlreadyExistsException("Username already exists: " + registrationDto.getUsername());
        }
        
        if (userRepository.findByEmail(registrationDto.getEmail()) != null) {
            throw new UserAlreadyExistsException("Email already exists: " + registrationDto.getEmail());
        }

        // Create new user
        User user = new User(
            registrationDto.getUsername(),
            registrationDto.getEmail(),
            passwordEncoder.encode(registrationDto.getPassword())
        );

        int result = userRepository.save(user);
        if (result <= 0) {
            throw new RuntimeException("Failed to save user");
        }
    
        return user;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findById(Long id) {
        return userRepository.findById(id);
    }
}
