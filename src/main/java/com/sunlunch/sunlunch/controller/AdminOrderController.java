package com.sunlunch.sunlunch.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sunlunch.sunlunch.dto.AdminOrderSummary;
import com.sunlunch.sunlunch.entity.Menu;
import com.sunlunch.sunlunch.entity.Order;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.MenuRepository;
import com.sunlunch.sunlunch.repository.OrderRepository;

import jakarta.servlet.http.HttpSession;

@Controller
public class AdminOrderController {
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;

    public AdminOrderController(OrderRepository orderRepository, MenuRepository menuRepository) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
    }

    @PostMapping("/admin/orders/pay")
    public String confirmPayment(@RequestParam("orderId") Long orderId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/admin/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            redirectAttributes.addFlashAttribute("error", "注文が見つかりません。");
            return "redirect:/admin/orders/list";
        }

        order.setPaid(true);
        order.setPaidDate(LocalDate.now());
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("message", "入金確認が完了しました。");
        return "redirect:/admin/orders/list";
    }

    @GetMapping("/admin/orders/today")
    public String todayOrdersPage(HttpSession session, Model model) {
        User loginUser = (User) session.getAttribute(("loginUser"));

        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        List<Order> todayOrders = orderRepository.findByOrderDate(LocalDate.now());
        Map<Long, Integer> countMap = new HashMap<>();

        int paidCount = 0;
        int unpaidCount = 0;

        for (Order order : todayOrders) {
            int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
            if (Boolean.TRUE.equals(order.getPaid())) {
                Long menuId = order.getMenuId();
                countMap.put(menuId, countMap.getOrDefault(menuId, 0) + quantity);
                paidCount += quantity;
            } else {
                unpaidCount += quantity;
            }
        }

        List<AdminOrderSummary> summaryList = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : countMap.entrySet()) {
            Long menuId = entry.getKey();
            Integer count = entry.getValue();

            Menu menu = menuRepository.findById(menuId).orElse(null);
            if (menu != null) {
                AdminOrderSummary summary = new AdminOrderSummary();
                summary.setMenuName(menu.getMenuName());
                summary.setCount(count);
                summaryList.add(summary);
            }
        }

        int totalOrders = todayOrders.stream()
                .filter(order -> Boolean.TRUE.equals(order.getPaid()))
                .mapToInt(order -> order.getQuantity() == null ? 1 : order.getQuantity())
                .sum();

        model.addAttribute("summaryList", summaryList);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("unpaidCount", unpaidCount);

        return "admin-orders-today";
    }
}
