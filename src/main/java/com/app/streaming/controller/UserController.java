package com.app.streaming.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


import com.app.streaming.DTO.LoginDTO;
import com.app.streaming.DTO.UserResponseDTO;


@Controller
public class UserController {

    @GetMapping("/login")
    public String getLoginPage(Model model) {
        model.addAttribute("loginForm", new LoginDTO());
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {

        String username = authentication.getName();
        UserResponseDTO userResponse = new UserResponseDTO(username);

        model.addAttribute("userResponse", userResponse);

        return "dashboard";
    }
}
