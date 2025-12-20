package theme;

import java.awt.*;
import javax.swing.*;

public interface AppTheme {

    Color getBackground();
    Color getPanelBackground();
    Color getTextMain();
    Color getTextMuted();
    Color getAccent();
    Color getBorderColor();
    Color getPrimary();
    Color getPrimaryHover();
    Color getSuccess();
    Color getSuccessHover();
    Color getDanger();
    Color getDangerHover();

    Font getHeaderFont();
    Font getTitleFont();

    void stylePanel(JPanel p);
    JTextField createTextField();
    JLabel createHeaderLabel(String t);
    JLabel createLabel(String t);
    void styleTable(JTable t);
}
