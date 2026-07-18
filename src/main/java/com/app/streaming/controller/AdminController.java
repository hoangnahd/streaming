package com.app.streaming.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.app.streaming.DTO.AccountDTO;
import com.app.streaming.DTO.UpdatePasswordRequest;
import com.app.streaming.DTO.RegisterRequestDTO;
import com.app.streaming.DTO.UpdateAccountRequest;
import com.app.streaming.service.CustomUserDetailsService;

import jakarta.validation.Valid;

@Controller
public class AdminController {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @GetMapping("/admin/manage/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public String manageAccounts(Model model) {
        // 1. Fetch existing accounts to display in the table rows
        List<AccountDTO> accountsList = userDetailsService.getAllAccounts();
        model.addAttribute("accounts", accountsList);

        // 2. Supply a fresh form tracking object for the "Add Account" modal
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountDTO());
        }

        // 3. The form object for updating passwords
        if (!model.containsAttribute("updatePasswordRequest")) {
            model.addAttribute("updatePasswordRequest", new UpdatePasswordRequest()); // Or whatever you named the class
        }

        model.addAttribute("registerRequestDTO", new RegisterRequestDTO());
                                        
        return "admin/manage/accounts/page";
    }

    @PostMapping("/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> register(
        @Valid @RequestBody RegisterRequestDTO registerForm
    ) {
        userDetailsService.register(registerForm);

        return ResponseEntity.status(201).body("{\"message\": \"Successfully registered a new user\"}");
    }

    @PostMapping("/admin/manage/accounts/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> changePassword(
        @PathVariable Long userId,
        @RequestBody @Valid UpdatePasswordRequest newPassword,
        BindingResult bindingResult
    ) {
        
        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(bindingResult));
        }
        
        userDetailsService.changePassword(userId, newPassword);

        return ResponseEntity.ok("{\"message\": \"Password updated successfully\"}");
    }

    @PostMapping("/admin/manage/accounts/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addAccount(
        @RequestBody @Valid RegisterRequestDTO accountForm,
        BindingResult bindingResult
    ) {
        // Check if passwords match (only if there are no prior validation errors)
        if (accountForm.getPassword() != null && !accountForm.getPassword().equals(accountForm.getConfirmedPassword())) {
            bindingResult.rejectValue("confirmedPassword", "error.confirmedPassword", "Passwords do not match!");
        }
        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(bindingResult));
        }

        userDetailsService.register(accountForm);
        return ResponseEntity.status(201).body("{\"message\": \"Account added successfully\"}");
    }

    @PostMapping("/admin/manage/accounts/{id}/update")
    public ResponseEntity<?> updateAccount(
        @PathVariable Long id,
        @RequestBody @Valid UpdateAccountRequest request,
        BindingResult bindingResult
    ) {

        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(bindingResult));
        }
        // Pass the data to your service layer
        userDetailsService.updateAccount(id, request);
        
        return ResponseEntity.ok().body("{\"message\": \"Account updated successfully\"}");
    }
    @PostMapping("/admin/manage/accounts/{id}/delete")
    public ResponseEntity<?> deleteAccount(
        @PathVariable Long id
    ) {
        userDetailsService.deleteAccount(id);
        return ResponseEntity.status(204).body("Delete successfully");
    }


    // --- Helper Method to format errors as JSON ---
    private Map<String, String> getValidationErrors(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            // This creates a JSON object like: { "username": "Username is required", "role": "Role is invalid" }
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}
