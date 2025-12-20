package app;

import javax.swing.SwingUtilities;
import ui.BillSplitter;

public class FairShareApp {

    private static BillSplitter app;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            app = new BillSplitter();
            app.setVisible(true);
        });
    }
}
