package com.app.streaming.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePasswordRequest {
    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // Getter and Setter
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
