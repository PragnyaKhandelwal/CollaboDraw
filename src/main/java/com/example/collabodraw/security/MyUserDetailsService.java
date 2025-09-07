package com.example.collabodraw.security;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class MyUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public MyUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            System.out.println("DEBUG: No user found with username = " + username);
            throw new UsernameNotFoundException("User not found: " + username);
        }
            System.out.println("DEBUG: Found user " + username + " with hash = " + user.getPasswordHash());

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPasswordHash(),
            Collections.emptyList()
        );
    }
}
