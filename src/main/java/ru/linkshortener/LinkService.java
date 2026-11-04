package ru.linkshortener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Создание ссылок, подсчёт переходов и проверка прав владельца. */
public class LinkService {
    private final AppConfig config;
    private final FileStorage storage;
    private final FileStorage.Data data;

    public LinkService(AppConfig config, FileStorage storage) throws IOException {
        this.config = config;
        this.storage = storage;
        data = storage.load();
    }

    public UUID getCurrentUser() {
        return data.currentUser;
    }

    public void newUser() throws IOException {
        data.currentUser = null;
        saveData();
    }

    public void switchUser(UUID user) throws IOException {
        if (!data.users.contains(user)) {
            throw new IllegalArgumentException("Пользователь с таким UUID не найден.");
        }
        data.currentUser = user;
        saveData();
    }

    public ShortLink create(String url, Integer requestedLimit) throws IOException {
        String normalizedUrl = ShortLink.validateUrl(url).toASCIIString();
        int limit = requestedLimit == null ? config.getDefaultLimit() : requestedLimit;
        validateLimit(limit);
        cleanupExpired();
        String code = generateCode();
        long createdAt = System.currentTimeMillis();
        long expiresAt = createdAt + config.getTtlMillis();
        if (data.currentUser == null) {
            data.currentUser = UUID.randomUUID();
            data.users.add(data.currentUser);
        }
        ShortLink link = new ShortLink(code, data.currentUser, normalizedUrl, createdAt, expiresAt, limit, 0);
        data.links.put(code, link);
        saveData();
        return link;
    }

    public ShortLink getForVisit(String input) throws IOException {
        ShortLink link = find(input);
        if (link.getVisits() >= link.getLimit()) {
            throw new IllegalStateException("Лимит переходов по ссылке " + link.getCode() + " исчерпан.");
        }
        return link;
    }

    /** Учитывает переход по ссылке, проверенной перед открытием браузера. */
    public void registerVisit(ShortLink link) throws IOException {
        if (link == null || data.links.get(link.getCode()) != link) {
            throw new IllegalArgumentException("Ссылка не найдена.");
        }
        // Срок проверен до открытия браузера. Здесь учитывается уже выполненный переход.
        link.incrementVisits();
        if (link.getVisits() == link.getLimit()) {
            notifyOwner(link.getOwner(), "Лимит переходов по ссылке " + shortUrl(link) + " исчерпан.");
        }
        saveData();
    }

    public List<ShortLink> listMine() throws IOException {
        cleanupExpired();
        List<ShortLink> links = new ArrayList<>();
        for (ShortLink link : data.links.values()) {
            if (link.getOwner().equals(data.currentUser)) {
                links.add(link);
            }
        }
        return links;
    }

    public void changeLimit(String input, int limit) throws IOException {
        validateLimit(limit);
        ShortLink link = owned(input);
        boolean wasAvailable = link.getVisits() < link.getLimit();
        link.setLimit(limit);
        if (wasAvailable && link.getVisits() >= limit) {
            notifyOwner(link.getOwner(), "Лимит переходов по ссылке " + shortUrl(link) + " исчерпан.");
        }
        saveData();
    }

    public void changeUrl(String input, String url) throws IOException {
        ShortLink.validateUrl(url);
        ShortLink link = owned(input);
        link.setOriginalUrl(url);
        saveData();
    }

    public void delete(String input) throws IOException {
        ShortLink link = owned(input);
        data.links.remove(link.getCode());
        saveData();
    }

    public int cleanupExpired() throws IOException {
        long now = System.currentTimeMillis();
        List<String> expiredCodes = new ArrayList<>();
        for (ShortLink link : data.links.values()) {
            if (link.isExpired(now)) {
                expiredCodes.add(link.getCode());
                notifyOwner(link.getOwner(), "Срок действия ссылки " + shortUrl(link) + " истёк. Ссылка удалена.");
            }
        }
        for (String code : expiredCodes) {
            data.links.remove(code);
        }
        if (!expiredCodes.isEmpty()) {
            saveData();
        }
        return expiredCodes.size();
    }

    public List<String> takeNotifications() throws IOException {
        List<String> messages = data.notifications.remove(data.currentUser);
        if (messages == null) {
            return new ArrayList<>();
        }
        saveData();
        return messages;
    }

    public String shortUrl(ShortLink link) {
        return config.getShortUrlPrefix() + link.getCode();
    }

    private String generateCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (data.links.containsKey(code));
        return code;
    }

    private ShortLink find(String input) throws IOException {
        cleanupExpired();
        String code = input;
        if (input.startsWith(config.getShortUrlPrefix())) {
            code = input.substring(config.getShortUrlPrefix().length());
        }
        if (!code.matches("[a-zA-Z0-9]{8}")) {
            throw new IllegalArgumentException("Введите короткую ссылку или её код из 8 букв и цифр.");
        }
        ShortLink link = data.links.get(code);
        if (link == null) {
            throw new IllegalArgumentException("Ссылка не найдена: она удалена или срок её действия истёк.");
        }
        return link;
    }

    private ShortLink owned(String input) throws IOException {
        ShortLink link = find(input);
        if (!link.getOwner().equals(data.currentUser)) {
            throw new IllegalArgumentException("Изменять и удалять ссылку может только её владелец.");
        }
        return link;
    }

    private void validateLimit(int limit) {
        if (limit <= 0 || limit > config.getMaxLimit()) {
            throw new IllegalArgumentException("Лимит должен быть от 1 до " + config.getMaxLimit() + ".");
        }
    }

    private void notifyOwner(UUID user, String message) {
        List<String> messages = data.notifications.get(user);
        if (messages == null) {
            messages = new ArrayList<>();
            data.notifications.put(user, messages);
        }
        messages.add(message);
    }

    private void saveData() throws IOException {
        storage.save(data);
    }
}
