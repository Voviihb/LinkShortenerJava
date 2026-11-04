package ru.linkshortener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageTest {
    @TempDir
    Path directory;

    @Test
    void savesAndLoadsUsersLinksAndNotifications() throws Exception {
        FileStorage storage = new FileStorage(directory.resolve("nested/links.json"));
        FileStorage.Data data = storage.load();
        UUID user = UUID.randomUUID();
        data.users.add(user);
        data.currentUser = user;
        ShortLink link = new ShortLink("abcd1234", user, "https://example.com/?q=тест&x=1", 100, 200, 10, 2);
        data.links.put(link.getCode(), link);
        data.notifications.put(user, new ArrayList<>(Arrays.asList("Срок действия истёк.")));

        storage.save(data);
        FileStorage.Data restored = storage.load();
        ShortLink restoredLink = restored.links.get(link.getCode());

        assertEquals(user, restored.currentUser);
        assertEquals(data.users, restored.users);
        assertEquals(link.getOwner(), restoredLink.getOwner());
        assertEquals(link.getOriginalUrl(), restoredLink.getOriginalUrl());
        assertEquals(2, restoredLink.getVisits());
        assertEquals(200, restoredLink.getExpiresAt());
        assertEquals(data.notifications, restored.notifications);
    }
}
