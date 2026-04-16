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
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
            return "redirect:/admin/login";
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
            return "redirect:/admin/login";
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
                                @RequestParam(value = "searchDate", required = false) String searchDate,
                                Model model,
                                HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);
        populateAdminMenuListModel(model, normalizedSort, searchDate);

        return "admin-menu-list";
    }

    @PostMapping("/admin/menu/delete")
    public String deleteMenu(@RequestParam("menuId") Long menuId,
                             @RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                             @RequestParam(value = "searchDate", required = false) String searchDate,
                             HttpSession session,
                             Model model) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);
        Menu menu = menuRepository.findById(menuId).orElse(null);
        if (menu == null) {
            return buildMenuListRedirectUrl(normalizedSort, searchDate);
        }

        boolean hasOrders = orderRepository.existsByMenuId(menuId);
        if (hasOrders) {
            model.addAttribute("error", "このメニューはすでに注文があるため、削除できません。");
            populateAdminMenuListModel(model, normalizedSort, searchDate);
            return "admin-menu-list";
        }

        menuRepository.delete(menu);
        return buildMenuListRedirectUrl(normalizedSort, searchDate);
    }

    @GetMapping("/admin/menu/edit")
    public String editMenuPage(@RequestParam("menuId") Long menuId,
                               @RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                               @RequestParam(value = "searchDate", required = false) String searchDate,
                               Model model,
                               HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);

        boolean hasOrders = orderRepository.existsByMenuId(menuId);
        if (hasOrders) {
            model.addAttribute("error", "このメニューはすでに注文があるため、編集できません。");
            populateAdminMenuListModel(model, normalizedSort, searchDate);
            return "admin-menu-list";
        }

        Menu menu = menuRepository.findById(menuId).orElse(null);
        if (menu == null) {
            return buildMenuListRedirectUrl(normalizedSort, searchDate);
        }
        model.addAttribute("menu", menu);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("searchDate", searchDate == null ? "" : searchDate.trim());
        return "admin-menu-edit";
    }

    @PostMapping("/admin/menu/edit")
    public String updateMenu(@RequestParam("id") Long id,
                             @RequestParam("menuName") String menuName,
                             @RequestParam("description") String description,
                             @RequestParam("price") Integer price,
                             @RequestParam("menuDate") String menuDate,
                             @RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                             @RequestParam(value = "searchDate", required = false) String searchDate,
                             @RequestParam(value = "menuImageUrl", required = false) String menuImageUrl,
                             @RequestParam(value = "removeImage", required = false) String removeImage,
                             HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
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
        return buildMenuListRedirectUrl(normalizedSort, searchDate);
    }

    private String normalizeSort(String sort) {
        if (SORT_DATE_DESC.equals(sort)) {
            return SORT_DATE_DESC;
        }
        return SORT_DATE_ASC;
    }

    private void populateAdminMenuListModel(Model model, String sort, String searchDate) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));

        String normalizedSearchDate = searchDate == null ? "" : searchDate.trim();
        LocalDate selectedDate = tryParseDate(normalizedSearchDate);
        boolean searchMode = selectedDate != null;

        List<Menu> displayMenuList;
        String displayTitle;
        String displayNote;

        if (searchMode) {
            displayMenuList = getMenuListByDate(sort, selectedDate);
            displayTitle = "検索結果";
            displayNote = selectedDate + " のメニューを表示しています。";
        } else {
            displayMenuList = getCurrentWeekMenuList(sort, weekStart, weekEnd);
            displayTitle = "今週のメニュー";
            displayNote = "表示期間: " + weekStart + " 〜 " + weekEnd;
        }

        List<LocalDate> historyDateOptions = getPastHistoryDateOptions(weekStart);

        model.addAttribute("displayMenuList", displayMenuList);
        model.addAttribute("displayTitle", displayTitle);
        model.addAttribute("displayNote", displayNote);
        model.addAttribute("searchDate", normalizedSearchDate);
        model.addAttribute("searchMode", searchMode);
        model.addAttribute("historyDateOptions", historyDateOptions);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekEnd);
        model.addAttribute("sort", sort);
    }

    private List<Menu> getCurrentWeekMenuList(String sort, LocalDate weekStart, LocalDate weekEnd) {
        if (SORT_DATE_DESC.equals(sort)) {
            return menuRepository.findByMenuDateBetweenOrderByMenuDateDescIdDesc(weekStart, weekEnd);
        }
        return menuRepository.findByMenuDateBetweenOrderByMenuDateAscIdAsc(weekStart, weekEnd);
    }

    private List<Menu> getMenuListByDate(String sort, LocalDate targetDate) {
        if (SORT_DATE_DESC.equals(sort)) {
            return menuRepository.findByMenuDateOrderByMenuDateDescIdDesc(targetDate);
        }
        return menuRepository.findByMenuDateOrderByMenuDateAscIdAsc(targetDate);
    }

    private List<LocalDate> getPastHistoryDateOptions(LocalDate weekStart) {
        List<Menu> pastMenus = menuRepository.findByMenuDateBeforeOrderByMenuDateDescIdDesc(weekStart);
        LinkedHashSet<LocalDate> uniqueDates = new LinkedHashSet<>();
        for (Menu menu : pastMenus) {
            if (menu.getMenuDate() != null) {
                uniqueDates.add(menu.getMenuDate());
            }
            if (uniqueDates.size() >= 14) {
                break;
            }
        }
        return new ArrayList<>(uniqueDates);
    }

    private LocalDate tryParseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildMenuListRedirectUrl(String sort, String searchDate) {
        String normalizedSearchDate = searchDate == null ? "" : searchDate.trim();
        if (!normalizedSearchDate.isEmpty()) {
            return "redirect:/admin/menu/list?sort=" + sort + "&searchDate=" + normalizedSearchDate;
        }
        return "redirect:/admin/menu/list?sort=" + sort;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String trimmed = imageUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
