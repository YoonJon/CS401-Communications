package client;


import java.awt.*;
import javax.swing.*;
import shared.enums.*;

public class ClientUI {
    private ClientController controller;
    private JFrame frame;
    private ScreenCards cards;
    private SelectUserWindow selectUserWindow;
    private AdminConversationSearchWindow adminConversationSearchWindow;
    private boolean selectingUser;
    private boolean adminSearchingConversation;

    public ClientUI(ClientController controller) {
        this.controller = controller;
        frame = new JFrame();
        frame.setTitle("Communication Application");
        cards = new ScreenCards();
        frame.add(cards);
        frame.pack();                 
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);       
        frame.setVisible(true);
        Toolkit.getDefaultToolkit().addAWTEventListener(
            e -> controller.updateLastActivity(),
            AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
        );
    }

    public void showLoginView() { cards.layout.show(cards, "login"); }
    public void showRegisterView() { cards.layout.show(cards, "register"); }
    public void showMainView() { cards.layout.show(cards, "main"); }
    public void showAdminMainView() {
        cards.main.directoryView.adminButton.setVisible(true);
        cards.layout.show(cards, "main");
    }

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

    public DefaultListModel getDirectoryViewModel() { return cards.main.directoryView.listModel; }
    public DefaultListModel getConversationViewModel() { return cards.main.conversationView.listModel; }
    public DefaultListModel getConversationListViewModel() { return conversationList.messageModel; }
    public DefaultListModel getSelectUserWindowModel() { return selectUserWindow.model; }
    public DefaultListModel getAdminConversationSearchWindowModel() { return adminConversationSearchWindow.model; }

    public void setDirectoryQuery(String query) { cards.main.directoryView.searchField.setText(query); }
    public String getDirectoryQuery() { return cards.main.directoryView.searchField.getText(); }

    public void setConversationQuery(String query) { cards.main.conversationView.text.setText(query); }
    public String getConversationQuery() { return cards.main.conversationView.text.getText(); }

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
        
        public void setMainDirectoryListModel(DefaultListModel model) {
        	main.setDirectoryListModel(model);
        }
        
        public DefaultListModel getMainDirectoryListModel() {
        	return main.getDirectoryListModel();
        }
        
        public void setMainConversationMessageListModel(DefaultListModel model) {
        	main.setMessageListModel(model);
        }
        
        public DefaultListModel getMainConversationMessageListModel() {
        	return main.getMessageListModel();
        }
        
        public void setMainConversationListModel(DefaultListModel model) {
        	main.setConversationListModel(model);
        }
        
        public DefaultListModel getMainConversationListModel() {
        	return main.getConversationListModel();
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
            		JOptionPane.showMessageDialog(null, "Passwords don't match. Type them again.", "Error", JOptionPane.ERROR_MESSAGE);
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
            
            
            loginButton.addActionListener(e -> {
            	controller.login(login_idField.getText(), passwordField.getPassword());
            });
            
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
        
        public void setDirectoryListModel(DefaultListModel model) {
        	directoryView.setListModel(model);
        }
        
        public DefaultListModel getDirectoryListModel() {
        	return directoryView.getListModel();
        }
        
        public void setMessageListModel(DefaultListModel model) {
        	conversationView.setListModel(model);
        }
        
        public DefaultListModel getMessageListModel() {
        	return conversationView.getListModel();
        }
        
        public void setConversationListModel(DefaultListModel model) {
        	conversationListView.setListModel(model);
        }
        
        public DefaultListModel getConversationListModel() {
        	return conversationListView.getListModel();
        }
    }

    // =========================================================================
    class ConversationView extends JPanel {
        JLabel participantsLabel;
        DefaultListModel messageModel;
        JList list;
        JButton addButton;
        JButton leaveButton;
        JTextField text;
        JButton sendButton;

        ConversationView() {
        	participantsLabel = new JLabel("Group A");
            messageModel = new DefaultListModel();
            list = new JList(messageModel);
            addButton = new JButton("Add");
            leaveButton = new JButton("Leave");
            text = new JTextField(15);
            sendButton = new JButton("Send");
            
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
            
            // list is located on the middle of the window
            add(new JScrollPane(list), BorderLayout.CENTER);
            
            // sendPane is located on the bottom of the window 
            JPanel sendPane = new JPanel(new BorderLayout());
            sendPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            sendPane.add(text, BorderLayout.CENTER);
            sendPane.add(sendButton, BorderLayout.EAST);
            
            add(sendPane, BorderLayout.SOUTH);
                      
        }
        
        public void setListModel(DefaultListModel model) {
        	this.messageModel = model;
        }
        
        public DefaultListModel getListModel() {
        	return this.messageModel;
        }
    }

    // =========================================================================
    class DirectoryView extends JPanel {
        JLabel userLabel;
        JLabel nameLabel;
        JTextField searchField;
        DefaultListModel listModel;
        JList list;
        JButton logoutButton;
        JButton createConversationButton;
        JButton adminButton;

        DirectoryView() {
        	userLabel = new JLabel("UserID");
        	nameLabel = new JLabel("RealName");
            listModel = new DefaultListModel();
            list = new JList(listModel);
            searchField = new JTextField(15);
            logoutButton = new JButton("Log Out");
            createConversationButton = new JButton("Create Conversation");
            adminButton = new JButton("Admin");
            // user is admin
            adminButton.setVisible(true);
            
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // userPanel is located upper side of the window
            JPanel userPane = new JPanel(new GridBagLayout());
            GridBagConstraints gridConst = new GridBagConstraints();
            gridConst.insets = new Insets(5, 5, 5, 5);
            gridConst.fill = GridBagConstraints.HORIZONTAL;
            
            
            gridConst.gridx = 0;
            gridConst.gridy = 0;
            userPane.add(userLabel, gridConst);
            
            gridConst.gridy = 1;
            userPane.add(nameLabel, gridConst);
            
            gridConst.gridy = 2;
            userPane.add(searchField, gridConst);
            
            gridConst.gridx = 1;
            gridConst.gridy = 0;
            gridConst.gridheight = 2;
            userPane.add(logoutButton, gridConst);
           
            add(userPane, BorderLayout.NORTH);
            
            
            // list is located center of the window with JScrollPane
            add(new JScrollPane(list), BorderLayout.CENTER);
        
            
            // buttonPane is located at the bottom side of the window
            JPanel buttonPane = new JPanel(new FlowLayout());
            buttonPane.add(createConversationButton);
            buttonPane.add(adminButton);
            
            add(buttonPane, BorderLayout.SOUTH);
        }
           
        
        public void setListModel(DefaultListModel model) {
        	this.listModel = model;
        }
        
        public DefaultListModel getListModel() {
        	return this.listModel;
        }
    }

    // =========================================================================
    class ConversationListView extends JPanel {
        JTextField searchField;
        DefaultListModel listModel;
        JList list;
        

        ConversationListView() {
        	searchField = new JTextField(15);
            listModel = new DefaultListModel();
            list = new JList(listModel);
            
            // searchField is located on the upper of the window.
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(searchField, BorderLayout.NORTH);
            
            // list is located on the middle of the window.
            add(new JScrollPane(list), BorderLayout.CENTER);
        }
        
        public void setListModel(DefaultListModel model) {
        	this.listModel = model;
        }
        
        public DefaultListModel getListModel() {
        	return this.listModel;
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
