package com.sunlunch.sunlunch.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.sunlunch.sunlunch.dto.MenuViewDTO;
import com.sunlunch.sunlunch.entity.Menu;
import com.sunlunch.sunlunch.entity.User;
import com.sunlunch.sunlunch.repository.MenuRepository;
import com.sunlunch.sunlunch.repository.OrderRepository;
import com.sunlunch.sunlunch.service.MenuImageStorageService;
import com.sunlunch.sunlunch.service.MenuSyncService;
import com.sunlunch.sunlunch.service.OrderDeadlineService;

import jakarta.servlet.http.HttpSession;

@Controller
public class MenuController {

    private static final String SORT_DATE_ASC = "dateAsc";
    private static final String SORT_DATE_DESC = "dateDesc";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderDeadlineService orderDeadlineService;
    private final MenuImageStorageService menuImageStorageService;
    private final MenuSyncService menuSyncService;

    public MenuController(MenuRepository menuRepository, OrderRepository orderRepository,
            OrderDeadlineService orderDeadlineService, MenuImageStorageService menuImageStorageService,
            MenuSyncService menuSyncService) {
        this.menuRepository = menuRepository;
        this.orderRepository = orderRepository;
        this.orderDeadlineService = orderDeadlineService;
        this.menuImageStorageService = menuImageStorageService;
        this.menuSyncService = menuSyncService;
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

        LocalTime orderDeadline = orderDeadlineService.getOrderDeadline();
        String orderDeadlineDisplay = orderDeadline.format(TIME_FORMATTER);

        if (closed != null) {
            model.addAttribute("error", "本日の注文受付は終了しました（締切: " + orderDeadlineDisplay + "）。");
        }
        if (success != null) {
            model.addAttribute("message", "注文が完了しました。");
        }
        if (alreadyOrdered != null) {
            model.addAttribute("error", "本日はすでに注文済みです。");
        }

        LocalTime now = LocalTime.now();
        LocalTime deadLine = orderDeadline;

        boolean orderAvailable = now.isBefore(deadLine);
        model.addAttribute("orderAvailable", orderAvailable);
        model.addAttribute("orderDeadlineDisplay", orderDeadlineDisplay);
        model.addAttribute("deadlineDateTimeIso", LocalDateTime.of(LocalDate.now(), deadLine).toString());

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
                             @RequestParam(value = "menuImageFile", required = false) MultipartFile menuImageFile,
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

        try {
            String imagePath = resolveImagePath(menuImageFile, menuImageUrl);
            menu.setImagePath(imagePath);
        } catch (IllegalArgumentException ex) {
            if (hasUrlOnlyInput(menuImageFile, menuImageUrl)) {
                menu.setImagePath(menuImageStorageService.getDefaultImagePath());
                model.addAttribute("message", "画像URLの取得に失敗したため、デフォルト画像を設定しました。");
            } else {
                setCreateFormValues(model, menuName, description, price, menuDate, menuImageUrl);
                model.addAttribute("error", ex.getMessage());
                return "admin-menu-form";
            }
        } catch (IOException ex) {
            if (hasUploadFile(menuImageFile)) {
                setCreateFormValues(model, menuName, description, price, menuDate, menuImageUrl);
                model.addAttribute("error", "画像アップロードに失敗しました。もう一度お試しください。");
                return "admin-menu-form";
            }
            menu.setImagePath(menuImageStorageService.getDefaultImagePath());
            model.addAttribute("message", "画像URLの取得に失敗したため、デフォルト画像を設定しました。");
        }

        menuRepository.save(menu);

        if (!model.containsAttribute("message")) {
            model.addAttribute("message", "メニューを作成しました。");
        }
        clearCreateFormValues(model);
        return "admin-menu-form";
    }

    @GetMapping("/admin/menu/list")
    public String adminMenuList(@RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                                @RequestParam(value = "searchDate", required = false) String searchDate,
                                @RequestParam(value = "syncSuccess", required = false) String syncSuccess,
                                @RequestParam(value = "syncError", required = false) String syncError,
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
        if (syncSuccess != null) {
            model.addAttribute("message", "本日のメニューを同期しました。");
        }
        if (syncError != null) {
            model.addAttribute("error", "メニュー取得に失敗しました。");
        }

        return "admin-menu-list";
    }

    @PostMapping("/admin/menu/sync")
    public String syncTodayMenu(@RequestParam(value = "sort", defaultValue = SORT_DATE_ASC) String sort,
                                @RequestParam(value = "searchDate", required = false) String searchDate,
                                HttpSession session) {
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/admin/login";
        }
        if (!"ADMIN".equals(loginUser.getRole())) {
            return "redirect:/home";
        }

        String normalizedSort = normalizeSort(sort);
        try {
            menuSyncService.syncTodayMenu();
            return buildMenuListRedirectUrl(normalizedSort, searchDate) + "&syncSuccess=1";
        } catch (Exception ex) {
            return buildMenuListRedirectUrl(normalizedSort, searchDate) + "&syncError=1";
        }
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
        menuImageStorageService.deleteManagedImageIfExists(menu.getImagePath());
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
                             @RequestParam(value = "menuImageFile", required = false) MultipartFile menuImageFile,
                             @RequestParam(value = "removeImage", required = false) String removeImage,
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

        Menu menu = menuRepository.findById(id).orElse(null);
        if (menu == null) {
            return "redirect:/admin/menu/list?sort=" + normalizedSort;
        }

        menu.setMenuName(menuName);
        menu.setDescription(description);
        menu.setPrice(price);
        menu.setMenuDate(LocalDate.parse(menuDate));

        String existingImagePath = menu.getImagePath();
        if ("on".equals(removeImage) || "true".equalsIgnoreCase(removeImage)) {
            menu.setImagePath(null);
            menuImageStorageService.deleteManagedImageIfExists(existingImagePath);
        } else {
            try {
                String newImagePath = resolveImagePath(menuImageFile, menuImageUrl);
                if (newImagePath != null) {
                    menu.setImagePath(newImagePath);
                    if (!newImagePath.equals(existingImagePath)) {
                        menuImageStorageService.deleteManagedImageIfExists(existingImagePath);
                    }
                }
            } catch (IllegalArgumentException ex) {
                if (hasUrlOnlyInput(menuImageFile, menuImageUrl)) {
                    String fallbackImagePath = menuImageStorageService.getDefaultImagePath();
                    menu.setImagePath(fallbackImagePath);
                    if (!fallbackImagePath.equals(existingImagePath)) {
                        menuImageStorageService.deleteManagedImageIfExists(existingImagePath);
                    }
                } else {
                    model.addAttribute("error", ex.getMessage());
                    model.addAttribute("menu", menu);
                    model.addAttribute("sort", normalizedSort);
                    model.addAttribute("searchDate", searchDate == null ? "" : searchDate.trim());
                    return "admin-menu-edit";
                }
            } catch (IOException ex) {
                if (hasUploadFile(menuImageFile)) {
                    model.addAttribute("error", "画像アップロードに失敗しました。もう一度お試しください。");
                    model.addAttribute("menu", menu);
                    model.addAttribute("sort", normalizedSort);
                    model.addAttribute("searchDate", searchDate == null ? "" : searchDate.trim());
                    return "admin-menu-edit";
                }
                String fallbackImagePath = menuImageStorageService.getDefaultImagePath();
                menu.setImagePath(fallbackImagePath);
                if (!fallbackImagePath.equals(existingImagePath)) {
                    menuImageStorageService.deleteManagedImageIfExists(existingImagePath);
                }
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

    private String resolveImagePath(MultipartFile imageFile, String imageUrl) throws IOException {
        boolean hasFile = hasUploadFile(imageFile);
        String normalizedImageUrl = normalizeImageUrl(imageUrl);
        boolean hasUrl = normalizedImageUrl != null;

        if (hasFile && hasUrl) {
            throw new IllegalArgumentException("画像はURLかファイルのどちらか一方のみ指定してください。");
        }
        if (hasFile) {
            return menuImageStorageService.storeUploadedImage(imageFile);
        }
        if (hasUrl) {
            return menuImageStorageService.downloadAndStoreImage(normalizedImageUrl);
        }
        return null;
    }

    private boolean hasUploadFile(MultipartFile imageFile) {
        return imageFile != null && !imageFile.isEmpty();
    }

    private boolean hasUrlOnlyInput(MultipartFile imageFile, String imageUrl) {
        return !hasUploadFile(imageFile) && normalizeImageUrl(imageUrl) != null;
    }

    private void setCreateFormValues(Model model, String menuName, String description, Integer price, String menuDate,
            String menuImageUrl) {
        model.addAttribute("menuName", menuName);
        model.addAttribute("description", description);
        model.addAttribute("price", price);
        model.addAttribute("menuDate", menuDate);
        model.addAttribute("menuImageUrl", normalizeImageUrl(menuImageUrl));
    }

    private void clearCreateFormValues(Model model) {
        model.addAttribute("menuName", "");
        model.addAttribute("description", "");
        model.addAttribute("price", null);
        model.addAttribute("menuDate", "");
        model.addAttribute("menuImageUrl", "");
    }
}
