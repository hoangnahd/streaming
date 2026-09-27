package com.app.streaming.controller;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.app.streaming.DTO.AccountDTO;
import com.app.streaming.DTO.MessageResponse;
import com.app.streaming.DTO.UpdatePasswordRequest;
import com.app.streaming.DTO.RegisterRequestDTO;
import com.app.streaming.DTO.UpdateAccountRequest;
import com.app.streaming.service.AccountService;

import jakarta.validation.Valid;

@RestController 
public class AdminController {

    @Autowired
    private AccountService accountService;

    @GetMapping("/api/admin/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AccountDTO> getAccounts() {
        return accountService.getAllAccounts();
    }

    @PostMapping("/api/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> register(
        @Valid @RequestBody RegisterRequestDTO registerForm
    ) {
        accountService.register(registerForm);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    new MessageResponse("Successfully registered a new user")
                );
    }

    @PostMapping("/api/admin/accounts/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> changePassword(
        @PathVariable Long userId,
        @RequestBody @Valid UpdatePasswordRequest newPassword
    ) {
        
        accountService.changePassword(userId, newPassword);

        return ResponseEntity.ok(
            new MessageResponse("Password updated successfully")
        );
    }

    @PutMapping("/api/admin/accounts/{id}")
    public ResponseEntity<MessageResponse> updateAccount(
        @PathVariable Long id,
        @RequestBody @Valid UpdateAccountRequest request
    ) {
        // Pass the data to your service layer
        accountService.updateAccount(id, request);
        
        return ResponseEntity.ok(
                new MessageResponse("Account updated successfully")
        );
    }
    @DeleteMapping("/api/admin/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(
        @PathVariable Long id
    ) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

}