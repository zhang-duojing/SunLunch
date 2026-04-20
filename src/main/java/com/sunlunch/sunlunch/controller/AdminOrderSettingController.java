package com.sunlunch.sunlunch.controller;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.service.OrderDeadlineService;

import jakarta.servlet.http.HttpSession;

@Controller
public class AdminOrderSettingController {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderDeadlineService orderDeadlineService;

    public AdminOrderSettingController(OrderDeadlineService orderDeadlineService) {
        this.orderDeadlineService = orderDeadlineService;
    }

    @GetMapping("/admin/order-settings")
    public String orderSettingsPage(@RequestParam(value = "success", required = false) String success,
            HttpSession session,
            Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        LocalTime orderDeadline = orderDeadlineService.getOrderDeadline();
        model.addAttribute("orderDeadline", orderDeadline.format(TIME_FORMATTER));
        if (success != null) {
            model.addAttribute("message", "注文締切時刻を更新しました。");
        }
        return "admin-order-settings";
    }

    @PostMapping("/admin/order-settings")
    public String updateOrderSettings(@RequestParam("orderDeadline") String orderDeadlineText,
            HttpSession session,
            Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        try {
            LocalTime orderDeadline = LocalTime.parse(orderDeadlineText);
            orderDeadlineService.updateOrderDeadline(orderDeadline);
            return "redirect:/admin/order-settings?success";
        } catch (Exception ex) {
            model.addAttribute("orderDeadline", orderDeadlineText);
            model.addAttribute("error", "時刻の形式が正しくありません。");
            return "admin-order-settings";
        }
    }
}
