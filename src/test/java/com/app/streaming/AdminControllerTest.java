package com.app.streaming;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.app.streaming.DTO.AccountDTO;
import com.app.streaming.DTO.MessageResponse;
import com.app.streaming.DTO.RegisterRequestDTO;
import com.app.streaming.DTO.UpdateAccountRequest;
import com.app.streaming.DTO.UpdatePasswordRequest;
import com.app.streaming.controller.AdminController;
import com.app.streaming.service.AccountService;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void getAccounts_shouldReturnAccounts() {

        List<AccountDTO> accounts = List.of(
            new AccountDTO(),
            new AccountDTO()
        );

        when(accountService.getAllAccounts())
                .thenReturn(accounts);

        List<AccountDTO> result =
                adminController.getAccounts();

        assertEquals(2, result.size());
        assertSame(accounts, result);

        verify(accountService)
                .getAllAccounts();
    }

    @Test
    void register_shouldRegisterAccount() {

        RegisterRequestDTO request =
                mock(RegisterRequestDTO.class);

        ResponseEntity<MessageResponse> response =
                adminController.register(request);

        assertEquals(
            HttpStatus.CREATED,
            response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
            "Successfully registered a new user",
            response.getBody().message()
        );

        verify(accountService)
                .register(request);
    }

    @Test
    void changePassword_shouldUpdatePassword() {

        Long userId = 10L;

        UpdatePasswordRequest request =
                mock(UpdatePasswordRequest.class);

        ResponseEntity<MessageResponse> response =
                (ResponseEntity<MessageResponse>) adminController.changePassword(
                    userId,
                    request
                );

        assertEquals(
            HttpStatus.OK,
            response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
            "Password updated successfully",
            response.getBody().message()
        );

        verify(accountService)
                .changePassword(userId, request);
    }

    @Test
    void updateAccount_shouldUpdateAccount() {

        Long accountId = 10L;

        UpdateAccountRequest request =
                mock(UpdateAccountRequest.class);

        ResponseEntity<MessageResponse> response =
                adminController.updateAccount(
                    accountId,
                    request
                );

        assertEquals(
            HttpStatus.OK,
            response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
            "Account updated successfully",
            response.getBody().message()
        );

        verify(accountService)
                .updateAccount(accountId, request);
    }

    @Test
    void deleteAccount_shouldDeleteAccount() {

        Long accountId = 10L;

        ResponseEntity<Void> response =
                adminController.deleteAccount(accountId);

        assertEquals(
            HttpStatus.NO_CONTENT,
            response.getStatusCode()
        );

        assertNull(response.getBody());

        verify(accountService)
                .deleteAccount(accountId);
    }
}
