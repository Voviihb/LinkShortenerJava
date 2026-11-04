package ru.linkshortener;

import java.net.URI;
import java.util.UUID;

/** Данные одной ссылки. Время хранится в миллисекундах от начала Unix epoch. */
public class ShortLink {
    private final String code;
    private final UUID owner;
    private String originalUrl;
    private final long createdAt;
    private final long expiresAt;
    private int limit;
    private int visits;

    public ShortLink(String code, UUID owner, String originalUrl, long createdAt,
                     long expiresAt, int limit, int visits) {
        this.code = code;
        this.owner = owner;
        this.originalUrl = validateUrl(originalUrl).toASCIIString();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.limit = limit;
        this.visits = visits;
        validate();
    }

    public String getCode() {
        return code;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public int getLimit() {
        return limit;
    }

    public int getVisits() {
        return visits;
    }

    public void setOriginalUrl(String url) {
        originalUrl = validateUrl(url).toASCIIString();
    }

    public void setLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Лимит должен быть положительным.");
        }
        this.limit = limit;
    }

    public void incrementVisits() {
        if (visits >= limit) {
            throw new IllegalStateException("Лимит переходов исчерпан.");
        }
        visits++;
    }

    public boolean isExpired(long now) {
        return now >= expiresAt;
    }

    private void validate() {
        if (code == null || !code.matches("[a-zA-Z0-9]{8}") || owner == null) {
            throw new IllegalArgumentException("Некорректный код ссылки или UUID владельца.");
        }
        validateUrl(originalUrl);
        if (expiresAt <= createdAt || limit <= 0 || visits < 0) {
            throw new IllegalArgumentException("Некорректный срок действия, лимит или счётчик переходов.");
        }
    }

    public static URI validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535))) {
                return uri;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Введите полный HTTP(S) URL, например https://example.com.", e);
        }
        throw new IllegalArgumentException("Введите полный HTTP(S) URL без логина и пароля.");
    }
}
