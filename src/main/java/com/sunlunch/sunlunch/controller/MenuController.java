package com.sunlunch.sunlunch.controller;

import com.sunlunch.sunlunch.dto.MenuViewDTO;
import com.sunlunch.sunlunch.entity.Menu;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.MenuRepository;
import com.sunlunch.sunlunch.repository.OrderRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class MenuController {

    private static final String SORT_DATE_ASC = "dateAsc";
    private static final String SORT_DATE_DESC = "dateDesc";

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;

    public MenuController(MenuRepository menuRepository, OrderRepository orderRepository) {
        this.menuRepository = menuRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/menu")
    public String menuPage(HttpSession session, Model model,
                           @RequestParam(value = "success", required = false) String success,
                           @RequestParam(value = "alreadyOrdered", required = false) String alreadyOrdered,
                           @RequestParam(value = "closed", required = false) String closed) {
        User loginUser = (User) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        List<Menu> menuList = menuRepository.findByMenuDate(LocalDate.now());
        List<MenuViewDTO> menuViewList = new ArrayList<>();
        for (Menu menu : menuList) {
            MenuViewDTO dto = new MenuViewDTO();
            dto.setId(menu.getId());
            dto.setMenuName(menu.getMenuName());
            dto.setDescription(menu.getDescription());
            dto.setPrice(menu.getPrice());
            dto.setMenuDate(menu.getMenuDate());
            dto.setImagePath(menu.getImagePath());

            dto.setReservedCount(orderRepository.countByMenuId(menu.getId()));
            dto.setPaidCount(orderRepository.countByMenuIdAndPaidTrue(menu.getId()));

            menuViewList.add(dto);
        }
        model.addAttribute("user", loginUser);
        model.addAttribute("menuList", menuViewList);

        if (closed != null) {
            model.addAttribute("error", "本日の注文受付は終了しました。");
        }
        if (success != null) {
            model.addAttribute("message", "注文が完了しました。");
        }
        if (alreadyOrdered != null) {
            model.addAttribute("error", "本日はすでに注文済みです。");
        }

        LocalTime now = LocalTime.now();
        LocalTime deadLine = LocalTime.of(23, 45);

        boolean orderAvailable = now.isBefore(deadLine);
        model.addAttribute("orderAvailable", orderAvailable);

        return "menu";
    }

    @GetMapping("/admin/menu/new")
    public String newMenuPage(HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }
        return "admin-menu-form";
    }

    @PostMapping("/admin/menu/new")
    public String createMenu(@RequestParam("menuName") String menuName,
                             @RequestParam("description") String description,
                             @RequestParam("price") Integer price,
                             @RequestParam("menuDate") String menuDate,
                             @RequestParam(value = "menuImageUrl", required = false) String menuImageUrl,
                             Model model,
                             HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        Menu menu = new Menu();
        menu.setMenuName(menuName);
        menu.setDescription(description);
        menu.setPrice(price);
        menu.setMenuDate(LocalDate.parse(menuDate));
        menu.setImagePath(normalizeImageUrl(menuImageUrl));

        menuRepository.save(menu);

        model.addAttribute("message", "メニューを作成しました。");
        return "admin-menu-form";
    }

    @GetMapping("/admin/menu/list")
    public String adminMenuList(@RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                                Model model,
                                HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);
        List<Menu> menuList = getAdminMenuList(normalizedSort);
        model.addAttribute("menuList", menuList);
        model.addAttribute("sort", normalizedSort);

        return "admin-menu-list";
    }

    @PostMapping("/admin/menu/delete")
    public String deleteMenu(@RequestParam("menuId") Long menuId,
                             @RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                             HttpSession session,
                             Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);
        Menu menu = menuRepository.findById(menuId).orElse(null);
        if (menu == null) {
            return "redirect:/admin/menu/list?sort=" + normalizedSort;
        }

        boolean hasOrders = orderRepository.existsByMenuId(menuId);
        if (hasOrders) {
            model.addAttribute("error", "このメニューはすでに注文があるため、削除できません。");
            model.addAttribute("menuList", getAdminMenuList(normalizedSort));
            model.addAttribute("sort", normalizedSort);
            return "admin-menu-list";
        }

        menuRepository.delete(menu);
        return "redirect:/admin/menu/list?sort=" + normalizedSort;
    }

    @GetMapping("/admin/menu/edit")
    public String editMenuPage(@RequestParam("menuId") Long menuId,
                               @RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                               Model model,
                               HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);

        boolean hasOrders = orderRepository.existsByMenuId(menuId);
        if (hasOrders) {
            model.addAttribute("error", "このメニューはすでに注文があるため、編集できません。");
            model.addAttribute("menuList", getAdminMenuList(normalizedSort));
            model.addAttribute("sort", normalizedSort);
            return "admin-menu-list";
        }

        Menu menu = menuRepository.findById(menuId).orElse(null);
        if (menu == null) {
            return "redirect:/admin/menu/list?sort=" + normalizedSort;
        }
        model.addAttribute("menu", menu);
        model.addAttribute("sort", normalizedSort);
        return "admin-menu-edit";
    }

    @PostMapping("/admin/menu/edit")
    public String updateMenu(@RequestParam("id") Long id,
                             @RequestParam("menuName") String menuName,
                             @RequestParam("description") String description,
                             @RequestParam("price") Integer price,
                             @RequestParam("menuDate") String menuDate,
                             @RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                             @RequestParam(value = "menuImageUrl", required = false) String menuImageUrl,
                             @RequestParam(value = "removeImage", required = false) String removeImage,
                             HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);

        Menu menu = menuRepository.findById(id).orElse(null);
        if (menu == null) {
            return "redirect:/admin/menu/list?sort=" + normalizedSort;
        }

        menu.setMenuName(menuName);
        menu.setDescription(description);
        menu.setPrice(price);
        menu.setMenuDate(LocalDate.parse(menuDate));

        if ("on".equals(removeImage) || "true".equalsIgnoreCase(removeImage)) {
            menu.setImagePath(null);
        } else {
            String normalizedImageUrl = normalizeImageUrl(menuImageUrl);
            if (normalizedImageUrl != null) {
                menu.setImagePath(normalizedImageUrl);
            }
        }

        menuRepository.save(menu);
        return "redirect:/admin/menu/list?sort=" + normalizedSort;
    }

    private String normalizeSort(String sort) {
        if (SORT_DATE_DESC.equals(sort)) {
            return SORT_DATE_DESC;
        }
        return SORT_DATE_ASC;
    }

    private List<Menu> getAdminMenuList(String sort) {
        if (SORT_DATE_DESC.equals(sort)) {
            return menuRepository.findAllByOrderByMenuDateDescIdDesc();
        }
        return menuRepository.findAllByOrderByMenuDateAscIdAsc();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String trimmed = imageUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
