package com.sunlunch.sunlunch.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OrderDetailDTO {
    private Long orderId;
    private String userName;
    private String menuName;
    private LocalDate orderDate;
    private Integer quantity;
    private Boolean paid;
}
