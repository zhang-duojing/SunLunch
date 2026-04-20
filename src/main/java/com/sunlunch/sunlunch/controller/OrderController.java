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
    private final OrderRepository orderRepository;
    private final OrderDeadlineService orderDeadlineService;

    public OrderController(OrderRepository orderRepository, OrderDeadlineService orderDeadlineService) {
        this.orderRepository = orderRepository;
        this.orderDeadlineService = orderDeadlineService;
    }

    @PostMapping("/order")
    public String createOrder(@RequestParam("menuId") Long menuId,
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
        order.setPaid(false);
        order.setPaidDate(null);

        orderRepository.save(order);
        return "redirect:/menu?success";
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

        orderRepository.delete(order);
        redirectAttributes.addFlashAttribute("message", "注文をキャンセルしました。");
        return "redirect:/my-orders";
    }

    // @PostMapping("/admin/order/pay")
    // public String payOrder(@RequestParam("orderId") Long orderId,
    // HttpSession session){
    // User loginUser = (User) session.getAttribute("loginUser");

    // if(loginUser == null||!"ADMIN".equals(loginUser.getRole())){
    // return "redirect:/login";
    // }

    // Order order = orderRepository.findByIdAndUserId(orderId, loginUser.getId());

    // if(order == null){
    // return "redirect:/my-orders";
    // }

    // order.setPaid(true);
    // order.setPaidDate(LocalDate.now());

    // orderRepository.save(order);

    // return "redirect:/admin/orders/list";
    // }
}
