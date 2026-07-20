package com.app.streaming.service;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.app.streaming.DTO.AccountDTO;
import com.app.streaming.DTO.LoginDTO;
import com.app.streaming.DTO.RegisterRequestDTO;
import com.app.streaming.DTO.UpdateAccountRequest;
import com.app.streaming.DTO.UpdatePasswordRequest;
import com.app.streaming.DTO.UserResponseDTO;
import com.app.streaming.model.User;
import com.app.streaming.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;

    public CustomUserDetailsService(UserRepository userRepository,
                                    PasswordEncoder encoder) {

        this.userRepository = userRepository;
        this.encoder = encoder;
        if(userRepository.existsByUsername("admin")) return;
        
        User adminUser = new User("admin2", encoder.encode("Ha!@#1427"));
        adminUser.setRole("ADMIN");
        userRepository.save(adminUser);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Username not found"));
                        
        return org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }

    public void register(RegisterRequestDTO register) {
        if(userRepository.existsByUsername(register.getUsername())) {
            throw new IllegalArgumentException("User already exist!!");
        }
        if(!register.getPassword().equals(register.getConfirmedPassword())) {
            throw new IllegalArgumentException("Confirmed password isn't match with password!");
        }

        User user = new User();
        user.setUsername(register.getUsername());
        user.setPassword(encoder.encode(register.getPassword()));

        userRepository.save(user);
    }

    public UserResponseDTO authenticateUser(LoginDTO loginForm) {
        System.out.println("Enter authenticate user");
        User existingUser = userRepository.findByUsername(loginForm.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found!!"));

        // CORRECT WAY to verify a hashed password
        if (!encoder.matches(loginForm.getPassword(), existingUser.getPassword())) {
            throw new IllegalArgumentException("Password was not match!");
        }

        return new UserResponseDTO(existingUser.getUsername());
    }

    public List<AccountDTO> getAllAccounts() {
        return userRepository.findAll().stream().map(
                                            user -> new AccountDTO(
                                                user.getId(), 
                                                user.getUsername(), 
                                                user.getRole(), 
                                                user.getCreatedAt()
                                            )
                                        ).toList();
    }

    @Transactional
    public void changePassword(Long userId, UpdatePasswordRequest newPasswordForm) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String encodedPassword = encoder.encode(newPasswordForm.getPassword());
        user.setPassword(encodedPassword);

    }

    @Transactional
    public void updateAccount(Long userId, UpdateAccountRequest updatedAccount) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setUsername(updatedAccount.getUsername());
        user.setRole(updatedAccount.getRole());
    }

    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

            userRepository.delete(user);
    }
}
