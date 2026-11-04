package ru.linkshortener;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {
    private final int ttlSeconds;
    private final int defaultLimit;
    private final int maxLimit;
    private final String shortUrlPrefix;
    private final Path dataFile;

    public AppConfig(int ttlSeconds, int defaultLimit, int maxLimit, String shortUrlPrefix, Path dataFile) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL должен быть положительным числом секунд.");
        }
        if (defaultLimit <= 0 || maxLimit < defaultLimit) {
            throw new IllegalArgumentException("Лимиты должны быть положительными; default-limit <= max-limit.");
        }
        ShortLink.validateUrl(shortUrlPrefix);
        if (!shortUrlPrefix.endsWith("/") || shortUrlPrefix.contains("?") || shortUrlPrefix.contains("#")) {
            throw new IllegalArgumentException("Префикс короткой ссылки должен оканчиваться / без query и fragment.");
        }
        if (dataFile == null) {
            throw new IllegalArgumentException("Не указан путь к файлу данных.");
        }
        this.ttlSeconds = ttlSeconds;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.shortUrlPrefix = shortUrlPrefix;
        this.dataFile = dataFile;
    }

    public long getTtlMillis() {
        return ttlSeconds * 1000L;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public String getShortUrlPrefix() {
        return shortUrlPrefix;
    }

    public Path getDataFile() {
        return dataFile;
    }

    public static AppConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        try {
            return new AppConfig(
                    Integer.parseInt(required(properties, "link.ttl.seconds")),
                    Integer.parseInt(required(properties, "link.default-limit")),
                    Integer.parseInt(required(properties, "link.max-limit")),
                    required(properties, "short.url.prefix"),
                    path.toAbsolutePath().getParent().resolve(required(properties, "data.file")).normalize());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ошибка конфигурации: " + e.getMessage(), e);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Не задан параметр " + key);
        }
        return value.trim();
    }
}
