package ru.linkshortener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Читает и записывает данные приложения в один JSON-файл. */
public class FileStorage {
    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FileStorage(Path path) {
        this.path = path.toAbsolutePath();
    }

    Data load() throws IOException {
        if (!Files.exists(path)) {
            Data data = new Data();
            data.users = new HashSet<>();
            data.links = new HashMap<>();
            data.notifications = new HashMap<>();
            return data;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Data data = gson.fromJson(reader, Data.class);
            if (data == null || data.users == null || data.links == null || data.notifications == null) {
                throw new IllegalArgumentException("Неверная структура данных.");
            }
            return data;
        } catch (RuntimeException e) {
            throw new IOException("Повреждён файл данных " + path + ". Он не был перезаписан.", e);
        }
    }

    void save(Data data) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }
    }

    static class Data {
        UUID currentUser;
        Set<UUID> users;
        Map<String, ShortLink> links;
        Map<UUID, List<String>> notifications;
    }
}
