package com.sunlunch.sunlunch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sunlunch.sunlunch.entity.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findTopByOrderByIdAsc();
}
