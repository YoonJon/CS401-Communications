package client;

import shared.networking.User.UserInfo;
import shared.enums.*;
import shared.payload.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.event.*;

public class ClientUI {

    /** Last time a global key/mouse AWT event was observed (UI “user active”). */
    private volatile long lastUserActivityMillis = System.currentTimeMillis();

    private ClientController controller;
    private JFrame frame;
    private ScreenCards cards;
    /** Fires on the EDT every 5s to compare wall clock to {@link #lastUserActivityMillis}. */
    private final Timer userIdlePollTimer;

    private static final int USER_IDLE_POLL_MS = 5_000;
    /** Optional: end session after this much in-app UI idle time (0 disables). Default 30 minutes. */
    private static final long USER_IDLE_LOGOUT_AFTER_MS = 30L * 60L * 1000L;

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
            e -> {
                lastUserActivityMillis = System.currentTimeMillis();
            },
            AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
        );
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

    public void showLoginView() { cards.layout.show(cards, "login"); }
    public void showRegisterView() { cards.layout.show(cards, "register"); }
    public void showMainView() { cards.layout.show(cards, "main"); }
    public void showAdminMainView() {
        cards.main.directoryView.adminButton.setVisible(true);
        cards.layout.show(cards, "main");
    }

    public void showRegisterError(RegisterStatus registerStatus) {
    	// need to identify which status???
        JOptionPane.showMessageDialog(frame, "Register is not completed. Start over again.");
    }

    public void showLoginError(LoginStatus loginStatus) {
    	// identify the cause??
    	JOptionPane.showMessageDialog(frame, "Log in is failed. Start over again.");
    }


    public void chooseMainView() { showMainView(); }
    public void chooseLoginView() { showLoginView(); }
    public void chooseRegisterView() { showRegisterView(); }

    public DefaultListModel getDirectoryViewModel() { return cards.getMainDirectoryListModel(); }
    public void setDirectoryViewModel(DefaultListModel model) {cards.setMainDirectoryListModel(model); }
    public DefaultListModel<Conversation> getConversationViewModel() { return cards.getMainConversationMessageListModel(); }
    public void setConversationViewModel(Conversation currentCon) {cards.setMainConversationMessageListModel(currentCon); }
    public DefaultListModel getConversationListViewModel() { return cards.getMainConversationListModel(); }
    public void setConversationListViewModel(DefaultListModel model) {cards.setMainConversationListModel(model); }
    public DefaultListModel getSelectUserWindowModel() { return selectUserWindow.model; }
    public DefaultListModel getAdminConversationSearchWindowModel() { return adminConversationSearchWindow.model; }

    public void setDirectoryQuery(String query) { cards.main.directoryView.searchField.setText(query); }
    public String getDirectoryQuery() { return cards.main.directoryView.searchField.getText(); }

    public void setConversationQuery(String query) { cards.main.conversationView.text.setText(query); }
    public String getConversationQuery() { return cards.main.conversationView.text.getText(); }

    public void setAdminConversationQuery(String q) { adminConversationSearchWindow.searchField.setText(q); }
    public String getAdminConversationQuery() { return adminConversationSearchWindow.searchField.getText(); }

    public boolean isSelectingUsers() { return cards.isSelectingUser(); }
    public boolean isAdminSearchingConversation() { return cards.isAdminSearching(); }

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
        
        public void setMainConversationMessageListModel(Conversation currConv) {
        	main.setMessageListModel(currConv);
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
        
        public Boolean isSelectingUser() {
        	return main.isSelectingUser();
        }
        
        public Boolean isAdminSearching() {
        	return main.isAdminSearching();
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
        
        public void setMessageListModel(Conversation model) {
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
        
        public Boolean isSelectingUser() {
        	return directoryView.isCreatingConversation() || conversationView.isAddingUser();
        }
        
        public Boolean isAdminSearching() {
        	return directoryView.isAdminSearching();
        }
    }

    // =========================================================================
    class ConversationView extends JPanel {
        JLabel participantsLabel;
        DefaultListModel<Message> messageModel = new DefaultListModel<>();
        JList<Message> list = new JList<>(messageModel);
        JButton addButton;
        JButton leaveButton;
        JTextField text;
        JButton sendButton;
        JDialog addDialog;

        ConversationView() {
        	participantsLabel = new JLabel();
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
            
            addButton.addActionListener(e -> {
            	
            	if (addDialog != null && addDialog.isVisible()) {
            		addDialog.toFront();
            		addDialog.requestFocus();
                    return;
                }

                SelectUserWindow panel = new SelectUserWindow();

                addDialog = new JDialog(frame, "Select User", false);
                addDialog.add(panel);
                addDialog.setSize(300, 400);
                addDialog.setLocationRelativeTo(frame);
                addDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                addDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        addDialog = null;
                    }
                });

                addDialog.setVisible(true);
                      	
            });
                      
        }
        
        public void setListModel(Conversation currConv) {
        	String member = "";
        	for(int i = 0; i < currConv.getParticipants().size(); i++) {
        		if(i != 0) {
        			member += ", ";
        		}
        		member += currConv.getParticipants().get(i).getName();
        	}
        	
        	participantsLabel.setText(member);
        	
        	for(int i = 0; i < currConv.getMessages().size(); i++) {
        		messageModel.addElement(currConv.getMessages().get(i));
        	}
        }
        
        public DefaultListModel getListModel() {
        	return this.messageModel;
        }
        
        public Boolean isAddingUser() {
        	return addDialog.isVisible();
        }
    }

    // =========================================================================
    class DirectoryView extends JPanel {
        JLabel userLabel;
        JLabel nameLabel;
        JTextField searchField;
        DefaultListModel listModel = new DefaultListModel<UserInfo>();
        JList<UserInfo> list = new JList<>(listModel);
        JButton logoutButton;
        JButton createConversationButton;
        JButton adminButton;
        JDialog createDialog;
        JDialog adminDialog;

        DirectoryView() {
        	userLabel = new JLabel(controller.getCurrentUserInfo().getUserId());
        	nameLabel = new JLabel(controller.getCurrentUserInfo().getName());
            
            
            searchField = new JTextField(15);
            logoutButton = new JButton("Log Out");
            createConversationButton = new JButton("Create Conversation");
            
            listModel.addElement(new UserInfo());
                        
            // user is admin
            if(controller.getCurrentUserInfo().getUserType().equals(UserType.ADMIN)) {
            	adminButton = new JButton("Admin");
            	adminButton.setVisible(true);
            }
                      
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
            
            // add action for searchField
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { filter(); }
                public void removeUpdate(DocumentEvent e) { filter(); }
                public void changedUpdate(DocumentEvent e) { filter(); }

                private void filter() {
                    String text = searchField.getText().toUpperCase();
                    listModel.clear();

                    for (UserInfo item : controller.getCurrentDirectory()) {
                        if (item.getName().contains(text)) {
                            listModel.addElement(item);
                        }
                    }
                }
            });
            
        
            // add action to the create conversation button
            createConversationButton.addActionListener(e -> {
            	
            	if (createDialog != null && createDialog.isVisible()) {
                    createDialog.toFront();
                    createDialog.requestFocus();
                    return;
                }

                SelectUserWindow panel = new SelectUserWindow();

                createDialog = new JDialog(frame, "Select User", false);
                createDialog.add(panel);
                createDialog.setSize(300, 400);
                createDialog.setLocationRelativeTo(frame);
                createDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

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

            	AdminConversationSearchWindow panel = new AdminConversationSearchWindow();

                adminDialog = new JDialog(frame, "Searching Conversations", false);
                adminDialog.add(panel);
                adminDialog.setSize(300, 400);
                adminDialog.setLocationRelativeTo(frame);
                adminDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                adminDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                    	adminDialog = null;
                    }
                });
                
                adminDialog.setVisible(true); 
            	
            });
            
            // add action for selecting an item from the list
            list.addListSelectionListener(e-> {
            	if (!e.getValueIsAdjusting()) {
					 UserInfo selectedUser = list.getSelectedValue();
					 if (selectedUser != null) {
					  
					 }
            	}
            });
            
        }           
        
        public void setListModel(DefaultListModel model) {
        	this.listModel = model;
        }
        
        public DefaultListModel getListModel() {
        	return this.listModel;
        }

        public Boolean isCreatingConversation() {
        	return createDialog.isVisible();
        }

        public Boolean isAdminSearching() {
        	return createDialog.isVisible();
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
            
            // add action for searchField
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { filter(); }
                public void removeUpdate(DocumentEvent e) { filter(); }
                public void changedUpdate(DocumentEvent e) { filter(); }

                private void filter() {
                    String text = searchField.getText().toUpperCase();
                    listModel.clear();

                    for (UserInfo item : controller.getCurrentDirectory()) {
                        if (item.getName().contains(text)) {
                            listModel.addElement(item);
                        }
                    }
                }
            });
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
            removeButton = new JButton("Remove");
            okButton = new JButton("OK");
            cancelButton = new JButton("Cancel");
            
            // List located on the middle of the window
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(new JScrollPane(list), BorderLayout.CENTER);
            
            // buttons are located on the bottom of the window
        	JPanel buttonPane = new JPanel(new FlowLayout());
        	buttonPane.add(okButton);
        	buttonPane.add(cancelButton);
        	
        	add(buttonPane, BorderLayout.SOUTH);
        	
            okButton.addActionListener(e -> {
            	// sent the list
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();            
            });
            
            cancelButton.addActionListener(e -> {
            	// clear the list
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();  
            });
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
            searchField = new JTextField(15);
            okButton = new JButton("OK");
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
        	
        	
            okButton.addActionListener(e -> {
            	// sent the list
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();            
            });
            
            cancelButton.addActionListener(e -> {
            	// clear the list
                Window window = SwingUtilities.getWindowAncestor(this);
                window.dispose();  
            });
        }
        
    }
}
