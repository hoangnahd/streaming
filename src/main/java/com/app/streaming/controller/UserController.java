package com.app.streaming.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.app.streaming.DTO.UserResponseDTO;


@RestController 
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> dashboard(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        
        // FIX: Use .orElse() to safely extract the string from the Optional container
        String role = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst()
            .orElse("ROLE_USER");

        UserResponseDTO userResponse = new UserResponseDTO(username, role);

        return ResponseEntity.ok(userResponse);
    }
}
