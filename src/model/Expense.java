package model;

import java.util.List;

public class Expense {
    public String payer;
    public double amount;
    public List<String> involved;
    public String mode;
    public String desc;

    public Expense(String payer,
                   double amount,
                   List<String> involved,
                   String mode,
                   String desc) {
        this.payer = payer;
        this.amount = amount;
        this.involved = involved;
        this.mode = mode;
        this.desc = desc;
    }
}
