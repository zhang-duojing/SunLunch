package com.sunlunch.sunlunch.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.sunlunch.sunlunch.entity.SystemSetting;
import com.sunlunch.sunlunch.repository.SystemSettingRepository;

@Service
public class OrderDeadlineService {
    private static final LocalTime DEFAULT_ORDER_DEADLINE = LocalTime.of(23, 45);

    private final SystemSettingRepository systemSettingRepository;

    public OrderDeadlineService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public LocalTime getOrderDeadline() {
        return systemSettingRepository.findTopByOrderByIdAsc()
                .map(SystemSetting::getOrderDeadline)
                .orElse(DEFAULT_ORDER_DEADLINE);
    }

    public void updateOrderDeadline(LocalTime deadline) {
        SystemSetting setting = systemSettingRepository.findTopByOrderByIdAsc()
                .orElseGet(SystemSetting::new);
        setting.setOrderDeadline(deadline);
        systemSettingRepository.save(setting);
    }
}
