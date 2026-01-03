package logic;

import java.util.List;
import model.Expense;
import model.Payment;

public interface SettlementStrategy {
    String calculate(List<String> participants, List<Expense> expenses, List<Payment> payments, String tripName);
}
