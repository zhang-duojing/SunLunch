package com.sunlunch.sunlunch.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MenuViewDTO {

    private Long id;
    private String menuName;
    private String description;
    private Integer price;
    private LocalDate menuDate;
    private String imagePath;

    private Integer reservedCount;
    private Integer paidCount;
}
