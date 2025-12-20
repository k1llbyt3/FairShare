package components;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ModernButton extends JButton {

    private final Color normalBg;
    private final Color hoverBg;
    private final Color normalText;
    private Color hoverText;

    public ModernButton(String text, Color normalBg, Color hoverBg, Color textColor) {
        super(text);

        this.normalBg = normalBg;
        this.hoverBg = hoverBg;
        this.normalText = textColor;
        this.hoverText = textColor;

        setBackground(this.normalBg);
        setForeground(this.normalText);
        setFocusPainted(false);
        setBorderPainted(false);
        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(8, 15, 8, 15));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(ModernButton.this.hoverBg);
                setForeground(hoverText);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(ModernButton.this.normalBg);
                setForeground(normalText);
            }
        });
    }

    public void setHoverTextColor(Color c) {
        this.hoverText = c;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g2.dispose();
        super.paintComponent(g);
    }
}
