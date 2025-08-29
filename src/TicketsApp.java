import java.io.BufferedReader;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class TicketsApp {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Некорректные параметры запуска. Корректно: java <путь к файлу TicketsApp> <путь к файлу tickets.json>");
            return;
        }
        TicketService ticketService = new TicketService();

        String json = ticketService.processFile(args[0]);
        if (json == null) {
            System.out.println("Ошибка чтения файла");
            return;
        }

        List<Ticket> tickets = ticketService.processJson(json);

        ticketService.printMinDurationsByCarrier(tickets);
        ticketService.printAvgPriceMedianPriceDifference(tickets);
    }
}

class TicketService {
    public String processFile(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line.trim());
            }
        } catch (Exception ex) {
            System.out.println("Ошибка при чтении файла: " + ex.getMessage());
            return null;
        }
        return stringBuilder.toString();
    }

    public List<Ticket> processJson(String json) {
        int start = json.indexOf("\"tickets\"");
        if (start < 0) {
            System.out.println("Поле tickets не найдено");
            return Collections.emptyList();
        }
        start = json.indexOf("[", start);
        int end = json.indexOf(']', start);
        if (start < 0 || end < 0) {
            System.out.println("Массив tickets не найден");
            return Collections.emptyList();
        }
        String ticketsArray = json.substring(start + 1, end);


        List<String> ticketJsons = splitJsonObjects(ticketsArray);

        List<Ticket> tickets = new ArrayList<>();
        for (String ticketJson : ticketJsons) {
            Map<String, String> values = parseJson(ticketJson);
            if ("VVO".equals(values.get("origin")) && "TLV".equals(values.get("destination"))) {
                try {
                    Ticket ticket = new Ticket(
                            values.get("origin"),
                            values.get("destination"),
                            values.get("departure_date"),
                            values.get("departure_time"),
                            values.get("arrival_date"),
                            values.get("arrival_time"),
                            values.get("carrier"),
                            Integer.parseInt(values.get("price"))
                    );
                    tickets.add(ticket);
                } catch (Exception e) {
                    System.out.println("Ошибка парсинга билета: " + e.getMessage());
                }
            }
        }
        if (tickets.isEmpty()) {
            System.out.println("Билеты из Владивостока в Тель-Авив не найдены");
        }
        return tickets;
    }

    private List<String> splitJsonObjects(String jsonArrayContent) {
        List<String> objects = new ArrayList<>();
        int level = 0;
        int start = 0;
        for (int i = 0; i < jsonArrayContent.length(); i++) {
            char c = jsonArrayContent.charAt(i);
            if (c == '{') {
                if (level == 0) start = i;
                level++;
            } else if (c == '}') {
                level--;
                if (level == 0) {
                    objects.add(jsonArrayContent.substring(start, i + 1));
                }
            }
        }
        return objects;
    }

    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();

        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }

        int length = json.length();
        boolean inQuotes = false;
        int start = 0;
        List<String> pairs = new ArrayList<>();

        for (int i = 0; i < length; i++) {
            char c = json.charAt(i);
            if (c == '"') {
                if (i == 0 || json.charAt(i - 1) != '\\') {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                pairs.add(json.substring(start, i));
                start = i + 1;
            }
        }
        pairs.add(json.substring(start, length));

        for (String pair : pairs) {
            int splitIndex = -1;
            inQuotes = false;
            for (int i = 0; i < pair.length(); i++) {
                char ch = pair.charAt(i);
                if (ch == '"') {
                    if (i == 0 || pair.charAt(i - 1) != '\\') {
                        inQuotes = !inQuotes;
                    }
                } else if (ch == ':' && !inQuotes) {
                    splitIndex = i;
                    break;
                }
            }
            if (splitIndex != -1) {
                String key = pair.substring(0, splitIndex).trim();
                String value = pair.substring(splitIndex + 1).trim();

                if (key.startsWith("\"") && key.endsWith("\"") && key.length() > 1) {
                    key = key.substring(1, key.length() - 1);
                }

                if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                    value = value.substring(1, value.length() - 1);
                    value = value.replace("\\\"", "\"");
                    value = value.replace("\\\\", "\\");
                }

                map.put(key, value);
            }
        }
        return map;
    }

    public void printMinDurationsByCarrier(List<Ticket> tickets) {
        Map<String, Long> minDurations = new HashMap<>();
        for (Ticket ticket : tickets) {
            long duration = ticket.getFlightDurationMinutes();
            String carrier = ticket.getCarrier();
            if (!minDurations.containsKey(carrier)) {
                minDurations.put(carrier, duration);
            } else {
                long currentMin = minDurations.get(carrier);
                if (duration < currentMin) {
                    minDurations.put(carrier, duration);
                }
            }
        }
        System.out.println("Минимальное время перелета между Владивостоком и Тель-Авивом:");
        for (Map.Entry<String, Long> entry : minDurations.entrySet()) {
            long hrs = entry.getValue() / 60;
            long mins = entry.getValue() % 60;
            System.out.printf("Перевозчик %s: %d часов %d минут\n", entry.getKey(), hrs, mins);
        }
    }

    public void printAvgPriceMedianPriceDifference(List<Ticket> tickets) {
        List<Integer> prices = new ArrayList<>();
        for (Ticket ticket : tickets) {
            prices.add(ticket.getPrice());
        }
        double avgPrice = prices.stream()
                .mapToInt(price -> price)
                .average()
                .orElse(0.0);

        Collections.sort(prices);
        double medianPrice;

        int size = prices.size();
        if (size % 2 == 1) {
            medianPrice = prices.get(size / 2);
        } else {
            medianPrice = (prices.get(size / 2 - 1) + prices.get(size / 2)) / 2.0;
        }

        double diff = avgPrice - medianPrice;
        System.out.printf("Средняя цена: %.2f\n", avgPrice);
        System.out.printf("Медиана цен: %.2f\n", medianPrice);
        System.out.printf("Разница между средней ценой и медианой: %.2f\n", diff);
    }
}

class Ticket {
    private String origin;
    private String destination;
    private Date departure;
    private Date arrival;
    private String carrier;
    private int price;

    public Ticket(String origin, String destination, String departureDate, String departureTime,
                  String arrivalDate, String arrivalTime, String carrier, int price) {
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yy HH:mm");
        try {
            this.departure = dateFormat.parse(departureDate + " " + departureTime);
            this.arrival = dateFormat.parse(arrivalDate + " " + arrivalTime);
        } catch (ParseException exception) {
            System.out.println("Не удалось прочитать время и дату: " + exception.getMessage());
        }
        this.origin = origin;
        this.destination = destination;
        this.carrier = carrier;
        this.price = price;
    }

    public String getCarrier() {
        return carrier;
    }

    public int getPrice() {
        return price;
    }

    public long getFlightDurationMinutes() {
        return (arrival.getTime() - departure.getTime()) / (1000 * 60);
    }
}