package model;

public class Payment {
    public String from, to;
    public double amount;

    public Payment(String f, String t, double a) {
        from = f;
        to = t;
        amount = a;
    }
}
