# VVO→TLV Tickets Analyzer (Java, CLI)

Утилита для анализа авиабилетов Владивосток → Тель-Авив.  
Читает входной файл `tickets.json` и выводит:

- минимальное время полёта для каждого авиаперевозчика;
- среднюю цену, медиану и разницу (средняя − медиана).

---

## Требования
- Java 17+
- Maven 3.8+

---

## Сборка
```bash
mvn -q -DskipTests package
```

---

## Запуск
```bash
java -jar target/vvo-tlv-analyzer-1.0.0-jar-with-dependencies.jar tickets.json
```

---

## Формат входного файла
```json
{
  "tickets": [
    {
      "origin": "VVO",
      "origin_name": "Владивосток",
      "destination": "TLV",
      "destination_name": "Тель-Авив",
      "departure_date": "12.05.18",
      "departure_time": "16:20",
      "arrival_date": "12.05.18",
      "arrival_time": "22:10",
      "carrier": "TK",
      "stops": 3,
      "price": 12400
    }
  ]
}
```
---

## Пример вывода

Минимальное время полёта Владивосток → Тель-Авив по авиаперевозчикам:
- SU: 16h 30m
- TK: 12h 50m

Цены Владивосток → Тель-Авив:
- Средняя цена: 12750.00 руб.
- Медиана: 12750.00 руб.
- Разница (средняя - медиана): 0.00 руб.

---

## Детали реализации

    •    Парсинг JSON: Jackson
    •    Время рассчитывается через ZonedDateTime с учётом таймзон:
    •    Asia/Vladivostok
    •    Asia/Jerusalem
---
