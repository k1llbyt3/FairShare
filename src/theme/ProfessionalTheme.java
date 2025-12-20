package theme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class ProfessionalTheme implements AppTheme {

    @Override
    public Color getBackground() { return new Color(20,20,25); }
    @Override
    public Color getPanelBackground() { return new Color(35,35,40); }
    
    @Override
    public Color getTextMain() { return Color.WHITE; }

    @Override
    public Color getTextMuted() { return new Color(220,220,220); }

    @Override
    public Color getAccent() { return new Color(255,193,7); }

    @Override
    public Color getBorderColor() { return new Color(80,80,80); }

    @Override
    public Color getPrimary() { return new Color(30,144,255); }

    @Override
    public Color getPrimaryHover() { return new Color(60,160,255); }

    @Override
    public Color getSuccess() { return new Color(46,204,113); }

    @Override
    public Color getSuccessHover() { return new Color(80,220,130); }

    @Override
    public Color getDanger() { return new Color(231,76,60); }
    
    @Override
    public Color getDangerHover() { return new Color(255,90,80); }

    @Override
    public Font getHeaderFont() { return new Font("Segoe UI", Font.BOLD, 15); }
    
    @Override
    public Font getTitleFont() { return new Font("Segoe UI", Font.BOLD, 24); }

    @Override
    public void stylePanel(JPanel p) {
        p.setBackground(getPanelBackground());
        p.setBorder(new EmptyBorder(15,15,15,15));
    }

    @Override
    public JTextField createTextField() {
        JTextField t = new JTextField();
        t.setBackground(getBackground());
        t.setForeground(getTextMain());
        t.setCaretColor(getAccent());
        t.setBorder(new CompoundBorder(new LineBorder(getBorderColor()), new EmptyBorder(8,8,8,8)));
        t.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        return t;
    }

    @Override
    public JLabel createHeaderLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(getAccent());
        l.setFont(getHeaderFont());
        return l;
    }

    @Override
    public JLabel createLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(getTextMain());
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        return l;
    }

    @Override
    public void styleTable(JTable t) {
        t.setBackground(getPanelBackground());
        t.setForeground(getTextMain());
        t.setRowHeight(30);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
    }
}
