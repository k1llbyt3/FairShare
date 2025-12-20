package components;

import java.awt.*;
import javax.swing.*;

public class ModernToggle extends JToggleButton {
    Color activeColor, inactiveColor;

    public ModernToggle(String text, Color active, Color inactive) {
        super(text);
        this.activeColor = active;
        this.inactiveColor = inactive;
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setForeground(Color.WHITE);
        setPreferredSize(new Dimension(100, 30));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(isSelected() ? activeColor : inactiveColor);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        super.paintComponent(g);
        g2.dispose();
    }
}
