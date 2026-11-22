package atm;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

interface Authenticator {
    boolean verifyPin(Card card, String enteredPin);
}

class PinAuthenticator implements Authenticator {
    @Override
    public boolean verifyPin(Card card, String enteredPin) {
        if (card != null && enteredPin != null) {
            return enteredPin.equals(card.getPin());
        }
        return false;
    }
}

class InvalidPinException extends Exception {
    public InvalidPinException(String message) {
        super(message);
    }
}

class DailyLimitExceededException extends Exception {
    public DailyLimitExceededException(String message) {
        super(message);
    }
}

class Account {
    private String accountNumber;
    private double balance;
    private double dailyLimit;
    private double withdrawnToday;

    public Account(String accountNumber, double balance, double dailyLimit) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.dailyLimit = dailyLimit;
        this.withdrawnToday = 0.0;
    }

    public synchronized void withdraw(double amount) throws DailyLimitExceededException, Exception {
        if (withdrawnToday + amount > dailyLimit) {
            throw new DailyLimitExceededException("Daily limit exceeded! Daily Limit: Rs." + dailyLimit 
                + ", Withdrawn Today: Rs." + withdrawnToday + ", Attempted: Rs." + amount);
        }
        if (amount > balance) {
            throw new Exception("Insufficient account balance! Available: Rs." + balance);
        }
        balance -= amount;
        withdrawnToday += amount;
    }

    public synchronized void deposit(double amount) {
        balance += amount;
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized double getDailyLimit() {
        return dailyLimit;
    }

    public synchronized double getWithdrawnToday() {
        return withdrawnToday;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}

class Card {
    private String cardNumber;
    private String pin;
    private Account account;

    public Card(String cardNumber, String pin, Account account) {
        this.cardNumber = cardNumber;
        this.pin = pin;
        this.account = account;
    }

    public Card(String cardNumber, String pin) {
        this(cardNumber, pin, new Account(cardNumber, 0.0, 0.0));
    }

    public Card() {
        this("", "", null);
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getPin() {
        return pin;
    }

    public Account getAccount() {
        return account;
    }
}

abstract class ATMOperation {
    protected Card card;
    protected String enteredPin;
    protected Authenticator authenticator;

    public ATMOperation(Card card, String enteredPin, Authenticator authenticator) {
        this.card = card;
        this.enteredPin = enteredPin;
        this.authenticator = authenticator;
    }

    public abstract String execute() throws InvalidPinException, DailyLimitExceededException, Exception;
}

class Withdrawal extends ATMOperation {
    private double amount;
    private CashVault vault;

    public Withdrawal(Card card, String enteredPin, Authenticator authenticator, double amount, CashVault vault) {
        super(card, enteredPin, authenticator);
        this.amount = amount;
        this.vault = vault;
    }

    @Override
    public String execute() throws InvalidPinException, DailyLimitExceededException, Exception {
        if (!authenticator.verifyPin(card, enteredPin)) {
            throw new InvalidPinException("Invalid PIN entered for Card: " + card.getCardNumber());
        }
        card.getAccount().withdraw(amount);
        vault.dispense(amount);
        return "Withdrawal of Rs." + amount + " successful!\nRemaining Account Balance: Rs." + card.getAccount().getBalance();
    }
}

class BalanceEnquiry extends ATMOperation {
    public BalanceEnquiry(Card card, String enteredPin, Authenticator authenticator) {
        super(card, enteredPin, authenticator);
    }

    @Override
    public String execute() throws InvalidPinException {
        if (!authenticator.verifyPin(card, enteredPin)) {
            throw new InvalidPinException("Invalid PIN entered for Card: " + card.getCardNumber());
        }
        Account acc = card.getAccount();
        double remainingLimit = acc.getDailyLimit() - acc.getWithdrawnToday();
        return "Current Balance: Rs." + acc.getBalance() + "\nDaily Limit Left Today: Rs." + remainingLimit;
    }
}

class Deposit extends ATMOperation {
    private double amount;
    private CashVault vault;

    public Deposit(Card card, String enteredPin, Authenticator authenticator, double amount, CashVault vault) {
        super(card, enteredPin, authenticator);
        this.amount = amount;
        this.vault = vault;
    }

    @Override
    public String execute() throws InvalidPinException, Exception {
        if (!authenticator.verifyPin(card, enteredPin)) {
            throw new InvalidPinException("Invalid PIN entered for Card: " + card.getCardNumber());
        }
        card.getAccount().deposit(amount);
        vault.deposit(amount);
        return "Deposit of Rs." + amount + " successful!\nUpdated Account Balance: Rs." + card.getAccount().getBalance();
    }
}

class CashVault {
    private double balance;

    public CashVault(double initialCash) {
        this.balance = initialCash;
    }

    public synchronized void dispense(double amount) throws Exception {
        if (amount > balance) {
            throw new Exception("Cash vault depleted! Machine has only Rs." + balance + " available.");
        }
        balance -= amount;
    }

    public synchronized void deposit(double amount) {
        balance += amount;
    }

    public synchronized double getBalance() {
        return balance;
    }
}

class ATM extends Thread {
    private String atmId;
    private CashVault vault;
    private ATMOperation operation;
    private String resultMessage;
    private Exception executionException;

    public ATM(String atmId, CashVault vault, ATMOperation operation) {
        super(atmId);
        this.atmId = atmId;
        this.vault = vault;
        this.operation = operation;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(300);
            resultMessage = operation.execute();
        } catch (Exception ex) {
            executionException = ex;
        }
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Exception getExecutionException() {
        return executionException;
    }

    public String getAtmId() {
        return atmId;
    }
}

public class ATMSystem extends JFrame {

    private CardLayout cardLayout;
    private JPanel containerPanel;

    private CashVault vault;
    private Authenticator authenticator;

    private Card[] storedCards;
    private int storedCardCount;

    private Card currentCard;
    private String currentAtmId;

    private JTextField regCardNumField;
    private JPasswordField regPinField;
    private JTextField regBalanceField;
    private JTextField regLimitField;

    private JTextField loginCardNumField;
    private JPasswordField loginPinField;

    private JLabel dashWelcomeLabel;
    private JTextField depositAmountField;
    private JTextField withdrawAmountField;

    public ATMSystem() {
        super("Bank ATM System");
        vault = new CashVault(50000.0);
        authenticator = new PinAuthenticator();
        storedCards = new Card[100];
        storedCardCount = 0;

        initUI();
    }

    private void storeCard(Card card) {
        if (storedCardCount < storedCards.length) {
            storedCards[storedCardCount] = card;
            storedCardCount++;
        }
    }

    private Card findCard(String cardNum) {
        try {
            int cardNumInt = Integer.parseInt(cardNum.trim());
            int index = cardNumInt - 1;
            if (index >= 0 && index < storedCardCount) {
                return storedCards[index];
            }
        } catch (NumberFormatException e) {
        }
        return null;
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 560);
        setLocationRelativeTo(null);
        setResizable(false);

        cardLayout = new CardLayout();
        containerPanel = new JPanel(cardLayout);

        containerPanel.add(createHomeScreen(), "HOME");
        containerPanel.add(createRegisterScreen(), "REGISTER");
        containerPanel.add(createLoginScreen(), "LOGIN");
        containerPanel.add(createDashboardScreen(), "DASHBOARD");

        add(containerPanel);
    }

    private JPanel createHomeScreen() {
        JPanel panel = new JPanel(new BorderLayout(10, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        panel.setBackground(new Color(245, 247, 250));

        JPanel header = new JPanel(new GridLayout(2, 1, 5, 5));
        header.setOpaque(false);
        JLabel title = new JLabel("Welcome to the Bank ATM", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(20, 40, 80));

        JLabel subtitle = new JLabel("Please select an option below", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(Color.GRAY);
        header.add(title);
        header.add(subtitle);
        panel.add(header, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 15, 15));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton btnGoRegister = new JButton("1. Create New Account / Card");
        btnGoRegister.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnGoRegister.setBackground(new Color(35, 120, 75));
        btnGoRegister.setForeground(Color.WHITE);
        btnGoRegister.setFocusPainted(false);
        btnGoRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                regCardNumField.setText(String.valueOf(storedCardCount + 1));
                cardLayout.show(containerPanel, "REGISTER");
            }
        });

        JButton btnGoLogin = new JButton("2. Login to ATM");
        btnGoLogin.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnGoLogin.setBackground(new Color(30, 90, 160));
        btnGoLogin.setForeground(Color.WHITE);
        btnGoLogin.setFocusPainted(false);
        btnGoLogin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(containerPanel, "LOGIN");
            }
        });

        btnPanel.add(btnGoRegister);
        btnPanel.add(btnGoLogin);
        panel.add(btnPanel, BorderLayout.CENTER);

        JLabel footer = new JLabel("Cash Vault", SwingConstants.CENTER);
        footer.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        footer.setForeground(Color.GRAY);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createRegisterScreen() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        panel.setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel("Create Account & Card", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(new Color(20, 40, 80));
        panel.add(title, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        centerWrapper.add(new JLabel("Card Number:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        regCardNumField = new JTextField(String.valueOf(storedCardCount + 1), 15);
        regCardNumField.setPreferredSize(new Dimension(180, 30));
        regCardNumField.setEditable(false);
        regCardNumField.setBackground(new Color(235, 235, 235));
        centerWrapper.add(regCardNumField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        centerWrapper.add(new JLabel("4-Digit PIN:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        regPinField = new JPasswordField(15);
        regPinField.setPreferredSize(new Dimension(180, 30));
        centerWrapper.add(regPinField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        centerWrapper.add(new JLabel("Initial Deposit (Rs.):"), gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        regBalanceField = new JTextField(15);
        regBalanceField.setPreferredSize(new Dimension(180, 30));
        centerWrapper.add(regBalanceField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        centerWrapper.add(new JLabel("Daily Limit (Rs.):"), gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        regLimitField = new JTextField(15);
        regLimitField.setPreferredSize(new Dimension(180, 30));
        centerWrapper.add(regLimitField, gbc);

        panel.add(centerWrapper, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setOpaque(false);

        JButton btnSubmit = new JButton("Create Account");
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSubmit.setBackground(new Color(35, 120, 75));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleRegister();
            }
        });

        JButton btnBack = new JButton("Back");
        btnBack.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(containerPanel, "HOME");
            }
        });

        btnPanel.add(btnSubmit);
        btnPanel.add(btnBack);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLoginScreen() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));
        panel.setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel("ATM Login", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(new Color(20, 40, 80));
        panel.add(title, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 8, 10, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        centerWrapper.add(new JLabel("Card Number:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        loginCardNumField = new JTextField(15);
        loginCardNumField.setPreferredSize(new Dimension(180, 30));
        centerWrapper.add(loginCardNumField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        centerWrapper.add(new JLabel("Enter 4-Digit PIN:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        loginPinField = new JPasswordField(15);
        loginPinField.setPreferredSize(new Dimension(180, 30));
        centerWrapper.add(loginPinField, gbc);

        panel.add(centerWrapper, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setOpaque(false);

        JButton btnLogin = new JButton("Login");
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setBackground(new Color(30, 90, 160));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });

        JButton btnBack = new JButton("Back");
        btnBack.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(containerPanel, "HOME");
            }
        });

        btnPanel.add(btnLogin);
        btnPanel.add(btnBack);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createDashboardScreen() {
        JPanel panel = new JPanel(new BorderLayout(10, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        panel.setBackground(new Color(245, 247, 250));

        dashWelcomeLabel = new JLabel("Welcome!", SwingConstants.CENTER);
        dashWelcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        dashWelcomeLabel.setForeground(new Color(20, 40, 80));
        dashWelcomeLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        panel.add(dashWelcomeLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        centerPanel.setOpaque(false);

        JPanel balancePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        balancePanel.setBorder(BorderFactory.createTitledBorder("Check Account Balance"));
        JButton btnCheckBalance = new JButton("Check Balance");
        btnCheckBalance.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnCheckBalance.setBackground(new Color(40, 90, 160));
        btnCheckBalance.setForeground(Color.WHITE);
        btnCheckBalance.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleCheckBalance();
            }
        });
        balancePanel.add(btnCheckBalance);
        centerPanel.add(balancePanel);

        JPanel depositPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        depositPanel.setBorder(BorderFactory.createTitledBorder("Cash Deposit"));
        depositPanel.add(new JLabel("Amount (Rs.):"));
        depositAmountField = new JTextField(8);
        depositPanel.add(depositAmountField);

        JButton btnDeposit = new JButton("Deposit Cash");
        btnDeposit.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDeposit.setBackground(new Color(30, 120, 75));
        btnDeposit.setForeground(Color.WHITE);
        btnDeposit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleDeposit();
            }
        });
        depositPanel.add(btnDeposit);
        centerPanel.add(depositPanel);

        JPanel withdrawPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        withdrawPanel.setBorder(BorderFactory.createTitledBorder("Cash Withdrawal"));
        withdrawPanel.add(new JLabel("Amount (Rs.):"));
        withdrawAmountField = new JTextField(8);
        withdrawPanel.add(withdrawAmountField);

        JButton btnWithdraw = new JButton("Withdraw Cash");
        btnWithdraw.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnWithdraw.setBackground(new Color(35, 120, 75));
        btnWithdraw.setForeground(Color.WHITE);
        btnWithdraw.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleWithdraw();
            }
        });
        withdrawPanel.add(btnWithdraw);
        centerPanel.add(withdrawPanel);

        panel.add(centerPanel, BorderLayout.CENTER);

        JButton btnLogout = new JButton("End Session");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnLogout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentCard = null;
                currentAtmId = null;
                loginPinField.setText("");
                cardLayout.show(containerPanel, "HOME");
            }
        });
        panel.add(btnLogout, BorderLayout.SOUTH);

        return panel;
    }

    private boolean isValidPin(String pin) {
        if (pin == null || pin.length() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(pin.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void handleRegister() {
        String cardNum = String.valueOf(storedCardCount + 1);
        String pin = new String(regPinField.getPassword()).trim();
        String balStr = regBalanceField.getText().trim();
        String limitStr = regLimitField.getText().trim();

        if (pin.isEmpty() || balStr.isEmpty() || limitStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields (PIN, Initial Deposit, and Daily Limit).", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!isValidPin(pin)) {
            JOptionPane.showMessageDialog(this, "PIN must be exactly 4 digits (e.g., 1234).", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            double bal = Double.parseDouble(balStr);
            double limit = Double.parseDouble(limitStr);

            if (bal < 0) {
                JOptionPane.showMessageDialog(this, "Initial deposit cannot be negative.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (limit <= 0) {
                JOptionPane.showMessageDialog(this, "Daily limit must be greater than 0.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Account acc = new Account(cardNum, bal, limit);
            Card card = new Card(cardNum, pin, acc);

            storeCard(card);
            JOptionPane.showMessageDialog(this, "Card and Account created successfully!\nYour Card Number is: " + cardNum 
                + "\nPlease use this Card Number to login.", "Account Created", JOptionPane.INFORMATION_MESSAGE);
            regPinField.setText("");
            regBalanceField.setText("");
            regLimitField.setText("");
            cardLayout.show(containerPanel, "HOME");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number entered for Balance or Limit.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleLogin() {
        String cardNum = loginCardNumField.getText().trim();
        String enteredPin = new String(loginPinField.getPassword()).trim();
        currentAtmId = "ATM";

        if (cardNum.isEmpty() || enteredPin.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter Card Number and PIN.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!isValidPin(enteredPin)) {
            JOptionPane.showMessageDialog(this, "PIN must be exactly 4 digits.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Card card = findCard(cardNum);
        if (card == null) {
            JOptionPane.showMessageDialog(this, "Card number " + cardNum + " not found!", "Login Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            if (!authenticator.verifyPin(card, enteredPin)) {
                throw new InvalidPinException("Invalid PIN entered for Card " + cardNum + " - Access Blocked!");
            }

            currentCard = card;
            dashWelcomeLabel.setText("Active Card: " + currentCard.getCardNumber());
            cardLayout.show(containerPanel, "DASHBOARD");
        } catch (InvalidPinException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "InvalidPinException", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String promptPin() {
        JPasswordField pinField = new JPasswordField();
        Object[] message = {
            "Enter 4-Digit PIN:", pinField
        };
        int option = JOptionPane.showConfirmDialog(this, message, "PIN Required", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            String entered = new String(pinField.getPassword()).trim();
            if (!isValidPin(entered)) {
                JOptionPane.showMessageDialog(this, "PIN must be exactly 4 digits.", "Invalid PIN", JOptionPane.ERROR_MESSAGE);
                return "";
            }
            return entered;
        }
        return null;
    }

    private void handleCheckBalance() {
        if (currentCard == null) {
            return;
        }

        String pin = promptPin();
        if (pin == null) {
            return;
        }

        BalanceEnquiry op = new BalanceEnquiry(currentCard, pin, authenticator);
        ATM atmThread = new ATM(currentAtmId, vault, op);

        try {
            atmThread.start();
            atmThread.join();

            Exception ex = atmThread.getExecutionException();
            if (ex != null) {
                if (ex instanceof InvalidPinException) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "InvalidPinException", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Operation Blocked", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, atmThread.getResultMessage(), "Balance Enquiry Receipt", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void handleDeposit() {
        if (currentCard == null) {
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(depositAmountField.getText().trim());
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "Please enter a valid deposit amount greater than 0.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid deposit amount entered.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String pin = promptPin();
        if (pin == null) {
            return;
        }

        Deposit op = new Deposit(currentCard, pin, authenticator, amount, vault);
        ATM atmThread = new ATM(currentAtmId, vault, op);

        try {
            atmThread.start();
            atmThread.join();

            Exception ex = atmThread.getExecutionException();
            if (ex != null) {
                if (ex instanceof InvalidPinException) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "InvalidPinException", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Transaction Blocked", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                depositAmountField.setText("");
                JOptionPane.showMessageDialog(this, atmThread.getResultMessage(), "Deposit Receipt", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleWithdraw() {
        if (currentCard == null) {
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(withdrawAmountField.getText().trim());
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "Please enter a valid amount greater than 0.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount entered.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String pin = promptPin();
        if (pin == null) {
            return;
        }

        Withdrawal op = new Withdrawal(currentCard, pin, authenticator, amount, vault);
        ATM atmThread = new ATM(currentAtmId, vault, op);

        try {
            atmThread.start();
            atmThread.join();

            Exception ex = atmThread.getExecutionException();
            if (ex != null) {
                if (ex instanceof DailyLimitExceededException) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "DailyLimitExceededException", JOptionPane.ERROR_MESSAGE);
                } else if (ex instanceof InvalidPinException) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "InvalidPinException", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Transaction Blocked", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                withdrawAmountField.setText("");
                JOptionPane.showMessageDialog(this, atmThread.getResultMessage(), "Withdrawal Receipt", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ATMSystem frame = new ATMSystem();
                frame.setVisible(true);
            }
        });
    }
}
