package logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Balance;
import model.Expense;
import model.Payment;

public class StandardSettlementStrategy implements SettlementStrategy {

    @Override
    public String calculate(List<String> participants, List<Expense> expenses, List<Payment> payments,
            String tripName) {
        if (participants.isEmpty()) {
            return "Error: Add members first.";
        }
        if (expenses.isEmpty() && payments.isEmpty()) {
            return "Error: Add expenses or payments first.";
        }

        Map<String, Double> balances = new HashMap<>();
        for (String p : participants)
            balances.put(p, 0.0);

        // Expense logic
        for (Expense e : expenses) {
            balances.put(e.getPayer(), balances.get(e.getPayer()) + e.getAmount());
            double share = e.getAmount() / e.getInvolved().size();
            for (String c : e.getInvolved()) {
                balances.put(c, balances.get(c) - share);
            }
        }

        // Payment logic (A paid B -> A gets credited)
        for (Payment p : payments) {
            balances.put(p.getFrom(), balances.get(p.getFrom()) + p.getAmount());
            balances.put(p.getTo(), balances.get(p.getTo()) - p.getAmount());
        }

        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();

        for (Map.Entry<String, Double> e : balances.entrySet()) {
            double v = e.getValue();
            if (Math.abs(v) < 0.01)
                continue;
            if (v < 0)
                debtors.add(new Balance(e.getKey(), Math.abs(v)));
            else
                creditors.add(new Balance(e.getKey(), v));
        }

        debtors.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));
        creditors.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));

        double totalSpend = expenses.stream().mapToDouble(Expense::getAmount).sum();
        double equalShare = participants.isEmpty() ? 0 : totalSpend / participants.size();

        if (tripName == null || tripName.trim().isEmpty())
            tripName = "TRIP";
        tripName = tripName.toUpperCase();

        StringBuilder sb = new StringBuilder();
        String line = "=====================================\n";

        sb.append(line);
        sb.append(String.format("             %s\n", tripName));
        sb.append(line);
        sb.append(String.format("Total Spent: ₹%.2f\n", totalSpend));
        sb.append(String.format("People: %d\n", participants.size()));
        sb.append(String.format("Per Person: ₹%.2f\n", equalShare));
        sb.append(line);

        if (debtors.isEmpty()) {
            sb.append("\n🎉 Everyone is already settled!\n");
        } else {
            sb.append("\nWho Pays Whom:\n\n");

            int i = 0, j = 0;
            while (i < debtors.size() && j < creditors.size()) {
                Balance d = debtors.get(i);
                Balance c = creditors.get(j);

                double amt = Math.min(d.getAmount(), c.getAmount());

                sb.append(String.format("• %s pays %s ₹%.2f\n", d.getName(), c.getName(), amt));

                d.setAmount(d.getAmount() - amt);
                c.setAmount(c.getAmount() - amt);

                if (d.getAmount() < 0.01)
                    i++;
                if (c.getAmount() < 0.01)
                    j++;
            }
        }

        sb.append("\n");
        sb.append(line);

        return sb.toString();
    }
}
