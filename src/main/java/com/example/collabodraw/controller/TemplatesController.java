package com.example.collabodraw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TemplatesController {
    @GetMapping("/templates")
    public String templates() {
        return "templates";
    }
}
