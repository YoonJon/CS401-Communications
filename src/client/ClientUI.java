package client;

import shared.enums.*;
import javax.swing.*;
import java.awt.*;

public class ClientUI {
    private ClientController controller;
    private JFrame frame;
    private ScreenCards cards;
    private RegisterView registerView;
    private LoginView loginView;
    private ConversationView chatView;
    private DirectoryView userDirectory;
    private ConversationListView conversationList;
    private SelectUserWindow selectUserWindow;
    private AdminConversationSearchWindow adminConversationSearchWindow;
    private boolean selectingUser;
    private boolean adminSearchingConversation;

    public ClientUI(ClientController controller) {
        this.controller = controller;
        // TODO: build JFrame, initialize views, wire listeners
    }

    public void showLoginView() { cards.layout.show(cards, "login"); }
    public void showRegisterView() { cards.layout.show(cards, "register"); }
    public void showMainView() { cards.layout.show(cards, "main"); }

    public void showRegisterError(RegisterStatus registerStatus) {
        // TODO
    }

    public void showLoginError(LoginStatus loginStatus) {
        // TODO
    }

    public void showCreateConversationWindow() {
        // TODO
    }

    public void showAdminConversationSearchWindow() {
        // TODO
    }

    public void chooseMainView() { showMainView(); }
    public void chooseLoginView() { showLoginView(); }
    public void chooseRegisterView() { showRegisterView(); }

    public DefaultListModel getDirectoryViewModel() { return userDirectory.listModel; }
    public DefaultListModel getConversationViewModel() { return chatView.listModel; }
    public DefaultListModel getConversationListViewModel() { return conversationList.messageModel; }
    public DefaultListModel getSelectUserWindowModel() { return selectUserWindow.model; }
    public DefaultListModel getAdminConversationSearchWindowModel() { return adminConversationSearchWindow.model; }

    public void setDirectoryQuery(String query) { userDirectory.searchField.setText(query); }
    public String getDirectoryQuery() { return userDirectory.searchField.getText(); }

    public void setConversationQuery(String query) { chatView.text.setText(query); }
    public String getConversationQuery() { return chatView.text.getText(); }

    public void setAdminConversationQuery(String q) { adminConversationSearchWindow.searchField.setText(q); }
    public String getAdminConversationQuery() { return adminConversationSearchWindow.searchField.getText(); }

    public boolean isCreatingConversation() { return selectingUser; }
    public boolean isAdminSearchingConversation() { return adminSearchingConversation; }

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
            // TODO: lay out components
        }
    }

    // =========================================================================
    class LoginView extends JPanel {
        JTextField login_idField;
        JPasswordField passwordField;
        JButton loginButton;
        JButton createButton;

        LoginView() {
            // TODO: lay out components
        }
    }

    // =========================================================================
    class MainView extends JPanel {
        ConversationView conversationView;
        DirectoryView directoryView;
        ConversationListView conversationListView;

        MainView() {
            // TODO: lay out components
        }
    }

    // =========================================================================
    class ConversationView extends JPanel {
        JLabel participantsLabel;
        DefaultListModel listModel;
        JList list;
        JButton addButton;
        JButton leaveButton;
        JTextField text;
        JButton sendButton;

        ConversationView() {
            listModel = new DefaultListModel();
            list = new JList(listModel);
            // TODO: lay out components
        }
    }

    // =========================================================================
    class DirectoryView extends JPanel {
        JLabel userLabel;
        JTextField searchField;
        DefaultListModel listModel;
        JList list;
        JButton logoutButton;
        JButton createConversationButton;
        JButton adminButton;
        JTextField searchBar;

        DirectoryView() {
            listModel = new DefaultListModel();
            list = new JList(listModel);
            searchField = new JTextField();
            searchBar = new JTextField();
            // TODO: lay out components
        }
    }

    // =========================================================================
    class ConversationListView extends JPanel {
        JLabel participantsLabel;
        DefaultListModel messageModel;
        JList list;
        JButton addButton;
        JButton leaveButton;
        JTextField text;
        JButton sendButton;

        ConversationListView() {
            messageModel = new DefaultListModel();
            list = new JList(messageModel);
            // TODO: lay out components
        }
    }

    // =========================================================================
    class SelectUserWindow extends JPanel {
        DefaultListModel model;
        JList list;
        JButton removeButton;
        JButton okButton;
        JButton cancelButton;

        SelectUserWindow() {
            model = new DefaultListModel();
            list = new JList(model);
            // TODO: lay out components
        }
    }

    // =========================================================================
    class AdminConversationSearchWindow extends JPanel {
        JTextField searchField;
        DefaultListModel model;
        JList list;
        JButton okButton;
        JButton cancelButton;

        AdminConversationSearchWindow() {
            model = new DefaultListModel();
            list = new JList(model);
            searchField = new JTextField();
            // TODO: lay out components
        }
    }
}
