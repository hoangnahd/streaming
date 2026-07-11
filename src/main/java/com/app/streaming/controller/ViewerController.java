package com.app.streaming.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewerController {
    
    @GetMapping("/dashboard")
    public String getCamViewer() {
        return "dashboard";
    }

    @GetMapping("/viewer")
    public String getViewer() {
        return "viewer";
    }

}
