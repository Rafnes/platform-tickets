import java.util.Date;

public class Ticket {
    private String origin;
    private String destination;
    private Date departure;
    private Date arrival;
    private String carrier;
    private int price;

    public Ticket(String origin, String destination, String departureDate, String departureTime, String arrivalDate, String arrivalTime, String carrier, int price) {
        this.origin = origin;
        this.destination = destination;
        this.carrier = carrier;
    }
}
