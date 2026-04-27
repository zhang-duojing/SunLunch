package com.sunlunch.sunlunch.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.UserRepository;
import com.sunlunch.sunlunch.service.MailService;
import com.sunlunch.sunlunch.service.SessionRegistry;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final SessionRegistry sessionRegistry;

    public AuthController(UserRepository userRepository, MailService mailService, SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam("email") String email,
                               @RequestParam("name") String name,
                               @RequestParam("password") String password,
                               Model model) {
        User existingUser = userRepository.findByEmail(email);
        if (existingUser != null) {
            model.addAttribute("error", "このメールアドレスは既に登録されています。");
            return "register";
        }

        String verificationCode = generateVerificationCode();

        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword(password);
        user.setRole("USER");
        user.setDeleted(false);
        user.setEnabled(false);
        user.setResetToken(verificationCode);
        userRepository.save(user);

        try {
            mailService.sendVerificationCode(email, verificationCode);
        } catch (Exception ex) {
            userRepository.delete(user);
            model.addAttribute("error", "確認メールの送信に失敗しました。時間をおいて再度お試しください。");
            return "register";
        }

        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return "redirect:/verify-email?email=" + encodedEmail + "&registered=true";
    }

    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam(value = "email", required = false) String email,
                                  Model model) {
        model.addAttribute("email", email);
        return "verify-email";
    }

    @PostMapping("/verify-email")
    public String verifyEmail(@RequestParam("email") String email,
                              @RequestParam("code") String code,
                              Model model) {
        User user = userRepository.findByEmailAndResetToken(email, code);
        model.addAttribute("email", email);

        if (user == null) {
            model.addAttribute("error", "認証コードが正しくありません。");
            return "verify-email";
        }

        user.setEnabled(true);
        user.setResetToken(null);
        userRepository.save(user);

        model.addAttribute("message", "認証が完了しました。ログインしてください。");
        return "login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String loginUser(@RequestParam("email") String email,
                            @RequestParam("password") String password,
                            Model model,
                            HttpSession session) {
        User user = userRepository.findByEmailAndPasswordAndDeletedFalseAndEnabledTrue(email, password);
        if (user == null) {
            User unverifiedUser = userRepository.findByEmailAndPasswordAndDeletedFalse(email, password);
            if (unverifiedUser != null && !unverifiedUser.isEnabled()) {
                model.addAttribute("error", "メール認証が完了していません。認証コードを入力してください。");
                model.addAttribute("verifyEmail", unverifiedUser.getEmail());
            } else {
                model.addAttribute("error", "メールアドレスまたはパスワードが正しくありません。");
            }
            return "login";
        }

        if ("ADMIN".equals(user.getRole())) {
            model.addAttribute("error", "メールアドレスまたはパスワードが正しくありません。");
            return "login";
        }

        session.setAttribute("loginUser", user);
        session.setAttribute("user", user);
        sessionRegistry.register(user.getId(), session);
        return "redirect:/home";
    }

    @GetMapping("/admin/login")
    public String adminLoginPage(HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser != null && "ADMIN".equals(loginUser.getRole())) {
            return "redirect:/admin/orders/today";
        }
        return "admin-login";
    }

    @PostMapping("/admin/login")
    public String adminLoginUser(@RequestParam("email") String email,
                                 @RequestParam("password") String password,
                                 Model model,
                                 HttpSession session) {
        User user = userRepository.findByEmailAndPasswordAndDeletedFalseAndEnabledTrue(email, password);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            model.addAttribute("error", "メールアドレスまたはパスワードが正しくありません。");
            return "admin-login";
        }

        session.setAttribute("loginUser", user);
        session.setAttribute("user", user);
        sessionRegistry.register(user.getId(), session);
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String homePage(HttpSession session, Model model, HttpServletResponse response) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        applyNoCacheHeaders(response);
        model.addAttribute("user", loginUser);
        return "home";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser != null) {
            sessionRegistry.remove(loginUser.getId());
        }
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", loginUser);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam("name") String name,
                                HttpSession session,
                                Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(loginUser.getId()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        user.setName(name);
        userRepository.save(user);
        session.setAttribute("loginUser", user);
        session.setAttribute("user", user);

        model.addAttribute("user", user);
        model.addAttribute("message", "プロフィールを更新しました。");
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam("oldPassword") String oldPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 HttpSession session,
                                 Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(loginUser.getId()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        if (!user.getPassword().equals(oldPassword)) {
            model.addAttribute("user", user);
            model.addAttribute("error", "現在のパスワードが正しくありません。");
            return "profile";
        }

        user.setPassword(newPassword);
        userRepository.save(user);

        sessionRegistry.remove(user.getId());
        session.invalidate();
        return "redirect:/login?passwordChanged=true";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String resetPassword(@RequestParam("email") String email,
                                @RequestParam("token") String token,
                                @RequestParam("newPassword") String newPassword,
                                Model model) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            model.addAttribute("error", "メールアドレスが見つかりません。");
            return "forgot-password";
        }

        if (user.getResetToken() == null || !user.getResetToken().equals(token)) {
            model.addAttribute("error", "認証コードが正しくありません。");
            return "forgot-password";
        }

        user.setPassword(newPassword);
        user.setResetToken(null);
        userRepository.save(user);

        model.addAttribute("message", "パスワードのリセットが完了しました。ログインしてください。");
        return "forgot-password";
    }

    @PostMapping("/generate-token")
    public String generateToken(@RequestParam("email") String email, Model model) {
        User user = userRepository.findByEmail(email);
        model.addAttribute("email", email);
        if (user == null) {
            model.addAttribute("error", "メールアドレスが存在しません。");
            return "forgot-password";
        }

        String token = generateVerificationCode();
        user.setResetToken(token);
        userRepository.save(user);

        try {
            mailService.sendVerificationCode(email, token);
            model.addAttribute("message", "認証コードをメールに送信しました。");
        } catch (Exception ex) {
            model.addAttribute("error", "認証コードのメール送信に失敗しました。");
        }

        return "forgot-password";
    }

    private void applyNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private String generateVerificationCode() {
        int code = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(code);
    }
}
