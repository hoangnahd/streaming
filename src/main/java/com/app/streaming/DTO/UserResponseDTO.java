package com.app.streaming.DTO;

public class UserResponseDTO {
    public String username;
    public UserResponseDTO() {}
    public UserResponseDTO(String username) {
        this.username = username;
    }
    
    public String getUsername() {return this.username;}
    public void setUsername(String username) {this.username = username;}
}
