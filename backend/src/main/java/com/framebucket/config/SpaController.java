package com.framebucket.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping({
            "/", "/register", "/verify-email", "/payment", "/login",
            "/dashboard", "/customer-portal",
            "/quote/{token}", "/team-invite/{token}", "/event-checkin/{token}",
            "/portfolio/{slug}", "/flipbook/{token}", "/admin", "/admin/{section}", "/enquire", "/enquire/{slug}"
    })
    public String forwardToApp() {
        return "forward:/index.html";
    }

    /** The phone interface used by the Android/iOS apps (hash routes, so one path is enough). */
    @GetMapping({"/app", "/app/"})
    public String forwardToMobileApp() {
        return "forward:/app.html";
    }

    /** The desktop app interface used by the Windows/Mac app. */
    @GetMapping({"/desktop", "/desktop/"})
    public String forwardToDesktopApp() {
        return "forward:/desktop.html";
    }
}
