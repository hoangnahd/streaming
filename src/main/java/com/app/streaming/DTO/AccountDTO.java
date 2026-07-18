package com.app.streaming.DTO;

import java.time.LocalDateTime;

/**
 * Data Transfer Object used exclusively for displaying account details 
 * in the management table. It deliberately excludes password fields 
 * to prevent unauthorized data exposure.
 */
public class AccountDTO {

    private Long id;
    private String username;
    private String role;
    private LocalDateTime createdAt;

    // Default zero-argument constructor (Required for JSON/Framework serialization)
    public AccountDTO() {
    }

    // All-arguments constructor for convenient instantiation in your service/repository layer
    public AccountDTO(Long id, String username, String role, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.createdAt = createdAt;
    }

    // ==========================================
    // Getters and Setters
    // ==========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}