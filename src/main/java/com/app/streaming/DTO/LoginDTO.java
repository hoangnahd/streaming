package com.app.streaming.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class LoginDTO {
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
    // Constuctor
    public LoginDTO() {}
    public LoginDTO(String username, String password) {
        this.username = username;
        this.password = password;
    }
    // Getter
    public String getUsername() {return this.username;}
    public String getPassword() {return this.password;}
    // Setter
    public void setUsername(String username) {this.username = username;}
    public void setPassword(String password) {this.password = password;}
}
