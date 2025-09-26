package com.example.collabodraw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SharedController {
    @GetMapping("/shared")
    public String shared() {
        return "shared";
    }
}
