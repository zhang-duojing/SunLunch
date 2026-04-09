package com.sunlunch.sunlunch.dto;
import lombok.Data;
import java.time.LocalDate;
@Data
public class OrderView {
    private Long orderId;
    private String menuName;
    private Integer price;
    private LocalDate orderDate;
    private Boolean paid;
}
