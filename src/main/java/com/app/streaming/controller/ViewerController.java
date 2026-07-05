package com.app.streaming.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewerController {
    
    @GetMapping("/camViewer")
    public String getCamViewer() {
        return "CamViewer";
    }

    @GetMapping("/viewer")
    public String getViewer() {
        return "viewer";
    }

}
