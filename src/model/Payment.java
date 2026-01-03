package model;

public class Payment extends Transaction {
    private String from;
    private String to;

    public Payment(String from, String to, double amount) {
        super(amount);
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }
}
