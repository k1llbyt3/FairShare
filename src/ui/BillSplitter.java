package ui;

import components.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.text.AbstractDocument;
import model.*;
import org.w3c.dom.events.MouseEvent;
import theme.*;

    public class BillSplitter extends JFrame {

    private final ArrayList<String> participants;
    private final ArrayList<Expense> expenses;
    private final ArrayList<Payment> payments;
    private final AppTheme theme;

    // UI Components
    private JTextField tripNameField;
    private JTextField participantField;
    private JComboBox<String> payerDropdown;
    private JTextField descField;
    private JPanel suggestionPanel;
    private JTextField amountField;

    private ModernToggle btnModeOnline;
    private ModernToggle btnModeCash;

    private JPanel chipsPanel;
    private JPanel participantsListPanel;
    private final List<ModernToggle> participantChips;
    private DefaultTableModel expenseTableModel;
    private DefaultTableModel paymentTableModel;
    private JTable expenseTable;
    private JTable paymentTable;
    private JTextArea settlementArea;
    private JLabel totalStatsLabel;
    private JComboBox<String> paymentFromDropdown;
    private JComboBox<String> paymentToDropdown;
    private JTextField paymentAmountField;

    public BillSplitter() {
        this.theme = new ProfessionalTheme();
        this.participants = new ArrayList<>();
        this.expenses = new ArrayList<>();
        this.payments = new ArrayList<>();
        this.participantChips = new ArrayList<>();

        setupWindow();
        setupUI();
        setVisible(true);
    }

    private void setupWindow() {
        setTitle("FairShare");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.9);
        int height = (int) (screenSize.height * 0.9);
        setSize(width, height);
        setLocationRelativeTo(null);
        getContentPane().setBackground(theme.getBackground());
        setLayout(new BorderLayout());
    }

    private void setupUI() {
        add(createHeader(), BorderLayout.NORTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createInputPanel(), createReportPanel());
        mainSplit.setResizeWeight(0.40);
        mainSplit.setDividerSize(0);
        mainSplit.setBorder(null);
        mainSplit.setBackground(theme.getBackground());

        add(mainSplit, BorderLayout.CENTER);
    }

   private JPanel createHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(theme.getPanelBackground());
    header.setBorder(new EmptyBorder(20, 30, 20, 30));

    // Trip name field with placeholder
    tripNameField = new JTextField();
    tripNameField.setFont(theme.getTitleFont());
    tripNameField.setBackground(theme.getPanelBackground());
    tripNameField.setForeground(theme.getTextMuted()); // placeholder color
    tripNameField.setBorder(null);
    tripNameField.setCaretColor(theme.getAccent());

    // Placeholder text
    final String PLACEHOLDER = "Trip (Rename)";
    tripNameField.setText(PLACEHOLDER);

    // Width + limit
    tripNameField.setColumns(26);
    ((AbstractDocument) tripNameField.getDocument())
            .setDocumentFilter(new LengthFilter(26));

    // Placeholder behavior
    tripNameField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            if (tripNameField.getText().equals(PLACEHOLDER)) {
                tripNameField.setText("");
                tripNameField.setForeground(theme.getAccent());
            }
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (tripNameField.getText().trim().isEmpty()) {
                tripNameField.setText(PLACEHOLDER);
                tripNameField.setForeground(theme.getTextMuted());
            }
        }
    });

    header.add(tripNameField, BorderLayout.WEST);

    JLabel brand = new JLabel("FAIRSHARE");
    brand.setFont(new Font("Segoe UI", Font.BOLD, 20));
    brand.setForeground(theme.getTextMain());
    header.add(brand, BorderLayout.EAST);

    return header;
}
 


    // --- LEFT PANEL ---
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBackground(theme.getBackground());
        panel.setBorder(new EmptyBorder(20, 20, 20, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(theme.getBackground());

        // 1. Participants Section
        JPanel pPanel = new JPanel(new BorderLayout(5, 10));
        theme.stylePanel(pPanel);
        pPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JLabel lblP = theme.createHeaderLabel("1. Members");
        pPanel.add(lblP, BorderLayout.NORTH);

        JPanel pInputBox = new JPanel(new BorderLayout(10, 0));
        pInputBox.setOpaque(false);

        participantField = theme.createTextField();
        participantField.setText("Name...");
        participantField.setForeground(Color.LIGHT_GRAY);
        participantField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (participantField.getText().equals("Name...")) {
                    participantField.setText("");
                    participantField.setForeground(theme.getTextMain());
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (participantField.getText().isEmpty()) {
                    participantField.setText("Name...");
                    participantField.setForeground(Color.LIGHT_GRAY);
                }
            }
        });
        participantField.addActionListener(e -> addParticipant());

        pInputBox.add(participantField, BorderLayout.CENTER);

        ModernButton btnAddP = new ModernButton("Add", theme.getPrimary(), theme.getPrimaryHover(), Color.WHITE);
        btnAddP.addActionListener(e -> addParticipant());
        pInputBox.add(btnAddP, BorderLayout.EAST);

        pPanel.add(pInputBox, BorderLayout.NORTH);

        // Participants list with delete buttons
        participantsListPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        participantsListPanel.setOpaque(false);
        participantsListPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JScrollPane participantsScroll = new JScrollPane(participantsListPanel);
        participantsScroll.setPreferredSize(new Dimension(0, 80));
        participantsScroll.setBorder(new LineBorder(theme.getBorderColor()));
        participantsScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        participantsScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));
        participantsScroll.getViewport().setBackground(theme.getPanelBackground());
        participantsScroll.setOpaque(false);
        participantsScroll.getViewport().setOpaque(false);

        pPanel.add(participantsScroll, BorderLayout.CENTER);
        content.add(pPanel);
        content.add(Box.createVerticalStrut(20));

        // 2. Transaction Section
        JPanel ePanel = new JPanel(new GridBagLayout());
        theme.stylePanel(ePanel);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.weightx = 1.0;

        gbc.gridx = 0;
        gbc.gridy = 0;
        ePanel.add(theme.createHeaderLabel("2. Transaction"), gbc);

        gbc.gridy++;
        ePanel.add(theme.createLabel("Who Paid?"), gbc);
        payerDropdown = new JComboBox<>();
        styleComboBox(payerDropdown);
        gbc.gridy++;
        ePanel.add(payerDropdown, gbc);

        gbc.gridy++;
        ePanel.add(theme.createLabel("For What?"), gbc);
        descField = theme.createTextField();
        gbc.gridy++;
        ePanel.add(descField, gbc);

        suggestionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        suggestionPanel.setOpaque(false);
        addSuggestionTag("Dinner");
        addSuggestionTag("Fuel");
        addSuggestionTag("Tickets");
        addSuggestionTag("Hotel");
        gbc.gridy++;
        ePanel.add(suggestionPanel, gbc);

        gbc.gridy++;
        ePanel.add(theme.createLabel("Amount (₹)"), gbc);
        amountField = theme.createTextField();
        gbc.gridy++;
        ePanel.add(amountField, gbc);

        gbc.gridy++;
        ePanel.add(theme.createLabel("Payment Mode"), gbc);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.setOpaque(false);

        btnModeOnline = new ModernToggle("Online", theme.getPrimary(), theme.getBackground());
        btnModeCash = new ModernToggle("Cash", theme.getPrimary(), theme.getBackground());

        btnModeOnline.addActionListener(e -> {
            btnModeOnline.setSelected(true);
            btnModeCash.setSelected(false);
        });
        btnModeCash.addActionListener(e -> {
            btnModeCash.setSelected(true);
            btnModeOnline.setSelected(false);
        });
        btnModeOnline.setSelected(true);

        modePanel.add(btnModeOnline);
        modePanel.add(Box.createHorizontalStrut(15));
        modePanel.add(btnModeCash);

        gbc.gridy++;
        ePanel.add(modePanel, gbc);

        gbc.gridy++;
        ePanel.add(theme.createLabel("Split Among"), gbc);

        chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        chipsPanel.setOpaque(false);

        JScrollPane scrollChips = new JScrollPane(chipsPanel);
        scrollChips.setPreferredSize(new Dimension(0, 100));
        scrollChips.setBorder(new LineBorder(theme.getBorderColor()));
        scrollChips.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        scrollChips.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));
        scrollChips.getViewport().setBackground(theme.getPanelBackground());

        gbc.gridy++;
        ePanel.add(scrollChips, gbc);

        JPanel chipActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chipActions.setOpaque(false);
        JButton btnAll = createTextButton("Select All");
        btnAll.addActionListener(e -> toggleChips(true));
        chipActions.add(btnAll);

        gbc.gridy++;
        ePanel.add(chipActions, gbc);

        ModernButton btnAddE = new ModernButton("Add Entry", theme.getSuccess(), theme.getSuccessHover(), Color.BLACK);
        btnAddE.addActionListener(e -> addExpense());
        gbc.gridy++;
        gbc.insets = new Insets(20, 0, 0, 0);
        ePanel.add(btnAddE, gbc);

        content.add(ePanel);
        content.add(Box.createVerticalStrut(20));

        // 3. Payment Section (Who Paid Whom)
        JPanel paymentPanel = new JPanel(new GridBagLayout());
        theme.stylePanel(paymentPanel);
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.insets = new Insets(8, 0, 8, 0);
        gbc2.weightx = 1.0;

        gbc2.gridx = 0;
        gbc2.gridy = 0;
        paymentPanel.add(theme.createHeaderLabel("3. Record Payment"), gbc2);

        gbc2.gridy++;
        paymentPanel.add(theme.createLabel("From"), gbc2);
        paymentFromDropdown = new JComboBox<>();
        styleComboBox(paymentFromDropdown);
        gbc2.gridy++;
        paymentPanel.add(paymentFromDropdown, gbc2);

        gbc2.gridy++;
        paymentPanel.add(theme.createLabel("To"), gbc2);
        paymentToDropdown = new JComboBox<>();
        styleComboBox(paymentToDropdown);
        gbc2.gridy++;
        paymentPanel.add(paymentToDropdown, gbc2);

        // When "From" changes, refresh "To" to avoid same member
        paymentFromDropdown.addActionListener(e -> refreshPaymentToDropdown());

        gbc2.gridy++;
        paymentPanel.add(theme.createLabel("Amount (₹)"), gbc2);
        paymentAmountField = theme.createTextField();
        gbc2.gridy++;
        paymentPanel.add(paymentAmountField, gbc2);

        ModernButton btnAddPayment = new ModernButton("Record Payment", theme.getAccent(), theme.getAccent().darker(), Color.BLACK);
        btnAddPayment.addActionListener(e -> addPayment());
        gbc2.gridy++;
        gbc2.insets = new Insets(20, 0, 0, 0);
        paymentPanel.add(btnAddPayment, gbc2);

        content.add(paymentPanel);

        JScrollPane mainScroll = new JScrollPane(content);
        mainScroll.setBorder(null);
        mainScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        mainScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));

        panel.add(mainScroll, BorderLayout.CENTER);
        return panel;
    }

    private void addSuggestionTag(String text) {
        JButton tag = new JButton(text);
        tag.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tag.setForeground(theme.getTextMuted());
        tag.setBackground(theme.getBackground());
        tag.setBorder(BorderFactory.createLineBorder(theme.getBorderColor()));
        tag.setFocusPainted(false);
        tag.setCursor(new Cursor(Cursor.HAND_CURSOR));
        tag.setMargin(new Insets(2, 5, 2, 5));
        tag.addActionListener(e -> descField.setText(text));

        tag.addMouseListener(new MouseAdapter() {
        @SuppressWarnings("unused")
        public void mouseEntered(MouseEvent e) {
            tag.setForeground(theme.getAccent());
            tag.setBorder(BorderFactory.createLineBorder(theme.getAccent()));
        }

         @SuppressWarnings("unused")
        public void mouseExited(MouseEvent e) {
             tag.setForeground(theme.getTextMuted());
             tag.setBorder(BorderFactory.createLineBorder(theme.getBorderColor()));
         }
        });


        suggestionPanel.add(tag);
    }

    // --- RIGHT PANEL ---
    private JPanel createReportPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBackground(theme.getBackground());
        panel.setBorder(new EmptyBorder(20, 10, 20, 20));

        // Table
        JPanel tableWrap = new JPanel(new BorderLayout());
        tableWrap.setBackground(theme.getPanelBackground());
        tableWrap.setBorder(new LineBorder(theme.getBorderColor()));
        tableWrap.setPreferredSize(new Dimension(0, 300));

        String[] cols = {"Payer", "Item", "Amount", "Mode", "Split"};
        expenseTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        expenseTable = new JTable(expenseTableModel);
        theme.styleTable(expenseTable);

        JScrollPane tableScroll = new JScrollPane(expenseTable);
        tableScroll.setBorder(null);
        tableScroll.getViewport().setBackground(theme.getPanelBackground());
        tableScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        tableScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));

        tableWrap.add(tableScroll, BorderLayout.CENTER);

        ModernButton btnRemove = new ModernButton("Delete", theme.getPanelBackground(), theme.getDanger(), theme.getDanger());
        btnRemove.setHoverTextColor(Color.WHITE);
        btnRemove.addActionListener(e -> removeExpense(expenseTable));
        tableWrap.add(btnRemove, BorderLayout.SOUTH);

        panel.add(tableWrap, BorderLayout.NORTH);

        // Payments Table
        JPanel paymentTableWrap = new JPanel(new BorderLayout());
        paymentTableWrap.setBackground(theme.getPanelBackground());
        paymentTableWrap.setBorder(new LineBorder(theme.getBorderColor()));
        paymentTableWrap.setPreferredSize(new Dimension(0, 150));

        String[] paymentCols = {"From", "To", "Amount"};
        paymentTableModel = new DefaultTableModel(paymentCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        paymentTable = new JTable(paymentTableModel);
        theme.styleTable(paymentTable);

        JScrollPane paymentTableScroll = new JScrollPane(paymentTable);
        paymentTableScroll.setBorder(null);
        paymentTableScroll.getViewport().setBackground(theme.getPanelBackground());
        paymentTableScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        paymentTableScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));

        paymentTableWrap.add(paymentTableScroll, BorderLayout.CENTER);

        ModernButton btnRemovePayment = new ModernButton("Delete Payment", theme.getPanelBackground(), theme.getDanger(), theme.getDanger());
        btnRemovePayment.setHoverTextColor(Color.WHITE);
        btnRemovePayment.addActionListener(e -> removePayment(paymentTable));
        paymentTableWrap.add(btnRemovePayment, BorderLayout.SOUTH);

        // Results
        JPanel resultWrap = new JPanel(new BorderLayout());
        resultWrap.setOpaque(false);

        JPanel resultHeader = new JPanel(new BorderLayout());
        resultHeader.setOpaque(false);
        resultHeader.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel resultTitle = new JLabel("Settlement Plan");
        resultTitle.setFont(theme.getHeaderFont());
        resultTitle.setForeground(theme.getAccent());
        resultHeader.add(resultTitle, BorderLayout.WEST);

        totalStatsLabel = new JLabel("Total: ₹ 0.00");
        totalStatsLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        totalStatsLabel.setForeground(theme.getTextMain());
        resultHeader.add(totalStatsLabel, BorderLayout.EAST);

        resultWrap.add(resultHeader, BorderLayout.NORTH);

        settlementArea = new JTextArea();
        settlementArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        settlementArea.setEditable(false);
        settlementArea.setBackground(theme.getPanelBackground());
        settlementArea.setForeground(theme.getAccent());
        settlementArea.setMargin(new Insets(15, 15, 15, 15));
        settlementArea.setText("Add expenses and click Calculate.");
        settlementArea.setLineWrap(true);
        settlementArea.setWrapStyleWord(true);
        settlementArea.setOpaque(true);
        settlementArea.setVisible(true);

        JPanel settlementWrap = new JPanel(new BorderLayout());
        settlementWrap.setBackground(theme.getPanelBackground());
        settlementWrap.setBorder(new LineBorder(theme.getBorderColor(), 2));
        settlementWrap.setPreferredSize(new Dimension(0, 300));
        settlementWrap.setMinimumSize(new Dimension(0, 200));

        JScrollPane resScroll = new JScrollPane(settlementArea);
        resScroll.setBorder(null);
        resScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        resScroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));
        resScroll.getViewport().setBackground(theme.getPanelBackground());

        settlementWrap.add(resScroll, BorderLayout.CENTER);
        resultWrap.add(settlementWrap, BorderLayout.CENTER);

        // Create a vertical container for payments table and results
        // Title for Payments Table
JLabel paymentsLabel = new JLabel("Recorded Payments");
paymentsLabel.setFont(theme.getHeaderFont());
paymentsLabel.setForeground(theme.getAccent());
paymentTableWrap.add(paymentsLabel, BorderLayout.NORTH);

// Better split so payments are visible immediately
JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, paymentTableWrap, resultWrap);
centerSplit.setResizeWeight(0.45); // show more of payment panel
centerSplit.setDividerLocation(200); // ensure visible space
centerSplit.setDividerSize(6);

        centerSplit.setDividerSize(5);
        centerSplit.setBorder(null);
        centerSplit.setBackground(theme.getBackground());
        centerSplit.setOpaque(false);

        panel.add(centerSplit, BorderLayout.CENTER);

        // Actions Grid
        JPanel btnGrid = new JPanel(new GridLayout(1, 4, 15, 0));
        btnGrid.setOpaque(false);
        btnGrid.setPreferredSize(new Dimension(0, 45));

        ModernButton btnCalc = new ModernButton("Calculate", theme.getPrimary(), theme.getPrimaryHover(), Color.BLACK);
        btnCalc.addActionListener(e -> calculateSettlement());

        ModernButton btnLedger = new ModernButton("Details", theme.getAccent(), theme.getAccent().darker(), Color.BLACK);
        btnLedger.setHoverTextColor(Color.WHITE);
        btnLedger.addActionListener(e -> showDetailedLedger());

        ModernButton btnCopy = new ModernButton("Copy", new Color(37, 211, 102), new Color(20, 180, 80), Color.BLACK);
        btnCopy.addActionListener(e -> copyToClipboard(settlementArea.getText()));

        ModernButton btnReset = new ModernButton("Reset", theme.getDanger(), theme.getDangerHover(), Color.BLACK);
        btnReset.addActionListener(e -> resetData());

        btnGrid.add(btnCalc);
        btnGrid.add(btnLedger);
        btnGrid.add(btnReset);

        panel.add(btnGrid, BorderLayout.SOUTH);

        return panel;
    }

    // --- LOGIC ---

    private void addParticipant() {
        String name = participantField.getText().trim();
        if (name.isEmpty() || name.equals("Name...")) return;

        for (String p : participants) {
            if (p.equalsIgnoreCase(name)) {
                showCustomDialog("Duplicate", "Member already exists.");
                return;
            }
        }

        participants.add(name);
        payerDropdown.addItem(name);

        ModernToggle chip = new ModernToggle(name, theme.getSuccess(), theme.getPanelBackground());
        chip.setSelected(true);

        participantChips.add(chip);
        chipsPanel.add(chip);
        chipsPanel.revalidate();
        chipsPanel.repaint();

        updateParticipantsList();
        updatePaymentDropdowns(); // <-- keep payment dropdowns in sync

        participantField.setText("");
        participantField.requestFocus();
    }

    private void updateParticipantsList() {
        participantsListPanel.removeAll();

        for (String participant : participants) {
            JPanel participantItem = new JPanel(new BorderLayout(5, 0));
            participantItem.setOpaque(false);

            JLabel nameLabel = new JLabel(participant);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            nameLabel.setForeground(theme.getTextMain());
            participantItem.add(nameLabel, BorderLayout.CENTER);

            ModernButton deleteBtn = new ModernButton("×", theme.getDanger(), theme.getDangerHover(), Color.WHITE);
            deleteBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
            deleteBtn.setPreferredSize(new Dimension(30, 30));
            deleteBtn.setMargin(new Insets(0, 0, 0, 0));
            deleteBtn.addActionListener(e -> removeParticipant(participant));

            participantItem.add(deleteBtn, BorderLayout.EAST);

            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(theme.getBorderColor()),
                    new EmptyBorder(5, 10, 5, 5)
            ));
            wrapper.setBackground(theme.getBackground());
            wrapper.add(participantItem);

            participantsListPanel.add(wrapper);
        }

        participantsListPanel.revalidate();
        participantsListPanel.repaint();
    }

    private void removeParticipant(String name) {
        // Check if participant is involved in any expenses or payments
        boolean isInvolved = false;
        for (Expense e : expenses) {
            if (e.payer.equals(name) || e.involved.contains(name)) {
                isInvolved = true;
                break;
            }
        }
        if (!isInvolved) {
            for (Payment p : payments) {
                if (p.from.equals(name) || p.to.equals(name)) {
                    isInvolved = true;
                    break;
                }
            }
        }

        if (isInvolved) {
            showCustomDialog("Cannot Remove", "Cannot remove member. They are involved in existing expenses or payments. Remove those first.");
            return;
        }

        // Remove from participants list
        participants.remove(name);

        // Remove from dropdowns
        payerDropdown.removeItem(name);

        // Remove chip
        ModernToggle chipToRemove = null;
        for (ModernToggle chip : participantChips) {
            if (chip.getText().equals(name)) {
                chipToRemove = chip;
                break;
            }
        }
        if (chipToRemove != null) {
            participantChips.remove(chipToRemove);
            chipsPanel.remove(chipToRemove);
            chipsPanel.revalidate();
            chipsPanel.repaint();
        }

        // Update participants list display
        updateParticipantsList();
        updatePaymentDropdowns(); // <-- refresh From/To options
    }

    // Rebuild payment From/To dropdowns from participants list
    private void updatePaymentDropdowns() {
        if (paymentFromDropdown == null || paymentToDropdown == null) return;

        paymentFromDropdown.removeAllItems();
        for (String p : participants) {
            paymentFromDropdown.addItem(p);
        }
        refreshPaymentToDropdown();
    }

    // Ensure To does not show the same person as From
    private void refreshPaymentToDropdown() {
        if (paymentFromDropdown == null || paymentToDropdown == null) return;

        String from = (String) paymentFromDropdown.getSelectedItem();
        paymentToDropdown.removeAllItems();
        for (String p : participants) {
            if (from == null || !p.equals(from)) {
                paymentToDropdown.addItem(p);
            }
        }
    }

    private void toggleChips(boolean select) {
        for (ModernToggle chip : participantChips) chip.setSelected(select);
        chipsPanel.repaint();
    }

    private void addExpense() {
        if (participants.isEmpty()) {
            showCustomDialog("Empty", "Add members first.");
            return;
        }

        String payer = (String) payerDropdown.getSelectedItem();
        String desc = descField.getText().trim();
        String amtStr = amountField.getText().trim();
        String mode = btnModeOnline.isSelected() ? "Online" : "Cash";

        List<String> involved = new ArrayList<>();
        for (ModernToggle chip : participantChips) {
            if (chip.isSelected()) involved.add(chip.getText());
        }

        if (payer == null || amtStr.isEmpty()) {
            showCustomDialog("Missing Info", "Payer and Amount required.");
            return;
        }

        if (involved.isEmpty()) {
            showCustomDialog("Selection", "Select at least one person.");
            return;
        }

        try {
            double amount = Double.parseDouble(amtStr);
            if (desc == null || desc.trim().isEmpty()) desc = "Misc";

            expenses.add(new Expense(payer, amount, involved, mode, desc));

            String splitText = (involved.size() == participants.size()) ? "Everyone" : involved.size() + " Ppl";
            expenseTableModel.addRow(new Object[]{payer, desc, String.format("₹ %.2f", amount), mode, splitText});

            resizeColumnWidth(expenseTable);
            updateTotal();

            descField.setText("");
            amountField.setText("");
            toggleChips(true);

        } catch (NumberFormatException e) {
            showCustomDialog("Error", "Invalid Amount.");
        }
    }

    private void removeExpense(JTable table) {
        int r = table.getSelectedRow();
        if (r != -1) {
            expenses.remove(r);
            expenseTableModel.removeRow(r);
            updateTotal();
        }
    }

    private void addPayment() {
        if (participants.size() < 2) {
            showCustomDialog("Insufficient Members", "Need at least 2 members to record a payment.");
            return;
        }

        String from = (String) paymentFromDropdown.getSelectedItem();
        String to = (String) paymentToDropdown.getSelectedItem();
        String amtStr = paymentAmountField.getText().trim();

        if (from == null || to == null || from.equals(to)) {
            showCustomDialog("Invalid Selection", "Please select different members for 'From' and 'To'.");
            return;
        }

        if (amtStr.isEmpty()) {
            showCustomDialog("Missing Amount", "Please enter the payment amount.");
            return;
        }

        try {
            double amount = Double.parseDouble(amtStr);
            if (amount <= 0) {
                showCustomDialog("Invalid Amount", "Amount must be greater than 0.");
                return;
            }

            // Store payment
            payments.add(new Payment(from, to, amount));
            paymentTableModel.addRow(new Object[]{from, to, String.format("₹ %.2f", amount)});
            resizeColumnWidth(paymentTable);

            paymentAmountField.setText("");

        } catch (NumberFormatException e) {
            showCustomDialog("Error", "Invalid Amount.");
        }
    }

    private void removePayment(JTable table) {
    int r = table.getSelectedRow();
    if (r == -1) {
        showCustomDialog("Select Row", "Please select a payment to delete.");
        return;
    }
    // Always sync model + backend list
    payments.remove(r);
    paymentTableModel.removeRow(r);
    table.clearSelection();
}


    private void updateTotal() {
        double t = expenses.stream().mapToDouble(e -> e.amount).sum();
        totalStatsLabel.setText(String.format("Total: ₹ %.2f", t));
    }

    private void resizeColumnWidth(JTable table) {
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = 15;
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width + 10, width);
            }
            if (width > 400) width = 400;
            columnModel.getColumn(column).setPreferredWidth(width);
        }
    }

    // --- SUPER DETAILED LOGIC (THE WHY) ---
    private void showDetailedLedger() {
        if (expenses.isEmpty()) {
            showCustomDialog("No Data", "Add expenses first.");
            return;
        }

        Map<String, Double> paid = new HashMap<>();
        Map<String, Double> consumed = new HashMap<>();

        // Track Lists for Details
        Map<String, List<String>> itemsPaid = new HashMap<>();
        Map<String, List<String>> itemsShared = new HashMap<>();

        for (String p : participants) {
            paid.put(p, 0.0);
            consumed.put(p, 0.0);
            itemsPaid.put(p, new ArrayList<>());
            itemsShared.put(p, new ArrayList<>());
        }

        for (Expense e : expenses) {
            // Payer Info
            paid.put(e.payer, paid.get(e.payer) + e.amount);
            itemsPaid.get(e.payer).add(String.format("%s (₹%.0f, %s)", e.desc, e.amount, e.mode));

            // Consumer Info
            double share = e.amount / e.involved.size();
            for (String consumer : e.involved) {
                consumed.put(consumer, consumed.get(consumer) + share);
                itemsShared.get(consumer).add(String.format("%s (₹%.0f)", e.desc, share));
            }
        }

        // Columns showing Itemized Lists
        String[] columns = {"Member", "Paid", "Items Paid For", "Fair Share", "Items Shared In", "Net", "Reasoning"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        StringBuilder copyBuffer = new StringBuilder();
        copyBuffer.append("DETAILED BREAKDOWN\n\n");

        for (String p : participants) {
            double pVal = paid.get(p);
            double cVal = consumed.get(p);
            double net = pVal - cVal;

            String status;
            String reason;

            if (Math.abs(net) < 0.01) {
                status = "Settled";
                reason = "Paid exactly what they consumed.";
            } else if (net > 0) {
                status = String.format("Gets ₹%.0f", net);
                reason = String.format("Spent ₹%.0f. Used ₹%.0f. Gets back ₹%.0f.", pVal, cVal, net);
            } else {
                status = String.format("Owes ₹%.0f", Math.abs(net));
                reason = String.format("Used ₹%.0f. Spent ₹%.0f. Owes ₹%.0f.", cVal, pVal, Math.abs(net));
            }

            // Format Lists
            String listPaid = String.join(", ", itemsPaid.get(p));
            if (listPaid.isEmpty()) listPaid = "-";

            String listShared = String.join(", ", itemsShared.get(p));
            if (listShared.isEmpty()) listShared = "-";

            model.addRow(new Object[]{
                    p,
                    String.format("₹%.0f", pVal),
                    listPaid,
                    String.format("₹%.0f", cVal),
                    listShared,
                    status,
                    reason
            });
            copyBuffer.append(String.format("%s: %s | %s\n  > Paid For: %s\n  > Shared In: %s\n\n", p, status, reason, listPaid, listShared));
        }

        JDialog dialog = new JDialog(this, "Details", true);
        dialog.setSize(1200, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel dPanel = new JPanel(new BorderLayout());
        dPanel.setBackground(theme.getBackground());
        dPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Analysis");
        title.setFont(theme.getTitleFont());
        title.setForeground(theme.getTextMain());
        dPanel.add(title, BorderLayout.NORTH);

        JTable ledgerTable = new JTable(model);
        theme.styleTable(ledgerTable);
        resizeColumnWidth(ledgerTable);

        JScrollPane scroll = new JScrollPane(ledgerTable);
        scroll.setBorder(new LineBorder(theme.getBorderColor()));
        scroll.getViewport().setBackground(theme.getPanelBackground());
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        scroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));
        dPanel.add(scroll, BorderLayout.CENTER);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnP.setOpaque(false);

        ModernButton copyBtn = new ModernButton("Copy Analysis", theme.getAccent(), theme.getAccent().darker(), Color.BLACK);
        copyBtn.addActionListener(e -> copyToClipboard(copyBuffer.toString()));

        ModernButton closeBtn = new ModernButton("Close", theme.getPanelBackground(), theme.getBorderColor(), theme.getTextMain());
        closeBtn.addActionListener(e -> dialog.dispose());

        btnP.add(copyBtn);
        btnP.add(closeBtn);
        dPanel.add(btnP, BorderLayout.SOUTH);

        dialog.add(dPanel);
        dialog.setVisible(true);
    }

    private void calculateSettlement() {

    if(participants.isEmpty()) {
        showCustomDialog("Error", "Add members first.");
        return;
    }
    if(expenses.isEmpty() && payments.isEmpty()) {
        showCustomDialog("Error", "Add expenses or payments first.");
        return;
    }

    Map<String, Double> balances = new HashMap<>();
    for(String p : participants) balances.put(p, 0.0);

    // Expense logic
    for(Expense e : expenses) {
        balances.put(e.payer, balances.get(e.payer) + e.amount);
        double share = e.amount / e.involved.size();
        for(String c : e.involved) {
            balances.put(c, balances.get(c) - share);
        }
    }

    // Payment logic (A paid B → A gets credited)
    for(Payment p : payments) {
        balances.put(p.from, balances.get(p.from) + p.amount);
        balances.put(p.to, balances.get(p.to) - p.amount);
    }

    List<Balance> debtors = new ArrayList<>();
    List<Balance> creditors = new ArrayList<>();

    for(Map.Entry<String, Double> e : balances.entrySet()) {
        double v = e.getValue();
        if(Math.abs(v) < 0.01) continue;
        if(v < 0) debtors.add(new Balance(e.getKey(), Math.abs(v)));
        else creditors.add(new Balance(e.getKey(), v));
    }

    debtors.sort((a,b)->Double.compare(b.amount,a.amount));
    creditors.sort((a,b)->Double.compare(b.amount,a.amount));

    double totalSpend = expenses.stream().mapToDouble(e -> e.amount).sum();
    double equalShare = participants.isEmpty() ? 0 : totalSpend / participants.size();

    String trip = tripNameField.getText().trim();
    if(trip.isEmpty()) trip = "TRIP";
    String tripName = trip.toUpperCase();

    StringBuilder sb = new StringBuilder();
    String line = "=====================================\n";

    sb.append(line);
    sb.append(String.format("             %s\n", tripName));
    sb.append(line);
    sb.append(String.format("Total Spent: ₹%.2f\n", totalSpend));
    sb.append(String.format("People: %d\n", participants.size()));
    sb.append(String.format("Per Person: ₹%.2f\n", equalShare));
    sb.append(line);

    if(debtors.isEmpty()) {
        sb.append("\n🎉 Everyone is already settled!\n");
    } else {
        sb.append("\nWho Pays Whom:\n\n");

        int i=0, j=0;
        while(i < debtors.size() && j < creditors.size()) {
            Balance d = debtors.get(i);
            Balance c = creditors.get(j);

            double amt = Math.min(d.amount, c.amount);

            // Option 1 wording ✔️
            sb.append(String.format("• %s pays %s ₹%.2f\n", d.name, c.name, amt));

            d.amount -= amt;
            c.amount -= amt;

            if(d.amount < 0.01) i++;
            if(c.amount < 0.01) j++;
        }
    }

    sb.append("\n");
    sb.append(line);

    // Receipt popup
    JDialog dialog = new JDialog(this, "Settlement Plan", true);
    dialog.setSize(520, 520);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout());

    JTextArea area = new JTextArea(sb.toString());
    area.setEditable(false);
    area.setBackground(theme.getPanelBackground());
    area.setForeground(theme.getAccent());
    area.setFont(new Font("Consolas", Font.PLAIN, 15));
    area.setMargin(new Insets(20, 20, 20, 20));

    JScrollPane scroll = new JScrollPane(area);
    scroll.setBorder(new LineBorder(theme.getBorderColor(), 2));
    scroll.getViewport().setBackground(theme.getPanelBackground());
    dialog.add(scroll, BorderLayout.CENTER);

    JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    btns.setOpaque(false);

    ModernButton copy = new ModernButton("Copy", new Color(37,211,102), new Color(20,180,80), Color.BLACK);
    copy.addActionListener(e -> copyToClipboard(sb.toString()));

    ModernButton close = new ModernButton("Close", theme.getPanelBackground(), theme.getBorderColor(), theme.getTextMain());
    close.addActionListener(e -> dialog.dispose());

    btns.add(copy);
    btns.add(close);
    dialog.add(btns, BorderLayout.SOUTH);

    dialog.getContentPane().setBackground(theme.getBackground());
    dialog.setVisible(true);
}



    private void copyToClipboard(String text) {
        StringSelection s = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
        showCustomDialog("Success", "Copied to clipboard!");
    }

    private void resetData() {
        participants.clear();
        expenses.clear();
        payments.clear();
        payerDropdown.removeAllItems();
        if (paymentFromDropdown != null) paymentFromDropdown.removeAllItems();
        if (paymentToDropdown != null) paymentToDropdown.removeAllItems();
        participantChips.clear();
        chipsPanel.removeAll();
        chipsPanel.repaint();
        expenseTableModel.setRowCount(0);
        if (paymentTableModel != null) paymentTableModel.setRowCount(0);
        settlementArea.setText("");
        totalStatsLabel.setText("Total: ₹ 0.00");
        updateParticipantsList();
    }

    private void showCustomDialog(String title, String message) {
        JDialog d = new JDialog(this, title, true);
        d.setSize(350, 180);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(theme.getBackground());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel l = new JLabel("<html><center>" + message + "</center></html>", SwingConstants.CENTER);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        l.setForeground(theme.getTextMain());
        p.add(l, BorderLayout.CENTER);

        ModernButton b = new ModernButton("OK", theme.getPanelBackground(), theme.getBorderColor(), theme.getTextMain());
        b.addActionListener(e -> d.dispose());

        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bp.setOpaque(false);
        bp.add(b);
        p.add(bp, BorderLayout.SOUTH);

        d.add(p);
        d.setVisible(true);
    }

    // --- STYLES ---

    private void styleComboBox(JComboBox<String> b) {
        b.setBackground(theme.getPanelBackground());
        b.setForeground(theme.getTextMain());
        b.setBorder(new LineBorder(theme.getBorderColor()));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 16));
    }

    private JButton createTextButton(String text) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setForeground(theme.getAccent());
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return b;
    }
}