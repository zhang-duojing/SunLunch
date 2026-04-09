package com.sunlunch.sunlunch.controller;

import com.sunlunch.sunlunch.dto.OrderDetailDTO;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.entity.Order;
import com.sunlunch.sunlunch.entity.Menu;
import com.sunlunch.sunlunch.repository.UserRepository;
import com.sunlunch.sunlunch.repository.OrderRepository;
import com.sunlunch.sunlunch.repository.MenuRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;


@Controller
public class AdminOrderListController {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final MenuRepository menuRepository;

    public AdminOrderListController(OrderRepository orderRepository,
            UserRepository userRepository,
            MenuRepository menuRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.menuRepository = menuRepository;
    }

    @GetMapping("/admin/orders/list")
    public String adminOrdersListPage(@RequestParam(value = "date", required = false) String date,
            HttpSession session, Model model) {
        User loginUser = (User) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        List<Order> orderList;
        if (date != null && !date.isEmpty()) {
            orderList = orderRepository.findByOrderDate(LocalDate.parse(date));
            model.addAttribute("selectedDate", date);
        } else {
            orderList = orderRepository.findAll();
        }

        List<OrderDetailDTO> paidOrderList = new ArrayList<>();
        List<OrderDetailDTO> unpaidOrderList = new ArrayList<>();

        for (Order order : orderList) {
            Optional<User> optionalUser = userRepository.findById(order.getUserId());
            Optional<Menu> optionalMenu = menuRepository.findById(order.getMenuId());

            if (optionalUser.isPresent() && optionalMenu.isPresent()) {
                User user = optionalUser.get();
                Menu menu = optionalMenu.get();

                OrderDetailDTO dto = new OrderDetailDTO();
                dto.setOrderId(order.getId());

                dto.setUserName(user.getName());
                dto.setMenuName(menu.getMenuName());
                dto.setOrderDate(order.getOrderDate());
                dto.setPaid(order.getPaid());

                if (Boolean.TRUE.equals(order.getPaid())) {
                    paidOrderList.add(dto);
                } else {
                    unpaidOrderList.add(dto);
                }
            }
        }

        model.addAttribute("paidOrderList", paidOrderList);
        model.addAttribute("unpaidOrderList", unpaidOrderList);

        return "admin-orders-list";
    }

    @PostMapping("/admin/order/cancel")
    public String orderCancel(@RequestParam("orderId") Long orderId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            redirectAttributes.addFlashAttribute("error", "注文が見つかりません。");
            return "admin-orders-list";
        }

        orderRepository.delete(order);
        redirectAttributes.addFlashAttribute("message", "注文をキャンセルしました。");
        
        return "redirect:/admin/orders/list";

    }

}
