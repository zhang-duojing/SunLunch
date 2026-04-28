package com.sunlunch.sunlunch.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sunlunch.sunlunch.entity.Order;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.OrderRepository;

import jakarta.servlet.http.HttpSession;
@Controller
public class AdminMonthlyOrderController {
    private final OrderRepository orderRepository;
    public AdminMonthlyOrderController(OrderRepository orderRepository){
        this.orderRepository = orderRepository;
    }

    @GetMapping("/admin/orders/monthly")
    public String monthlyOrdersPage(@RequestParam(value = "month", required = false) String month,
                                    HttpSession session,
                                    Model model){
        User loginUser = (User) session.getAttribute("loginUser");
        if(loginUser == null){
            return "redirect:/admin/login";
        }
        if(!"ADMIN".equals(loginUser.getRole())){
            return "redirect:/home";
        }

        YearMonth yearMonth;
        if(month !=null && !month.isEmpty()){
            yearMonth = YearMonth.parse(month);
            model.addAttribute("selectedMonth",month);
        }
        else{
            yearMonth = YearMonth.now();
            model.addAttribute("selectedMonth",yearMonth.toString());
        }
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Order> orderList = orderRepository.findByOrderDateBetween(startDate,endDate);
        Map<LocalDate,Integer> countMap =new TreeMap<>();

        for(Order order : orderList){
            if (!Boolean.TRUE.equals(order.getPaid())) {
                continue;
            }
            LocalDate date = order.getOrderDate();
            int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
            countMap.put(date,countMap.getOrDefault(date,0)+quantity);
        }

        LocalDate today = LocalDate.now();
        int todayCount = countMap.getOrDefault(today, 0);

        model.addAttribute("todayDate", today);
        model.addAttribute("todayCount", todayCount);
        int monthTotalOrders = orderList.stream()
                .filter(order -> Boolean.TRUE.equals(order.getPaid()))
                .mapToInt(order -> order.getQuantity() == null ? 1 : order.getQuantity())
                .sum();
        model.addAttribute("monthTotalOrders", monthTotalOrders);

        return "admin-orders-monthly";
    }
}

