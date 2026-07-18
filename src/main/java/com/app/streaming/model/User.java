package com.app.streaming.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "users")
public class User {
    // Attributes
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    @NotNull
    private String username;
    @Column(nullable = false, length = 60)
    @NotNull
    private String password;
    @Column(nullable = false)
    @NotNull
    private String role = "USER";
    @CreationTimestamp
    private LocalDateTime createdAt;
    // Constuctor
    public User() {}
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
    // Getter
    public Long getId() {return this.id;}
    public String getUsername() {return this.username;}
    public String getPassword() {return this.password;}
    public String getRole() {return this.role;}
    public LocalDateTime getCreatedAt() {return this.createdAt;}
    // Setter
    public void setUsername(String username) {this.username = username;}
    public void setPassword(String password) {this.password = password;}
    public void setRole(String role) {this.role = role;}
}
