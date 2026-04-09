package com.sunlunch.sunlunch.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarDayDTO {

    private LocalDate date;
    private Integer dayNumber;
    private Integer orderCount;
    private boolean currentMonth;
}