package com.sunlunch.sunlunch.service;

import com.sunlunch.sunlunch.entity.Menu;
import com.sunlunch.sunlunch.repository.MenuRepository;
import com.sunlunch.sunlunch.repository.OrderRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MenuSyncService {

    private static final Logger log = LoggerFactory.getLogger(MenuSyncService.class);

    // Adjust these selectors to match the actual bento shop website HTML structure.
    private static final String TODAY_MENU_BOX_SELECTOR = ".menuBox";
    private static final String TODAY_DATE_SELECTOR = ".menuBoxHead h2";
    private static final String TODAY_IMAGE_SELECTOR = ".mainPhoto img";
    private static final String TODAY_CONTENT_SELECTOR = ".textTop h4";

    // Adjust these selectors to match the actual bento shop website HTML structure.
    private static final String WEEK_MENU_ITEM_SELECTOR = ".weekMenu ul li";
    private static final String WEEK_DATE_SELECTOR = ".menuBoxHead h2";
    private static final String WEEK_IMAGE_SELECTOR = ".menuImg img";
    private static final String WEEK_NAME_SELECTOR = "h3";
    private static final String WEEK_DESCRIPTION_SELECTOR = "p";

    private static final String CLOSED_DAY_TEXT = "\u4F11\u696D\u65E5";

    private static final int DEFAULT_MENU_PRICE = 550;
    private static final Pattern MONTH_DAY_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*\\u6708\\s*(\\d{1,2})\\s*\\u65E5");

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final MenuImageStorageService menuImageStorageService;

    @Value("${menu.sync.url}")
    private String menuSyncUrl;

    @Value("${menu.sync.auto-enabled:false}")
    private boolean autoSyncEnabled;

    public MenuSyncService(MenuRepository menuRepository, OrderRepository orderRepository,
                           MenuImageStorageService menuImageStorageService) {
        this.menuRepository = menuRepository;
        this.orderRepository = orderRepository;
        this.menuImageStorageService = menuImageStorageService;
    }

    @Transactional
    public void syncTodayMenu() throws IOException {
        LocalDate today = LocalDate.now();
        log.info("Menu sync started. date={}, url={}", today, menuSyncUrl);

        Document doc = Jsoup.connect(menuSyncUrl)
                .timeout(15000)
                .get();

        log.info("doc.title()={}", doc.title());
        log.info("Today menuBox count={}", doc.select(TODAY_MENU_BOX_SELECTOR).size());
        log.info("Week menu li count={}", doc.select(WEEK_MENU_ITEM_SELECTOR).size());

        List<MenuDraft> drafts = parseTodayMenus(doc);
        if (drafts.isEmpty()) {
            drafts = parseWeekMenus(doc);
        }

        if (drafts.isEmpty()) {
            log.warn("Menu sync failed. No valid menu item was parsed from url={}", menuSyncUrl);
            throw new IllegalStateException("No valid menu item was found to sync.");
        }

        clearExistingMenusForDates(drafts);

        int successCount = 0;
        for (MenuDraft draft : drafts) {
            try {
                Menu menu = new Menu();
                menu.setMenuName(draft.menuName);
                menu.setDescription(draft.description);
                menu.setPrice(draft.price == null ? 0 : draft.price);
                menu.setMenuDate(draft.menuDate);
                menu.setImagePath(downloadImageIfPresent(draft.imageUrl));
                menuRepository.save(menu);
                successCount++;
                log.info("Saved menu item successfully. name='{}', date={}", menu.getMenuName(), menu.getMenuDate());
            } catch (Exception ex) {
                log.error("Failed to save menu item. name='{}', date={}, reason={}",
                        draft.menuName, draft.menuDate, ex.getMessage(), ex);
            }
        }

        log.info("Menu sync completed. parsedCount={}, savedCount={}", drafts.size(), successCount);
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void scheduledSyncTodayMenu() {
        if (!autoSyncEnabled) {
            return;
        }
        try {
            syncTodayMenu();
        } catch (Exception ex) {
            log.error("Scheduled menu sync failed. reason={}", ex.getMessage(), ex);
        }
    }

    private List<MenuDraft> parseTodayMenus(Document doc) {
        List<MenuDraft> result = new ArrayList<>();
        Element box = doc.selectFirst(TODAY_MENU_BOX_SELECTOR);
        if (box == null) {
            return result;
        }

        String dateText = box.select(TODAY_DATE_SELECTOR).text().trim();
        String imageUrl = resolveImageUrl(box, TODAY_IMAGE_SELECTOR);
        String menuName = convertBrToNewline(box.select(TODAY_CONTENT_SELECTOR).html());
        String description = convertBrToNewline(box.select("p").html());

        if (menuName == null || menuName.isBlank() || menuName.contains(CLOSED_DAY_TEXT)) {
            return result;
        }

        LocalDate menuDate = parseMenuDateOrToday(dateText);
        Integer price = DEFAULT_MENU_PRICE;

        log.info("Parsed today menu: date={}, name={}, desc={}, image={}",
                dateText, menuName, description, imageUrl);
        result.add(new MenuDraft(menuName, description, menuDate, imageUrl, price));
        return result;
    }

    private List<MenuDraft> parseWeekMenus(Document doc) {
        List<MenuDraft> result = new ArrayList<>();
        Elements list = doc.select(WEEK_MENU_ITEM_SELECTOR);
        log.info("Found menu block count={}", list.size());

        for (Element li : list) {
            Element h3 = li.selectFirst(WEEK_NAME_SELECTOR);
            if (h3 == null) {
                continue;
            }

            String menuName = h3.text();
            if (menuName == null || menuName.isBlank() || menuName.contains(CLOSED_DAY_TEXT)) {
                continue;
            }

            String dateText = li.select(WEEK_DATE_SELECTOR).text().trim();
            String imageUrl = resolveImageUrl(li, WEEK_IMAGE_SELECTOR);
            String description = li.select(WEEK_DESCRIPTION_SELECTOR).text();
            LocalDate menuDate = parseMenuDateOrToday(dateText);
            Integer price = DEFAULT_MENU_PRICE;

            log.info("Parsed week menu: date={}, name={}, description={}, imageUrl={}",
                    dateText, menuName, description, imageUrl);
            result.add(new MenuDraft(menuName, description, menuDate, imageUrl, price));
        }
        return result;
    }

    private void clearExistingMenusForDates(List<MenuDraft> drafts) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (MenuDraft draft : drafts) {
            dates.add(draft.menuDate);
        }

        for (LocalDate date : dates) {
            List<Menu> existingMenus = menuRepository.findByMenuDate(date);
            if (existingMenus.isEmpty()) {
                continue;
            }

            // Business decision required: if menus on the target date already have related orders,
            // deletion may impact order history consistency.
            boolean hasOrderLinkedMenu = existingMenus.stream()
                    .filter(menu -> menu.getId() != null)
                    .anyMatch(menu -> Boolean.TRUE.equals(orderRepository.existsByMenuId(menu.getId())));
            if (hasOrderLinkedMenu) {
                log.warn("Menu sync aborted. Existing menus for {} are linked to orders.", date);
                throw new IllegalStateException("Menus linked to orders exist on " + date + ". Sync is not allowed.");
            }

            menuRepository.deleteByMenuDate(date);
            log.info("Existing menus for {} were deleted before sync. count={}", date, existingMenus.size());
        }
    }

    private String downloadImageIfPresent(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            return menuImageStorageService.saveFromUrl(imageUrl);
        } catch (Exception ex) {
            log.warn("Image download failed. imageUrl='{}', reason={}", imageUrl, ex.getMessage());
            return null;
        }
    }

    private String resolveImageUrl(Element root, String selector) {
        Element imageElement = root.selectFirst(selector);
        if (imageElement == null) {
            return "";
        }
        String raw = imageElement.attr("src");
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String abs = imageElement.absUrl("src");
        return (abs == null || abs.isBlank()) ? raw.trim() : abs.trim();
    }

    // private String extractWholeText(Element element) {
    //     if (element == null) {
    //         return "";
    //     }
    //     String wholeText = element.wholeText();
    //     if (wholeText == null || wholeText.isBlank()) {
    //         return element.text() == null ? "" : element.text().trim();
    //     }
    //     return wholeText.trim();
    // }

    private String convertBrToNewline(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replace("<br>", "\n")
                .replace("<br />", "\n");
    }

    private LocalDate parseMenuDateOrToday(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return LocalDate.now();
        }
        Matcher matcher = MONTH_DAY_PATTERN.matcher(dateText);
        if (!matcher.find()) {
            return LocalDate.now();
        }

        int year = LocalDate.now().getYear();
        int month = Integer.parseInt(matcher.group(1));
        int day = Integer.parseInt(matcher.group(2));
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception ex) {
            return LocalDate.now();
        }
    }

    private static class MenuDraft {
        private final String menuName;
        private final String description;
        private final LocalDate menuDate;
        private final String imageUrl;
        private final Integer price;

        private MenuDraft(String menuName, String description, LocalDate menuDate, String imageUrl, Integer price) {
            this.menuName = menuName;
            this.description = description;
            this.menuDate = menuDate;
            this.imageUrl = imageUrl;
            this.price = price;
        }
    }
}
