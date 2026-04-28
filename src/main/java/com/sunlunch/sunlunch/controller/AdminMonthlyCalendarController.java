package com.sunlunch.sunlunch.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sunlunch.sunlunch.dto.CalendarDayDTO;
import com.sunlunch.sunlunch.entity.Order;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.OrderRepository;

import jakarta.servlet.http.HttpSession;

@Controller
public class AdminMonthlyCalendarController {

    private final OrderRepository orderRepository;

    public AdminMonthlyCalendarController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/admin/orders/monthly-calendar")
    public String monthlyCalendarPage(@RequestParam(value = "month", required = false) String month,
                                      HttpSession session,
                                      Model model) {

        User loginUser = (User) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/admin/login";
        }

        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        YearMonth yearMonth;
        if (month != null && !month.isEmpty()) {
            yearMonth = YearMonth.parse(month);
            model.addAttribute("selectedMonth", month);
        } else {
            yearMonth = YearMonth.now();
            model.addAttribute("selectedMonth", yearMonth.toString());
        }

        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();


        LocalDate calendarStart = firstDay;
        while (calendarStart.getDayOfWeek() != DayOfWeek.SUNDAY) {
            calendarStart = calendarStart.minusDays(1);
        }


        LocalDate calendarEnd = lastDay;
        while (calendarEnd.getDayOfWeek() != DayOfWeek.SATURDAY) {
            calendarEnd = calendarEnd.plusDays(1);
        }

        List<Order> monthlyOrders = orderRepository.findByOrderDateBetween(calendarStart, calendarEnd);

        List<CalendarDayDTO> calendarDays = new ArrayList<>();
        LocalDate current = calendarStart;

        while (!current.isAfter(calendarEnd)) {
            int count = 0;
            for (Order order : monthlyOrders) {
                if (current.equals(order.getOrderDate())) {
                    count += order.getQuantity() == null ? 1 : order.getQuantity();
                }
            }

            CalendarDayDTO dto = new CalendarDayDTO();
            dto.setDate(current);
            dto.setDayNumber(current.getDayOfMonth());
            dto.setOrderCount(count);
            dto.setCurrentMonth(current.getMonthValue() == yearMonth.getMonthValue());

            calendarDays.add(dto);
            current = current.plusDays(1);
        }

        model.addAttribute("calendarDays", calendarDays);

        return "admin-orders-monthly-calendar";
    }
}
