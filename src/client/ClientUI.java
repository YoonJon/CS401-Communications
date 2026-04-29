package client;

import shared.enums.*;
import shared.networking.User.UserInfo;
import shared.payload.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.LineBorder;

import java.awt.*;
import java.awt.event.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
        showLoginView(null);
    }

    /** #139B: pre-fill login id (e.g. with the just-registered loginName) before showing the screen. */
    public void showLoginView(String prefilledLoginId) {
        SwingUtilities.invokeLater(() -> {
            // Fix 5: dispose any open dialogs before switching to login
            DirectoryView dv = cards.main.directoryView;
            ConversationView cv = cards.main.conversationView;
            if (dv.createDialog != null && dv.createDialog.isVisible()) dv.createDialog.dispose();
            if (dv.adminDialog != null && dv.adminDialog.isVisible()) dv.adminDialog.dispose();
            if (cv.addDialog != null && cv.addDialog.isVisible()) cv.addDialog.dispose();

            clearLoginAndRegisterFields();
            if (prefilledLoginId != null && !prefilledLoginId.isEmpty()) {
                cards.login.login_idField.setText(prefilledLoginId);
            }
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
                JLabel nameL = cards.main.directoryView.profileNameLabel;
                JLabel idL   = cards.main.directoryView.profileUserIdLabel;
                nameL.setText(currentUser.getName());
                nameL.setFont(nameL.getFont().deriveFont(Font.BOLD, nameL.getFont().getSize() + 2f));
                idL.setText(currentUser.getUserId());
                idL.setFont(idL.getFont().deriveFont(Font.PLAIN, idL.getFont().getSize() - 1f));
                idL.setForeground(Color.GRAY);
            }
            cards.layout.show(cards, "main");
            // Fix 2b: repack and re-center after switching to main card
            frame.pack();
            frame.setLocationRelativeTo(null);
        });
    }

    /** Escapes HTML special chars, wraps the result in {@code <html><body style='width:Npx'>...},
     *  and emits a JLabel-ready string. Shared between {@link ConversationView.MessageCellRenderer}
     *  and the admin viewer's read-only renderer so wrapping width math stays in one place. */
    static String escapeAndWrapHtml(String raw, int maxPx) {
        if (raw == null) raw = "";
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;");  break;
                case '\n': sb.append("<br/>");  break;
                case '\r': /* skip */            break;
                default:   sb.append(c);
            }
        }
        int safe = Math.max(80, maxPx);
        return "<html><body style='width:" + safe + "px'>" + sb + "</body></html>";
    }

    /** #141: wrap a password field with a Show/Hide toggle button. The wrapper JPanel takes the
     *  field's place in any layout; the field reference itself is unchanged. */
    private static JPanel passwordFieldWithToggle(JPasswordField field) {
        JPanel wrap = new JPanel(new BorderLayout(2, 0));
        JButton toggle = new JButton("Show");
        toggle.setMargin(new Insets(0, 6, 0, 6));
        toggle.setFocusable(true);
        char defaultEcho = field.getEchoChar();
        toggle.addActionListener(e -> {
            if (field.getEchoChar() == 0) {
                field.setEchoChar(defaultEcho == 0 ? '•' : defaultEcho);
                toggle.setText("Show");
            } else {
                field.setEchoChar((char) 0);
                toggle.setText("Hide");
            }
        });
        wrap.add(field, BorderLayout.CENTER);
        wrap.add(toggle, BorderLayout.EAST);
        return wrap;
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
        SwingUtilities.invokeLater(() -> {
            String msg;
            switch (registerStatus) {
                case USER_ID_TAKEN:
                    msg = "Employee ID is already registered. Please try again.";
                    break;
                case USER_ID_INVALID:
                    // Server returns this status when the Employee ID isn't in the authorized
                    // list OR the User Name doesn't match the name on file for that ID.
                    msg = "Employee ID and User Name don't match the authorized list. "
                        + "Please verify both fields and try again.";
                    break;
                case LOGIN_NAME_TAKEN:
                    msg = "Login ID is already taken. Please try again.";
                    break;
                case LOGIN_NAME_INVALID:
                    msg = "Login ID is invalid. Use only letters, numbers, hyphens, or underscores.";
                    break;
                default:
                    return;
            }
            JOptionPane.showMessageDialog(frame, msg, "Registration Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    public void showLoginError(LoginStatus loginStatus) {
        SwingUtilities.invokeLater(() -> {
            // #139A: always clear the password on login failure.
            cards.login.passwordField.setText("");
            String msg;
            String title = "Login Error";
            switch (loginStatus) {
                case INVALID_CREDENTIALS:
                    msg = "Invalid credentials. Please try again.";
                    break;
                case NO_ACCOUNT_EXISTS:
                    msg = "No account exists. Please create an account.";
                    break;
                case DUPLICATE_SESSION:
                    // #144: coherent, actionable copy that doesn't contradict itself.
                    title = "Session Already Active";
                    msg = "You are already logged in from another location. Please log out of "
                        + "that session first, then try again. If you closed the app without "
                        + "logging out, the previous session may still be active on the server.";
                    break;
                default:
                    return;
            }
            JOptionPane.showMessageDialog(frame, msg, title, JOptionPane.ERROR_MESSAGE);
        });
    }

    /** #138: shown when the server is unreachable so the user isn't stuck staring at a hung UI. */
    public void showNetworkError() {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                frame,
                "Cannot reach the server. Please check that the server is running and try again.",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE));
    }

    /** #140/#142: drives the visible "submit in flight" state for both login and register. */
    public void setLoginInFlight(boolean inFlight) {
        SwingUtilities.invokeLater(() -> {
            if (cards == null) return; // pre-EDT init guard
            cards.login.loginButton.setEnabled(!inFlight);
            cards.login.loginButton.setText(inFlight ? "Logging in..." : "Login");
            cards.register.createButton.setEnabled(!inFlight);
            cards.register.createButton.setText(inFlight ? "Creating..." : "Create Account");
        });
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
            ArrayList<Message> msgs = conversation.getMessages();
            for (Message message : msgs) {
                conversationMessageModel.addElement(message);
            }
            if (!msgs.isEmpty()) {
                Message last = msgs.get(msgs.size() - 1);
                UserInfo me = controller.getCurrentUserInfo();
                if (me != null) {
                    me.setLastRead(conversation.getConversationId(), last.getSequenceNumber());
                }
                controller.updateReadMessages(conversation.getConversationId(), last.getSequenceNumber());
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

    public void repaintMessageList() {
        SwingUtilities.invokeLater(() -> cards.main.conversationView.list.repaint());
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

    /** Routes a silent admin read into the right pane of the open admin viewer. No-op if
     *  the viewer dialog isn't open (defensive — late responses after dialog close are dropped). */
    public void showAdminConversationView(Conversation conv) {
        if (conv == null) return;
        SwingUtilities.invokeLater(() -> {
            DirectoryView dv = cards.main.directoryView;
            if (dv.adminConversationSearchWindow == null) return;
            dv.adminConversationSearchWindow.loadConversation(conv);
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

        	// #143: tooltips clarify which ID is which.
        	userId.setToolTipText("Your organization-assigned employee ID. Must be pre-authorized.");
        	loginName.setToolTipText("Choose a username for logging in. Letters, numbers, hyphens, or underscores only.");

        	// #146: heading at the top of the form.
        	JLabel heading = new JLabel("Register / Create Account", SwingConstants.CENTER);
        	heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize() + 6f));
        	heading.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        	add(heading, BorderLayout.NORTH);

        	// layout for  the buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttonPanel.add(createButton);
            buttonPanel.add(backButton);

        	// use grid layout to put the parts
        	GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;

            // #143: "User ID" → "Employee ID" (organization-assigned, validated against authorized list).
            gridConst.gridx = 0;
            gridConst.gridy = 0;
            inputPanel.add(new JLabel("Employee ID"), gridConst);

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

            // #141: password text field with Show/Hide toggle on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(passwordFieldWithToggle(password), gridConst);

            // put the label for the password (confirmation)
            gridConst.gridx = 0;
            gridConst.gridy = 4;
            inputPanel.add(new JLabel("Confirm Password"), gridConst);

            // #141: confirmation password field also gets a toggle
            gridConst.gridx = 1;
            inputPanel.add(passwordFieldWithToggle(passwordAgain), gridConst);

            // put the button layout below the fields
            gridConst.gridx = 1;
            gridConst.gridy = 5;
            inputPanel.add(buttonPanel, gridConst);

            // locate these parts at the center of the window
            add(inputPanel, BorderLayout.CENTER);

            // #137: extract submit lambda so Enter on any field also fires it.
            ActionListener createAction = e -> {
                char[] pwd1 = password.getPassword();
                char[] pwd2 = passwordAgain.getPassword();
                if (java.util.Arrays.equals(pwd1, pwd2)) {
                    controller.register(userId.getText(), name.getText(), loginName.getText(), password.getPassword());
                } else {
                    // Fix 4: use frame as parent instead of null
                    JOptionPane.showMessageDialog(frame, "Passwords don't match. Type them again.", "Error", JOptionPane.ERROR_MESSAGE);
                }
                java.util.Arrays.fill(pwd1, '0');
                java.util.Arrays.fill(pwd2, '0');
            };
            createButton.addActionListener(createAction);
            userId.addActionListener(createAction);
            name.addActionListener(createAction);
            loginName.addActionListener(createAction);
            password.addActionListener(createAction);
            passwordAgain.addActionListener(createAction);

            // #148: route through showLoginView so clearLoginAndRegisterFields() is called.
            backButton.addActionListener(e -> showLoginView());
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

            // #143: tooltip clarifies the field is the login username, not the Employee ID.
            login_idField.setToolTipText("Your chosen login username (not your Employee ID).");

            // #146: heading at the top of the form.
            JLabel heading = new JLabel("Login", SwingConstants.CENTER);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize() + 6f));
            heading.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
            add(heading, BorderLayout.NORTH);

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
            // #141: password text field with Show/Hide toggle on the right side of the label
            gridConst.gridx = 1;
            inputPanel.add(passwordFieldWithToggle(passwordField), gridConst);
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

            // #148 (symmetric): route through showRegisterView so fields are cleared.
            createButton.addActionListener(e -> showRegisterView());
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
            private final JLabel label = new JLabel();

            MessageCellRenderer() {
                setLayout(new BorderLayout());
                setOpaque(true);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(new Color(180, 180, 180), 1, true),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
            }

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends Message> list, Message value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                Message msg = value;

                // Resolve sender's display name.
                // Short-circuit to own name first so own messages always render correctly
                // even if the cached conversation's participants list is stale or empty.
                // Use historicalParticipants (not participants) so removed users still show their name.
                String senderName = null;
                UserInfo me = controller.getCurrentUserInfo();
                if (me != null && me.getUserId() != null && me.getUserId().equals(msg.getSenderId())) {
                    senderName = me.getName();
                }
                if (senderName == null) {
                    Conversation conv = controller.getCurrentConversation();
                    if (conv != null) {
                        for (UserInfo p : conv.getHistoricalParticipants()) {
                            if (p.getUserId().equals(msg.getSenderId())) {
                                senderName = p.getName();
                                break;
                            }
                        }
                    }
                }
                // Last-resort fallback if even the historical roster has no record of this sender.
                if (senderName == null) {
                    senderName = "(former participant)";
                }
                ZonedDateTime zdt = msg.getTimestamp().toInstant().atZone(ZoneId.systemDefault());
                DateTimeFormatter dtf = zdt.getYear() == ZonedDateTime.now().getYear()
                        ? DateTimeFormatter.ofPattern("LLL dd, HH:mm", java.util.Locale.ENGLISH)
                        : DateTimeFormatter.ofPattern("LLL dd yyyy, HH:mm", java.util.Locale.ENGLISH);
                String ts = zdt.format(dtf);
                String rawText = msg.getText() == null ? "" : msg.getText();
                String displayText = ts + " " + senderName + ": " + rawText;

                long lastReadSeq = controller.getCurrentUserInfo().getLastRead(controller.getCurrentConversationId());

                // Remove prior label so we can re-add in the correct position
                removeAll();
                setBackground(list.getBackground());

                // Compute the wrap width from the JList's current width.
                // 70% of list width keeps the bubble within the viewport even with scrollbar margin.
                int listW = list.getWidth();
                int wrapPx = listW > 0 ? (int) (listW * 0.70) : 360;

                // displaying the current user's messages on the right side
                if (msg.getSenderId().equals(controller.getCurrentUserInfo().getUserId())) {
                    label.setBackground(new Color(0xDC, 0xF8, 0xC6));
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                    label.setText(escapeAndWrapHtml(displayText, wrapPx));
                    add(label, BorderLayout.EAST);
                } else { // displaying the other participants' messages on the left side
                    label.setBackground(list.getBackground());
                    if (msg.getSequenceNumber() > lastReadSeq) {
                        label.setFont(label.getFont().deriveFont(Font.BOLD));
                        label.setText(escapeAndWrapHtml("● " + displayText, wrapPx));
                    } else {
                        label.setFont(label.getFont().deriveFont(Font.PLAIN));
                        label.setText(escapeAndWrapHtml(displayText, wrapPx));
                    }
                    add(label, BorderLayout.WEST);
                }

                return this;
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

            // Fix #183: re-invalidate cell height cache when JList is resized so HTML wrap recomputes.
            list.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    list.setFixedCellHeight(10);
                    list.setFixedCellHeight(-1);
                }
            });

            // Fix 8: create a layered center panel with the placeholder on top when no conversation
            JPanel centerPanel = new JPanel(new BorderLayout());
            JScrollPane messageScrollPane = new JScrollPane(list);
            messageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            messageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            centerPanel.add(messageScrollPane, BorderLayout.CENTER);
            placeholderLabel.setVisible(true);
            centerPanel.add(placeholderLabel, BorderLayout.SOUTH);
            add(centerPanel, BorderLayout.CENTER);

            // sendPane is located on the bottom of the window
            JLabel charCountLabel = new JLabel("0/500");
            charCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            JPanel sendPane = new JPanel(new BorderLayout());
            sendPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            sendPane.add(text, BorderLayout.CENTER);
            JPanel sendRight = new JPanel(new BorderLayout());
            sendRight.add(charCountLabel, BorderLayout.WEST);
            sendRight.add(sendButton, BorderLayout.EAST);
            sendPane.add(sendRight, BorderLayout.EAST);

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
                    // #149: focus the list on open so Tab order is correct and Delete works.
                    @Override
                    public void windowOpened(WindowEvent e) {
                        addUserWindow.list.requestFocusInWindow();
                    }
                });

                // Single-user picker fix: clear directory selection so the first click
                // always emits a fresh ListSelectionEvent (see createConversationButton).
                cards.main.directoryView.list.clearSelection();
                cards.main.directoryView.selecting = null;

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

            // 500-char hard cap
            ((javax.swing.text.AbstractDocument) text.getDocument()).setDocumentFilter(
                    new javax.swing.text.DocumentFilter() {
                        private static final int MAX = 500;
                        @Override
                        public void insertString(FilterBypass fb, int offset, String s, javax.swing.text.AttributeSet a)
                                throws javax.swing.text.BadLocationException {
                            if (fb.getDocument().getLength() + s.length() <= MAX)
                                super.insertString(fb, offset, s, a);
                        }
                        @Override
                        public void replace(FilterBypass fb, int offset, int length, String s, javax.swing.text.AttributeSet a)
                                throws javax.swing.text.BadLocationException {
                            int newLen = fb.getDocument().getLength() - length + (s == null ? 0 : s.length());
                            if (newLen <= MAX)
                                super.replace(fb, offset, length, s, a);
                            else {
                                int allowed = MAX - fb.getDocument().getLength() + length;
                                if (allowed > 0 && s != null)
                                    super.replace(fb, offset, length, s.substring(0, allowed), a);
                            }
                        }
                    });

            // add action to text field (detecting if there is a text)
            text.getDocument().addDocumentListener(new DocumentListener() {

                private void update() {
                    int len = text.getText().length();
                    charCountLabel.setText(len + "/500");
                    charCountLabel.setForeground(len >= 500 ? Color.RED : UIManager.getColor("Label.foreground"));
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
            sendButton.addActionListener(e -> doSend());

            text.addActionListener(e -> doSend());

        }

        private void doSend() {
            if (controller.getCurrentConversation() == null) return;
            String msg = text.getText();
            if (msg.trim().isEmpty()) return;
            controller.sendMessage(controller.getCurrentConversation().getConversationId(), msg);
            text.setText("");
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
            // #127: tooltip explains the prerequisite (a user must be selected). Label kept
            // as "Admin" per the silent-viewer feature; the dialog itself shows what the
            // admin is doing.
            adminButton = new JButton("Admin");
            adminButton.setToolTipText("Select a user in the directory, then click to view their conversations");
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
                    // #149: focus the list on open so Tab order is correct and Delete works.
                    @Override
                    public void windowOpened(WindowEvent e) {
                        createConversationUserWindow.list.requestFocusInWindow();
                    }
                });

                // Single-user picker fix: clear directory selection so the first click
                // always emits a fresh ListSelectionEvent. Without this, when the directory
                // has exactly one user, Swing auto-selects index 0 on render, and a click
                // on that already-selected row is a no-op — addUser is never called.
                list.clearSelection();
                selecting = null;

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
                adminDialog.setSize(900, 600);
                adminDialog.setLocationRelativeTo(frame);
                adminDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                bindEscapeToDispose(adminDialog);

                adminDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                    	// #128: clear all admin-dialog state so reopening shows a fresh empty list.
                    	adminDialog = null;
                    	adminConversationSearchWindow = null;
                    	controller.clearAdminConversationSearch();
                    	adminButton.setEnabled(false);
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
    					if (!selected.getMessages().isEmpty()) {
    					    Message last = selected.getMessages().get(selected.getMessages().size() - 1);
    					    controller.getCurrentUserInfo().setLastRead(
    					            selected.getConversationId(), last.getSequenceNumber());
    					    controller.updateReadMessages(selected.getConversationId(), last.getSequenceNumber());
    					}
    					cards.main.conversationView.setListModel(selected);
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

            // #149: Delete key on the list invokes Remove (so the button is reachable by keyboard).
            list.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeSelected");
            list.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "removeSelected");
            list.getActionMap().put("removeSelected", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (removeButton.isEnabled()) removeButton.doClick();
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
        JButton closeButton;
        ViewerPanel viewerPanel;

        // #126: render each search result as "[ID: N] TYPE • K participant(s) • last active <ts>"
        // (or "no messages yet"). Modeled on ConversationCellRenderer above.
        private final class AdminConversationCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ConversationMetadata) {
                    ConversationMetadata m = (ConversationMetadata) value;
                    int n = m.getParticipants() != null ? m.getParticipants().size() : 0;
                    String activity;
                    long ts = m.getLastMessageTimestampMillis();
                    if (ts <= 0L) {
                        activity = "no messages yet";
                    } else {
                        ZonedDateTime zdt = java.time.Instant.ofEpochMilli(ts)
                                .atZone(ZoneId.systemDefault());
                        DateTimeFormatter dtf = zdt.getYear() == ZonedDateTime.now().getYear()
                                ? DateTimeFormatter.ofPattern("LLL dd, HH:mm", java.util.Locale.ENGLISH)
                                : DateTimeFormatter.ofPattern("LLL dd yyyy, HH:mm", java.util.Locale.ENGLISH);
                        activity = "last active " + zdt.format(dtf);
                    }
                    setText("[ID: " + m.getConversationId() + "]  "
                            + m.getType() + "  •  "
                            + n + " participant(s)  •  "
                            + activity);
                }
                return this;
            }
        }

        // Read-only renderer for the right-pane message list. Holds a local Conversation
        // reference (set by ViewerPanel before populating the model) and resolves sender
        // names from that conversation's historicalParticipants — does NOT touch
        // controller.getCurrentConversation()/Id/UserInfo. No unread-bold logic; admins
        // have no read pointer in the viewed conversation.
        private final class AdminMessageCellRenderer extends JPanel implements ListCellRenderer<Message> {
            private final JLabel label = new JLabel();
            private Conversation viewedConv;

            AdminMessageCellRenderer() {
                setLayout(new BorderLayout());
                setOpaque(true);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(new Color(180, 180, 180), 1, true),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
            }

            void setConversation(Conversation conv) { this.viewedConv = conv; }

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends Message> list, Message msg, int index,
                    boolean isSelected, boolean cellHasFocus) {
                String senderName = null;
                if (viewedConv != null) {
                    for (UserInfo p : viewedConv.getHistoricalParticipants()) {
                        if (p.getUserId().equals(msg.getSenderId())) {
                            senderName = p.getName();
                            break;
                        }
                    }
                }
                if (senderName == null) senderName = "(former participant)";

                ZonedDateTime zdt = msg.getTimestamp().toInstant().atZone(ZoneId.systemDefault());
                DateTimeFormatter dtf = zdt.getYear() == ZonedDateTime.now().getYear()
                        ? DateTimeFormatter.ofPattern("LLL dd, HH:mm", java.util.Locale.ENGLISH)
                        : DateTimeFormatter.ofPattern("LLL dd yyyy, HH:mm", java.util.Locale.ENGLISH);
                String ts = zdt.format(dtf);
                String rawText = msg.getText() == null ? "" : msg.getText();
                String displayText = ts + " " + senderName + ": " + rawText;

                int listW = list.getWidth();
                int wrapPx = listW > 0 ? (int) (listW * 0.70) : 360;

                removeAll();
                setBackground(list.getBackground());
                label.setBackground(list.getBackground());
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                label.setText(escapeAndWrapHtml(displayText, wrapPx));
                add(label, BorderLayout.WEST);
                return this;
            }
        }

        // Right pane of the split: read-only message viewer for the selected conversation.
        // Starts on a placeholder; replaced by the message scroll list once a conversation
        // is loaded. No input field, no Send/Add/Leave buttons.
        final class ViewerPanel extends JPanel {
            JLabel participantsLabel = new JLabel(" ");
            DefaultListModel<Message> msgModel = new DefaultListModel<>();
            JList<Message> msgList = new JList<>(msgModel);
            JLabel placeholder = new JLabel("Select a conversation to preview", SwingConstants.CENTER);
            JScrollPane scroll;
            AdminMessageCellRenderer renderer = new AdminMessageCellRenderer();
            boolean populated = false;

            ViewerPanel() {
                setLayout(new BorderLayout());
                setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                participantsLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
                add(participantsLabel, BorderLayout.NORTH);

                msgList.setCellRenderer(renderer);
                scroll = new JScrollPane(msgList);
                scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                msgList.addComponentListener(new ComponentAdapter() {
                    @Override public void componentResized(ComponentEvent e) {
                        msgList.setFixedCellHeight(10);
                        msgList.setFixedCellHeight(-1);
                    }
                });

                add(placeholder, BorderLayout.CENTER);
            }

            void loadConversation(Conversation conv) {
                if (conv == null) return;
                renderer.setConversation(conv);
                ArrayList<UserInfo> active = conv.getParticipants();
                ArrayList<UserInfo> historical = conv.getHistoricalParticipants();
                StringBuilder names = new StringBuilder();
                ArrayList<UserInfo> sourceList = (!active.isEmpty()) ? historical : historical;
                for (int i = 0; i < sourceList.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(sourceList.get(i).getName());
                }
                String prefix = active.isEmpty() ? "Former participants: " : "Participants: ";
                participantsLabel.setText(prefix + (sourceList.isEmpty() ? "(none)" : names.toString()));

                msgModel.clear();
                ArrayList<Message> messages = conv.getMessages();
                if (messages.isEmpty()) {
                    placeholder.setText("No messages yet");
                    if (populated) {
                        remove(scroll);
                        add(placeholder, BorderLayout.CENTER);
                        populated = false;
                    }
                } else {
                    for (Message m : messages) msgModel.addElement(m);
                    if (!populated) {
                        remove(placeholder);
                        add(scroll, BorderLayout.CENTER);
                        populated = true;
                    }
                    SwingUtilities.invokeLater(() -> {
                        msgList.setFixedCellHeight(10);
                        msgList.setFixedCellHeight(-1);
                        int last = msgModel.getSize() - 1;
                        if (last >= 0) msgList.ensureIndexIsVisible(last);
                    });
                }
                revalidate();
                repaint();
            }
        }

        AdminConversationSearchWindow() {
            searchField = makePlaceholderField("Search conversations...", 15);
            closeButton = new JButton("Close");

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // LEFT pane — search field + conversation list
            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.add(searchField, BorderLayout.NORTH);
            // #126: install the custom cell renderer so each row shows ID, type, count, recency.
            list.setCellRenderer(new AdminConversationCellRenderer());
            leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);

            // RIGHT pane — read-only message viewer
            viewerPanel = new ViewerPanel();

            // Split-pane combines list + viewer
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, viewerPanel);
            split.setDividerLocation(320);
            split.setResizeWeight(0.0);
            add(split, BorderLayout.CENTER);

            // Close at the bottom right
            JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPane.add(closeButton);
            add(buttonPane, BorderLayout.SOUTH);

            // #55/#124: seed from the controller's cache. If the ADMIN_CONVERSATION_RESULT
            // response arrived before this window was constructed (race on fast loopback),
            // the data is already in the cache and we render it immediately. The async
            // updateAdminConversationSearchModel callback path stays for late responses.
            for (ConversationMetadata m : controller.getCurrentAdminConversationSearch()) {
                model.addElement(m);
            }

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

            // Selecting a row pulls the full conversation silently — no Join button.
            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                ConversationMetadata sel = list.getSelectedValue();
                if (sel != null) {
                    controller.adminViewConversation(sel.getConversationId());
                }
            });

            // Close button just disposes the dialog; existing windowClosed handler does cleanup.
            closeButton.addActionListener(e -> {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window != null) window.dispose();
            });
        }

        public DefaultListModel<ConversationMetadata> getAdminConversationSearch() {
        	return model;
        }

        /** #193 entry point — called from ClientUI.showAdminConversationView. */
        public void loadConversation(Conversation conv) {
            viewerPanel.loadConversation(conv);
        }

    }
}
