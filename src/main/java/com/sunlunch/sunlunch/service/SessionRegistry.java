package com.sunlunch.sunlunch.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSession;

@Component
public class SessionRegistry {

    private final ConcurrentHashMap<Long, HttpSession> sessionMap = new ConcurrentHashMap<>();

    public void register(Long userId, HttpSession session) {
        if (userId == null || session == null) {
            return;
        }

        HttpSession oldSession = sessionMap.put(userId, session);
        if (oldSession != null && oldSession != session) {
            try {
                oldSession.invalidate();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void invalidate(Long userId) {
        if (userId == null) {
            return;
        }

        HttpSession session = sessionMap.remove(userId);
        if (session == null) {
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
        }
    }

    public void remove(Long userId) {
        if (userId == null) {
            return;
        }
        sessionMap.remove(userId);
    }
}
