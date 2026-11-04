package ru.linkshortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkServiceTest {
    @TempDir
    Path directory;

    private AppConfig config;
    private FileStorage storage;
    private LinkService service;

    @BeforeEach
    void setUp() throws IOException {
        config = new AppConfig(86400, 10, 100, "https://short.local/", directory.resolve("links.json"));
        storage = new FileStorage(config.getDataFile());
        service = new LinkService(config, storage);
    }

    @Test
    void createsUniqueLinksForDifferentUsersAndSeveralForOneUser() throws Exception {
        assertNull(service.getCurrentUser());
        ShortLink first = service.create("https://example.com", null);
        UUID firstUser = service.getCurrentUser();
        ShortLink second = service.create("https://example.com", null);
        assertEquals(2, service.listMine().size());
        assertEquals(10, first.getLimit());
        assertEquals(firstUser, first.getOwner());

        service.newUser();
        assertNull(service.getCurrentUser());
        ShortLink third = service.create("https://example.com", 3);
        assertNotEquals(firstUser, service.getCurrentUser());
        assertEquals(3, new HashSet<>(Arrays.asList(first.getCode(), second.getCode(), third.getCode())).size());
        assertEquals(1, service.listMine().size());
        assertEquals(third.getCode(), service.listMine().get(0).getCode());
    }

    @Test
    void permitsLastVisitThenBlocksAndNotifiesOnlyTheOwner() throws Exception {
        ShortLink link = service.create("https://example.com", 2);
        UUID owner = service.getCurrentUser();
        service.newUser();
        service.registerVisit(service.getForVisit(link.getCode()));
        service.registerVisit(service.getForVisit(service.shortUrl(link)));

        assertEquals(2, link.getVisits());
        assertThrows(IllegalStateException.class, () -> service.getForVisit(link.getCode()));
        assertThrows(IllegalStateException.class, () -> service.registerVisit(link));
        assertTrue(service.takeNotifications().isEmpty());
        service.switchUser(owner);
        List<String> messages = service.takeNotifications();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("Лимит"));
        assertTrue(service.takeNotifications().isEmpty());
    }

    @Test
    void permitsOnlyTheOwnerToEditAndDelete() throws Exception {
        ShortLink link = service.create("https://example.com", 2);
        UUID owner = service.getCurrentUser();
        service.newUser();
        service.create("https://other.example.com", 1);

        assertThrows(IllegalArgumentException.class, () -> service.changeLimit(link.getCode(), 5));
        assertThrows(IllegalArgumentException.class, () -> service.changeUrl(link.getCode(), "https://new.example.com"));
        assertThrows(IllegalArgumentException.class, () -> service.delete(link.getCode()));

        service.switchUser(owner);
        service.changeLimit(link.getCode(), 5);
        service.changeUrl(link.getCode(), "https://new.example.com");
        assertEquals(5, link.getLimit());
        assertEquals("https://new.example.com", link.getOriginalUrl());
        service.delete(link.getCode());
        assertTrue(service.listMine().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getForVisit(link.getCode()));
    }

    @Test
    void changingParametersPreservesVisitsAndExpiryAndCanRestoreAccess() throws Exception {
        ShortLink link = service.create("https://example.com", 3);
        long expiresAt = link.getExpiresAt();
        long createdAt = link.getCreatedAt();
        service.registerVisit(service.getForVisit(link.getCode()));
        service.changeLimit(link.getCode(), 1);
        assertThrows(IllegalStateException.class, () -> service.getForVisit(link.getCode()));
        assertEquals(1, service.takeNotifications().size());

        service.changeLimit(link.getCode(), 2);
        service.changeUrl(link.getCode(), "https://example.com/updated");
        assertEquals("https://example.com/updated", service.getForVisit(link.getCode()).getOriginalUrl());
        service.registerVisit(service.getForVisit(link.getCode()));
        assertEquals(2, link.getVisits());
        assertEquals(expiresAt, link.getExpiresAt());
        assertEquals(createdAt, link.getCreatedAt());
    }

    @Test
    void rejectsInvalidUrlsLimitsUsersAndLinks() throws Exception {
        String[] invalidUrls = {"", "example.com", "ftp://example.com", "javascript:alert(1)",
            "https:///path", "https://example.com/a b", "https://user:pass@example.com", "http://example.com:65536"};
        for (String url : invalidUrls) {
            assertThrows(IllegalArgumentException.class, () -> service.create(url, 1));
        }
        for (int limit : new int[]{0, -1, 101}) {
            assertThrows(IllegalArgumentException.class, () -> service.create("https://example.com", limit));
        }
        assertNull(service.getCurrentUser());
        assertTrue(service.listMine().isEmpty());

        ShortLink link = service.create("https://example.com/path?q=тест#part", 100);
        assertTrue(link.getOriginalUrl().contains("%D1%82"));
        assertThrows(IllegalArgumentException.class, () -> service.switchUser(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.getForVisit("zzzzzzzz"));
        assertThrows(IllegalArgumentException.class, () -> service.getForVisit("https://wrong.local/abcd1234"));
        assertThrows(IllegalArgumentException.class, () -> service.changeLimit(link.getCode(), 0));
        assertThrows(IllegalArgumentException.class, () -> service.changeLimit(link.getCode(), 101));
        assertThrows(IllegalArgumentException.class, () -> service.changeUrl(link.getCode(), "bad url"));
        assertEquals(100, link.getLimit());
    }

    @Test
    void blocksExpiredLinkAndSavesTheOwnersNotification() throws Exception {
        FileStorage.Data data = storage.load();
        UUID owner = UUID.randomUUID();
        ShortLink expired = new ShortLink("aaaaaaaa", owner, "https://example.com", 1000, 2000, 2, 0);
        data.users.add(owner);
        data.currentUser = owner;
        data.links.put(expired.getCode(), expired);
        storage.save(data);
        service = new LinkService(config, storage);

        assertThrows(IllegalArgumentException.class, () -> service.getForVisit(expired.getCode()));
        assertTrue(storage.load().links.isEmpty());
        assertTrue(service.takeNotifications().get(0).contains("истёк"));
        assertTrue(service.takeNotifications().isEmpty());
    }

    @Test
    void cleanupRemovesExpiredLinksAndKeepsActiveLinksAndEachOwnersMessages() throws Exception {
        FileStorage.Data data = storage.load();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        data.users.add(firstOwner);
        data.users.add(secondOwner);
        data.currentUser = firstOwner;
        data.links.put("aaaaaaaa", new ShortLink("aaaaaaaa", firstOwner, "https://example.com", 1000, 2000, 2, 0));
        data.links.put("bbbbbbbb", new ShortLink("bbbbbbbb", secondOwner, "https://example.com", 1000, 2000, 2, 0));
        long now = System.currentTimeMillis();
        data.links.put("cccccccc", new ShortLink("cccccccc", firstOwner, "https://example.com/active",
                now, now + 86400000, 2, 0));
        storage.save(data);
        service = new LinkService(config, storage);

        assertEquals(2, service.cleanupExpired());
        assertEquals(0, service.cleanupExpired());
        assertEquals(1, storage.load().links.size());
        assertEquals("cccccccc", service.listMine().get(0).getCode());
        assertEquals(1, service.takeNotifications().size());
        service.switchUser(secondOwner);
        assertTrue(service.listMine().isEmpty());
        assertEquals(1, service.takeNotifications().size());
    }

    @Test
    void restoresUsersCountersCurrentSessionAndPendingNotifications() throws Exception {
        ShortLink first = service.create("https://example.com", 1);
        UUID firstOwner = service.getCurrentUser();
        service.registerVisit(service.getForVisit(first.getCode()));
        service.newUser();
        ShortLink second = service.create("https://example.com/second", 3);
        UUID secondOwner = service.getCurrentUser();

        service = new LinkService(config, new FileStorage(config.getDataFile()));
        assertEquals(secondOwner, service.getCurrentUser());
        assertEquals(second.getCode(), service.listMine().get(0).getCode());
        assertTrue(service.takeNotifications().isEmpty());
        service.switchUser(firstOwner);
        assertEquals(1, service.listMine().get(0).getVisits());
        assertEquals(1, service.takeNotifications().size());
        assertThrows(IllegalStateException.class, () -> service.getForVisit(first.getCode()));
    }

    @Test
    void checksExactExpiryBoundaryAndUsesConfiguredTtlForNewLinks() throws Exception {
        ShortLink link = new ShortLink("aaaaaaaa", UUID.randomUUID(), "https://example.com", 1000, 2000, 1, 0);
        assertFalse(link.isExpired(1999));
        assertTrue(link.isExpired(2000));
        assertTrue(link.isExpired(2001));

        ShortLink created = service.create("https://example.com", 1);
        assertEquals(config.getTtlMillis(), created.getExpiresAt() - created.getCreatedAt());
    }

    @Test
    void countsAcceptedVisitWhenLinkExpiresBeforeRegistration() throws Exception {
        AppConfig shortTtl = new AppConfig(1, 10, 100, "https://short.local/", config.getDataFile());
        service = new LinkService(shortTtl, storage);
        ShortLink created = service.create("https://example.com", 2);
        ShortLink accepted = service.getForVisit(created.getCode());

        // Ждём в текущем тесте, как при долгом открытии браузера.
        Thread.sleep(1100);
        assertTrue(accepted.isExpired(System.currentTimeMillis()));
        service.registerVisit(accepted);

        assertEquals(1, accepted.getVisits());
        assertEquals(1, storage.load().links.get(created.getCode()).getVisits());
        assertEquals(1, service.cleanupExpired());
        assertTrue(storage.load().links.isEmpty());
    }
}
