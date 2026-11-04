package ru.linkshortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {
    @TempDir
    Path directory;

    private static final String SETTINGS = String.join("\n",
            "link.ttl.seconds=3600",
            "link.default-limit=5",
            "link.max-limit=100",
            "short.url.prefix=https://short.local/",
            "data.file=data/links.json");

    @Test
    void loadsAllSettingsAndResolvesRelativeDataPath() throws Exception {
        Path path = directory.resolve("application.properties");
        Files.write(path, SETTINGS.getBytes(StandardCharsets.UTF_8));
        AppConfig config = AppConfig.load(path);

        assertEquals(3600000L, config.getTtlMillis());
        assertEquals(5, config.getDefaultLimit());
        assertEquals(100, config.getMaxLimit());
        assertEquals("https://short.local/", config.getShortUrlPrefix());
        assertEquals(directory.resolve("data/links.json"), config.getDataFile());
    }

    @Test
    void rejectsMissingAndInvalidSettings() throws Exception {
        Path path = directory.resolve("application.properties");
        String[] invalidSettings = {
            SETTINGS.replace("link.ttl.seconds=3600", "link.ttl.seconds=0"),
            SETTINGS.replace("link.default-limit=5", "link.default-limit=-1"),
            SETTINGS.replace("link.max-limit=100", "link.max-limit=4"),
            SETTINGS.replace("https://short.local/", "https://short.local"),
            SETTINGS.replace("https://short.local/", "ftp://short.local/"),
            SETTINGS.replace("data.file=data/links.json", ""),
            SETTINGS.replace("link.ttl.seconds=3600", "link.ttl.seconds=abc")
        };
        for (String settings : invalidSettings) {
            Files.write(path, settings.getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalArgumentException.class, () -> AppConfig.load(path));
        }
    }
}
