package com.sunlunch.sunlunch.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.UserRepository;
import com.sunlunch.sunlunch.service.SessionRegistry;

import jakarta.servlet.http.HttpSession;

@RestController
public class SessionCheckController {

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;

    public SessionCheckController(UserRepository userRepository, SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/check-session")
    public Map<String, Boolean> checkSession(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            sessionUser = (User) session.getAttribute("loginUser");
            if (sessionUser != null) {
                session.setAttribute("user", sessionUser);
            }
        }

        if (sessionUser == null) {
            return Map.of("valid", false);
        }

        User latestUser = userRepository.findById(sessionUser.getId()).orElse(null);
        if (latestUser == null || latestUser.isDeleted() || !"ADMIN".equals(latestUser.getRole())) {
            sessionRegistry.remove(sessionUser.getId());
            session.invalidate();
            return Map.of("valid", false);
        }

        return Map.of("valid", true);
    }
}
