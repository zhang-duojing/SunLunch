package com.sunlunch.sunlunch.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.OrderRepository;
import com.sunlunch.sunlunch.repository.UserRepository;
import com.sunlunch.sunlunch.service.SessionRegistry;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Controller
public class AdminUserController {
    private static final List<String> ALLOWED_ROLES = Arrays.asList("USER", "ADMIN");

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SessionRegistry sessionRegistry;

    public AdminUserController(UserRepository userRepository, OrderRepository orderRepository, SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/admin/users")
    public String userManagementPage(@RequestParam(value = "keyword", required = false) String keyword,
            HttpSession session,
            Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<User> users;
        if (StringUtils.hasText(normalizedKeyword)) {
            users = userRepository
                    .findByDeletedFalseAndNameContainingIgnoreCaseOrDeletedFalseAndEmailContainingIgnoreCaseOrderByIdAsc(
                            normalizedKeyword, normalizedKeyword);
        } else {
            users = userRepository.findByDeletedFalseOrderByIdAsc();
        }

        List<User> adminUsers = users.stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .collect(Collectors.toList());
        List<User> normalUsers = users.stream()
                .filter(u -> "USER".equals(u.getRole()))
                .collect(Collectors.toList());

        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("adminUsers", adminUsers);
        model.addAttribute("normalUsers", normalUsers);
        model.addAttribute("loginUserId", loginUser.getId());
        model.addAttribute("roles", ALLOWED_ROLES);
        return "admin-users";
    }

    @PostMapping("/admin/users/role")
    public String updateUserRole(@RequestParam("userId") Long userId,
            @RequestParam("role") String role,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        if (!ALLOWED_ROLES.contains(role)) {
            redirectAttributes.addFlashAttribute("error", "無効な権限です。");
            return "redirect:/admin/users";
        }

        Optional<User> optionalUser = userRepository.findByIdAndDeletedFalse(userId);
        if (optionalUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "ユーザーが見つかりません。");
            return "redirect:/admin/users";
        }

        User targetUser = optionalUser.get();
        if (targetUser.getId().equals(loginUser.getId()) && !"ADMIN".equals(role)) {
            redirectAttributes.addFlashAttribute("error", "自分自身の権限を変更できません。");
            return "redirect:/admin/users";
        }

        boolean roleChanged = !role.equals(targetUser.getRole());
        targetUser.setRole(role);
        userRepository.save(targetUser);
        if (roleChanged) {
            sessionRegistry.invalidate(targetUser.getId());
        }

        redirectAttributes.addFlashAttribute("message", "ユーザー権限を更新しました。");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/delete")
    @Transactional
    public String deleteUser(@RequestParam("userId") Long userId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        Optional<User> optionalUser = userRepository.findByIdAndDeletedFalse(userId);
        if (optionalUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "ユーザーが見つかりません。");
            return "redirect:/admin/users";
        }

        User targetUser = optionalUser.get();
        if (targetUser.getId().equals(loginUser.getId())) {
            redirectAttributes.addFlashAttribute("error", "自分自身は削除できません。");
            return "redirect:/admin/users";
        }

        try {
            orderRepository.deleteByUserId(targetUser.getId());
            targetUser.setDeleted(true);
            userRepository.save(targetUser);
            redirectAttributes.addFlashAttribute("message", "ユーザーを削除しました。");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "ユーザー削除に失敗しました。");
        }
        return "redirect:/admin/users";
    }
}

