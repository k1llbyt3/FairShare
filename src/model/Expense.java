package model;

import java.util.List;

public class Expense extends Transaction {
    private String payer;
    private List<String> involved;
    private String mode;
    private String desc;

    public Expense(String payer, double amount, List<String> involved, String mode, String desc) {
        super(amount);
        this.payer = payer;
        this.involved = involved;
        this.mode = mode;
        this.desc = desc;
    }

    public String getPayer() {
        return payer;
    }

    public List<String> getInvolved() {
        return involved;
    }

    public String getMode() {
        return mode;
    }

    public String getDesc() {
        return desc;
    }
}
