package com.app.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.app.streaming.controller.UserController;
import com.app.streaming.DTO.UserResponseDTO;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    @Test
    void dashboard_shouldReturnUsername() {

        // Arrange
        String username = "john";

        when(authentication.getName())
                .thenReturn(username);

        // Act
        ResponseEntity<UserResponseDTO> response =
                userController.dashboard(authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
                username,
                response.getBody().getUsername()
        );

        // Verify
        verify(authentication).getName();
    }
}
