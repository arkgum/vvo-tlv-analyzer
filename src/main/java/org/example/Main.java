package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CLI-утилита для анализа билетов Владивосток → Тель-Авив из файла tickets.json.
 * <p>
 * Печатает минимальное время полёта по каждому перевозчику, а также
 * среднюю цену, медиану и их разницу (средняя − медиана).
 *
 * @author arkgum
 * @version 1.0
 */
public class Main {

    /**
     * Модель билета из входного JSON.
     */
    record Ticket(
            String origin,
            String origin_name,
            String destination,
            String destination_name,
            String departure_date,
            String departure_time,
            String arrival_date,
            String arrival_time,
            String carrier,
            int stops,
            int price
    ){}

    static final ZoneId VVO = ZoneId.of("Asia/Vladivostok");
    static final ZoneId TLV = ZoneId.of("Asia/Jerusalem");

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Точка входа. Ожидает путь к файлу tickets.json в первом аргументе.
     * @param args аргументы командной строки
     * @throws Exception при ошибках чтения/парсинга
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java -jar vvo-tlv-analyzer.jar <path/to/tickets.json>");
            System.exit(2);
        }
        Path jsonPath = Path.of(args[0]);
        if (!Files.exists(jsonPath)) {
            System.err.println("File not found: " + jsonPath);
            System.exit(2);
        }

        List<Ticket> tickets = readTickets(jsonPath.toFile());

        List<Ticket> vvoToTlv = tickets.stream()
                .filter(t -> "Владивосток".equalsIgnoreCase(Optional.ofNullable(t.origin_name).orElse("").trim())
                        && "Тель-Авив".equalsIgnoreCase(Optional.ofNullable(t.destination_name).orElse("").trim()))
                .collect(Collectors.toList());

        if (vvoToTlv.isEmpty()) {
            System.out.println("Нет билетов Владивосток → Тель-Авив в файле.");
            return;
        }

        Map<String, Long> minDurationByCarrier = new HashMap<>();
        for (Ticket t : vvoToTlv) {
            long minutes = computeFlightMinutes(t);
            minDurationByCarrier.merge(t.carrier, minutes, Math::min);
        }

        List<Integer> prices = vvoToTlv.stream().map(t -> t.price).sorted().toList();
        double avg = prices.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);

        double median;
        int n = prices.size();
        if (n % 2 == 1) {
            median = prices.get(n / 2);
        } else {
            median = (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0;
        }
        double diff = avg - median;

        System.out.println("Минимальное время полёта Владивосток → Тель-Авив по авиаперевозчикам:");
        minDurationByCarrier.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("- %s: %s%n", e.getKey(), formatHm(e.getValue())));

        DecimalFormat df = new DecimalFormat("#0.00");
        System.out.println();
        System.out.println("Цены Владивосток → Тель-Авив:");
        System.out.println("- Средняя цена: " + df.format(avg) + " руб.");
        System.out.println("- Медиана: " + df.format(median) + " руб.");
        System.out.println("- Разница (средняя - медиана): " + df.format(diff) + " руб.");
    }

    /**
     * Форматирует длительность в человекочитаемый вид "Hh Mm".
     * @param totalMinutes длительность в минутах
     * @return строка вида "12h 30m"
     */
    static String formatHm(long totalMinutes) {
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        return String.format("%dh %02dm", h, m);
    }

    /**
     * Считает длительность полёта с учётом часовых поясов VVO → TLV.
     * @param t билет
     * @return длительность в минутах
     */
    static long computeFlightMinutes(Ticket t) {
        LocalDate depDate = LocalDate.parse(t.departure_date.replaceAll("\\s+", ""), DATE);
        LocalTime depTime = LocalTime.parse(t.departure_time.replaceAll("\\s+", ""), TIME);
        LocalDate arrDate = LocalDate.parse(t.arrival_date.replaceAll("\\s+", ""), DATE);
        LocalTime arrTime = LocalTime.parse(t.arrival_time.replaceAll("\\s+", ""), TIME);

        ZonedDateTime dep = ZonedDateTime.of(LocalDateTime.of(depDate, depTime), VVO);
        ZonedDateTime arr = ZonedDateTime.of(LocalDateTime.of(arrDate, arrTime), TLV);

        return Duration.between(dep, arr).toMinutes();
    }

    /**
     * Читает tickets.json и мапит его в список {@link Ticket}.
     * @param file файл JSON
     * @return список билетов (может быть пустым)
     * @throws IOException при ошибке чтения/парсинга
     */
    static List<Ticket> readTickets(File file) throws IOException {
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(file);
        JsonNode arr = root.get("tickets");
        if (arr == null || !arr.isArray()) {
            return List.of();
        }

        List<Ticket> res = new ArrayList<>();
        for (JsonNode n : arr) {
            res.add(new Ticket(
                    optText(n, "origin"),
                    optText(n, "origin_name"),
                    optText(n, "destination"),
                    optText(n, "destination_name"),
                    optText(n, "departure_date"),
                    optText(n, "departure_time"),
                    optText(n, "arrival_date"),
                    optText(n, "arrival_time"),
                    optText(n, "carrier"),
                    n.path("stops").asInt(0),
                    n.path("price").asInt()
            ));
        }
        return res;
    }

    /**
     * Безопасно извлекает текстовое поле из JSON-узла.
     * @param n JSON-узел
     * @param field имя поля
     * @return значение поля или null
     */
    static String optText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}