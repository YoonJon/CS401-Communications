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
import java.util.List;

public class ClientUI {

    private final ClientController controller;
    private JFrame frame;
    private ScreenCards cards;
    private final IdleWatcher idleWatcher;
    private volatile ConversationView activeConversationView;

    private int unreadWhileAway = 0;
    private volatile boolean suppressActivationReset = false;
    private static final String BASE_TITLE = "Communication Application";

    private static final Dimension MAIN_FRAME_MIN_SIZE       = new Dimension(900, 600);
    private static final Dimension SELECT_USER_DIALOG_SIZE   = new Dimension(300, 400);
    private static final Dimension ADMIN_SEARCH_DIALOG_SIZE  = new Dimension(900, 600);
    private static final double    BUBBLE_WRAP_FRACTION      = 0.70;

    private static final class Constants {
        private Constants() {}
        static final String NIMBUS_LAF_FQCN = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

        static final String CARD_LOGIN    = "login";
        static final String CARD_REGISTER = "register";
        static final String CARD_MAIN     = "main";

        static final String DLG_SELECT_USER  = "Select User";
        static final String DLG_ADMIN_SEARCH = "Searching Conversations";

        static final String DM_PREFIX                     = "DM: ";
        static final String GROUP_PREFIX                  = "Group: ";
        static final String EMPTY_PARTICIPANT_PLACEHOLDER = "(empty)";
        static final int    UNREAD_BADGE_CAP              = 99;
        static final String UNREAD_BADGE_CAP_TEXT         = "99+";

        static final int MIN_HTML_WRAP_PX           = 80;
        static final int MIN_BUBBLE_WIDTH_PX        = 120;
        static final int BUBBLE_INSET_PX            = 28;
        static final int SCROLL_BOTTOM_TOLERANCE_PX = 4;
        static final int ADMIN_SPLIT_DIVIDER_PX     = 320;
        static final int APP_ICON_SIZE_PX           = 32;
        static final Dimension DIVIDER_PANEL_DIM    = new Dimension(10, 22);
    }

    private static final class Theme {
        static final Color SURFACE_BG          = new Color(43, 43, 43);
        static final Color FIELD_BG            = new Color(60, 63, 65);
        static final Color TEXT_PRIMARY        = new Color(220, 220, 220);
        static final Color TEXT_ON_OWN_BUBBLE  = new Color(235, 235, 235);
        static final Color WHITE               = new Color(255, 255, 255);
        static final Color NIMBUS_FOCUS         = new Color(115, 164, 209);
        static final Color NIMBUS_GREEN         = new Color(176, 179, 50);
        static final Color NIMBUS_INFO_BLUE     = new Color(66, 139, 221);
        static final Color NIMBUS_ORANGE        = new Color(191, 98, 4);
        static final Color NIMBUS_RED           = new Color(169, 46, 34);
        static final Color NIMBUS_ALERT_YELLOW  = new Color(248, 187, 0);
        static final Color NIMBUS_DISABLED_TEXT = new Color(128, 128, 128);
        static final Color NIMBUS_SELECTION_BG  = new Color(75, 110, 175);
        static final Color OWN_BUBBLE_BG = new Color(66, 95, 120);
        static final Color BUBBLE_BORDER = new Color(180, 180, 180);
        static final Color BANNER_FG     = new Color(0, 100, 180);
        static final Color APP_ICON_BG   = new Color(33, 150, 243);
    }

    public ClientUI(ClientController controller) {
        this.controller = controller;
        this.idleWatcher = new IdleWatcher();

        SwingUtilities.invokeLater(() -> {
            applyNimbusLookAndFeel();
            applyDarkModeTheme();
            initFrameAndCards();
            installWindowListener();
            installKeyboardShortcuts();
            idleWatcher.installAwtListener();
            idleWatcher.start();
        });
    }

    private void applyNimbusLookAndFeel() {
        try {
            UIManager.setLookAndFeel(Constants.NIMBUS_LAF_FQCN);
        } catch (Exception ignored) {
            // If Nimbus is unavailable, keep current LAF and continue.
        }
    }

    private void applyDarkModeTheme() {
        UIManager.put("control", Theme.SURFACE_BG);
        UIManager.put("info", Theme.FIELD_BG);
        UIManager.put("nimbusBase", Theme.FIELD_BG);
        UIManager.put("nimbusAlertYellow", Theme.NIMBUS_ALERT_YELLOW);
        UIManager.put("nimbusDisabledText", Theme.NIMBUS_DISABLED_TEXT);
        UIManager.put("nimbusFocus", Theme.NIMBUS_FOCUS);
        UIManager.put("nimbusGreen", Theme.NIMBUS_GREEN);
        UIManager.put("nimbusInfoBlue", Theme.NIMBUS_INFO_BLUE);
        UIManager.put("nimbusLightBackground", Theme.SURFACE_BG);
        UIManager.put("nimbusOrange", Theme.NIMBUS_ORANGE);
        UIManager.put("nimbusRed", Theme.NIMBUS_RED);
        UIManager.put("nimbusSelectedText", Theme.WHITE);
        UIManager.put("nimbusSelectionBackground", Theme.NIMBUS_SELECTION_BG);
        UIManager.put("text", Theme.TEXT_PRIMARY);
        UIManager.put("Panel.background", Theme.SURFACE_BG);
        UIManager.put("Label.foreground", Theme.TEXT_PRIMARY);
        UIManager.put("TextField.background", Theme.FIELD_BG);
        UIManager.put("TextField.foreground", Theme.TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", Theme.TEXT_PRIMARY);
        UIManager.put("TextArea.background", Theme.FIELD_BG);
        UIManager.put("TextArea.foreground", Theme.TEXT_PRIMARY);
        UIManager.put("TextArea.caretForeground", Theme.TEXT_PRIMARY);
        UIManager.put("Button.background", Theme.FIELD_BG);
        UIManager.put("Button.foreground", Theme.TEXT_PRIMARY);
        UIManager.put("List.background", Theme.SURFACE_BG);
        UIManager.put("List.foreground", Theme.TEXT_PRIMARY);
        UIManager.put("List.selectionBackground", Theme.NIMBUS_SELECTION_BG);
        UIManager.put("List.selectionForeground", Theme.WHITE);
        UIManager.put("OptionPane.background", Theme.SURFACE_BG);
        UIManager.put("OptionPane.messageForeground", Theme.TEXT_PRIMARY);
    }

    private void initFrameAndCards() {
        frame = new JFrame();
        frame.setTitle(BASE_TITLE);
        frame.setIconImage(createAppIcon());
        frame.setMinimumSize(MAIN_FRAME_MIN_SIZE);
        cards = new ScreenCards();
        activeConversationView = cards.main.conversationView;
        frame.add(cards);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void installWindowListener() {
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowActivated(WindowEvent e) {
                // drain reads deferred while window inactive
                controller.setWindowActive(true);
                controller.replayReadAdvanceIfNeeded();
                if (suppressActivationReset) {
                    suppressActivationReset = false;
                    return;
                }
                unreadWhileAway = 0;
                frame.setTitle(BASE_TITLE);
            }
            @Override public void windowDeactivated(WindowEvent e) {
                controller.setWindowActive(false);
            }
            @Override public void windowClosing(WindowEvent e) {
                idleWatcher.stop();
                idleWatcher.uninstallAwtListener();
            }
        });
    }

    private void installKeyboardShortcuts() {
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
    }

    /** Tracks UI idleness via a global AWT key/mouse listener and ends the
     *  session if the user has been inactive past {@link #LOGOUT_AFTER_MS}. */
    private final class IdleWatcher {
        private static final int POLL_MS = 5_000;
        private static final long LOGOUT_AFTER_MS = 30L * 60L * 1000L;

        private volatile long lastActivityMillis = System.currentTimeMillis();
        private final Timer pollTimer;
        private AWTEventListener awtActivityListener;

        IdleWatcher() {
            pollTimer = new Timer(POLL_MS, e -> onTick());
            pollTimer.setRepeats(true);
        }

        void start() { pollTimer.start(); }

        void stop() { pollTimer.stop(); }

        void installAwtListener() {
            awtActivityListener = e -> lastActivityMillis = System.currentTimeMillis();
            Toolkit.getDefaultToolkit().addAWTEventListener(
                awtActivityListener,
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
            );
        }

        void uninstallAwtListener() {
            if (awtActivityListener != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(awtActivityListener);
                awtActivityListener = null;
            }
        }

        private void onTick() {
            long idleMs = System.currentTimeMillis() - lastActivityMillis;
            if (controller.isLoggedIn() && idleMs >= LOGOUT_AFTER_MS) {
                controller.logout();
            }
        }
    }

    public void showLoginView() {
        showLoginView(null);
    }

    /** Pre-fills login id before showing the screen (e.g. with the just-registered loginName). */
    public void showLoginView(String prefilledLoginId) {
        SwingUtilities.invokeLater(() -> {
            DirectoryView dv = cards.main.directoryView;
            ConversationView cv = cards.main.conversationView;
            disposeIfVisible(dv.createDialog);
            disposeIfVisible(dv.adminDialog);
            disposeIfVisible(cv.addDialog);

            clearLoginAndRegisterFields();
            if (prefilledLoginId != null && !prefilledLoginId.isEmpty()) {
                cards.login.loginIdField.setText(prefilledLoginId);
            }
            cards.main.directoryView.adminButton.setVisible(false);
            cards.main.directoryView.revalidate();
            cards.main.directoryView.repaint();
            cards.layout.show(cards, Constants.CARD_LOGIN);
        });
    }
    public void showRegisterView() {
        SwingUtilities.invokeLater(() -> {
            clearLoginAndRegisterFields();
            cards.layout.show(cards, Constants.CARD_REGISTER);
        });
    }

    public void showMainView() {
        SwingUtilities.invokeLater(() -> {
            clearLoginAndRegisterFields();
            // Same-JVM relogin: stale messages would render as "(former participant)" with no body.
            cards.main.conversationView.clearConversation();
            cards.main.conversationListView.list.clearSelection();
            UserInfo currentUser = controller.getCurrentUserInfo();
            boolean isAdmin = currentUser != null && currentUser.getUserType() == UserType.ADMIN;
            cards.main.directoryView.adminButton.setVisible(isAdmin);
            String myId = (currentUser != null) ? currentUser.getUserId() : null;
            cards.main.directoryView.listModel.clear();
            for (UserInfo userInfo : controller.getFilteredDirectory("")) {
                if (myId == null || !userInfo.getUserId().equals(myId)) {
                    cards.main.directoryView.listModel.addElement(userInfo);
                }
            }
            cards.main.conversationListView.listModel.clear();
            for (Conversation c : controller.getFilteredConversationList("")) {
                cards.main.conversationListView.listModel.addElement(c);
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
            cards.layout.show(cards, Constants.CARD_MAIN);
            frame.pack();
            frame.setLocationRelativeTo(null);
        });
    }

    /** Wraps escaped HTML in a width-bounded body so JLabel wraps the same way in both renderers. */
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
        int safe = Math.max(Constants.MIN_HTML_WRAP_PX, maxPx);
        return "<html><body style='width:" + safe + "px'>" + sb + "</body></html>";
    }

    /** Outer JPanel wraps field so layout swaps survive Show/Hide toggle. */
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
        cards.login.loginIdField.setText("");
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
            // Always clear the password on login failure.
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

    /** Shown when the server is unreachable so the user isn't stuck staring at a hung UI. */
    public void showNetworkError() {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                frame,
                "Cannot reach the server. Please check that the server is running and try again.",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE));
    }

    /** Drives the visible "submit in flight" state for both login and register. */
    public void setLoginInFlight(boolean inFlight) {
        SwingUtilities.invokeLater(() -> {
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
            cards.main.directoryView.listModel.clear();
            for (UserInfo user : users) {
                cards.main.directoryView.listModel.addElement(user);
            }
        });
    }

    public void updateConversationListModel(ArrayList<Conversation> conversations) {
        SwingUtilities.invokeLater(() -> {
            ConversationListView clv = cards.main.conversationListView;
            clv.withSuppressedSelection(() -> {
                Conversation selected = clv.list.getSelectedValue();
                Long selectedId = (selected != null) ? selected.getConversationId() : null;

                // Sort by highest latest sequence number descending (most recent first),
                // which also keeps offline-received updates ordered correctly on initial paint.
                conversations.sort((a, b) -> {
                    ArrayList<Message> aMsgs = a.getMessages();
                    ArrayList<Message> bMsgs = b.getMessages();
                    long aSeq = aMsgs.isEmpty()
                            ? a.getConversationId()
                            : aMsgs.get(aMsgs.size() - 1).getSequenceNumber();
                    long bSeq = bMsgs.isEmpty()
                            ? b.getConversationId()
                            : bMsgs.get(bMsgs.size() - 1).getSequenceNumber();
                    return Long.compare(bSeq, aSeq);
                });

                cards.main.conversationListView.listModel.clear();
                for (Conversation conversation : conversations) {
                    cards.main.conversationListView.listModel.addElement(conversation);
                }

                // Restore selection after rebuild.
                if (selectedId != null) {
                    for (int i = 0; i < cards.main.conversationListView.listModel.getSize(); i++) {
                        if (cards.main.conversationListView.listModel.getElementAt(i).getConversationId() == selectedId.longValue()) {
                            clv.list.setSelectedIndex(i);
                            break;
                        }
                    }
                }
            });
        });
    }

    public void updateMessageListModel(Conversation conversation) {
        SwingUtilities.invokeLater(() -> {
            if (conversation == null) {
                cards.main.conversationView.clearConversation();
                return;
            }

            // Route through ConversationView so auto-switch flows (first conversation,
            // leave-to-next conversation, etc.) also enable input and action buttons.
            cards.main.conversationView.setListModel(conversation);
            // Auto-switch should hand focus to message input, not leave it on a stale
            // directory selection.
            cards.main.directoryView.clearSelecting();
            cards.main.conversationView.focusInput();

            ArrayList<Message> msgs = conversation.getMessages();
            if (!msgs.isEmpty()) {
                markConversationRead(conversation, msgs.get(msgs.size() - 1).getSequenceNumber());
            }
        });
    }

    /** Marks {@code conversation} read up to {@code sequenceNumber} both locally and on the server. */
    private void markConversationRead(Conversation conversation, long sequenceNumber) {
        UserInfo me = controller.getCurrentUserInfo();
        if (me != null) {
            me.setLastRead(conversation.getConversationId(), sequenceNumber);
        }
        controller.updateReadMessages(conversation.getConversationId(), sequenceNumber);
    }

    public void appendMessageToConversationView(Message message) {
        SwingUtilities.invokeLater(() -> {
            ConversationView cv = cards.main.conversationView;
            // "Actively viewing" requires BOTH window focused AND parked at bottom; userIsAtBottom
            // defaults to true, so checking it alone would suppress the divider in the alt-tab case.
            UserInfo me = controller.getCurrentUserInfo();
            boolean fromOther = me != null && !message.getSenderId().equals(me.getUserId());
            boolean userActivelyViewing = frame.isActive() && cv.userIsAtBottom;
            if (fromOther && !userActivelyViewing && !cv.modelHasDivider) {
                cards.main.conversationView.conversationMessageListModel.addElement(ConversationView.NewMessagesDivider.INSTANCE);
                cv.modelHasDivider = true;
            }
            cards.main.conversationView.conversationMessageListModel.addElement(message);
            // Auto-scroll to newest only when the user is parked at the bottom. Otherwise
            // preserve their scroll position so they can read history.
            if (cv.userIsAtBottom && !cv.scrollPending) {
                cv.scrollPending = true;
                SwingUtilities.invokeLater(() -> {
                    cv.scrollPending = false;
                    int last = cv.list.getModel().getSize() - 1;
                    if (last >= 0) cv.list.ensureIndexIsVisible(last);
                });
            }
            // deferred replay: window was inactive when message arrived
            if (frame.isActive() && cv.userIsAtBottom) {
                cv.markDisplayedReadUpTo(message.getSequenceNumber());
            }
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

    /** Pass-through used by {@link ClientController#replayReadAdvanceIfNeeded()} to
     *  re-sync the open conversation's per-message snapshot after a deferred replay. */
    public void markDisplayedReadUpTo(long sequenceNumber) {
        SwingUtilities.invokeLater(() ->
                cards.main.conversationView.markDisplayedReadUpTo(sequenceNumber));
    }

    /** Called from network reader thread; activeConversationView and its userIsAtBottom field are volatile. */
    public boolean userIsViewingBottom() {
        ConversationView cv = activeConversationView;
        return cv == null || cv.userIsAtBottom;
    }

    public void repaintConversationList() {
        SwingUtilities.invokeLater(() -> cards.main.conversationListView.list.repaint());
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

    /** Late responses dropped after dialog close. */
    public void showAdminConversationView(Conversation conv) {
        if (conv == null) return;
        SwingUtilities.invokeLater(() -> {
            DirectoryView dv = cards.main.directoryView;
            if (dv.adminConversationSearchWindow == null) return;
            dv.adminConversationSearchWindow.loadConversation(conv);
        });
    }

    private void bindEscapeToDispose(JDialog dialog) {
        JRootPane rp = dialog.getRootPane();
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "closeDialog");
        rp.getActionMap().put("closeDialog", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent evt) { dialog.dispose(); }
        });
    }

    private static DocumentListener filteringListener(Runnable onChange) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onChange.run(); }
            @Override public void removeUpdate(DocumentEvent e) { onChange.run(); }
            @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
        };
    }

    private JDialog buildModalDialog(String title, JComponent content, Dimension size) {
        JDialog dialog = new JDialog(frame, title, false);
        dialog.add(content);
        dialog.setSize(size);
        dialog.setLocationRelativeTo(frame);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        bindEscapeToDispose(dialog);
        return dialog;
    }

    private static ArrayList<UserInfo> excludeSelf(List<UserInfo> participants, String myUserId) {
        ArrayList<UserInfo> others = new ArrayList<>();
        for (UserInfo p : participants) {
            if (myUserId == null || !p.getUserId().equals(myUserId)) others.add(p);
        }
        return others;
    }

    private static String buildParticipantSummary(List<UserInfo> others, int maxNames) {
        if (others.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(maxNames, others.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(others.get(i).getName());
        }
        if (others.size() > maxNames) {
            sb.append(" +").append(others.size() - maxNames).append(" more");
        }
        return sb.toString();
    }

    private static ArrayList<UserInfo> displayParticipants(ArrayList<UserInfo> active, ArrayList<UserInfo> historical) {
        return !active.isEmpty() ? active : historical;
    }

    private Image createAppIcon() {
        int size = Constants.APP_ICON_SIZE_PX;
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.APP_ICON_BG);
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.fillRoundRect(6, 7, 20, 14, 6, 6);
        int[] xs = {10, 10, 16};
        int[] ys = {19, 26, 20};
        g.fillPolygon(xs, ys, 3);
        g.dispose();
        return img;
    }

    private static final DateTimeFormatter FMT_SAME_YEAR =
            DateTimeFormatter.ofPattern("LLL dd, HH:mm", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter FMT_OTHER_YEAR =
            DateTimeFormatter.ofPattern("LLL dd yyyy, HH:mm", java.util.Locale.ENGLISH);

    /** Includes year only when not current year. */
    private static String formatMessageTimestamp(java.util.Date when) {
        ZonedDateTime zdt = when.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter dtf = zdt.getYear() == ZonedDateTime.now().getYear() ? FMT_SAME_YEAR : FMT_OTHER_YEAR;
        return zdt.format(dtf);
    }

    /** Preserves removed-user names for historical render. */
    private static String resolveHistoricalSenderName(Conversation conv, String senderId) {
        if (conv == null) return null;
        for (UserInfo p : conv.getHistoricalParticipants()) {
            if (p.getUserId().equals(senderId)) return p.getName();
        }
        return null;
    }

    private static boolean isDialogShowing(JDialog d) {
        return d != null && d.isVisible();
    }

    private static void disposeIfVisible(JDialog d) {
        if (isDialogShowing(d)) d.dispose();
    }

    /** If {@code d} is already on screen, raise it and return true so the caller
     *  can short-circuit instead of opening a second instance. */
    private static boolean focusExistingDialog(JDialog d) {
        if (!isDialogShowing(d)) return false;
        d.toFront();
        d.requestFocus();
        return true;
    }

    /** Display-ready snapshot of a {@link Message} for the cell renderers.
     *  Header text and sender resolution are computed once instead of being
     *  recomputed inside each renderer's getListCellRendererComponent. */
    private static final class MessageRow {
        final String headerText;
        final String body;
        final String senderName;
        final String timestamp;
        final boolean isOwn;

        private MessageRow(String headerText, String body, String senderName,
                           String timestamp, boolean isOwn) {
            this.headerText = headerText;
            this.body = body;
            this.senderName = senderName;
            this.timestamp = timestamp;
            this.isOwn = isOwn;
        }

        /** Built for the live conversation view: own messages elide the sender
         *  name in the header so the bubble side ("EAST") carries the identity. */
        static MessageRow forConversation(Message msg, Conversation conv, UserInfo me) {
            String ts = formatMessageTimestamp(msg.getTimestamp());
            String body = msg.getText() == null ? "" : msg.getText();
            boolean isOwn = me != null && me.getUserId() != null
                    && me.getUserId().equals(msg.getSenderId());
            String senderName;
            if (isOwn) {
                senderName = me.getName();
            } else {
                String resolved = resolveHistoricalSenderName(conv, msg.getSenderId());
                senderName = resolved != null ? resolved : "(former participant)";
            }
            String header = isOwn ? ts : (ts + " " + senderName + ":");
            return new MessageRow(header, body, senderName, ts, isOwn);
        }

        /** Built for the admin viewer: no "me" concept, sender name always shown. */
        static MessageRow forAdmin(Message msg, Conversation viewedConv) {
            String ts = formatMessageTimestamp(msg.getTimestamp());
            String body = msg.getText() == null ? "" : msg.getText();
            String resolved = resolveHistoricalSenderName(viewedConv, msg.getSenderId());
            String senderName = resolved != null ? resolved : "(former participant)";
            return new MessageRow(ts + " " + senderName + ":", body, senderName, ts, false);
        }
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

    class ScreenCards extends JPanel {
        private CardLayout layout;
        private MainView main;
        private LoginView login;
        private RegisterView register;

        ScreenCards() {
            layout = new CardLayout();
            setLayout(layout);
            login = new LoginView();
            register = new RegisterView();
            main = new MainView();
            add(login, Constants.CARD_LOGIN);
            add(register, Constants.CARD_REGISTER);
            add(main, Constants.CARD_MAIN);
        }
    }

    class RegisterView extends JPanel {
        JTextField userId;
        JTextField loginName;
        JPasswordField password;
        JPasswordField passwordAgain;
        JTextField name;
        JButton createButton;
        JButton backButton;

        RegisterView() {
            buildLayout();
            wireRegisterButton();
            wireBackButton();
        }

        private void buildLayout() {
            setLayout(new BorderLayout());

            JPanel inputPanel = new JPanel(new GridBagLayout());
            userId = new JTextField(15);
            name = new JTextField(15);
            loginName = new JTextField(15);
            password = new JPasswordField(15);
            passwordAgain = new JPasswordField(15);
            createButton = new JButton("Create Account");
            backButton = new JButton("Back");

            userId.setToolTipText("Your organization-assigned employee ID. Must be pre-authorized.");
            loginName.setToolTipText("Choose a username for logging in. Letters, numbers, hyphens, or underscores only.");

            JLabel heading = new JLabel("Register / Create Account", SwingConstants.CENTER);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize() + 6f));
            heading.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
            add(heading, BorderLayout.NORTH);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttonPanel.add(createButton);
            buttonPanel.add(backButton);

            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;

            gridConst.gridx = 0; gridConst.gridy = 0;
            inputPanel.add(new JLabel("Employee ID"), gridConst);
            gridConst.gridx = 1; gridConst.gridwidth = 2;
            inputPanel.add(userId, gridConst);

            gridConst.gridx = 0; gridConst.gridy = 1; gridConst.gridwidth = 1;
            inputPanel.add(new JLabel("User Name"), gridConst);
            gridConst.gridx = 1;
            inputPanel.add(name, gridConst);

            gridConst.gridx = 0; gridConst.gridy = 2;
            inputPanel.add(new JLabel("Login ID"), gridConst);
            gridConst.gridx = 1;
            inputPanel.add(loginName, gridConst);

            gridConst.gridx = 0; gridConst.gridy = 3;
            inputPanel.add(new JLabel("Password"), gridConst);
            gridConst.gridx = 1;
            inputPanel.add(passwordFieldWithToggle(password), gridConst);

            gridConst.gridx = 0; gridConst.gridy = 4;
            inputPanel.add(new JLabel("Confirm Password"), gridConst);
            gridConst.gridx = 1;
            inputPanel.add(passwordFieldWithToggle(passwordAgain), gridConst);

            gridConst.gridx = 1; gridConst.gridy = 5;
            inputPanel.add(buttonPanel, gridConst);

            add(inputPanel, BorderLayout.CENTER);
        }

        private void wireRegisterButton() {
            // Extract submit lambda so Enter on any field also fires it.
            ActionListener createAction = e -> {
                char[] pwd1 = password.getPassword();
                char[] pwd2 = passwordAgain.getPassword();
                if (java.util.Arrays.equals(pwd1, pwd2)) {
                    controller.register(userId.getText(), name.getText(), loginName.getText(), password.getPassword());
                } else {
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
        }

        private void wireBackButton() {
            backButton.addActionListener(e -> showLoginView());
        }
    }

    class LoginView extends JPanel {
        JTextField loginIdField;
        JPasswordField passwordField;
        JButton loginButton;
        JButton createButton;

        LoginView() {
            setLayout(new BorderLayout());

            loginIdField = new JTextField(15);
            passwordField = new JPasswordField(15);
            loginButton = new JButton("Login");
            createButton = new JButton("Create Account");

            loginIdField.setToolTipText("Your chosen login username (not your Employee ID).");

            JLabel heading = new JLabel("Login", SwingConstants.CENTER);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize() + 6f));
            heading.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
            add(heading, BorderLayout.NORTH);

            JPanel inputPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;

            gridConst.gridx = 0;
            gridConst.gridy = 0;
            inputPanel.add(new JLabel("Login ID"), gridConst);
            gridConst.gridx = 1;
            inputPanel.add(loginIdField, gridConst);
            gridConst.gridx = 0;
            gridConst.gridy = 1;
            inputPanel.add(new JLabel("Password"), gridConst);
            gridConst.gridx = 1;
            inputPanel.add(passwordFieldWithToggle(passwordField), gridConst);
            gridConst.gridx = 2;
            gridConst.gridy = 0;
            gridConst.gridheight = 2;
            inputPanel.add(loginButton, gridConst);

            gridConst.gridx = 1;
            gridConst.gridy = 2;
            gridConst.insets = new Insets(20, 5, 5, 5);

            JPanel createBtnPane = new JPanel();
            createBtnPane.setLayout(new FlowLayout(FlowLayout.CENTER));
            createBtnPane.add(createButton);
            inputPanel.add(createBtnPane, gridConst);

            add(inputPanel, BorderLayout.CENTER);

            ActionListener loginAction = e -> controller.login(loginIdField.getText(), passwordField.getPassword());
            loginButton.addActionListener(loginAction);
            loginIdField.addActionListener(loginAction);
            passwordField.addActionListener(loginAction);

            createButton.addActionListener(e -> showRegisterView());
        }
    }

    class MainView extends JPanel {
        private static final int MIN_CONVERSATION_LIST_WIDTH = 240;
        ConversationView conversationView;
        DirectoryView directoryView;
        ConversationListView conversationListView;

        MainView() {
            setLayout(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            conversationView = new ConversationView();
            directoryView = new DirectoryView();
            conversationListView = new ConversationListView();
            conversationListView.setMinimumSize(new Dimension(MIN_CONVERSATION_LIST_WIDTH, 0));

            gridConst.gridy = 0;
            gridConst.fill = GridBagConstraints.BOTH;
            gridConst.weighty = 1.0;

            gridConst.gridx = 0;
            gridConst.weightx = 0.2;
            add(directoryView, gridConst);

            gridConst.gridx = 1;
            gridConst.weightx = 0.6;
            add(conversationView, gridConst);

            gridConst.gridx = 2;
            gridConst.weightx = 0.2;
            add(conversationListView, gridConst);

        }

    }

    class ConversationView extends JPanel {
        private JLabel participantsLabel;
        final DefaultListModel<Object> conversationMessageListModel = new DefaultListModel<>();
        final JList<Object> list = new JList<>(conversationMessageListModel);
        private JScrollPane messageScrollPane;
        // Snapshot of read state at the time this conversation view was opened/refreshed.
        // Renderer uses this instead of live lastRead updates so unread markers remain meaningful.
        private long displayedLastReadSeq = 0L;
        // Tracks whether the vertical scrollbar is at (or within 4px of) the bottom.
        // Volatile because the network reader thread reads it via userIsViewingBottom() while the
        // EDT mutates it in the AdjustmentListener.
        private volatile boolean userIsAtBottom = true;
        // Tracks whether NewMessagesDivider.INSTANCE is currently in the list model.
        // Set true on insert by setListModel or appendMessageToConversationView; cleared on
        // model clear (setListModel/clearConversation). EDT-only access.
        private boolean modelHasDivider = false;
        private boolean scrollPending = false;
        private JButton addButton;
        private JButton leaveButton;
        private JTextField messageInputField;
        private JButton sendButton;
        private JDialog addDialog;
        private SelectUserWindow addUserWindow;

        private final JLabel placeholderLabel = new JLabel("Select a conversation to start chatting", SwingConstants.CENTER);

        // Sentinel inserted into the message-list model to mark the boundary
        // between messages already read at conversation-open time and unread messages
        // that arrived since. Renders as a single horizontal "─── New messages ───" cue.
        private static final class NewMessagesDivider {
            static final NewMessagesDivider INSTANCE = new NewMessagesDivider();
            private NewMessagesDivider() {}
        }

        private final class MessageCellRenderer extends JPanel implements ListCellRenderer<Object> {
            private final JPanel bubble = new JPanel(new BorderLayout(0, 2));
            private final JTextArea headerArea = new JTextArea();
            private final JTextArea bodyArea = new JTextArea();
            // A separate component returned for NewMessagesDivider sentinels.
            private final JPanel dividerPanel = new JPanel(new BorderLayout(6, 0));
            private final JLabel dividerLabel = new JLabel("New messages", SwingConstants.CENTER);
            private final java.util.HashMap<Long, MessageRow> rowCache = new java.util.HashMap<>();
            private Conversation cachedFor;

            MessageCellRenderer() {
                setLayout(new BorderLayout());
                setOpaque(true);
                bubble.setOpaque(true);
                bubble.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(Theme.BUBBLE_BORDER, 1, true),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
                headerArea.setOpaque(false);
                headerArea.setLineWrap(true);
                headerArea.setWrapStyleWord(true);
                headerArea.setEditable(false);
                headerArea.setFocusable(false);
                headerArea.setBorder(null);
                bodyArea.setOpaque(false);
                bodyArea.setLineWrap(true);
                // Character wrapping avoids long-token overflow when no whitespace exists.
                bodyArea.setWrapStyleWord(false);
                bodyArea.setEditable(false);
                bodyArea.setFocusable(false);
                bodyArea.setBorder(null);
                bubble.add(headerArea, BorderLayout.NORTH);
                bubble.add(bodyArea, BorderLayout.CENTER);

                Color alert = UIManager.getColor("nimbusAlertYellow");
                if (alert == null) alert = Theme.NIMBUS_ALERT_YELLOW;
                dividerPanel.setOpaque(true);
                dividerPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                dividerLabel.setForeground(alert);
                dividerLabel.setFont(dividerLabel.getFont().deriveFont(Font.BOLD));
                JSeparator left = new JSeparator(SwingConstants.HORIZONTAL);
                JSeparator right = new JSeparator(SwingConstants.HORIZONTAL);
                left.setForeground(alert);
                right.setForeground(alert);
                dividerPanel.add(left, BorderLayout.WEST);
                dividerPanel.add(dividerLabel, BorderLayout.CENTER);
                dividerPanel.add(right, BorderLayout.EAST);
                dividerPanel.setPreferredSize(new Dimension(Constants.DIVIDER_PANEL_DIM));
                dividerPanel.getAccessibleContext().setAccessibleName("New messages divider");
            }

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends Object> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                if (value instanceof NewMessagesDivider) {
                    dividerPanel.setBackground(list.getBackground());
                    dividerLabel.setOpaque(false);
                    return dividerPanel;
                }

                Message msg = (Message) value;
                Conversation curr = controller.getCurrentConversation();
                if (curr != cachedFor) {
                    rowCache.clear();
                    cachedFor = curr;
                }
                MessageRow row = rowCache.get(msg.getSequenceNumber());
                if (row == null) {
                    row = MessageRow.forConversation(msg, curr, controller.getCurrentUserInfo());
                    rowCache.put(msg.getSequenceNumber(), row);
                }

                removeAll();
                setBackground(list.getBackground());
                // Compute wrap width from the viewport (not just list width) so bubbles
                // adapt correctly when the conversation pane is resized.
                configureBubble(row.isOwn, row.headerText, row.body,
                        computeWrapWidthPx(list), list.getFont(), list.getBackground());

                // Accessible name for screen readers. Truncate long bodies so the
                // announcement stays manageable. Suffix "(unread)" when the message is past
                // the per-cell read snapshot so a sweep through the list announces boundaries.
                boolean isUnreadMsg = msg.getSequenceNumber() > displayedLastReadSeq;
                String suffix = isUnreadMsg ? " (unread)" : "";
                String shortBody = row.body.length() > 80 ? row.body.substring(0, 77) + "…" : row.body;
                getAccessibleContext().setAccessibleName(
                        row.senderName + " at " + row.timestamp + ", " + shortBody + suffix);

                return this;
            }

            private void configureBubble(boolean isOwn, String header, String rawText,
                                         int wrapPx, Font listFont, Color listBg) {
                Color bg = isOwn ? Theme.OWN_BUBBLE_BG : listBg;
                Color fg = isOwn ? Theme.TEXT_ON_OWN_BUBBLE : Theme.TEXT_PRIMARY;
                bubble.setBackground(bg);
                headerArea.setBackground(bg);
                bodyArea.setBackground(bg);
                headerArea.setForeground(fg);
                bodyArea.setForeground(fg);
                headerArea.setText(header);
                headerArea.setFont(listFont.deriveFont(Font.PLAIN));
                bodyArea.setFont(listFont.deriveFont(Font.PLAIN));
                bodyArea.setText(rawText);
                installBubbleWidth(wrapPx);
                add(bubble, isOwn ? BorderLayout.EAST : BorderLayout.WEST);
            }

            private void installBubbleWidth(int wrapPx) {
                int safe = Math.max(Constants.MIN_BUBBLE_WIDTH_PX, wrapPx);
                headerArea.setSize(new Dimension(safe, Short.MAX_VALUE));
                Dimension headerPref = headerArea.getPreferredSize();
                bodyArea.setSize(new Dimension(safe, Short.MAX_VALUE));
                Dimension bodyPref = bodyArea.getPreferredSize();
                Insets insets = bubble.getInsets();
                int bubbleHeight = insets.top
                        + headerPref.height
                        + 2
                        + bodyPref.height
                        + insets.bottom
                        + 2; // extra descent padding to avoid clipping single-line text
                bubble.setPreferredSize(new Dimension(safe, bubbleHeight));
            }

            private static int computeWrapWidthPx(JList<?> list) {
                int baseWidth = list.getVisibleRect().width;
                if (baseWidth <= 0) {
                    baseWidth = list.getWidth();
                }
                Container parent = list.getParent();
                if (parent instanceof JViewport) {
                    JViewport viewport = (JViewport) parent;
                    baseWidth = viewport.getExtentSize().width;
                    Insets vi = viewport.getInsets();
                    if (vi != null) {
                        baseWidth -= (vi.left + vi.right);
                    }
                }
                // Keep extra headroom for bubble border/padding and width rounding so
                // default-size windows do not miss wraps by a few pixels.
                JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, list);
                if (sp != null) {
                    JScrollBar vsb = sp.getVerticalScrollBar();
                    if (vsb != null && vsb.isVisible()) {
                        baseWidth -= vsb.getWidth();
                    }
                }
                int usable = baseWidth - Constants.BUBBLE_INSET_PX;
                return usable > 0 ? (int) (usable * BUBBLE_WRAP_FRACTION) : 220;
            }
        }

        ConversationView() {
            participantsLabel = new JLabel();
            addButton = new JButton("Add");
            leaveButton = new JButton("Leave");
            messageInputField = new JTextField(15);
            messageInputField.setEnabled(false);
            sendButton = new JButton("Send");
            addButton.setEnabled(false);
            leaveButton.setEnabled(false);
            sendButton.setEnabled(false);

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

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

            list.setCellRenderer(new MessageCellRenderer());

            // Re-invalidate cached row heights whenever either the list or its viewport
            // changes size so wrap width updates with window resizing.
            ComponentAdapter wrapResizeAdapter = new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    list.setFixedCellHeight(10);
                    list.setFixedCellHeight(-1);
                    list.revalidate();
                    list.repaint();
                }
            };
            list.addComponentListener(wrapResizeAdapter);

            JPanel centerPanel = new JPanel(new BorderLayout());
            messageScrollPane = new JScrollPane(list);
            messageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            messageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            messageScrollPane.getViewport().addComponentListener(wrapResizeAdapter);
            // Track whether the user is parked at the bottom of the message list.
            // When they scroll up to read history, suppress the read-advance and per-message
            // snapshot slide so the unread cue persists. Drain the deferred-read buffer on
            // the up→bottom transition so the catch-up wire request fires once.
            messageScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
                JScrollBar vsb = (JScrollBar) e.getSource();
                int max = vsb.getMaximum() - vsb.getVisibleAmount();
                boolean newAtBottom = (vsb.getValue() >= max - Constants.SCROLL_BOTTOM_TOLERANCE_PX);
                boolean wasAtBottom = userIsAtBottom;
                userIsAtBottom = newAtBottom;
                if (newAtBottom && !wasAtBottom) {
                    onScrollReachedBottom();
                }
            });
            centerPanel.add(messageScrollPane, BorderLayout.CENTER);
            placeholderLabel.setVisible(true);
            centerPanel.add(placeholderLabel, BorderLayout.SOUTH);
            add(centerPanel, BorderLayout.CENTER);

            JLabel charCountLabel = new JLabel("0/500");
            charCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            JPanel sendPane = new JPanel(new BorderLayout());
            sendPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            sendPane.add(messageInputField, BorderLayout.CENTER);
            JPanel sendRight = new JPanel(new BorderLayout());
            sendRight.add(charCountLabel, BorderLayout.WEST);
            sendRight.add(sendButton, BorderLayout.EAST);
            sendPane.add(sendRight, BorderLayout.EAST);

            add(sendPane, BorderLayout.SOUTH);

            addButton.addActionListener(e -> {

                if (focusExistingDialog(addDialog)) return;

                addUserWindow = new SelectUserWindow(
                    users -> controller.addToConversation(users, controller.getCurrentConversationId()));

                addDialog = buildModalDialog(Constants.DLG_SELECT_USER, addUserWindow, SELECT_USER_DIALOG_SIZE);

                addDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        addDialog = null;
                    }
                    // Focus the list on open so Tab order is correct and Delete works.
                    @Override
                    public void windowOpened(WindowEvent e) {
                        addUserWindow.list.requestFocusInWindow();
                    }
                });

                // Single-user picker fix: clear directory selection so the first click
                // always emits a fresh ListSelectionEvent (see createConversationButton).
                cards.main.directoryView.clearSelecting();

                addDialog.setVisible(true);

            });

            leaveButton.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(frame, "Are you sure to leave this conversation?", "Confirm to Leave", JOptionPane.YES_NO_OPTION);
                if(result == JOptionPane.YES_OPTION) {
                    controller.leaveConversation(controller.getCurrentConversationId());
                }
            });

            ((javax.swing.text.AbstractDocument) messageInputField.getDocument()).setDocumentFilter(
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

            messageInputField.getDocument().addDocumentListener(new DocumentListener() {

                private void update(DocumentEvent e) {
                    int len = e.getDocument().getLength();
                    charCountLabel.setText(len + "/500");
                    charCountLabel.setForeground(len >= 500 ? Color.RED : UIManager.getColor("Label.foreground"));
                    sendButton.setEnabled(len > 0 && !messageInputField.getText().trim().isEmpty());
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    update(e);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    update(e);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    update(e);
                }
            });

            sendButton.addActionListener(e -> doSend());

            messageInputField.addActionListener(e -> doSend());

        }

        private void doSend() {
            if (controller.getCurrentConversation() == null) return;
            String msg = messageInputField.getText();
            if (msg.trim().isEmpty()) return;
            controller.sendMessage(controller.getCurrentConversation().getConversationId(), msg);
            messageInputField.setText("");
            // Sending a reply is an unambiguous read-acknowledgement.
            removeDividerNow();
        }

        public void setListModel(Conversation currConv) {
            long lastReadAtOpen = controller.getCurrentUserInfo().getLastRead(currConv.getConversationId());
            setListModel(currConv, lastReadAtOpen);
        }

        /** Fix E2: caller atomically captures (lastRead, messages); divider can't drift. */
        public void setListModel(ClientController.ConversationOpenSnapshot snap) {
            setListModel(snap.conversation, snap.lastReadAtOpen, snap.messages);
        }

        public void setListModel(Conversation currConv, long lastReadAtOpen) {
            setListModel(currConv, lastReadAtOpen, currConv.getMessages());
        }

        private void setListModel(Conversation currConv, long lastReadAtOpen, ArrayList<Message> msgs) {
            UserInfo me = controller.getCurrentUserInfo();
            String myId = (me != null) ? me.getUserId() : null;
            final String finalMember = buildParticipantSummary(excludeSelf(currConv.getParticipants(), myId), 3);
            final long finalLastReadAtOpen = lastReadAtOpen;
            SwingUtilities.invokeLater(() -> {
                displayedLastReadSeq = finalLastReadAtOpen;
                // Opening a conversation auto-scrolls to bottom (see scrollToBottom
                // below), so the user is parked at the bottom by definition. Reset the
                // tracker so a stale "scrolled up" state from a previous conversation
                // doesn't suppress the read-advance for the freshly-opened one.
                userIsAtBottom = true;
                participantsLabel.setText(finalMember);
                conversationMessageListModel.clear();
                // Insert the "New messages" divider between read and unread.
                // Suppress when finalLastReadAtOpen == 0 (nothing has ever been read; a
                // divider above message 1 is meaningless).
                boolean dividerInserted = false;
                for (int i = 0; i < msgs.size(); i++) {
                    Message m = msgs.get(i);
                    if (!dividerInserted
                            && finalLastReadAtOpen > 0L
                            && m.getSequenceNumber() > finalLastReadAtOpen) {
                        conversationMessageListModel.addElement(NewMessagesDivider.INSTANCE);
                        dividerInserted = true;
                    }
                    conversationMessageListModel.addElement(m);
                }
                modelHasDivider = dividerInserted;
                refreshWrapLayout();
                messageInputField.setEnabled(true);
                addButton.setEnabled(true);
                leaveButton.setEnabled(true);
                sendButton.setEnabled(!messageInputField.getText().trim().isEmpty());
                placeholderLabel.setVisible(false);
                int last = list.getModel().getSize() - 1;
                if (last >= 0) list.ensureIndexIsVisible(last);
                scrollToBottom();
                refreshWrapLayout();
            });
        }

        public void clearConversation() {
            SwingUtilities.invokeLater(() -> {
                displayedLastReadSeq = 0L;
                // Model is cleared so no divider remains; reset bottom tracker.
                modelHasDivider = false;
                userIsAtBottom = true;
                participantsLabel.setText("");
                conversationMessageListModel.clear();
                refreshWrapLayout();
                messageInputField.setEnabled(false);
                addButton.setEnabled(false);
                leaveButton.setEnabled(false);
                sendButton.setEnabled(false);
                placeholderLabel.setVisible(true);
            });
        }

        private void refreshWrapLayout() {
            // Toggle fixed cell height to clear JList renderer-size cache, then repaint.
            list.setFixedCellHeight(10);
            list.setFixedCellHeight(-1);
            list.revalidate();
            list.repaint();
        }

        public DefaultListModel<Object> getListModel() {
            return this.conversationMessageListModel;
        }

        void focusInput() {
            messageInputField.requestFocusInWindow();
        }

        public boolean isAddingUser() {
            return isDialogShowing(addDialog);
        }

        public void propagateAddUserSelection(UserInfo user) {
            if (user != null && addUserWindow != null) addUserWindow.addUser(user);
        }

        private void onScrollReachedBottom() {
            controller.replayReadAdvanceIfNeeded();
        }

        public void markDisplayedReadUpTo(long sequenceNumber) {
            displayedLastReadSeq = Math.max(displayedLastReadSeq, sequenceNumber);
            removeDividerIfCaughtUp();
        }

        /** Drops the divider once every message below it has been read. Without this the
         *  {@code modelHasDivider} guard above keeps the divider pinned at first insertion. */
        private void removeDividerIfCaughtUp() {
            if (!modelHasDivider) return;
            int dividerIdx = -1;
            for (int i = 0; i < conversationMessageListModel.size(); i++) {
                if (conversationMessageListModel.get(i) == NewMessagesDivider.INSTANCE) {
                    dividerIdx = i;
                    break;
                }
            }
            if (dividerIdx < 0) {
                modelHasDivider = false;
                return;
            }
            for (int i = dividerIdx + 1; i < conversationMessageListModel.size(); i++) {
                Object el = conversationMessageListModel.get(i);
                if (el instanceof Message
                        && ((Message) el).getSequenceNumber() > displayedLastReadSeq) {
                    return;
                }
            }
            conversationMessageListModel.remove(dividerIdx);
            modelHasDivider = false;
        }

        /** Unconditional divider removal — for cases where the act itself (e.g. sending)
         *  is the read-acknowledgement signal, regardless of snapshot state. */
        private void removeDividerNow() {
            if (!modelHasDivider) return;
            for (int i = 0; i < conversationMessageListModel.size(); i++) {
                if (conversationMessageListModel.get(i) == NewMessagesDivider.INSTANCE) {
                    conversationMessageListModel.remove(i);
                    break;
                }
            }
            modelHasDivider = false;
        }

        private void scrollToBottom() {
            JScrollBar vsb = messageScrollPane.getVerticalScrollBar();
            // Defer one more tick so layout/model updates settle before forcing bottom.
            SwingUtilities.invokeLater(() -> vsb.setValue(vsb.getMaximum()));
        }
    }

    class DirectoryView extends JPanel {
        private JLabel profileUserIdLabel;
        private JLabel profileNameLabel;
        private JLabel pickerBannerLabel;
        private JTextField searchField;
        private final DefaultListModel<UserInfo> listModel = new DefaultListModel<>();
        private final JList<UserInfo> list = new JList<>(listModel);
        private JButton logoutButton;
        private JButton createConversationButton;
        private JButton adminButton;
        private JDialog createDialog;
        private JDialog adminDialog;
        private UserInfo selectedDirectoryUser;
        private SelectUserWindow createConversationUserWindow;
        private AdminConversationSearchWindow adminConversationSearchWindow;

        DirectoryView() {
            buildLayout();
            wireSearchField();
            wireLogoutButton();
            wireCreateConversationButton();
            wireAdminButton();
            wireListSelectionListener();
        }

        private void buildLayout() {
            profileUserIdLabel = new JLabel();
            profileNameLabel = new JLabel();

            pickerBannerLabel = new JLabel("Selecting participants — click to add", SwingConstants.CENTER);
            pickerBannerLabel.setForeground(Theme.BANNER_FG);
            pickerBannerLabel.setFont(pickerBannerLabel.getFont().deriveFont(Font.BOLD));
            pickerBannerLabel.setVisible(false);

            searchField = new PlaceholderTextField("Search users...", 15);
            logoutButton = new JButton("Log Out");
            createConversationButton = new JButton("Create Conversation");
            createConversationButton.setEnabled(false);

            // Tooltip explains the prerequisite (a user must be selected). Label kept
            // as "Admin" per the silent-viewer feature; the dialog itself shows what the
            // admin is doing.
            adminButton = new JButton("Admin");
            adminButton.setToolTipText("Select a user in the directory, then click to view their conversations");
            adminButton.setEnabled(false);
            adminButton.setVisible(false);

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel userPane = new JPanel(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;

            gridConst.gridx = 0; gridConst.gridy = 0;
            userPane.add(profileUserIdLabel, gridConst);
            gridConst.gridy = 1;
            userPane.add(profileNameLabel, gridConst);
            gridConst.gridy = 2;
            userPane.add(searchField, gridConst);
            gridConst.gridx = 1; gridConst.gridy = 0; gridConst.gridheight = 2;
            userPane.add(logoutButton, gridConst);

            JPanel northPanel = new JPanel(new BorderLayout());
            northPanel.add(userPane, BorderLayout.NORTH);
            northPanel.add(pickerBannerLabel, BorderLayout.SOUTH);
            add(northPanel, BorderLayout.NORTH);

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            add(new JScrollPane(list), BorderLayout.CENTER);

            JPanel buttonPane = new JPanel(new FlowLayout());
            buttonPane.add(createConversationButton);
            buttonPane.add(adminButton);
            add(buttonPane, BorderLayout.SOUTH);
        }

        private void wireSearchField() {
            searchField.getDocument().addDocumentListener(filteringListener(this::applyDirectoryFilter));
        }

        private void applyDirectoryFilter() {
            String query = searchField.getText().toUpperCase();
            listModel.clear();
            UserInfo me = controller.getCurrentUserInfo();
            String myId = (me != null) ? me.getUserId() : null;
            for (UserInfo item : controller.getFilteredDirectory(query)) {
                if (myId == null || !item.getUserId().equals(myId)) {
                    listModel.addElement(item);
                }
            }
        }

        private void wireLogoutButton() {
            logoutButton.addActionListener(e -> controller.logout());
        }

        private void wireCreateConversationButton() {
            createConversationButton.addActionListener(e -> {
                if (focusExistingDialog(createDialog)) return;

                createConversationUserWindow = new SelectUserWindow(users -> controller.createConversation(users));
                // Seed the picker with the current directory selection so the
                // first selection is honored without requiring reselection.
                if (selectedDirectoryUser != null) {
                    createConversationUserWindow.addUser(selectedDirectoryUser);
                }
                createDialog = buildModalDialog(Constants.DLG_SELECT_USER, createConversationUserWindow, SELECT_USER_DIALOG_SIZE);

                createDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        createDialog = null;
                    }
                    // Focus the list on open so Tab order is correct and Delete works.
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
                selectedDirectoryUser = null;

                createDialog.setVisible(true);
            });
        }

        private void wireAdminButton() {
            adminButton.addActionListener(e -> {
                if (focusExistingDialog(adminDialog)) return;

                if (selectedDirectoryUser == null) {
                    return;
                }
                controller.adminGetUserConversations(selectedDirectoryUser.getUserId());
                adminConversationSearchWindow = new AdminConversationSearchWindow();
                adminDialog = buildModalDialog(Constants.DLG_ADMIN_SEARCH, adminConversationSearchWindow, ADMIN_SEARCH_DIALOG_SIZE);

                adminDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        // Clear all admin-dialog state so reopening shows a fresh empty list.
                        // Do NOT disable adminButton here — the directory selection listener
                        // owns its enable/disable state, and disabling on close traps the
                        // button in a dead state when the same user is still selected (the
                        // listener only fires on selection-change events).
                        adminDialog = null;
                        adminConversationSearchWindow = null;
                        controller.clearAdminConversationSearch();
                    }
                });

                adminDialog.setVisible(true);
            });
        }

        private void wireListSelectionListener() {
            list.getSelectionModel().addListSelectionListener(e -> {
                UserInfo selectedValue = list.getSelectedValue();
                if (e.getValueIsAdjusting()) {
                    return;
                }
                selectedDirectoryUser = selectedValue;

                ConversationView convView = cards.main.conversationView;
                boolean addingUser = convView.isAddingUser();
                boolean inPickerMode = isDialogShowing(createDialog) || addingUser;
                pickerBannerLabel.setVisible(inPickerMode);

                if (selectedDirectoryUser != null && !isDialogShowing(createDialog) && !isDialogShowing(adminDialog) && !addingUser) {
                    createConversationButton.setEnabled(true);
                    adminButton.setEnabled(true);
                } else if (isDialogShowing(createDialog)) {
                    createConversationUserWindow.addUser(selectedDirectoryUser);
                } else if (addingUser) {
                    convView.propagateAddUserSelection(selectedDirectoryUser);
                }
            });
        }

        public DefaultListModel<UserInfo> getListModel() {
            return this.listModel;
        }

        public boolean isCreatingConversation() {
            return isDialogShowing(createDialog);
        }

        public boolean isAdminSearching() {
            return isDialogShowing(adminDialog);
        }

        /** Drops the selected directory user without firing the selection listener side effects. */
        void clearSelecting() {
            list.clearSelection();
            selectedDirectoryUser = null;
        }


    }

    class ConversationListView extends JPanel {
        JTextField searchField;
        DefaultListModel<Conversation> listModel = new DefaultListModel<>();
        final JList<Conversation> list = new JList<>(listModel);
        // Suppresses the selection listener while an external rebuild restores selection.
        // Don't toggle this directly — go through withSuppressedSelection(...).
        private boolean suppressSelectionEvents = false;

        /** Runs {@code body} with the selection listener silenced; restores the flag in
         *  a finally so an exception in {@code body} can't leave the listener wedged. */
        void withSuppressedSelection(Runnable body) {
            suppressSelectionEvents = true;
            try { body.run(); }
            finally { suppressSelectionEvents = false; }
        }

        private final class ConversationCellRenderer extends JPanel implements ListCellRenderer<Conversation> {
            private final JLabel nameLabel = new JLabel();
            private final JLabel badgeLabel = new JLabel();

            ConversationCellRenderer() {
                setLayout(new BorderLayout(6, 0));
                setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                setOpaque(true);

                Color badgeBg = UIManager.getColor("nimbusRed");
                if (badgeBg == null) badgeBg = Theme.NIMBUS_RED;
                badgeLabel.setOpaque(true);
                badgeLabel.setBackground(badgeBg);
                badgeLabel.setForeground(Color.WHITE);
                badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD));
                badgeLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
                badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);

                add(nameLabel, BorderLayout.CENTER);
                add(badgeLabel, BorderLayout.EAST);
            }

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends Conversation> list, Conversation value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                UserInfo me = controller.getCurrentUserInfo();
                String myId = (me != null) ? me.getUserId() : null;
                ArrayList<UserInfo> others = excludeSelf(value.getParticipants(), myId);

                String display;
                if (value.getType() == shared.enums.ConversationType.PRIVATE) {
                    String otherName = others.isEmpty() ? Constants.EMPTY_PARTICIPANT_PLACEHOLDER : others.get(0).getName();
                    display = Constants.DM_PREFIX + otherName;
                } else {
                    String summary = buildParticipantSummary(others, Integer.MAX_VALUE);
                    display = Constants.GROUP_PREFIX + (summary.isEmpty() ? Constants.EMPTY_PARTICIPANT_PLACEHOLDER : summary);
                }

                int unread = ClientController.unreadCount(value, me);
                if (unread > 0) {
                    nameLabel.setFont(list.getFont().deriveFont(Font.BOLD));
                    badgeLabel.setText(unread > Constants.UNREAD_BADGE_CAP ? Constants.UNREAD_BADGE_CAP_TEXT : Integer.toString(unread));
                    badgeLabel.setVisible(true);
                    getAccessibleContext().setAccessibleName(display + " (" + unread + " unread)");
                } else {
                    nameLabel.setFont(list.getFont().deriveFont(Font.PLAIN));
                    badgeLabel.setVisible(false);
                    getAccessibleContext().setAccessibleName(display);
                }
                nameLabel.setText(display);

                if (isSelected) {
                    setBackground(list.getSelectionBackground());
                    nameLabel.setForeground(list.getSelectionForeground());
                } else {
                    setBackground(list.getBackground());
                    nameLabel.setForeground(list.getForeground());
                }

                return this;
            }
        }

        ConversationListView() {
            buildLayout();
            wireSearchField();
            wireListSelectionListener();
        }

        private void buildLayout() {
            searchField = new PlaceholderTextField("Search conversations...", 15);

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(searchField, BorderLayout.NORTH);

            list.setCellRenderer(new ConversationCellRenderer());

            add(new JScrollPane(list), BorderLayout.CENTER);
        }

        private void wireSearchField() {
            searchField.getDocument().addDocumentListener(filteringListener(this::applyConversationFilter));
        }

        private void applyConversationFilter() {
            String query = searchField.getText().toUpperCase();
            listModel.clear();
            for (Conversation item : controller.getFilteredConversationList(query)) {
                listModel.addElement(item);
            }
        }

        private void wireListSelectionListener() {
            list.addListSelectionListener(e -> {
                if (suppressSelectionEvents) {
                    return;
                }
                if (!e.getValueIsAdjusting()) {
                    if (list.getSelectedValue() != null) {
                        Conversation selected = list.getSelectedValue();
                        // Atomic open: capture (lastRead, messages) and publish currentConversationId
                        // under one lock so a concurrent inbound can't advance lastRead past the
                        // new message between snapshot and divider check.
                        ClientController.ConversationOpenSnapshot snap =
                                controller.openConversationAtomically(selected.getConversationId());
                        if (snap == null) return;
                        if (!snap.messages.isEmpty()) {
                            Message last = snap.messages.get(snap.messages.size() - 1);
                            controller.getCurrentUserInfo().setLastRead(
                                    selected.getConversationId(), last.getSequenceNumber());
                            controller.updateReadMessages(selected.getConversationId(), last.getSequenceNumber());
                        }
                        cards.main.conversationView.setListModel(snap);
                    }
                }
            });
        }

        public DefaultListModel<Conversation> getListModel() {
            return this.listModel;
        }
    }

    class SelectUserWindow extends JPanel {
        final DefaultListModel<UserInfo> model = new DefaultListModel<>();
        final JList<UserInfo> list = new JList<>(model);
        JButton removeButton;
        JButton okButton;
        JButton cancelButton;
        private final java.util.function.Consumer<ArrayList<UserInfo>> onConfirm;

        SelectUserWindow(java.util.function.Consumer<ArrayList<UserInfo>> onConfirm) {
            this.onConfirm = onConfirm;
            buildLayout();
            wireRemoveButton();
            wireConfirmButton();
            wireCancelButton();
            wireListSelectionListener();
            installDeleteKeyShortcut();
        }

        private void buildLayout() {
            removeButton = new JButton("Remove");
            removeButton.setEnabled(false);
            okButton = new JButton("OK");
            cancelButton = new JButton("Cancel");

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(new JScrollPane(list), BorderLayout.CENTER);

            JPanel buttonPane = new JPanel(new FlowLayout());
            buttonPane.add(removeButton);
            buttonPane.add(okButton);
            buttonPane.add(cancelButton);
            add(buttonPane, BorderLayout.SOUTH);
        }

        private void wireRemoveButton() {
            removeButton.addActionListener(e -> {
                UserInfo selected = list.getSelectedValue();
                if (selected != null) {
                    model.removeElement(selected);
                }
            });
        }

        private void wireConfirmButton() {
            okButton.addActionListener(e -> {
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
        }

        private void wireCancelButton() {
            cancelButton.addActionListener(e -> {
                model.clear();
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();
            });
        }

        private void wireListSelectionListener() {
            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    UserInfo toRemove = list.getSelectedValue();
                    removeButton.setEnabled(toRemove != null);
                }
            });
        }

        private void installDeleteKeyShortcut() {
            // Delete key on the list invokes Remove (so the button is reachable by keyboard).
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

        public void addUser(UserInfo user) {
            for(int i = 0; i < model.size(); i++) {
                if(user.getUserId().equals(model.get(i).getUserId())){
                    return;
                }
            }
            model.addElement(user);
        }


    }

    class AdminConversationSearchWindow extends JPanel {
        JTextField searchField;
        final DefaultListModel<ConversationMetadata> model = new DefaultListModel<>();
        final JList<ConversationMetadata> list = new JList<>(model);
        JButton closeButton;
        ViewerPanel viewerPanel;

        private final class AdminConversationCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ConversationMetadata) {
                    ConversationMetadata m = (ConversationMetadata) value;
                    ArrayList<UserInfo> active = m.getParticipants();
                    ArrayList<UserInfo> sourceList = displayParticipants(active, m.getHistoricalParticipants());
                    boolean orphan = active.isEmpty();

                    String participantsSummary;
                    if (sourceList == null || sourceList.isEmpty()) {
                        participantsSummary = "0 participant(s)";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        if (orphan) sb.append("(left) ");
                        for (int i = 0; i < sourceList.size(); i++) {
                            if (i > 0) sb.append(", ");
                            UserInfo p = sourceList.get(i);
                            sb.append(p.getName()).append(" (").append(p.getUserId()).append(')');
                        }
                        participantsSummary = sb.toString();
                    }

                    String lastActivityLabel;
                    long ts = m.getLastMessageTimestampMillis();
                    if (ts <= 0L) {
                        lastActivityLabel = "no messages yet";
                    } else {
                        lastActivityLabel = "last active " + formatMessageTimestamp(new java.util.Date(ts));
                    }
                    setText("[ID: " + m.getConversationId() + "]  "
                            + m.getType() + "  •  "
                            + participantsSummary + "  •  "
                            + lastActivityLabel);
                }
                return this;
            }
        }

        // Resolves senders from the locally-held Conversation (admin has no current-conversation context).
        private final class AdminMessageCellRenderer extends JPanel implements ListCellRenderer<Message> {
            private final JLabel label = new JLabel();
            private Conversation viewedConv;
            private final java.util.HashMap<Long, String> htmlCache = new java.util.HashMap<>();
            private int cachedWrapPx = -1;

            AdminMessageCellRenderer() {
                setLayout(new BorderLayout());
                setOpaque(true);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(Theme.BUBBLE_BORDER, 1, true),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
            }

            void setConversation(Conversation conv) {
                this.viewedConv = conv;
                htmlCache.clear();
            }

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends Message> list, Message msg, int index,
                    boolean isSelected, boolean cellHasFocus) {
                int listW = list.getWidth();
                int wrapPx = listW > 0 ? (int) (listW * BUBBLE_WRAP_FRACTION) : 360;
                if (wrapPx != cachedWrapPx) {
                    htmlCache.clear();
                    cachedWrapPx = wrapPx;
                }
                String html = htmlCache.get(msg.getSequenceNumber());
                if (html == null) {
                    MessageRow row = MessageRow.forAdmin(msg, viewedConv);
                    html = escapeAndWrapHtml(row.headerText + " " + row.body, wrapPx);
                    htmlCache.put(msg.getSequenceNumber(), html);
                }

                removeAll();
                setBackground(list.getBackground());
                label.setBackground(list.getBackground());
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                label.setText(html);
                add(label, BorderLayout.WEST);
                return this;
            }
        }

        // Right pane of the split: read-only message viewer; placeholder until a conversation loads.
        final class ViewerPanel extends JPanel {
            private static final String CARD_EMPTY = "empty";
            private static final String CARD_MESSAGES = "messages";

            JLabel participantsLabel = new JLabel(" ");
            DefaultListModel<Message> msgModel = new DefaultListModel<>();
            JList<Message> msgList = new JList<>(msgModel);
            JLabel emptyStateLabel = new JLabel("Select a conversation to preview", SwingConstants.CENTER);
            JScrollPane scroll;
            AdminMessageCellRenderer renderer = new AdminMessageCellRenderer();
            private final CardLayout bodyCards = new CardLayout();
            private final JPanel body = new JPanel(bodyCards);

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

                body.add(emptyStateLabel, CARD_EMPTY);
                body.add(scroll, CARD_MESSAGES);
                add(body, BorderLayout.CENTER);
            }

            void loadConversation(Conversation conv) {
                if (conv == null) return;
                renderer.setConversation(conv);
                ArrayList<UserInfo> active = conv.getParticipants();
                ArrayList<UserInfo> sourceList = displayParticipants(active, conv.getHistoricalParticipants());
                String summary = buildParticipantSummary(sourceList, Integer.MAX_VALUE);
                String prefix = active.isEmpty() ? "Former participants: " : "Participants: ";
                participantsLabel.setText(prefix + (summary.isEmpty() ? "(none)" : summary));

                msgModel.clear();
                ArrayList<Message> messages = conv.getMessages();
                if (messages.isEmpty()) {
                    emptyStateLabel.setText("No messages yet");
                    bodyCards.show(body, CARD_EMPTY);
                } else {
                    for (Message m : messages) msgModel.addElement(m);
                    bodyCards.show(body, CARD_MESSAGES);
                    SwingUtilities.invokeLater(() -> {
                        msgList.setFixedCellHeight(10);
                        msgList.setFixedCellHeight(-1);
                        int last = msgModel.getSize() - 1;
                        if (last >= 0) msgList.ensureIndexIsVisible(last);
                    });
                }
            }
        }

        AdminConversationSearchWindow() {
            buildLayout();
            seedFromCache();
            wireSearchField();
            wireListSelectionListener();
            wireCloseButton();
        }

        private void buildLayout() {
            searchField = new PlaceholderTextField("Search conversations...", 15);
            closeButton = new JButton("Close");

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.add(searchField, BorderLayout.NORTH);
            list.setCellRenderer(new AdminConversationCellRenderer());
            leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);

            viewerPanel = new ViewerPanel();

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, viewerPanel);
            split.setDividerLocation(Constants.ADMIN_SPLIT_DIVIDER_PX);
            split.setResizeWeight(0.0);
            add(split, BorderLayout.CENTER);

            JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPane.add(closeButton);
            add(buttonPane, BorderLayout.SOUTH);
        }

        private void seedFromCache() {
            // Seed from the controller's cache. If the ADMIN_CONVERSATION_RESULT
            // response arrived before this window was constructed (race on fast loopback),
            // the data is already in the cache and we render it immediately. The async
            // updateAdminConversationSearchModel callback path stays for late responses.
            for (ConversationMetadata m : controller.getCurrentAdminConversationSearch()) {
                model.addElement(m);
            }
        }

        private void wireSearchField() {
            searchField.getDocument().addDocumentListener(filteringListener(this::applyAdminSearchFilter));
        }

        private void applyAdminSearchFilter() {
            String query = searchField.getText().toUpperCase();
            model.clear();
            for (ConversationMetadata item : controller.getFilteredAdminConversationSearch(query)) {
                model.addElement(item);
            }
        }

        private void wireListSelectionListener() {
            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                ConversationMetadata sel = list.getSelectedValue();
                if (sel != null) {
                    controller.adminViewConversation(sel.getConversationId());
                }
            });
        }

        private void wireCloseButton() {
            closeButton.addActionListener(e -> {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window != null) window.dispose();
            });
        }

        public DefaultListModel<ConversationMetadata> getAdminConversationSearch() {
            return model;
        }

        /** Called from ClientUI.showAdminConversationView. */
        public void loadConversation(Conversation conv) {
            viewerPanel.loadConversation(conv);
        }

    }
}
