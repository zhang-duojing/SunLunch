package com.sunlunch.sunlunch.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "orders")
@Data

public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long menuId;
    private LocalDate orderDate;
    private Boolean paid = false;
    private LocalDate paidDate;
}
