package com.framebucket.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping({
            "/", "/register", "/verify-email", "/payment", "/login",
            "/dashboard", "/customer-portal",
            "/quote/{token}", "/team-invite/{token}", "/event-checkin/{token}"
    })
    public String forwardToApp() {
        return "forward:/index.html";
    }
}
