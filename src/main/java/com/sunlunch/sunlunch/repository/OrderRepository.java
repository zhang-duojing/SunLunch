package com.sunlunch.sunlunch.repository;

import com.sunlunch.sunlunch.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface OrderRepository extends JpaRepository<Order,Long>{
    List<Order> findByUserId(Long userId);
    Order findByUserIdAndMenuId(Long userId,long menuId);
    Order findByUserIdAndOrderDate(Long userId,LocalDate orderDate);
    List<Order> findByOrderDate(LocalDate orderDate);
    Order findByIdAndUserId(Long id,Long userId);
    Boolean existsByMenuId(Long menuId);
    List<Order> findByOrderDateBetween(LocalDate startDate,LocalDate endDate);
    int countByMenuId(Long menuId);
    int countByMenuIdAndPaidTrue(Long menuId);
}
