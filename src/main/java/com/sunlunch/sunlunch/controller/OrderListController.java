package com.sunlunch.sunlunch.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sunlunch.sunlunch.dto.OrderView;
import com.sunlunch.sunlunch.entity.Menu;
import com.sunlunch.sunlunch.entity.Order;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.MenuRepository;
import com.sunlunch.sunlunch.repository.OrderRepository;

import jakarta.servlet.http.HttpSession;
@Controller
public class OrderListController {
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;

    public OrderListController(OrderRepository orderRepository,MenuRepository menuRepository){
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
    }
    @GetMapping("/my-orders")
    public String myOrderPage(@RequestParam(value = "date", required = false) String date,
     HttpSession session,Model model){
        User loginUser = (User) session.getAttribute("loginUser");
        if(loginUser==null){
            return "redirect:/login";
        }

        LocalDate selectedDate;
        if(date!=null && !date.isEmpty()){
            selectedDate = LocalDate.parse(date);
        }
        else{
            selectedDate = LocalDate.now();
        }


        List<Order> orderList = orderRepository.findByUserIdAndOrderDate(loginUser.getId(), selectedDate);
        List<OrderView> orderViewList = new ArrayList<>();

        for(Order order : orderList){
            Optional<Menu> optionalMenu = menuRepository.findById(order.getMenuId());

            if(optionalMenu.isPresent()){
                Menu menu = optionalMenu.get();

                OrderView orderView = new OrderView();
                orderView.setOrderId(order.getId());
                orderView.setMenuName(menu.getMenuName());
                orderView.setPrice(menu.getPrice());
                orderView.setOrderDate(order.getOrderDate());
                orderView.setPaid(order.getPaid());
                orderViewList.add(orderView);
            }
        }
        model.addAttribute("user",loginUser);
        model.addAttribute("orderViewList",orderViewList);
        model.addAttribute("selectedDate", selectedDate);
        return "my-orders";
    }
}
