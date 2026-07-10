package com.example.collabodraw.security;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
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
        User user;
        try {
            user = userRepository.findByUsername(username);
        } catch (DataAccessException ex) {
            throw new AuthenticationServiceException("Database unavailable", ex);
        }
        if (user == null) {
            // Intentionally do not log the attempted username: logging it (or a hash) on
            // every failed login enables credential harvesting from log/console access and
            // username enumeration. Authentication failures are already surfaced to Spring
            // Security's own auth failure handling.
            throw new UsernameNotFoundException("User not found");
        }

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPasswordHash(),
            Collections.emptyList()
        );
    }
}
