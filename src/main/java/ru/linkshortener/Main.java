package ru.linkshortener;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/** Консольное меню и точка входа приложения. */
public class Main {
    private final LinkService service;
    private final Scanner scanner;
    private final PrintStream output;

    Main(LinkService service, Scanner scanner, PrintStream output) {
        this.service = service;
        this.scanner = scanner;
        this.output = output;
    }

    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.load(Paths.get("application.properties"));
            LinkService service = new LinkService(config, new FileStorage(config.getDataFile()));
            try (Scanner scanner = new Scanner(System.in, "UTF-8")) {
                Main app = new Main(service, scanner, System.out);
                app.run();
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Не удалось продолжить работу: " + e.getMessage());
        }
    }

    void run() throws IOException {
        output.println("Сервис сокращения ссылок. Для справки выберите 8.");
        while (true) {
            service.cleanupExpired();
            showNotifications();
            showMenu();
            String choice = read("Выберите действие или вставьте короткую ссылку: ");
            // Пользователь мог долго не вводить команду - снова проверяем сроки.
            service.cleanupExpired();
            showNotifications();
            if (choice == null || "0".equals(choice)) {
                output.println("До свидания!");
                return;
            }
            try {
                switch (choice) {
                    case "1":
                        create();
                        break;
                    case "2":
                        open(required("Короткая ссылка или код: "));
                        break;
                    case "3":
                        list();
                        break;
                    case "4":
                        changeLimit();
                        break;
                    case "5":
                        changeUrl();
                        break;
                    case "6":
                        service.delete(required("Короткая ссылка или код: "));
                        output.println("Ссылка удалена.");
                        break;
                    case "7":
                        switchUser();
                        break;
                    case "8":
                        help();
                        break;
                    default:
                        open(choice);
                        break;
                }
            } catch (NumberFormatException e) {
                output.println("Ошибка: введите целое число в допустимом диапазоне.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                output.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private void showMenu() {
        output.println("\nТекущий пользователь: "
                + (service.getCurrentUser() == null ? "новый (UUID появится при создании ссылки)" : service.getCurrentUser()));
        output.println("1. Сократить ссылку\n2. Перейти по ссылке\n3. Мои ссылки"
                + "\n4. Изменить лимит\n5. Изменить URL\n6. Удалить ссылку"
                + "\n7. Сменить пользователя\n8. Помощь\n0. Выход");
    }

    private void showNotifications() throws IOException {
        for (String notification : service.takeNotifications()) {
            output.println("Уведомление: " + notification);
        }
    }

    private void create() throws IOException {
        String url = required("Исходный URL: ");
        String value = required("Лимит переходов (Enter - из конфигурации): ");
        Integer limit = null;
        if (!value.isEmpty()) {
            limit = Integer.valueOf(value);
        }
        ShortLink link = service.create(url, limit);
        output.println("Ваш UUID: " + service.getCurrentUser());
        output.println("Короткая ссылка: " + service.shortUrl(link));
        output.println("Лимит: " + link.getLimit() + "; действует до " + formatTime(link.getExpiresAt()));
    }

    private void open(String input) throws IOException {
        ShortLink link = service.getForVisit(input);
        openInBrowser(URI.create(link.getOriginalUrl()));
        service.registerVisit(link);
        output.println("Открыт URL: " + link.getOriginalUrl()
                + ". Переходы: " + link.getVisits() + "/" + link.getLimit());
    }

    private void changeLimit() throws IOException {
        String link = required("Короткая ссылка или код: ");
        int limit = Integer.parseInt(required("Новый лимит: "));
        service.changeLimit(link, limit);
        output.println("Лимит изменён. Счётчик переходов сохранён.");
    }

    private void changeUrl() throws IOException {
        String link = required("Короткая ссылка или код: ");
        service.changeUrl(link, required("Новый исходный URL: "));
        output.println("URL изменён. Срок действия и счётчик сохранены.");
    }

    private void list() throws IOException {
        List<ShortLink> links = service.listMine();
        if (links.isEmpty()) {
            output.println("У вас пока нет действующих ссылок.");
        }
        for (ShortLink link : links) {
            output.println(service.shortUrl(link) + " -> " + link.getOriginalUrl()
                    + " | переходы " + link.getVisits() + "/" + link.getLimit()
                    + " | до " + formatTime(link.getExpiresAt())
                    + (link.getVisits() >= link.getLimit() ? " | лимит исчерпан" : ""));
        }
    }

    private void switchUser() throws IOException {
        String value = required("Введите сохранённый UUID или new для нового пользователя: ");
        if ("new".equalsIgnoreCase(value)) {
            service.newUser();
            output.println("Новая сессия. UUID будет создан вместе с первой ссылкой.");
        } else {
            UUID user;
            try {
                user = UUID.fromString(value);
                if (!user.toString().equalsIgnoreCase(value)) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Некорректный UUID. Пример: 123e4567-e89b-12d3-a456-426614174000.");
            }
            service.switchUser(user);
        }
    }

    private void help() {
        output.println("Создание: выберите 1, введите https://example.com и положительный лимит."
                + "\nПереход: выберите 2 или вставьте короткую ссылку прямо в меню."
                + "\nСсылки работают внутри этой программы. TTL задаётся в application.properties."
                + "\nПросроченные ссылки удаляются при запуске и перед командами, без фоновой задачи."
                + "\nСохраните UUID: по нему можно вернуться к своим ссылкам через пункт 7."
                + "\nРедактировать и удалять ссылки может только их владелец."
                + "\nУвеличение лимита восстанавливает доступ, если срок ещё не истёк.");
    }

    private String read(String prompt) {
        output.print(prompt);
        output.flush();
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return null;
    }

    private String required(String prompt) {
        String value = read(prompt);
        if (value == null) {
            throw new IllegalArgumentException("Ввод завершён. Операция отменена.");
        }
        return value;
    }

    private String formatTime(long timestamp) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
    }

    void openInBrowser(URI uri) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IllegalStateException("Открытие браузера недоступно. Запустите приложение в графической среде.");
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException | SecurityException e) {
            throw new IllegalStateException("Не удалось открыть браузер. Переход не засчитан.", e);
        }
    }
}
