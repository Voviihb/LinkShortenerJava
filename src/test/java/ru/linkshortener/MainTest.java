package ru.linkshortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir
    Path directory;

    @Test
    void runsCreateOpenBlockEditListDeleteAndHelpThroughTheConsole() throws Exception {
        AppConfig config = new AppConfig(86400, 10, 100, "https://short.local/", directory.resolve("links.json"));
        LinkService service = new LinkService(config, new FileStorage(config.getDataFile()));
        ShortLink first = service.create("https://example.com/start", 1);
        String input = String.join("\n", "1", "https://example.com/new", "", service.shortUrl(first),
                "2", first.getCode(), "4", first.getCode(), "3", "5", first.getCode(),
                "https://example.com/updated", "2", first.getCode(), "3", "6", first.getCode(), "8", "0", "");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TestConsole app = new TestConsole(service, input, bytes);
        app.run();

        String output = bytes.toString("UTF-8");
        assertEquals(Arrays.asList(URI.create("https://example.com/start"), URI.create("https://example.com/updated")), app.opened);
        assertTrue(output.contains("Короткая ссылка: https://short.local/"));
        assertTrue(output.contains("Уведомление: Лимит"));
        assertTrue(output.contains("Ошибка: Лимит"));
        assertTrue(output.contains("Ссылка удалена."));
        assertTrue(output.contains("TTL задаётся в application.properties"));
        assertTrue(output.contains("До свидания!"));
        assertEquals(1, service.listMine().size());
        assertEquals("https://example.com/new", service.listMine().get(0).getOriginalUrl());
    }

    @Test
    void handlesBadInputBrowserFailureUserSwitchingAndEndOfInput() throws Exception {
        AppConfig config = new AppConfig(86400, 10, 100, "https://short.local/", directory.resolve("links.json"));
        FileStorage storage = new FileStorage(config.getDataFile());
        LinkService service = new LinkService(config, storage);
        ShortLink first = service.create("https://example.com/first", 2);
        String input = String.join("\n", "unknown", "1", "invalid-url", "1",
                "1", "https://example.com", "abc", "2", first.getCode(), "7", "bad-uuid", "7", "new",
                "1", "https://example.com/second", "", "7", first.getOwner().toString(), "3", "1", "");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TestConsole app = new TestConsole(service, input, bytes);
        app.failBrowser = true;
        app.run();

        String output = bytes.toString("UTF-8");
        assertTrue(output.contains("введите целое число"));
        assertTrue(output.contains("Некорректный UUID"));
        assertTrue(output.contains("Браузер недоступен"));
        assertTrue(output.contains("Ввод завершён. Операция отменена."));
        assertTrue(output.contains("До свидания!"));
        assertTrue(app.opened.isEmpty());
        assertEquals(first.getOwner(), service.getCurrentUser());
        assertEquals(0, service.listMine().get(0).getVisits());
        assertEquals(0, storage.load().links.get(first.getCode()).getVisits());
        assertEquals(2, storage.load().users.size());
        assertEquals(2, storage.load().links.size());
        assertTrue(service.takeNotifications().isEmpty());
    }

    /** В тестах запоминаем адреса вместо запуска настоящего браузера. */
    private static class TestConsole extends Main {
        private final List<URI> opened = new ArrayList<>();
        private boolean failBrowser;

        TestConsole(LinkService service, String input, ByteArrayOutputStream output) throws Exception {
            super(service, new Scanner(input), new PrintStream(output, true, "UTF-8"));
        }

        @Override
        void openInBrowser(URI uri) {
            if (failBrowser) {
                throw new IllegalStateException("Браузер недоступен");
            }
            opened.add(uri);
        }
    }
}
