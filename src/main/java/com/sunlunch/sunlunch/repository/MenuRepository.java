package com.sunlunch.sunlunch.repository;

import com.sunlunch.sunlunch.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MenuRepository extends JpaRepository<Menu,Long> {

    List<Menu> findByMenuDate(LocalDate menuDate);
    List<Menu> findByMenuDateOrderByMenuDateAscIdAsc(LocalDate menuDate);
    List<Menu> findByMenuDateOrderByMenuDateDescIdDesc(LocalDate menuDate);
    List<Menu> findAllByOrderByMenuDateAscIdAsc();
    List<Menu> findAllByOrderByMenuDateDescIdDesc();
    List<Menu> findByMenuDateBetweenOrderByMenuDateAscIdAsc(LocalDate startDate, LocalDate endDate);
    List<Menu> findByMenuDateBetweenOrderByMenuDateDescIdDesc(LocalDate startDate, LocalDate endDate);
    List<Menu> findByMenuDateBeforeOrderByMenuDateAscIdAsc(LocalDate date);
    List<Menu> findByMenuDateBeforeOrderByMenuDateDescIdDesc(LocalDate date);
    void deleteByMenuDate(LocalDate menuDate);

}
