package com.sunlunch.sunlunch.dto;

import lombok.Data;

import java.time.LocalDate;
@Data
public class MonthlyOrderSummaryDTO {
    private LocalDate orderDate;
    private Integer count;
}
