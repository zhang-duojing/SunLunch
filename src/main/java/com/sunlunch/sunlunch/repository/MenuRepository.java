package com.sunlunch.sunlunch.repository;

import com.sunlunch.sunlunch.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MenuRepository extends JpaRepository<Menu,Long> {

    List<Menu> findByMenuDate(LocalDate menuDate);
    List<Menu> findAllByOrderByMenuDateAscIdAsc();
    List<Menu> findAllByOrderByMenuDateDescIdDesc();

}
