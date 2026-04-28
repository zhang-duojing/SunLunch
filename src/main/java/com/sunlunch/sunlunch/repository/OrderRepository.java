package com.sunlunch.sunlunch.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sunlunch.sunlunch.entity.Order;

public interface OrderRepository extends JpaRepository<Order,Long>{
    List<Order> findByUserId(Long userId);
    Order findByUserIdAndMenuId(Long userId,long menuId);
    List<Order> findByUserIdAndOrderDate(Long userId,LocalDate orderDate);
    List<Order> findByOrderDate(LocalDate orderDate);
    
    Order findByIdAndUserId(Long id,Long userId);
    Boolean existsByMenuId(Long menuId);
    Boolean existsByUserId(Long userId);
    void deleteByUserId(Long userId);
    List<Order> findByOrderDateBetween(LocalDate startDate,LocalDate endDate);
    int countByMenuId(Long menuId);
    int countByMenuIdAndPaidTrue(Long menuId);
    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM Order o WHERE o.menuId = :menuId")
    Integer sumQuantityByMenuId(@Param("menuId") Long menuId);
    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM Order o WHERE o.menuId = :menuId AND o.paid = true")
    Integer sumQuantityByMenuIdAndPaidTrue(@Param("menuId") Long menuId);
}
