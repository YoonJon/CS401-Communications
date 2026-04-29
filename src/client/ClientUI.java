package client;

import shared.enums.*;
import shared.networking.User.UserInfo;
import shared.payload.*;

import javax.swing.*;
import javax.swing.event.*;

import java.awt.*;
import java.awt.event.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class ClientUI {

    /** Last time a global key/mouse AWT event was observed (UI "user active"). */
    private volatile long lastUserActivityMillis = System.currentTimeMillis();

    private final ClientController controller;
    private JFrame frame;
    private ScreenCards cards;
    private DefaultListModel<UserInfo> directoryModel;
    private DefaultListModel<Message> conversationMessageModel;
    private DefaultListModel<Conversation> conversationListModel;
    /** Fires on the EDT every 5s to compare wall clock to {@link #lastUserActivityMillis}. */
    private final Timer userIdlePollTimer;

    private static final int USER_IDLE_POLL_MS = 5_000;
    /** Optional: end session after this much in-app UI idle time (0 disables). Default 30 minutes. */
    private static final long USER_IDLE_LOGOUT_AFTER_MS = 30L * 60L * 1000L;

    private int unreadWhileAway = 0;
    private volatile boolean suppressActivationReset = false;
    private static final String BASE_TITLE = "Communication Application";

    public ClientUI(ClientController controller) {
        this.controller = controller;

        // Fix 3: wrap ALL frame setup in invokeLater so it runs on the EDT.
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame();
            frame.setTitle(BASE_TITLE);
            frame.setIconImage(createAppIcon());
            // Fix 2a: enforce minimum window size
            frame.setMinimumSize(new java.awt.Dimension(900, 600));
            cards = new ScreenCards();
            directoryModel = cards.main.directoryView.getListModel();
            conversationMessageModel = cards.main.conversationView.getListModel();
            conversationListModel = cards.main.conversationListView.getListModel();
            frame.add(cards);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowActivated(WindowEvent e) {
                    if (suppressActivationReset) {
                        suppressActivationReset = false;
                        return;
                    }
                    unreadWhileAway = 0;
                    frame.setTitle(BASE_TITLE);
                }
            });

            JRootPane rp = frame.getRootPane();
            InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = rp.getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), "shortcutLogout");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "shortcutNewConv");
            am.put("shortcutLogout", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (controller.isLoggedIn()) controller.logout();
                }
            });
            am.put("shortcutNewConv", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (controller.isLoggedIn()
                            && cards.main.directoryView.createConversationButton.isEnabled()) {
                        cards.main.directoryView.createConversationButton.doClick();
                    }
                }
            });

            Toolkit.getDefaultToolkit().addAWTEventListener(
                e -> {
                    lastUserActivityMillis = System.currentTimeMillis();
                },
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
            );
        });

        userIdlePollTimer = new Timer(USER_IDLE_POLL_MS, e -> onUserIdlePollTick());
        userIdlePollTimer.setRepeats(true);
        userIdlePollTimer.start();
    }

    /** Runs on EDT each timer tick: elapsed time since last AWT user input. */
    private void onUserIdlePollTick() {
        long idleMs = System.currentTimeMillis() - lastUserActivityMillis;
        if (USER_IDLE_LOGOUT_AFTER_MS > 0
                && controller.isLoggedIn()
                && idleMs >= USER_IDLE_LOGOUT_AFTER_MS) {
            controller.logout();
        }
    }

    public void showLoginView() {
        SwingUtilities.invokeLater(() -> {
            // Fix 5: dispose any open dialogs before switching to login
            DirectoryView dv = cards.main.directoryView;
            ConversationView cv = cards.main.conversationView;
            if (dv.createDialog != null && dv.createDialog.isVisible()) dv.createDialog.dispose();
            if (dv.adminDialog != null && dv.adminDialog.isVisible()) dv.adminDialog.dispose();
            if (cv.addDialog != null && cv.addDialog.isVisible()) cv.addDialog.dispose();

            clearLoginAndRegisterFields();
            cards.main.directoryView.adminButton.setVisible(false);
            cards.main.directoryView.revalidate();
            cards.main.directoryView.repaint();
            cards.layout.show(cards, "login");
        });
    }
    public void showRegisterView() {
        SwingUtilities.invokeLater(() -> {
            clearLoginAndRegisterFields();
            cards.layout.show(cards, "register");
        });
    }

    // currentUser is set by the time this is called
    public void showMainView() {
        SwingUtilities.invokeLater(() -> {
            clearLoginAndRegisterFields();
            UserInfo currentUser = controller.getCurrentUserInfo();
            boolean isAdmin = currentUser != null && currentUser.getUserType() == UserType.ADMIN;
            cards.main.directoryView.adminButton.setVisible(isAdmin);
            // Refresh directory list from the latest controller-side cache on main-view entry.
            // Fix 6: exclude the logged-in user from the directory picker list
            String myId = (currentUser != null) ? currentUser.getUserId() : null;
            directoryModel.clear();
            for (UserInfo userInfo : controller.getFilteredDirectory("")) {
                if (myId == null || !userInfo.getUserId().equals(myId)) {
                    directoryModel.addElement(userInfo);
                }
            }
            // Populate conversation list from login result.
            conversationListModel.clear();
            for (Conversation c : controller.getFilteredConversationList("")) {
                conversationListModel.addElement(c);
            }
            cards.main.directoryView.revalidate();
            cards.main.directoryView.repaint();
            if (currentUser != null) {
                cards.main.directoryView.profileUserIdLabel.setText(currentUser.getUserId());
                cards.main.directoryView.profileNameLabel.setText(currentUser.getName());
            }
            cards.layout.show(cards, "main");
            // Fix 2b: repack and re-center after switching to main card
            frame.pack();
            frame.setLocationRelativeTo(null);
        });
    }

    private void clearLoginAndRegisterFields() {
        cards.login.login_idField.setText("");
        cards.login.passwordField.setText("");
        cards.register.userId.setText("");
        cards.register.name.setText("");
        cards.register.loginName.setText("");
        cards.register.password.setText("");
        cards.register.passwordAgain.setText("");
    }

    public void showRegisterError(RegisterStatus registerStatus) {
        switch (registerStatus) {
            case USER_ID_TAKEN:
                JOptionPane.showMessageDialog(frame, "User ID is already taken. Please try again.");
                break;
            case USER_ID_INVALID:
                JOptionPane.showMessageDialog(frame, "User ID is invalid. Please try again.");
                break;
            case LOGIN_NAME_TAKEN:
                JOptionPane.showMessageDialog(frame, "Login name is already taken. Please try again.");
                break;
            case LOGIN_NAME_INVALID:
                JOptionPane.showMessageDialog(frame, "Login name is invalid. Use only letters, numbers, hyphens, or underscores.");
                break;
        }
    }

    public void showLoginError(LoginStatus loginStatus) {
    	switch (loginStatus) {
            case INVALID_CREDENTIALS:
                JOptionPane.showMessageDialog(frame, "Invalid credentials. Please try again.");
                break;
            case NO_ACCOUNT_EXISTS:
                JOptionPane.showMessageDialog(frame, "No account exists. Please create an account.");
                break;
            case DUPLICATE_SESSION:
                JOptionPane.showMessageDialog(frame, "Duplicate session. Please log in again.");
                break;
        }
    }

    public boolean isSelectingUsers() {
        return cards.main.directoryView.isCreatingConversation() || cards.main.conversationView.isAddingUser();
    }
    public boolean isAdminSearchingConversation() { return cards.main.directoryView.isAdminSearching(); }

    public void updateDirectoryModel(ArrayList<UserInfo> users) {
        SwingUtilities.invokeLater(() -> {
            directoryModel.clear();
            for (UserInfo user : users) {
                directoryModel.addElement(user);
            }
        });
    }

    public void updateConversationListModel(ArrayList<Conversation> conversations) {
        SwingUtilities.invokeLater(() -> {
            // Fix 7: preserve selection across model rebuild
            JList<Conversation> convList = cards.main.conversationListView.list;
            Conversation selected = convList.getSelectedValue();
            Long selectedId = (selected != null) ? selected.getConversationId() : null;

            // Fix 1: sort by last message timestamp descending (most recent first)
            conversations.sort((a, b) -> {
                ArrayList<Message> aMsgs = a.getMessages();
                ArrayList<Message> bMsgs = b.getMessages();
                long aTime = aMsgs.isEmpty() ? a.getConversationId() : aMsgs.get(aMsgs.size() - 1).getTimestamp().getTime();
                long bTime = bMsgs.isEmpty() ? b.getConversationId() : bMsgs.get(bMsgs.size() - 1).getTimestamp().getTime();
                return Long.compare(bTime, aTime);
            });

            conversationListModel.clear();
            for (Conversation conversation : conversations) {
                conversationListModel.addElement(conversation);
            }

            // Restore selection after rebuild
            if (selectedId != null) {
                for (int i = 0; i < conversationListModel.getSize(); i++) {
                    if (conversationListModel.getElementAt(i).getConversationId() == selectedId) {
                        convList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });
    }

    public void updateMessageListModel(Conversation conversation) {
        SwingUtilities.invokeLater(() -> {
            conversationMessageModel.clear();
            if (conversation == null) {
                return;
            }
            for (Message message : conversation.getMessages()) {
                conversationMessageModel.addElement(message);
            }
        });
    }

    public void appendMessageToConversationView(Message message) {
        SwingUtilities.invokeLater(() -> {
            conversationMessageModel.addElement(message);
            // Fix 9: auto-scroll to newest message
            SwingUtilities.invokeLater(() -> {
                int last = cards.main.conversationView.list.getModel().getSize() - 1;
                if (last >= 0) cards.main.conversationView.list.ensureIndexIsVisible(last);
            });
            // Issue #176: notify user when window is not active
            UserInfo me = controller.getCurrentUserInfo();
            boolean fromOther = me != null && !message.getSenderId().equals(me.getUserId());
            if (fromOther && !frame.isActive()) {
                unreadWhileAway++;
                frame.setTitle(BASE_TITLE + " (" + unreadWhileAway + " new)");
                Toolkit.getDefaultToolkit().beep();
                suppressActivationReset = true;
                frame.toFront();
            }
        });
    }

    public void updateAdminConversationSearchModel(ArrayList<ConversationMetadata> conversations) {
        SwingUtilities.invokeLater(() -> {
            if (cards.main.directoryView.adminConversationSearchWindow == null
                    || cards.main.directoryView.adminConversationSearchWindow.model == null) {
                return;
            }
            DefaultListModel<ConversationMetadata> model = cards.main.directoryView.adminConversationSearchWindow.model;
            model.clear();
            for (ConversationMetadata conversation : conversations) {
                model.addElement(conversation);
            }
        });
    }

    private JTextField makePlaceholderField(String placeholder, int cols) {
        return new PlaceholderTextField(placeholder, cols);
    }

    private void bindEscapeToDispose(JDialog dialog) {
        JRootPane rp = dialog.getRootPane();
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "closeDialog");
        rp.getActionMap().put("closeDialog", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent evt) { dialog.dispose(); }
        });
    }

    private Image createAppIcon() {
        int size = 32;
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(33, 150, 243));
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.fillRoundRect(6, 7, 20, 14, 6, 6);
        int[] xs = {10, 10, 16};
        int[] ys = {19, 26, 20};
        g.fillPolygon(xs, ys, 3);
        g.dispose();
        return img;
    }

    private static class PlaceholderTextField extends JTextField {
        private final String placeholder;

        PlaceholderTextField(String placeholder, int columns) {
            super(columns);
            this.placeholder = placeholder;
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.GRAY);
                g2.setFont(getFont().deriveFont(Font.ITALIC));
                Insets ins = getInsets();
                int x = ins.left + 2;
                int y = g2.getFontMetrics().getAscent() + ins.top;
                g2.drawString(placeholder, x, y);
                g2.dispose();
            }
        }
    }

    // =========================================================================
    class ScreenCards extends JPanel {
        CardLayout layout;
        MainView main;
        LoginView login;
        RegisterView register;

        ScreenCards() {
            layout = new CardLayout();
            setLayout(layout);
            login = new LoginView();
            register = new RegisterView();
            main = new MainView();
            add(login, "login");
            add(register, "register");
            add(main, "main");
        }
    }

    // =========================================================================
    class RegisterView extends JPanel {
        JTextField userId;
        JTextField loginName;
        JPasswordField password;
        JPasswordField passwordAgain;
        JTextField name;
        JButton createButton;
        JButton backButton;

        RegisterView() {
        	setLayout(new BorderLayout());

        	// initialize components
        	JPanel inputPanel = new JPanel(new GridBagLayout());
        	userId = new JTextField(15);
        	name = new JTextField(15);
        	loginName = new JTextField(15);
        	password = new JPasswordField(15);
        	passwordAgain = new JPasswordField(15);
        	createButton = new JButton("Create Account");
        	backButton = new JButton("Back");

        	// layout for  the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttonPanel.add(createButton);
            buttonPanel.add(backButton);

        	// use grid layout to put the parts
        	GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;

            // put the label for User ID
            gridConst.gridx = 0;
            gridConst.gridy = 0;
            inputPanel.add(new JLabel("User ID"), gridConst);

            // put the text field on the right side of the label
            gridConst.gridx = 1;
            gridConst.gridwidth = 2;
            inputPanel.add(userId, gridConst);

            // put the label for user name below User ID
            gridConst.gridx = 0;
            gridConst.gridy = 1;
            gridConst.gridwidth = 1;
            inputPanel.add(new JLabel("User Name"), gridConst);

            // put the text field on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(name, gridConst);

            // put the label for user name below User ID
            gridConst.gridx = 0;
            gridConst.gridy = 2;
            inputPanel.add(new JLabel("Login ID"), gridConst);

            // put the text field on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(loginName, gridConst);

            // put the label for password below login Name
            gridConst.gridx = 0;
            gridConst.gridy = 3;
            inputPanel.add(new JLabel("Password"), gridConst);

            // put the password text field on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(password, gridConst);

            // put the label for the password (confirmation)
            gridConst.gridx = 0;
            gridConst.gridy = 4;
            inputPanel.add(new JLabel("Confirm Password"), gridConst);

            // put the password text field (confirmation) on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(passwordAgain, gridConst);

            // put the button layout below the fields
            gridConst.gridx = 1;
            gridConst.gridy = 5;
            inputPanel.add(buttonPanel, gridConst);

            // locate these parts at the center of the window
            add(inputPanel, BorderLayout.CENTER);

            // add an action when click "Create Account" button
            createButton.addActionListener(e -> {
            	char[] pwd1 = password.getPassword();
            	char[] pwd2 = passwordAgain.getPassword();
            	// if the passwords are the same, send the information to clientController
            	if(java.util.Arrays.equals(pwd1, pwd2)) {
            		controller.register(userId.getText(), name.getText(), loginName.getText(), password.getPassword());
            	} else {
            		// if not, show the error message
            		// Fix 4: use frame as parent instead of null
            		JOptionPane.showMessageDialog(frame, "Passwords don't match. Type them again.", "Error", JOptionPane.ERROR_MESSAGE);
            	}
            	// fill with 0 for secure reason
            	java.util.Arrays.fill(pwd1, '0');
                java.util.Arrays.fill(pwd2, '0');
            });

            // add an action when click
            backButton.addActionListener(e -> {
            	// go back to login window
            	cards.layout.show(cards, "login");
            });
        }
    }

    // =========================================================================
    class LoginView extends JPanel {
        JTextField login_idField;
        JPasswordField passwordField;
        JButton loginButton;
        JButton createButton;

        LoginView() {

            setLayout(new BorderLayout());

            // initialize components
            login_idField = new JTextField(15);
            passwordField = new JPasswordField(15);
            loginButton = new JButton("Login");
            createButton = new JButton("Create Account");
            JPanel inputPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;


            // add label for login_id
            gridConst.gridx = 0;
            gridConst.gridy = 0;
            inputPanel.add(new JLabel("Login ID"), gridConst);
            // add text field on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(login_idField, gridConst);
            // add label for password below the login_id
            gridConst.gridx = 0;
            gridConst.gridy = 1;
            inputPanel.add(new JLabel("Password"), gridConst);
            // add text field on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(passwordField, gridConst);
            // add login button next to the fields
            gridConst.gridx = 2;
            gridConst.gridy = 0;
            gridConst.gridheight = 2;
            inputPanel.add(loginButton, gridConst);

            // add create button below the text fields
            gridConst.gridx = 1;
            gridConst.gridy = 2;
            gridConst.insets = new Insets(20, 5, 5, 5);

            // wrap a layout with create button which can be located on the middle
            JPanel createBtnPane = new JPanel();
            createBtnPane.setLayout(new FlowLayout(FlowLayout.CENTER));
            createBtnPane.add(createButton);
            inputPanel.add(createBtnPane, gridConst);

            // add to loginView
            add(inputPanel, BorderLayout.CENTER);


            ActionListener loginAction = e -> controller.login(login_idField.getText(), passwordField.getPassword());
            loginButton.addActionListener(loginAction);
            login_idField.addActionListener(loginAction);
            passwordField.addActionListener(loginAction);

            createButton.addActionListener(e -> {
            	cards.layout.show(cards, "register");
            });
        }
    }

    // =========================================================================
    class MainView extends JPanel {
        ConversationView conversationView;
        DirectoryView directoryView;
        ConversationListView conversationListView;

        MainView() {
        	setLayout(new GridBagLayout());
        	GridBagConstraints gridConst = new GridBagConstraints();
        	conversationView = new ConversationView();
            directoryView = new DirectoryView();
            conversationListView = new ConversationListView();

        	gridConst.gridy = 0;
        	gridConst.fill = GridBagConstraints.BOTH;
        	gridConst.weighty = 1.0;

        	// left
        	gridConst.gridx = 0;
        	gridConst.weightx = 0.2; // 20%
        	add(directoryView, gridConst);

        	// middle
        	gridConst.gridx = 1;
        	gridConst.weightx = 0.6; // 60%
        	add(conversationView, gridConst);

        	// right
        	gridConst.gridx = 2;
        	gridConst.weightx = 0.2; // 20%
        	add(conversationListView, gridConst);

        }

    }

    // =========================================================================
    class ConversationView extends JPanel {
        JLabel participantsLabel;
        DefaultListModel<Message> conversationMessageListModel = new DefaultListModel<>();
        JList<Message> list = new JList<>(conversationMessageListModel);
        JButton addButton;
        JButton leaveButton;
        JTextField text;
        JButton sendButton;
        JDialog addDialog;
        SelectUserWindow addUserWindow;

        // Fix 8: placeholder label shown when no conversation is selected
        private final JLabel placeholderLabel = new JLabel("Select a conversation to start chatting", SwingConstants.CENTER);

        // Fix 6: cell renderer fields — reuse panel and label instead of creating new ones each call
        private final class MessageCellRenderer extends JPanel implements ListCellRenderer<Message> {
            private final JPanel panel = new JPanel(new BorderLayout());
            private final JLabel label = new JLabel();

            MessageCellRenderer() {
                setLayout(new BorderLayout());
                label.setOpaque(true);
                panel.setOpaque(true);
            }

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends Message> list, Message value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                Message msg = value;

                // Resolve sender's display name from conversation participants
                String senderName = null;
                Conversation conv = controller.getCurrentConversation();
                if (conv != null) {
                    for (UserInfo p : conv.getParticipants()) {
                        if (p.getUserId().equals(msg.getSenderId())) {
                            senderName = p.getName();
                            break;
                        }
                    }
                }
                // Fix 4: fall back to "(former participant)" if sender is not in current participant list
                if (senderName == null) {
                    senderName = "(former participant)";
                }
                String ts = msg.getTimestamp().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("LLL dd HH:mm"));
                String displayText = ts + " " + senderName + ": " + msg.getText();

                long lastReadSeq = controller.getCurrentUserInfo().getLastRead(controller.getCurrentConversationId());

                // Reset panel — remove any prior child so we can re-add in correct position
                panel.removeAll();

                // displaying the current user's messages on the right side
                if (msg.getSenderId().equals(controller.getCurrentUserInfo().getUserId())) {
                    label.setBackground(Color.LIGHT_GRAY);
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                    label.setText(displayText);
                    panel.add(label, BorderLayout.EAST);
                } else { // displaying the other participants' messages on the left side
                    if (msg.getSequenceNumber() > lastReadSeq) {
                        label.setFont(label.getFont().deriveFont(Font.BOLD));
                        label.setText("● " + displayText);
                    } else {
                        label.setFont(label.getFont().deriveFont(Font.PLAIN));
                        label.setText(displayText);
                    }
                    panel.add(label, BorderLayout.WEST);
                }

                return panel;
            }
        }

        ConversationView() {
        	participantsLabel = new JLabel();
            addButton = new JButton("Add");
            leaveButton = new JButton("Leave");
            text = new JTextField(15);
            text.setEnabled(false);
            sendButton = new JButton("Send");
            // Fix 10: gate buttons — disabled until a conversation is selected
            addButton.setEnabled(false);
            leaveButton.setEnabled(false);
            sendButton.setEnabled(false);

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // conversation info is located on the upper side of the window
            JPanel infoPane = new JPanel(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;

            gridConst.gridx = 0;
            gridConst.gridy = 0;
            infoPane.add(participantsLabel, gridConst);

            gridConst.gridx = 1;
            infoPane.add(addButton, gridConst);

            gridConst.gridx = 2;
            infoPane.add(leaveButton, gridConst);

            add(infoPane, BorderLayout.NORTH);

            // Fix 6: install the reusable cell renderer
            list.setCellRenderer(new MessageCellRenderer());

            // Fix 8: create a layered center panel with the placeholder on top when no conversation
            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.add(new JScrollPane(list), BorderLayout.CENTER);
            placeholderLabel.setVisible(true);
            centerPanel.add(placeholderLabel, BorderLayout.SOUTH);
            add(centerPanel, BorderLayout.CENTER);

            // sendPane is located on the bottom of the window
            JPanel sendPane = new JPanel(new BorderLayout());
            sendPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            sendPane.add(text, BorderLayout.CENTER);
            sendPane.add(sendButton, BorderLayout.EAST);

            add(sendPane, BorderLayout.SOUTH);

            // add action to add button
            addButton.addActionListener(e -> {

            	if (addDialog != null && addDialog.isVisible()) {
            		addDialog.toFront();
            		addDialog.requestFocus();
                    return;
                }

                addUserWindow = new SelectUserWindow(
                    users -> controller.addToConversation(users, controller.getCurrentConversationId()));

                addDialog = new JDialog(frame, "Select User", false);
                addDialog.add(addUserWindow);
                addDialog.setSize(300, 400);
                addDialog.setLocationRelativeTo(frame);
                addDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                bindEscapeToDispose(addDialog);

                addDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        addDialog = null;
                    }
                });

                addDialog.setVisible(true);

            });

            // add action to leave button
            leaveButton.addActionListener(e -> {
            	// Fix 4: use frame as parent instead of null
            	int result = JOptionPane.showConfirmDialog(frame, "Are you sure to leave this conversation?", "Confirm to Leave", JOptionPane.YES_NO_OPTION);
            	if(result == JOptionPane.YES_OPTION) {
            		controller.leaveConversation(controller.getCurrentConversationId());
            	}
            });

            // add action to text field (detecting if there is a text)
            text.getDocument().addDocumentListener(new DocumentListener() {

                private void update() {
                    sendButton.setEnabled(!text.getText().trim().isEmpty());
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    update();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    update();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    update();
                }
            });

            // add action to send button
            sendButton.addActionListener(e -> {
                if(controller.getCurrentConversation() == null) {
                    return;
                }
            	controller.sendMessage(controller.getCurrentConversation().getConversationId(), text.getText());
                text.setText("");
            });

            text.addActionListener(e -> sendButton.doClick());

        }

        public void setListModel(Conversation currConv) {
            // Fix 3: exclude self, show up to 3 names, append "+N more" if needed
            UserInfo me = controller.getCurrentUserInfo();
            String myId = (me != null) ? me.getUserId() : null;
            ArrayList<UserInfo> others = new ArrayList<>();
            for (UserInfo p : currConv.getParticipants()) {
                if (!p.getUserId().equals(myId)) {
                    others.add(p);
                }
            }
            StringBuilder memberSb = new StringBuilder();
            int limit = Math.min(3, others.size());
            for (int i = 0; i < limit; i++) {
                if (i > 0) memberSb.append(", ");
                memberSb.append(others.get(i).getName());
            }
            if (others.size() > 3) {
                memberSb.append(" +").append(others.size() - 3).append(" more");
            }
            final String finalMember = memberSb.toString();
        	SwingUtilities.invokeLater(() -> {
        		participantsLabel.setText(finalMember);
                conversationMessageListModel.clear();
                for(int i = 0; i < currConv.getMessages().size(); i++) {
                    conversationMessageListModel.addElement(currConv.getMessages().get(i));
                }
                text.setEnabled(true);
                // Fix 10: enable buttons when a conversation is selected
                addButton.setEnabled(true);
                leaveButton.setEnabled(true);
                sendButton.setEnabled(!text.getText().trim().isEmpty());
                // Fix 8: hide placeholder once a conversation is loaded
                placeholderLabel.setVisible(false);
                // Fix 9: auto-scroll to newest message after loading
                SwingUtilities.invokeLater(() -> {
                    int last = list.getModel().getSize() - 1;
                    if (last >= 0) list.ensureIndexIsVisible(last);
                });
        	});
        }

        /** Clears the view and disables action buttons (no conversation active). */
        public void clearConversation() {
            SwingUtilities.invokeLater(() -> {
                participantsLabel.setText("");
                conversationMessageListModel.clear();
                text.setEnabled(false);
                // Fix 10: disable buttons when no conversation is active
                addButton.setEnabled(false);
                leaveButton.setEnabled(false);
                sendButton.setEnabled(false);
                // Fix 8: show placeholder again
                placeholderLabel.setVisible(true);
            });
        }

        public DefaultListModel<Message> getListModel() {
        	return this.conversationMessageListModel;
        }

        public boolean isAddingUser() {
        	return addDialog != null && addDialog.isVisible();
        }
    }

    // =========================================================================
    class DirectoryView extends JPanel {
        JLabel profileUserIdLabel;
        JLabel profileNameLabel;
        // Fix 5: banner shown when directory is in picker mode
        JLabel pickerBannerLabel;
        JTextField searchField;
        DefaultListModel<UserInfo> listModel = new DefaultListModel<>();
        JList<UserInfo> list = new JList<>(listModel);
        JButton logoutButton;
        JButton createConversationButton;
        JButton adminButton;
        JDialog createDialog;
        JDialog adminDialog;
        UserInfo selecting;
        SelectUserWindow createConversationUserWindow;
        AdminConversationSearchWindow adminConversationSearchWindow;

        DirectoryView() {
        	profileUserIdLabel = new JLabel();
        	profileNameLabel = new JLabel();

            // Fix 5: picker mode banner — hidden by default
            pickerBannerLabel = new JLabel("Selecting participants — click to add", SwingConstants.CENTER);
            pickerBannerLabel.setForeground(new Color(0, 100, 180));
            pickerBannerLabel.setFont(pickerBannerLabel.getFont().deriveFont(Font.BOLD));
            pickerBannerLabel.setVisible(false);

            searchField = makePlaceholderField("Search users...", 15);
            logoutButton = new JButton("Log Out");
            createConversationButton = new JButton("Create Conversation");
            // gray-out until select the user
            createConversationButton.setEnabled(false);

            // construct admin button unconditionally and enable later if the user is admin
            adminButton = new JButton("Admin");
            adminButton.setEnabled(false);
            adminButton.setVisible(false);

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // userPanel is located upper side of the window
            JPanel userPane = new JPanel(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;


            gridConst.gridx = 0;
            gridConst.gridy = 0;
            userPane.add(profileUserIdLabel, gridConst);

            gridConst.gridy = 1;
            userPane.add(profileNameLabel, gridConst);

            gridConst.gridy = 2;
            userPane.add(searchField, gridConst);

            gridConst.gridx = 1;
            gridConst.gridy = 0;
            gridConst.gridheight = 2;
            userPane.add(logoutButton, gridConst);

            // Fix 5: wrap userPane and pickerBannerLabel in a combined north panel
            JPanel northPanel = new JPanel(new BorderLayout());
            northPanel.add(userPane, BorderLayout.NORTH);
            northPanel.add(pickerBannerLabel, BorderLayout.SOUTH);
            add(northPanel, BorderLayout.NORTH);


            // list is located center of the window with JScrollPane
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            add(new JScrollPane(list), BorderLayout.CENTER);


            // buttonPane is located at the bottom side of the window
            JPanel buttonPane = new JPanel(new FlowLayout());
            buttonPane.add(createConversationButton);
            buttonPane.add(adminButton);

            add(buttonPane, BorderLayout.SOUTH);

            // add action for searchField
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { filter(); }
                @Override public void removeUpdate(DocumentEvent e) { filter(); }
                @Override public void changedUpdate(DocumentEvent e) { filter(); }

                private void filter() {
                    String text = searchField.getText().toUpperCase();
                    listModel.clear();
                    // Fix 6: exclude the logged-in user from the directory list
                    UserInfo me = controller.getCurrentUserInfo();
                    String myId = (me != null) ? me.getUserId() : null;
                    for(UserInfo item: controller.getFilteredDirectory(text)) {
                        if (myId == null || !item.getUserId().equals(myId)) {
                            listModel.addElement(item);
                        }
                    }
                }
            });

            // add action to the log in button
            logoutButton.addActionListener(e -> {
            	controller.logout();
            });

            // add action to the create conversation button
            createConversationButton.addActionListener(e -> {
            	// if createDialog is open
            	if (createDialog != null && createDialog.isVisible()) {
                    createDialog.toFront();
                    createDialog.requestFocus();
                    return;
                }

            	createConversationUserWindow = new SelectUserWindow(users -> controller.createConversation(users));
                createDialog = new JDialog(frame, "Select User", false);
                createDialog.add(createConversationUserWindow);
                createDialog.setSize(300, 400);
                createDialog.setLocationRelativeTo(frame);
                createDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                bindEscapeToDispose(createDialog);

                createDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        createDialog = null;
                    }
                });

                createDialog.setVisible(true);

            });

            // add action to admin button
            adminButton.addActionListener(e -> {
            	if (adminDialog != null && adminDialog.isVisible()) {
            		adminDialog.toFront();
            		adminDialog.requestFocus();
                    return;
                }

                if(selecting == null) {
                    return;
                }
            	controller.adminGetUserConversations(selecting.getUserId());
            	adminConversationSearchWindow= new AdminConversationSearchWindow();
                adminDialog = new JDialog(frame, "Searching Conversations", false);
                adminDialog.add(adminConversationSearchWindow);
                adminDialog.setSize(300, 400);
                adminDialog.setLocationRelativeTo(frame);
                adminDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                bindEscapeToDispose(adminDialog);

                adminDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                    	adminDialog = null;
                    }
                });

                adminDialog.setVisible(true);

            });

            // add action for selecting an item from the list
            list.getSelectionModel().addListSelectionListener(e -> {
                UserInfo selectedValue = list.getSelectedValue();
                if (e.getValueIsAdjusting()) {
                    return;
                }
                selecting = selectedValue;

                // Fix 5: show picker banner when a dialog is open (picker mode active)
                boolean inPickerMode = (createDialog != null && createDialog.isVisible())
                        || (cards.main.conversationView.addDialog != null && cards.main.conversationView.addDialog.isVisible());
                pickerBannerLabel.setVisible(inPickerMode);

                // if the another window is not visible, shows the button
                if(selecting != null && (createDialog == null || !createDialog.isVisible()) && (adminDialog == null || !adminDialog.isVisible())
                        && (cards.main.conversationView.addDialog == null || !cards.main.conversationView.addDialog.isVisible())) {
                    createConversationButton.setEnabled(true);
                    // adminButton is always constructed; visibility is toggled at login time
                    adminButton.setEnabled(true);
                } else if(createDialog != null && createDialog.isVisible()) { // if selectUser window is open
                    createConversationUserWindow.addUser(selecting);
                } else if(cards.main.conversationView.addDialog != null && cards.main.conversationView.addDialog.isVisible()) {
                    if(selecting != null) cards.main.conversationView.addUserWindow.addUser(selecting);
                }
            });

            // Helpful debug signal when user clicks an already-selected row.
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int clickedIndex = list.locationToIndex(e.getPoint());
                    if (clickedIndex >= 0) {
                        list.setSelectedIndex(clickedIndex);
                    }
                }
            });

        }

        // Fix 1: setListModel now also calls list.setModel() so the JList reflects the new model
        public void setListModel(DefaultListModel<UserInfo> model) {
        	this.listModel = model;
        	list.setModel(model);
        }

        public DefaultListModel<UserInfo> getListModel() {
        	return this.listModel;
        }

        public boolean isCreatingConversation() {
            return createDialog != null && createDialog.isVisible();
        }

        public boolean isAdminSearching() {
            return adminDialog != null && adminDialog.isVisible();
        }

        public DefaultListModel<ConversationMetadata> getAdminConversationModel() {
            if(adminConversationSearchWindow == null) {
                return new DefaultListModel<>();
            }
        	return adminConversationSearchWindow.getAdminConversationSearch();
        }


    }

    // =========================================================================
    class ConversationListView extends JPanel {
        JTextField searchField;
        DefaultListModel<Conversation> listModel = new DefaultListModel<>();
        JList<Conversation>	list = new JList<>(listModel);

        // Fix 2: custom renderer that shows a friendly display name
        private final class ConversationCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Conversation) {
                    Conversation conv = (Conversation) value;
                    UserInfo me = controller.getCurrentUserInfo();
                    String myId = (me != null) ? me.getUserId() : null;

                    // Collect other participants (excluding self)
                    ArrayList<UserInfo> others = new ArrayList<>();
                    for (UserInfo p : conv.getParticipants()) {
                        if (!p.getUserId().equals(myId)) {
                            others.add(p);
                        }
                    }

                    String display;
                    if (conv.getType() == shared.enums.ConversationType.PRIVATE) {
                        String otherName = others.isEmpty() ? "(empty)" : others.get(0).getName();
                        display = "DM: " + otherName;
                    } else {
                        StringBuilder sb = new StringBuilder("Group: ");
                        for (int i = 0; i < others.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(others.get(i).getName());
                        }
                        if (others.isEmpty()) sb.append("(empty)");
                        display = sb.toString();
                    }
                    setText(display);
                }
                return this;
            }
        }

        ConversationListView() {
        	searchField = makePlaceholderField("Search conversations...", 15);


            // searchField is located on the upper of the window.
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(searchField, BorderLayout.NORTH);

            // Fix 2: install the custom cell renderer
            list.setCellRenderer(new ConversationCellRenderer());

            // list is located on the middle of the window.
            add(new JScrollPane(list), BorderLayout.CENTER);

            // add action for searchField
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { filter(); }
                @Override public void removeUpdate(DocumentEvent e) { filter(); }
                @Override public void changedUpdate(DocumentEvent e) { filter(); }

                private void filter() {
                    String text = searchField.getText().toUpperCase();
                    listModel.clear();

                    for(Conversation item: controller.getFilteredConversationList(text)) {
                    	listModel.addElement(item);
                    }
                }
            });

            // TODO: label the conversation
            // TODO: unread markers
            // add action for selecting an item from the list
            list.addListSelectionListener(e-> {
            	if (!e.getValueIsAdjusting()) {
    				if(list.getSelectedValue() != null) {
    					Conversation selected = list.getSelectedValue();
    					controller.setCurrentConversationId(selected.getConversationId());
    					// delegates to ConversationView.setListModel so both the
    					// participant label and the message list are updated together
    					cards.main.conversationView.setListModel(selected);
    					if (!selected.getMessages().isEmpty()) {
    					    Message last = selected.getMessages().get(selected.getMessages().size() - 1);
    					    controller.updateReadMessages(selected.getConversationId(), last.getSequenceNumber());
    					}
    				}
            	}
            });
        }

        // Fix 1: setListModel now also calls list.setModel() so the JList reflects the new model
        public void setListModel(DefaultListModel<Conversation> model) {
        	this.listModel = model;
        	list.setModel(model);
        }

        public DefaultListModel<Conversation> getListModel() {
        	return this.listModel;
        }
    }

    // =========================================================================
    class SelectUserWindow extends JPanel {
        DefaultListModel<UserInfo> model = new DefaultListModel<>();
        JList<UserInfo> list = new JList<>(model);
        JButton removeButton;
        JButton okButton;
        JButton cancelButton;
        private final java.util.function.Consumer<ArrayList<UserInfo>> onConfirm;

        SelectUserWindow(java.util.function.Consumer<ArrayList<UserInfo>> onConfirm) {
            this.onConfirm = onConfirm;
            removeButton = new JButton("Remove");
            removeButton.setEnabled(false);
            okButton = new JButton("OK");
            cancelButton = new JButton("Cancel");

            // List located on the middle of the window
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(new JScrollPane(list), BorderLayout.CENTER);

            // buttons are located on the bottom of the window
        	JPanel buttonPane = new JPanel(new FlowLayout());
        	buttonPane.add(removeButton);
        	buttonPane.add(okButton);
        	buttonPane.add(cancelButton);

        	add(buttonPane, BorderLayout.SOUTH);

        	// add action to Remove button
        	removeButton.addActionListener(e -> {

        	    UserInfo selected = list.getSelectedValue();

        	    if (selected != null) {
        	        model.removeElement(selected);
        	    }
        	});

        	// add action to OK button
            okButton.addActionListener(e -> {
                // Fix 7: warn and do not proceed if no participants are selected
                if (model.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Please select at least one participant.",
                            "No participants selected", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                ArrayList<UserInfo> selected = new ArrayList<>();
                for (int i = 0; i < model.size(); i++) selected.add(model.get(i));
                onConfirm.accept(selected);
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();
            });

            // add action to cancel button
            cancelButton.addActionListener(e -> {
            	model.clear();
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();
            });


            // add action for selecting an item from the list
            list.addListSelectionListener(e-> {
            	if (!e.getValueIsAdjusting()) {
						 UserInfo selecting = list.getSelectedValue();
						 // if the another window is not visible, shows the button
						 removeButton.setEnabled(selecting != null);
			    }
            });
        }

        // add user to selecting list
        public void addUser(UserInfo user) {
        	for(int i = 0; i < model.size(); i++) {
        		if(user.getUserId().equals(model.get(i).getUserId())){
        			return;
        		}
        	}
        	model.addElement(user);
        }


    }

    // =========================================================================
    class AdminConversationSearchWindow extends JPanel {
        JTextField searchField;
        DefaultListModel<ConversationMetadata> model = new DefaultListModel<>();
        JList<ConversationMetadata> list = new JList<>(model);
        JButton okButton;
        JButton cancelButton;

        AdminConversationSearchWindow() {
            searchField = makePlaceholderField("Search conversations...", 15);
            okButton = new JButton("OK");
            okButton.setFocusTraversalKeysEnabled(false);
            cancelButton = new JButton("Cancel");

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // searchField is located on the upper side of the window
            add(searchField, BorderLayout.NORTH);

            // the list is located on the center of the window
            add(new JScrollPane(list), BorderLayout.CENTER);

            // buttons are located on the bottom of the window
        	JPanel buttonPane = new JPanel(new FlowLayout());
        	buttonPane.add(okButton);
        	buttonPane.add(cancelButton);

        	add(buttonPane, BorderLayout.SOUTH);

        	// add action to searchField
        	searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { filter(); }
                @Override public void removeUpdate(DocumentEvent e) { filter(); }
                @Override public void changedUpdate(DocumentEvent e) { filter(); }

                private void filter() {
                    String text = searchField.getText().toUpperCase();
                    model.clear();

                    for(ConversationMetadata item: controller.getFilteredAdminConversationSearch(text)) {
                    	model.addElement(item);
                    }
                }
            });

        	// add action for selecting an item from the list
            list.addListSelectionListener(e-> {
            	if (!e.getValueIsAdjusting()) {
						 ConversationMetadata selecting = list.getSelectedValue();
						 // if the another window is not visible, shows the buttons
						 okButton.setEnabled(selecting != null);
			    }
            });

        	// add action to okButton
            okButton.addActionListener(e -> {
                if(list.getSelectedValue() == null) {
                    return;
                }
            	controller.joinConversation(list.getSelectedValue().getConversationId());
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();
            });

            // add action to cancelButton
            cancelButton.addActionListener(e -> {
            	model.clear();
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();
            });
        }

        public DefaultListModel<ConversationMetadata> getAdminConversationSearch() {
        	return model;
        }

    }
}
