package com.sunlunch.sunlunch.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name="menus")
@Data

public class Menu {
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String menuName;

    @Column(length = 500)
    private  String description;

    private Integer price;

    private LocalDate menuDate;

    @Column(name = "image_path", length = 255)
    private String imagePath;
}
