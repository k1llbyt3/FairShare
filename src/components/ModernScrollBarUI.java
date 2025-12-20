package components;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import theme.AppTheme;

public class ModernScrollBarUI extends BasicScrollBarUI {

    private final AppTheme t;

    public ModernScrollBarUI(AppTheme t) {
        this.t = t;
    }

    @Override
    protected void configureScrollBarColors() {
        thumbColor = t.getAccent();
        trackColor = t.getPanelBackground();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private JButton createZeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }
}
