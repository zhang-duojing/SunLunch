package com.sunlunch.sunlunch.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sunlunch.sunlunch.entity.Order;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.OrderRepository;
import com.sunlunch.sunlunch.service.OrderDeadlineService;

import jakarta.servlet.http.HttpSession;

@Controller
public class OrderController {
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 5;

    private final OrderRepository orderRepository;
    private final OrderDeadlineService orderDeadlineService;

    public OrderController(OrderRepository orderRepository, OrderDeadlineService orderDeadlineService) {
        this.orderRepository = orderRepository;
        this.orderDeadlineService = orderDeadlineService;
    }

    @PostMapping("/order")
    public String createOrder(@RequestParam("menuId") Long menuId,
            @RequestParam(value = "quantity", required = false) Integer quantity,
            HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        LocalTime now = LocalTime.now();
        LocalTime deadLine = orderDeadlineService.getOrderDeadline();
        if (!now.isBefore(deadLine)) {
            return "redirect:/menu?closed";
        }

        LocalDate today = LocalDate.now();
        List<Order> existingTodayOrder = orderRepository.findByUserIdAndOrderDate(loginUser.getId(), today);
        if (!existingTodayOrder.isEmpty()) {
            return "redirect:/menu?alreadyOrdered";
        }

        Order order = new Order();
        order.setUserId(loginUser.getId());
        order.setMenuId(menuId);
        order.setOrderDate(today);
        order.setQuantity(normalizeQuantity(quantity));
        order.setPaid(false);
        order.setPaidDate(null);

        orderRepository.save(order);
        return "redirect:/menu?success";
    }

    @PostMapping("/order/quantity")
    public String updateOrderQuantity(@RequestParam("orderId") Long orderId,
            @RequestParam("quantity") Integer quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            redirectAttributes.addFlashAttribute("error", "注文が見つかりません。");
            return "redirect:/my-orders";
        }

        if (!order.getUserId().equals(loginUser.getId())) {
            redirectAttributes.addFlashAttribute("error", "自分の注文のみ変更できます。");
            return "redirect:/my-orders";
        }

        if (Boolean.TRUE.equals(order.getPaid())) {
            redirectAttributes.addFlashAttribute("error", "支払い済みの注文は数量を変更できません。");
            return "redirect:/my-orders";
        }

        if (isAfterDeadline(order.getOrderDate())) {
            redirectAttributes.addFlashAttribute("error", "締切時間を過ぎたため、数量を変更できません。");
            return "redirect:/my-orders";
        }

        if (quantity == null || quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            redirectAttributes.addFlashAttribute("error", "数量は1から5の範囲で入力してください。");
            return "redirect:/my-orders";
        }

        order.setQuantity(quantity);
        orderRepository.save(order);
        redirectAttributes.addFlashAttribute("message", "注文数量を変更しました。");
        return "redirect:/my-orders";
    }

    @PostMapping("/order/cancel")
    public String cancelOrder(@RequestParam("orderId") Long orderId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            redirectAttributes.addFlashAttribute("error", "注文が見つかりません。");
            return "redirect:/my-orders";
        }

        if (!order.getUserId().equals(loginUser.getId())) {
            redirectAttributes.addFlashAttribute("error", "自分の注文のみキャンセルできます。");
            return "redirect:/my-orders";
        }

        if (Boolean.TRUE.equals(order.getPaid())) {
            redirectAttributes.addFlashAttribute("error", "支払い済みの注文はキャンセルできません。");
            return "redirect:/my-orders";
        }

        if (isAfterDeadline(order.getOrderDate())) {
            redirectAttributes.addFlashAttribute("error", "締切時間を過ぎたため、キャンセルできません。");
            return "redirect:/my-orders";
        }

        orderRepository.delete(order);
        redirectAttributes.addFlashAttribute("message", "注文をキャンセルしました。");
        return "redirect:/my-orders";
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity < MIN_QUANTITY) {
            return MIN_QUANTITY;
        }
        if (quantity > MAX_QUANTITY) {
            return MAX_QUANTITY;
        }
        return quantity;
    }

    private boolean isAfterDeadline(LocalDate orderDate) {
        LocalDate today = LocalDate.now();
        if (orderDate.isBefore(today)) {
            return true;
        }
        if (orderDate.isAfter(today)) {
            return false;
        }
        LocalTime deadLine = orderDeadlineService.getOrderDeadline();
        return !LocalTime.now().isBefore(deadLine);
    }
}
