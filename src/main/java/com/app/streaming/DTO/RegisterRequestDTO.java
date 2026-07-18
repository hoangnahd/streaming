package com.app.streaming.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequestDTO {
    // Attributes
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 30, message = "Name must be between 3 and 30 charater")
    @Pattern(
        regexp = "^[a-zA-Z0-9]+$", 
        message = "Username must only contain letters and numbers (no spaces or special characters)"
    )
    private String username;
    @Size(min = 8, max = 12)
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
        message = "Password must contain at least one digit, one lowercase letter, one uppercase letter, and one special character"
    )
    private String password;

    private String confirmedPassword;

    // private String role;

    public RegisterRequestDTO() {}

    public RegisterRequestDTO(String username, String password, String confirmedPassword, String role) {
        this.username = username;
        this.password = password;
        this.confirmedPassword = confirmedPassword;
        // this.role = role;
    }

    // Getter
    public String getUsername() {return this.username;}
    public String getPassword() {return this.password;}
    public String getConfirmedPassword() {return this.confirmedPassword;}
    // public String getRole() {return this.role;}
    // Setter
    public void setUsername(String username) {this.username = username;}
    public void setPassword(String password) {this.password = password;}
    public void setConfirmedPassword(String confirmedPassword) {this.confirmedPassword = confirmedPassword;}
    // public void setRole(String role) {this.role = role;}
}
