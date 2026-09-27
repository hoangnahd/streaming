package com.app.streaming.DTO;

public class UserResponseDTO {
    public String username;
    public String role;
    public UserResponseDTO() {}
    public UserResponseDTO(String username, String role) {
        this.username = username;
        this.role = role;
    }
    
    public String getUsername() {return this.username;}
    public void setUsername(String username) {this.username = username;}

    public String getRole() {return this.role;}
    public void setRole(String role) {this.role = role;}
}
