# Software Requirements Specification

# Revision History
## Date | Version | Description | Author
- 02/13/2026 | 1.0 | Initial Version                                                                       | Quan
- 02/15/2026 | 2.0 | Add Section 1,2,3, and 4                                                              | Quan
- 02/21/2026 | 2.1 | Modified Section 1,2,3 and 4                                                          | Quan
- 02/27/2026 | 3.0 | Add Use Case Diagram                                                                  | Harumi Ueda
- 03/03/2026 | 4.0 | Added Class Candidate Diagram                                                         | Jon Yoon
- 03/05/2026 | 4.1 | Modified UC-03                                                                        | Harumi Ueda
- 04/04/2026 | 5.1 | Added 3.1.3.18 and modified UC-01, 02, 07, and 08                                     | Jon Yoon
- 04/09/2026 | 5.2 | Modified 2.5; revised 4.1.8; fixed minor spelling mistakes                              | Jon Yoon
- 04/09/2026 | 5.3 | Modified clientUI class, View components, UC-07, 08, and 11                           | Harumi Ueda

- 05/01/2026 | 5.4 | Added 3.1.2.21; fixed requirement refs, revision note, payloads, UC names, and typos   | Team
- 05/01/2026 | 5.5 | Client UI/controller model cleanup; admin search behavior note; naming consistency        | Team

# Table of Contents

[1. Purpose](#1-purpose)  
- [1.1 Scope](#11-scope)
- [1.2 Definitions, Acronyms, Abbreviations](#12-definitions-acronyms-abbreviations)
- [1.3 References](#13-references)
- [1.4 Overview](#14-overview)

[2. Overall Description](#2-overall-description)
- [2.1 Product Perspective](#21-product-perspective)
- [2.2 Product Architecture](#22-product-architecture)
- [2.3 Product Functionality/Features](#23-product-functionalityfeatures)
- [2.4 Constraints](#24-constraints)
- [2.5 Assumptions and Dependencies](#25-assumptions-and-dependencies)

[3. Specific Requirements](#3-specific-requirements)
- [3.1 Functional Requirements](#31-functional-requirements)
- [3.2 External Interface Requirements](#32-external-interface-requirements)
- [3.3 Internal Interface Requirements](#33-internal-interface-requirements)

[4. Non-Functional Requirements](#4-non-functional-requirements)
- [4.1 Security and Privacy Requirements](#41-security-and-privacy-requirements)
- [4.2 Environmental Requirements](#42-environmental-requirements)
- [4.3 Performance Requirements](#43-performance-requirements)

[5. System Architecture](#5-system-architecture)
- [5.1 Classes Diagram](#51-classes-diagram)
- [5.2 Classes](#52-classes)
- [5.3 Use Case Specification](#53-use-case-specification)
- [5.4 Use Case Diagrams](#54-use-case-diagrams)
- [5.5 Sequence Diagrams](#55-sequence-diagrams)


# 1. Purpose

This document outlines the requirements for the Communication System Application (CSA).

## 1.1. Scope
This project encompasses the design and implementation of a Java-based client–server internal messaging system operating over TCP/IP.

The system includes user authentication, private and group-based text messaging, synchronous and asynchronous communication support, persistent conversation logging, and administrative oversight capabilities.

The server functions as the authoritative source of truth, managing user accounts, sessions, message distribution, and data synchronization.

All data must be stored using text-based file persistence without the use of external databases, frameworks, libraries, or web technologies.

The client application provides a graphical interface for authentication and message interaction.

The project deliverables include complete Java source code, JUnit-based unit testing, formal documentation, version-controlled repository management, and a structured project presentation.
 
## 1.2. Definitions, Acronyms, Abbreviations
### TCP/IP (Transmission Control Protocol / Internet Protocol)
A standardized suite of communication protocols used to establish reliable network communication between client and server applications.

### Client Application
The Java-based graphical user interface (GUI) program used by employees to access the communications system, authenticate, and exchange messages.

### Server Application
The centralized Java application responsible for managing user authentication, message storage, synchronization, session tracking, and persistent logging.  
It acts as the system's authoritative source of truth.

### GUI (Graphical User Interface)
The visual interface through which users interact with the client application, including login screens, conversation views, and message input fields.

### Synchronous Communication
Real-time message exchange where users who are logged in receive messages immediately.

### Asynchronous Communication
Message exchange where messages sent to offline users are stored on the server and delivered when the recipient logs in.

### Private Conversation
A text-based communication channel between two authorized users.

### Group Conversation
A text-based communication channel involving three or more users.

### IT User / Administrator (Admin)
A privileged user role with oversight capabilities, including access to all logged conversations for monitoring and auditing purposes.

### Session
An authenticated connection between a client application and the server representing an active logged-in user.

### Persistence
The process of permanently storing system data (e.g., users, messages, conversations) in text-based files on the server.

### Source of Truth
The server's authoritative role in maintaining and validating all official system data, including user credentials, conversation records, and login states.

### Immutable Data
Data that cannot be modified after creation. In this system, once a message is sent and logged, it cannot be edited or deleted.

### JUnit
A Java testing framework used to create and execute automated unit tests validating system functionality.

### Concurrency
The ability of the server to handle multiple client connections simultaneously using multithreading.

### Socket
A Java networking endpoint used to establish TCP/IP communication between the client and server.

### Directory
The collection of all registered users within the organization, searchable by authorized users.

## 1.3. References
[Use Case Specification document](#53-use-case-specification)

[Use Cases Diagram](#54-use-case-diagrams)

[UML Class Candidates Diagram](#51-classes-diagram)

[UML Sequence Diagrams](#55-sequence-diagrams)

## 1.4. Overview
This project involves the development of a distributed internal communications platform designed for use within a large organization. 

The system will enable authenticated employees to exchange text-based messages through private and group conversations using a graphical desktop client application connected to a centralized server over TCP/IP. 

The platform supports both real-time message delivery for active users and buffered delivery for offline users, ensuring continuous communication.

The server manages authentication, session control, message synchronization, and permanent conversation logging, while administrative users are granted oversight access to all communications.

The project emphasizes the implementation of networking, concurrency, data management, and file-based persistence using core Java technologies within a structured software engineering framework.

# 2. Overall Description
## 2.1. Product Perspective
The proposed system is a standalone, distributed internal communications platform developed specifically for use within a large organizational environment. 

Consistent with the Project Overview and Scope, the product follows a centralized client–server architecture in which a Java-based desktop client application communicates with a dedicated server over TCP/IP. 

The server operates as the authoritative source of truth, maintaining all user accounts, session states, conversation records, and message logs, while ensuring synchronized data distribution to connected clients. 

The client application functions solely as a user interaction interface, providing authentication, conversation management, and message exchange capabilities with minimal local data retention.

The system does not integrate with external databases, third-party services, web technologies, or external frameworks, and instead relies exclusively on core Java libraries for networking, concurrency management, data structures, and file-based persistence. 

All data storage is performed through structured text-based files managed by the server. Administrators are granted elevated privileges that allow oversight access to all communications, consistent with organizational policies that limit end-user privacy expectations. 

Overall, the product is designed as a self-contained enterprise messaging solution that emphasizes centralized control, persistent logging, structured communication, and strict adherence to implementation constraints defined in the project scope.

## 2.2. Product Architecture
The system will be organized into **two major modules**: the **Client module** and the **Server module**.

The **Client module** is responsible for providing the graphical user interface (GUI), handling user authentication input, conversation navigation, message composition, and displaying synchronized data received from the server while maintaining minimal local state. 

**The Server module** serves as the centralized core of the system and the authoritative source of truth, managing user accounts, authentication, session control, message processing, conversation logging, administrative oversight, concurrency handling, and text-based persistent storage. 

The architecture follows standard object-oriented design principles, ensuring clear separation of concerns, encapsulation of responsibilities, modular class structure, and maintainable communication between components via well-defined TCP/IP interfaces.


## 2.3. Product Functionality/Features
### Client-Side Features
- User account creation using Employee ID
- Verification of Employee ID ownership through name confirmation
- Unique login name and password selection during registration
- User login with login name and password
- User logout
- TCP/IP connection to centralized server (company network or Wi-Fi)
- Receive synchronized data from server in text form
- Single active message input buffer
- Edit message text prior to sending
- Send text message to selected conversation
- Message immutability after transmission
- Search directory of users by name
- Initiate new private conversation
- Create new group conversation with selected participants
- Add participants to an existing group conversation
- Leave a conversation
- View conversations ordered by most recent activity
- Search conversations by participant
- Restrict conversation search to participating users (admin override allowed)
- View message history within selected conversation
- View unread messages received while logged out
- GUI-based interaction interface
- Distinct GUI interface for administrative users
- Conversations private by default

### Server-Side Features
- Creation and storage of user objects
- Enforcement of unique Employee ID and login name
- Authentication of login name and password
- Tracking of active user sessions
- Enforcement of single active login session per user
- Logout handling (user-initiated or server-initiated)
- Persistent storage of user data
- Persistent storage of conversation and message logs
- Synchronization of incoming data from multiple clients
- Immediate distribution of new messages to logged-in participants
- Buffering of messages for offline users
- Delivery of buffered messages upon user login
- Maintenance of conversation participant lists
- Administrative visibility of all conversations
- Optional hidden admin participation in conversations
- Use of immutable message data structures
- Indexing of users and conversations using hashed data structures
- Use of unique internal numeric IDs for efficient data access
- Centralized control as system source of truth
- Persistent server-side data storage in text format only
- Server execution without runtime control interface


## 2.4. Constraints
- The system shall be implemented exclusively in Java using only the standard Java libraries.
- The system shall not use any external libraries, frameworks, or third-party dependencies.
- The system shall not use any database management system; all persistent data shall be stored using server-managed text-based files.
- The system shall use a TCP/IP socket-based client–server architecture; communication shall not rely on HTTP, web services, or browser-based technologies.
- The client application shall provide access through a desktop GUI only; command-line-only interaction is not permitted for normal use.
- The client shall maintain only one active message input buffer at any time, ensuring that only one unsent text field is editable concurrently.
- Client-side storage shall be runtime-only; the client shall not persist user, conversation, or message records beyond program execution.
- Server-stored communication data (e.g., messages and logs) shall be immutable once recorded, meaning sent messages cannot be edited or deleted.
- The server shall be the authoritative source of truth for all user identity data, session state, and message synchronization; the client shall not make independent state decisions.
- The system shall support role-based interfaces, requiring a distinct GUI experience for administrative users versus standard users.
- Conversations shall be private by default, with access restricted to participants unless overridden by authorized administrative privileges.
- The system shall use unique numeric identifiers internally to streamline storage, indexing, and retrieval operations.


## 2.5. Assumptions and Dependencies
### Assumptions
- The organization issues unique Employee IDs in advance; Employee ID values required for account creation are available to the server for verification.
- Login name and Employee ID uniqueness will be enforced by the server at account-creation time.
- The target deployment is a single authoritative server (host device) on the organizational network; the server machine provides sufficient CPU, memory, and disk resources for the expected load.
- Typical message payloads are text-only. The system will not be required to support file attachments, images, or multimedia.
- Network connectivity between clients and the server is reliable LAN/Wi-Fi with low latency for synchronous delivery; occasional disconnections are expected and handled via server-side buffering for offline delivery.
- There will be one active session per user enforced by the server; concurrent logins from multiple devices are rejected.
- Administrative users (IT) and the list of admin accounts are predetermined and available to the server for role assignment at account creation.
- Clients are desktop machines capable of running a Java GUI and have a standard keyboard/mouse interface.
- Timekeeping for timestamps uses the server clock as the authoritative time source; clients do not supply authoritative timestamps.
- All persistent data required by the system (users, conversations, messages) will remain text-based files on the server filesystem; no external DBs or services will be used.

### Dependencies
- Java SE Development Kit (JDK) — a specific supported version will be required on both server and client machines to compile and run the applications.
- Standard Java runtime libraries only — the implementation depends exclusively on Java SE libraries (e.g., java.net, java.io, java.util, java.nio, Swing/JavaFX) and must not rely on third-party jars.
- JUnit (or the standard Java unit-testing framework available in the environment) for unit tests; test execution assumes an appropriate test runner is available.
- Operating system support — server and client run on common desktop/server OSs (Windows 10/11, Ubuntu Linux, or macOS).
- TCP/IP network stack — functioning network between clients and server (fixed IP or resolvable hostname) and the availability of a configurable TCP port for server socket binding.
- Version control — a Git-compatible environment for source control and submission (the project deliverable assumes a Git repository).
- No external persistence services — the system must not depend on external databases, cloud services, or third-party authentication providers.
- Administrator provisioning mechanism — a simple configuration file or server-side list used to define accounts that are granted administrative privileges at account creation.


# 3. Specific Requirements
## 3.1. Functional Requirements
### 3.1.1. Common Requirements:
3.1.1.1 The system shall follow a client–server architecture using TCP/IP socket communication.

3.1.1.2 The system shall be implemented entirely in Java using only standard Java SE libraries.

3.1.1.3 The system shall not use external libraries, frameworks, databases, or web technologies.

3.1.1.4 The Server shall act as the authoritative source of truth for all user, session, conversation, and message data.

3.1.1.5 All persistent data shall be stored in structured text-based files on the Server.

3.1.1.6 All internal entities (users, conversations) shall use unique numeric identifiers generated by the Server.

3.1.1.7 Employee ID and login name values shall be unique within the system.

3.1.1.8 Once a message is sent and stored by the Server, it shall be immutable.

3.1.1.9 The system shall support both synchronous (real-time) and asynchronous (buffered) message delivery.

3.1.1.10 The system shall enforce role-based access control with two roles: User and Administrator.

3.1.1.11 All user interactions shall be performed through a graphical user interface on the Client side.

### 3.1.2 Client Module Requirements:
3.1.2.1 The Client shall provide a graphical user interface (GUI) for all user interactions.

3.1.2.2 The Client shall allow a user to create a new account using a valid Employee ID.

3.1.2.3 The Client shall allow verification of Employee ID ownership through name confirmation.

3.1.2.4 The Client shall allow the user to select a unique login name and password during registration.

3.1.2.5 The Client shall allow login using login name and password.

3.1.2.6 The Client shall allow users to log out of the system.

3.1.2.7 The Client shall establish and maintain a TCP/IP connection to the Server.

3.1.2.8 The Client shall provide exactly one active message input buffer at any time.

3.1.2.9 The Client shall allow modification of text within the input field prior to sending.

3.1.2.10 The Client shall transmit message content to the Server for processing and storage.

3.1.2.11 The Client shall display conversations ordered by most recent activity.

3.1.2.12 The Client shall display the most recent conversations up to a fixed maximum count defined in client configuration.

3.1.2.13 The Client shall display message history within a selected conversation.

3.1.2.14 The Client shall visually indicate unread messages within conversations.

3.1.2.15 The Client shall allow users to search the directory by name.

3.1.2.16 The Client shall allow users to start a new private conversation.

3.1.2.17 The Client shall allow users to create a new group conversation.

3.1.2.18 The Client shall allow users to add participants to an existing group conversation.

3.1.2.19 The Client shall allow users to leave a conversation.

3.1.2.20 The Client shall provide a distinct interface for administrative users.


3.1.2.21 The Client shall allow administrative users to search conversations by participant and to join a selected conversation through the administrative interface.

### 3.1.3. Server Module Requirements:
3.1.3.1 The Server shall accept and manage concurrent TCP/IP connections from multiple Clients.

3.1.3.2 The Server shall create, store, and manage User accounts, including role assignment (User or Administrator).

3.1.3.3 The Server shall verify Employee ID ownership during account creation and enforce uniqueness of login name.

3.1.3.4 The Server shall track and manage active user sessions, enforcing a single active session per non-administrative user.

3.1.3.5 The Server shall record user logouts and properly terminate associated session state.

3.1.3.6 The Server shall create, store, and manage Conversations, including private and group conversations.

3.1.3.7 The Server shall process and persist all Messages associated with conversations.

3.1.3.8 The Server shall use efficient data structures to enable fast searching and retrieval of users, conversations, and messages.

3.1.3.9 The Server shall distribute new messages and conversation updates to all connected participants without unnecessary delay.

3.1.3.10 The Server shall buffer messages for offline users and deliver them upon their next successful login.

3.1.3.11 The Server shall enforce conversation participant policies, including:

    3.1.3.11.1 Adding new members to existing group conversations.

    3.1.3.11.2 Creating new group conversations

3.1.3.12 The Server shall record per-user read status for each conversation to support unread message indicators.

3.1.3.13 The Server shall support user and conversation search functionality, restricting results to participating users except for administrative accounts, which may access all conversations.

3.1.3.14 The Server shall generate authoritative timestamps for all messages and system events.

3.1.3.15 The Server shall persistently store all users, conversations, messages, participant changes, and read-status data in structured text files.

3.1.3.16 The Server shall load all required data from persistent storage to restore the system to its previous operational state upon restart.

3.1.3.17 The Server shall not include a runtime GUI and shall be configured and launched using predefined configuration files or startup parameters.

3.1.3.18 The Server shall enforce the uniqueness of private conversations.

## 3.2. External Interface Requirements
3.2.1 The system shall provide a network interface using TCP/IP socket communication between the Client and Server modules.

3.2.2 The Server shall listen on a configurable TCP port and accept connection requests from authorized Client applications.

3.2.3 The Client shall establish a socket connection to the Server using a specified IP address (or hostname) and port number.

3.2.4 The system shall interface with the host operating system's file system to persist user data, conversation records, and message logs in structured text files.

3.2.5 The system shall operate within a Java SE runtime environment and shall not interface with external databases, third-party APIs, or enterprise systems.

3.2.6 The Client shall provide a desktop graphical user interface for all user interactions.

## 3.3 Internal Interface Requirements
3.3.1 The Client module shall communicate with the Server module using a structured request–response protocol defined by the system.

3.3.2 The communication protocol shall define standardized message formats for operations including user registration, authentication, message transmission, conversation management, search operations, and logout.

3.3.3 The Server shall ensure that internal data structures representing Users, Conversations, Messages, and Sessions are consistently accessed through controlled interfaces to maintain data integrity.

3.3.4 The Server shall manage synchronization between internal components to ensure thread-safe operation during concurrent client interactions.


# 4. Non-Functional Requirements
## 4.1. Security and Privacy Requirements
4.1.1 The system shall require user authentication (Login) before granting access to messaging features.

4.1.2 The system shall enforce uniqueness of Employee ID and login name during account creation.

4.1.3 The system shall securely store user passwords.

4.1.4 The system shall enforce role-based access control with at least two roles: User and Administrator.

4.1.5 The system shall restrict access to conversations to participating users, except for authorized administrative users.

4.1.6 The system shall enforce a single active session per non-administrative user account.

4.1.7 The system shall validate all client requests before processing them.

4.1.8 The system shall ensure that stored messages are immutable and cannot be modified or deleted.

4.1.9 The system shall maintain data integrity during concurrent access and file storage operations.

4.1.10 The system shall ensure that only authenticated clients may establish active messaging sessions with the Server.

## 4.2. Environmental Requirements
4.2.1 The system shall operate in environments that support Java SE (compatible JDK installed on both Client and Server machines).

4.2.2 The Client application shall run on desktop operating systems capable of supporting a Java-based graphical user interface.

4.2.3 The Server application shall run on a host machine with continuous network connectivity.

4.2.4 The Server shall be deployable on standard desktop or Linux-based server infrastructure.

4.2.5 The system shall operate within an internal organizational network environment (LAN or Wi-Fi).

4.2.6 The Server host machine shall provide sufficient disk storage for persistent text-based data files.

4.2.7 The system shall not require installation of external database management systems, web servers, or third-party frameworks.

4.2.8 The system shall not depend on cloud infrastructure or external enterprise services.

## 4.3. Performance Requirements
4.3.1 The system shall handle concurrent active client connections while maintaining system stability, responsiveness, and reliable message delivery under standard operational conditions.

4.3.2 The Server shall guarantee data persistence without incurring noticeable service slowdown.

4.3.3 The Client interface shall remain responsive during message transmission and reception operations.

4.3.4 The Server shall process valid requests in a non-blocking manner to prevent system-wide stalls.

4.3.5 Message delivery to connected users shall occur promptly upon server acceptance.

4.3.6 The Server shall handle multiple client requests at the same time without errors or delays.

4.3.7 The Server shall use proper synchronization mechanisms to prevent conflicts when multiple clients access shared data simultaneously.

4.3.8 The Server shall ensure that concurrent operations do not corrupt or overwrite shared data.

4.3.9 File storage operations shall not delay or block other unrelated client requests.

4.3.10 The system shall ensure thread-safe access to shared data structures using controlled synchronization techniques.

4.3.11 The Server shall load only the necessary conversation data into memory to reduce overall memory usage.

4.3.12 The Client shall clear all temporary session data from memory after logout or session termination.


# 5. System Architecture
## 5.1. Classes Diagram

### System Diagram
![System diagram](Class%20Diagram/System%20diagram.png)

### GUI Diagram
![GUI diagram](Class%20Diagram/GUI%20diagram.png)

### Networking Diagram
![Networking diagram](Class%20Diagram/Networking%20diagram.png)

## 5.2. Classes

### Enums

```java
enum UserType
    USER,
    ADMIN

enum ConversationType
    PRIVATE,
    GROUP

enum RequestType
    MESSAGE,
    UPDATE_READ,
    REGISTER,
    LOGIN,
    LOGOUT,
    SEARCH_DIRECTORY,
    CREATE_CONVERSATION,
    ADD_PARTICIPANT,
    LEAVE_CONVERSATION,
    JOIN_CONVERSATION,
    ADMIN_CONVERSATION_QUERY,
    PING

enum ResponseType
    MESSAGE
    READ_UPDATED
    REGISTER_RESULT
    LOGIN_RESULT
    LOGOUT_RESULT
    DIRECTORY_RESULT
    CONVERSATION
    CONVERSATION_METADATA
    LEAVE_RESULT
    PONG
    CONNECTED

enum RegisterStatus
    SUCCESS,
    USER_ID_TAKEN,
    USER_ID_INVALID,
    LOGIN_NAME_TAKEN,
    LOGIN_NAME_INVALID

enum LoginStatus
    SUCCESS,
    INVALID_CREDENTIALS,
    NO_ACCOUNT_EXISTS,
    DUPLICATE_SESSION

enum ConnectionStatus
    CONNECTED,
    NOT_CONNECTED,
    CONNECTING
```

### Client-side Classes

The active directory, conversation list, and conversation panes are owned by **`MainView`** (`directoryView`, `conversationListView`, `conversationView` under `ScreenCards`). `ClientUI` coordinates card layout, login/register screens, and modal windows (`SelectUserWindow`, `AdminConversationSearchWindow`).

```
class ClientUI

- controller: ClientController
- frame: JFrame
- cards: ScreenCards
- registerView: RegisterView
- loginView: LoginView
- selectUserWindow: SelectUserWindow
- adminConversationSearchWindow: AdminConversationSearchWindow
- selectingUser: bool
- adminSearchingConversation: bool

+ ClientUI(controller: ClientController)
+ showLoginView(): void
+ showRegisterView(): void
+ showMainView(): void
+ showRegisterError(registerStatus: RegisterStatus): void
+ showLoginError(loginStatus: LoginStatus): void
+ showCreateConversationWindow(): void
+ showAdminConversationSearchWindow(): void
+ chooseMainView(): void
+ chooseLoginView(): void
+ chooseRegisterView(): void
+ getDirectoryViewModel(): DefaultListModel
+ getConversationViewModel(): DefaultListModel
+ getConversationListViewModel(): DefaultListModel
+ getSelectUserWindowModel(): DefaultListModel
+ getAdminConversationSearchWindowModel(): DefaultListModel
+ setDirectoryQuery(query: String): void
+ getDirectoryQuery(): String
+ setConversationQuery(query: String): void
+ getConversationQuery(): String
+ setAdminConversationQuery(q: String): void
+ getAdminConversationQuery(): String
+ isCreatingConversation(): bool
+ isAdminSearchingConversation(): bool

  <<nested>> class ScreenCards extends JPanel
  - layout: CardLayout
  - login: LoginView
  - register: RegisterView
  + ScreenCards()

  <<nested>> class RegisterView extends JPanel
  - userId: JTextField
  - loginName: JTextField
  - password: JPasswordField
  - passwordAgain: JPasswordField
  - name: JTextField
  - createButton: JButton
  - backButton: JButton
  
  + RegisterView()

  <<nested>> class LoginView extends JPanel
  - loginName: JTextField
  - passwordField: JPasswordField
  - loginButton: JButton
  - createButton: JButton
  
  + LoginView()

  <<nested>> class MainView extends JPanel
  - conversationView: ConversationView
  - directoryView: DirectoryView
  - conversationListView: ConversationListView
  
  + MainView()

  <<nested>> class ConversationView extends JPanel
  - participantsLabel: JLabel
  - listModel: DefaultListModel
  - list: JList<>(listModel)
  - addButton: JButton
  - leaveButton: JButton
  - text: JTextField
  - sendButton: JButton

  + ConversationView()

  <<nested>> class DirectoryView extends JPanel
  - userLabel: JLabel
  - searchField: JTextField
  - listModel: DefaultListModel<>
  - list: JList<>(listModel)
  - logoutButton: JButton
  - createConversationButton: JButton
  - adminButton: JButton

  + DirectoryView()

  <<nested>> class ConversationListView extends JPanel
  - participantsLabel: JLabel
  - messageModel: DefaultListModel<>
  - list: JList<>(messageModel)
  - addButton: JButton
  - leaveButton: JButton
  - text: JTextField
  - sendButton: JButton
  
  + ConversationListView()
  

  <<nested>> class SelectUserWindow extends JPanel
  - model: DefaultListModel<>
  - list: JList<>(model)
  - removeButton: JButton
  - okButton: JButton
  - cancelButton: JButton
  
  + SelectUserWindow()

  <<nested>> class AdminConversationSearchWindow extends JPanel
  - searchField: JTextField
  - model: DefaultListModel
  - list: JList<>(model)
  - okButton: JButton
  - cancelButton: JButton
  
  + AdminConversationSearchWindow()



class ClientController

- gui: ClientUI
- connectionStatus: ConnectionStatus
- requestQueue: Deque<Request>
- loggedIn: boolean
- currentUser: UserInfo
- hostIp: String
- hostPort: int
- socket: Socket
- conversations: ArrayList<Conversation>
- currentConversationID: String
- currentDirectory: ArrayList<UserInfo>
- currentConversationList: ArrayList<Conversation>
- currentAdminConversationSearch: ArrayList<Conversation>
- responseListenerThread: Thread
- inactivityDetectorThread: Thread

+ main(args: String[]): void
+ ClientController(hostIp: String, hostPort: int)
+ close(): void
- processResponse(response: Response): void
- ensureConnected(): void
+ register(userId: String, realName: String, loginName: String, password: String): void
+ login(loginName: String, password: String): void
+ logout(): void
+ sendMessage(conversationId: String, m: String): void
+ searchDirectory(query: String): void
+ searchConversationList(query: String): void
+ adminConversationSearch(query: String): void
+ createConversation(p: ArrayList<UserInfo>): void
+ addToConversation(p: ArrayList<UserInfo>, conversationId: String): void
+ leaveConversation(conversationId: String): void
+ adminGetUserConversations(userId: String): void
+ joinConversation(conversationId: String): void
+ getCurrentUserInfo(): UserInfo
+ getFilteredDirectory(query: String): ArrayList<UserInfo>
+ getFilteredConversationList(query: String): ArrayList<Conversation>
+ getFilteredAdminConversationSearch(query: String): ArrayList<Conversation>
+ setCurrentConversationID(conversationId: String): void
+ getCurrentConversationID(): String
+ getCurrentConversation(): Conversation
- sendRequest(r: Request): void
- enqueueRequest(r: Request): void

  <<nested>> class ResponseListener implements Runnable
  + run(): void
  + ResponseListener()

  <<nested>> class InactivityDetector implements Runnable
  + run(): void
  + InactivityDetector()

  <<nested>> class UserInfo
  - name: String
  - userId: String
  - userType: UserType
  - lastRead: Map<c_id: String, sequenceNumber: long>
  - UserInfo()
  + getName(): String
  + getUserId(): String
  + getUserType(): UserType
  + getLastRead(c_id: String): long
  + setLastRead(c_id: String, sequenceNumber: long): void
```

**Admin conversation search:** `adminGetUserConversations` issues a server request (`ADMIN_CONVERSATION_QUERY`) and refreshes the cached admin result set (`currentAdminConversationSearch`). `adminConversationSearch` filters that cache locally for the admin UI without an additional server round-trip.

### Server-side Classes

```
class ServerController

- dataFilePath: String
- activeSessions: Map<String, ConnectionHandler>
- dataManager: DataManager
- connectionListener: ConnectionListener

+ main(args: String[]): void
+ close(): void
+ ServerController(dataFilePath: String, port: int)
+ processRequest(request: Request): Response
- distributeResponse(r: Response, Set<userID: String>): void
- hasActiveSession(userId: String): boolean
- removeSession(userId: String): void
- addSession(userId: String, handler: ConnectionHandler): void


class DataManager

- usersByUserID: Map<String, User>
- usersByLoginName: Map<String, User>
- conversationsIDsByUserID: Map<String, Set<String>>
- userIDsByConversationID: Map<String, Set<String>>
- conversationsByConversationID: Map<String, Conversation>
- authorizedUsers: Map<String, String>
- authorizedAdminIds: List<String>
- dataFilePath: String

+ DataManager(dataFilePath: String)
+ close(): void
+ handleRegister(rc: RegisterCredentials): RegisterResult
+ handleLogin(lc: LoginCredentials): LoginResult
+ handleLogout(): void
+ handleSendMessage(rm: RawMessage): Message
+ handleUpdateReadMessages(u: UpdateReadMessages)
+ handleSearchDirectory(query: DirectoryQuery): DirectoryResult
+ handleCreateConversation(cc: CreateConversationPayload): Conversation
+ handleAddToConversation(atc: AddToConversationPayload): Conversation
+ handleLeaveConversation(lc: LeaveConversationPayload): LeaveResult
+ handleAdminConversationQuery(q: AdminConversationQuery): AdminConversationResult
+ handleJoinConversation(jc: JoinConversationPayload): Conversation
+ getParticipantList(c_id: String): ArrayList<UserInfo>


class User

- userId: String
- name: String
- loginName: String
- password: String
- userType: UserType
- lastRead: Map<c_id: String, sequenceNumber: long>

+ User(id: String, n: String, ln: String, p: String)
+ getUserId(): String
+ getName(): String
+ getLoginName(): String
+ getPassword(): String
+ getUserType(): UserType
+ getUserInfo(): UserInfo
+ getLastRead(c_id: String): long
+ setLastRead(c_id: String, sequenceNumber: long): void
+ fromFile(f: File): User
```

### Shared / Networking Components

```
class ConnectionListener

- threadPool: ExecutorService
- hostPort: int
- serverController: ServerController

+ ConnectionListener(hostPort: int, serverController: ServerController)
+ listen(): void
+ close(): void


class ConnectionHandler implements Runnable

- userInfo: UserInfo
- authenticated: boolean
- socket: Socket
- serverController: ServerController
- responseQueue: Queue<Response>
- requestThread: Thread
- responseThread: Thread
- lastPingReceived: long

+ ConnectionHandler(socket: Socket, serverController: ServerController)
+ run(): void
+ close(): void
+ sendResponse(res: Response): void
+ getUserInfo(): UserInfo
+ isAuthenticated(): boolean

  <<nested>> class RequestListener implements Runnable
  + run(): void
  + RequestListener()

  <<nested>> class ResponseSender implements Runnable
  + run(): void
```

### Requests & Responses

```
class Request implements Serializable

- type: RequestType
- payload: Payload

+ Request(t: RequestType, p: Payload)
+ getType(): RequestType
+ getPayload(): Payload


class Response implements Serializable

- type: ResponseType
- payload: Payload

+ Response(t: ResponseType, p: Payload)
+ getType(): ResponseType
+ getPayload(): Payload
```

### Payloads

```
interface Payload
```

#### Request Payloads

```
class RawMessage implements Payload
- text: String
- targetConversationId: String
+ RawMessage(t: String, c_id: String)
+ getText(): String
+ getTargetConversationId(): String

class ReadMessagesUpdated implements Payload

class LoginCredentials implements Payload
- loginName: String
- password: String
+ LoginCredentials(ln: String, p: String)
+ getLoginName(): String
+ getPassword(): String

class RegisterCredentials implements Payload
- userId: String
- loginName: String
- password: String
- name: String
+ RegisterCredentials(id: String, ln: String, p: String, n: String)
+ getUserId(): String
+ getLoginName(): String
+ getPassword(): String
+ getName(): String

class DirectoryQuery implements Payload
- query: String
+ DirectoryQuery(q: String)
+ getQuery(): String

class CreateConversationPayload implements Payload
- participants: ArrayList<UserInfo>
+ CreateConversationPayload(p: ArrayList<UserInfo>)
+ getParticipants(): ArrayList<UserInfo>

class AddToConversationPayload implements Payload
- participants: ArrayList<UserInfo>
- targetConversationId: String
+ AddToConversationPayload(p: ArrayList<UserInfo>, t: String)
+ getParticipants(): ArrayList<UserInfo>
+ getTargetConversationId(): String

class LeaveConversationPayload implements Payload
- targetConversationId: String
+ LeaveConversationPayload(t: String)
+ getTargetConversationId(): String

class JoinConversationPayload implements Payload
- targetConversationId: String
+ JoinConversationPayload(t: String)
+ getTargetConversationId(): String

class AdminConversationQuery implements Payload
- conversationQuery: String
+ AdminConversationQuery(q: String)
+ getQuery(): String
```

#### Response Payloads

```
class Conversation implements Payload
- conversationId: String
- messages: ArrayList<Message>
- participants: ArrayList<UserInfo>
- historicalParticipants: ArrayList<UserInfo>
- type: ConversationType
+ Conversation(c_id: String, t: ConversationType)
+ getConversationId(): String
+ getMessages(): ArrayList<Message>
+ getParticipants(): ArrayList<UserInfo>
+ getHistoricalParticipants(): ArrayList<UserInfo>
+ setConversationMetadata(cm: ConversationMetadata): void
+ getConversationMetadata(): ConversationMetadata
+ getType(): ConversationType
+ fromFile(f: File): Conversation
+ append(m: Message): void

class ConversationMetadata implements Payload
- conversationId: String
- participants: ArrayList<UserInfo>
- historicalParticipants: ArrayList<UserInfo>
- type: ConversationType
+ ConversationMetadata(c_id: String, p: ArrayList<UserInfo>, hp: ArrayList<UserInfo>, t: ConversationType)
+ getConversationId(): String
+ getParticipants(): ArrayList<UserInfo>
+ getHistoricalParticipants(): ArrayList<UserInfo>
+ getType(): ConversationType

class AdminConversationResult implements Payload
- result: ArrayList<ConversationMetadata>
+ AdminConversationResult(cm: ArrayList<ConversationMetadata>)
+ getConversations(): ArrayList<ConversationMetadata>

class Message implements Payload
- text: String
- sequenceNumberCounter: long
- sequenceNumber: long
- timestamp: Date
- senderId: String
- conversationId: String
+ Message(t: String, sn: long, time: Date, senderID: String, c_id: String)
+ getText(): String
+ getConversationId(): String
+ getSenderId(): String
+ getSequenceNumber(): long
+ getTimestamp(): Date

class UpdateReadMessages implements Payload
- conversationID: String
+ UpdateReadMessages(c_id: String)
+ getConversationID(): String

class RegisterResult implements Payload
- result: RegisterStatus
+ RegisterResult(r: RegisterStatus)
+ getRegisterStatus(): RegisterStatus

class LoginResult implements Payload
- result: LoginStatus
- userInfo: UserInfo
- conversationList: ArrayList<Conversation>
+ LoginResult(r: LoginStatus, ui: UserInfo, cl: ArrayList<Conversation>)
+ getLoginStatus(): LoginStatus
+ getUserInfo(): UserInfo
+ getConversationList(): ArrayList<Conversation>

class DirectoryResult implements Payload
- result: ArrayList<UserInfo>
+ DirectoryResult(r: ArrayList<UserInfo>)
+ getDirectoryResult(): ArrayList<UserInfo>

class LeaveResult implements Payload
- c_id: String
+ LeaveResult(c_id: String)
+ getLeftConversationID(): String
```

## 5.3. Use Case Specification

### Use Case ID: UC-01
#### Use Case Name: Create an Account

#### Relevant Requirements:
3.1.1.4

3.1.1.5

3.1.1.6

3.1.1.7

3.1.1.10

3.1.2.2

3.1.2.3

3.1.2.4

3.1.3.2

#### Primary Actor:
User

#### Pre-conditions:
User has a valid Employee ID already issued

#### Post-conditions:
A new user account is created

#### Basic Flow or Main Scenario:
1. User enters the correct real name and employee ID
2. Server verifies that Employee ID exists and matches the name. If not, repeat step 1.
3. User enters a login name and password.
4. Server checks that the login name is unique. If not unique, repeat step 3 with a new login name.
5. The system creates the account with the login name and password.
6. The system assigns the role based on the information.

#### Extensions or Alternate Flows:
None.

#### Exceptions:
User enters an invalid real name or employee ID

User enters an existing login name

Unable to establish TCP connection

User credentials data not found

#### Related Use Cases:
None

---

### Use Case ID: UC-02
#### Use Case Name: Log in to the System

#### Relevant Requirements:
3.1.1.4

3.1.1.9

3.1.1.10

3.1.2.5

3.1.2.7

3.1.3.3

3.1.3.4

3.1.3.5

4.1.1

4.1.6

4.1.7

4.3.1

#### Primary Actor:
User

#### Pre-conditions:
User has an existing registered account

There is no existing active session associated with the user's ID

#### Post-conditions:
User has an online status.

A session is created on the server.

Conversation history imported from the server

#### Basic Flow or Main Scenario:
1. User enters a valid login name and password.
2. Client sends request to server.
3. The server verifies the login name and password.
4. The system displays the main conversation interface.

#### Extensions or Alternate Flows:
None.

#### Exceptions:
User enters an invalid login name or password

There is a preexisting active session associated with the user's ID

Unable to establish TCP connection

User credentials data not found

#### Related Use Cases:
2, 3, 4, 5, 6, 7, 8, 9, 10, 11

---

### Use Case ID: UC-03
#### Use Case Name: Send a Message to a Conversation

#### Relevant Requirements:
3.1.1.8

3.1.1.9

3.1.2.8

3.1.2.9

3.1.2.10

3.1.3.7

3.1.3.9

3.1.3.10

3.1.3.14

3.1.3.15

4.1.9

4.3.2

4.3.6

#### Primary Actor:
User

#### Pre-conditions:
User is logged in

User has at least one existing conversation

User is viewing the conversation

#### Post-conditions:
Conversation is updated on the client and server.

#### Basic Flow or Main Scenario:
1. User selects an existing conversation.
2. System displays recent messages.
3. User enters text.
4. User presses the send button.

#### Extensions or Alternate Flows:
None

#### Exceptions:
Empty message

Unable to establish TCP connection

Conversation history data not found

#### Related Use Cases:
1, 2, 6, 7, 11

---

### Use Case ID: UC-04
#### Use Case Name: Browse User Directory

#### Relevant Requirements:
3.1.2.15

3.1.3.8

3.1.3.13

4.3.4

#### Primary Actor:
User

#### Pre-conditions:
User logged in

#### Post-conditions:
The client GUI displays matching user or "no result found" if there is no matching user

#### Basic Flow or Main Scenario:
1. Select the directory search box.
2. Enter the name of the desired employee.
3. The system returns the result.

#### Extensions or Alternate Flows:
None

#### Exceptions:
Unable to establish TCP connection.

Directory data not found

#### Related Use Cases:
1, 2, 7, 8, 11

---

### Use Case ID: UC-05
#### Use Case Name: Browse Conversation History

#### Relevant Requirements:
3.1.2.11

3.1.2.12

3.1.2.13

3.1.3.8

3.1.3.13

4.3.4

#### Primary Actor:
User

#### Pre-conditions:
User logged in

#### Post-conditions:
Results are displayed by order of most recent activity.

If user is not a participant of any conversation, the GUI displays an empty conversation history.

#### Basic Flow or Main Scenario:
1. User enters the name in the conversation history search box.
2. Client GUI displays conversations involving the searched user.
3. User selects the desired conversation for viewing.

#### Extensions or Alternate Flows:
None

#### Exceptions:
Unable to establish TCP connection

Conversation history data not found

#### Related Use Cases:
1, 2, 3, 6, 7, 8, 9, 11

---

### Use Case ID: UC-06
#### Use Case Name: View a Conversation

#### Relevant Requirements:
3.1.2.13

3.1.2.14

3.1.3.7

3.1.3.12

3.1.3.15

4.3.3

#### Primary Actor:
User

#### Pre-conditions:
User logged in

User is a participant of a conversation

#### Post-conditions:
The client GUI displays the message history of the selected conversation.

Unread messages are indicated when viewing a conversation for the first time since changes were made to the conversation.

#### Basic Flow or Main Scenario:
1. User selects a conversation.
2. The client GUI displays the message history of the selected conversation.
3. If the conversation is too large to be displayed in the GUI, the user should be able to scroll up or down to view more of the conversation history.

#### Extensions or Alternate Flows:
None

#### Exceptions:
Unable to establish TCP connection

Conversation history data not found

#### Related Use Cases:
1, 2, 3, 5, 7, 8, 9, 11

---

### Use Case ID: UC-07
#### Use Case Name: Create a New Conversation

#### Relevant Requirements:
3.1.2.16

3.1.2.17

3.1.3.6

3.1.3.11.2

3.1.3.18

3.1.3.15

3.1.1.6

4.1.5

#### Primary Actor:
User

#### Pre-conditions:
Client GUI is working

User is logged in and connected to the server

User exists in the system

At least one valid user is available to select

#### Post-conditions:
New conversation is created

Conversation stored on the server with a unique conversation ID

The participant list is updated

#### Basic Flow or Main Scenario:
Create private conversation:
1. User selects another user to create a private conversation by clicking on the desired user in the directory.
2. System checks if the private conversation already exists.
3. If not, the system creates a new private conversation with the user.
4. The system assigns a unique conversation ID.
5. The system stores the conversation.
6. GUI creates conversation box for each user.

#### Extensions or Alternate Flows:
Create multi-user conversation from scratch:
1. User clicks "Create Conversation" button.
2. GUI displays "Selecting users" window.
3. User selects desired participants by clicking on desired users in the directory.
4. User confirms selection by clicking OK button.
5. Server creates new group conversation.
6. Server assigns a unique conversation ID.
7. Server stores the conversation with participant list.
8. A new conversation is added to the user's conversation history, and the GUI displays the new conversation.

#### Exceptions:
Unable to establish TCP connection

User directory data not found

User conversation history data not found

#### Related Use Cases:
1, 2, 3, 5, 6, 8, 9, 11

---

### Use Case ID: UC-08
#### Use Case Name: Add User to Existing Group Conversation

#### Relevant Requirements:
3.1.2.18

3.1.3.6

3.1.3.11.2

3.1.3.15

4.1.5

#### Primary Actor:
User

#### Pre-conditions:
User is logged in

The group conversation exists

User is a participant in the conversation

The added user isn't a participant in the conversation

The user has selected and is viewing the desired conversation

#### Post-conditions:
The new user has a new conversation.

The system added the user to the existing conversation.

Conversation remains a part of the new user's conversation history.

#### Basic Flow or Main Scenario:
1. User selects a group conversation.
2. User selects the “add” on ConversationView.
3. The system shows SelectUserWindow.
4. User searches the name of the desired participant in the DirectoryView.
5. The user selects the user, and the system shows the addition of participants on SelectUserWindow.
6. User confirms selection by clicking OK button on SelectUserWindow.
7. The system adds the user to the conversation.
8. GUI displays the conversation for the added user.

#### Extensions or Alternate Flows:
None.

#### Exceptions:
No group conversation under the user's conversation history.

Unable to establish TCP connection

User directory data not found

User conversation history not found

#### Related Use Cases:
1, 2, 3, 5, 6, 7, 9, 11

---

### Use Case ID: UC-09
#### Use Case Name: Leave a Conversation

#### Relevant Requirements:
3.1.2.19

3.1.3.6

3.1.3.11

3.1.3.15

4.1.5

#### Primary Actor:
User

#### Pre-conditions:
User logged in

User is a participant in the conversation

User is currently viewing the desired conversation

#### Post-conditions:
User is not a participant of the conversation.

The conversation is no longer displayed on the client GUI for the user that left.

#### Basic Flow or Main Scenario:
1. User selects the conversation.
2. User clicks the "leave conversation" button.
3. The system removes the user from the conversation.

#### Extensions or Alternate Flows:
None.

#### Exceptions:
Unable to establish TCP connection

#### Related Use Cases:
1, 2, 3, 5, 6, 7, 11

---

### Use Case ID: UC-10
#### Use Case Name: Log Out from Client Application

#### Relevant Requirements:
3.1.2.6

3.1.3.4

3.1.3.5

4.1.6

#### Primary Actor:
User

#### Pre-conditions:
User has logged in status.

User has an active session.

#### Post-conditions:
The session is terminated.

The user's status is set to offline.

The client GUI displays the login prompt screen.

#### Basic Flow or Main Scenario:
1. User clicks the "Log Out" button.
2. The GUI displays login screen.

#### Extensions or Alternate Flows:
Case 1: Client application inactivity
1. Client application does not detect user input for a certain time threshold.
2. Client application automatically logs the user out.
3. The client GUI displays the login prompt screen.

Case 2: Networking issues
1. The client loses connection to the server.
2. The server detects the lost connection.
3. The server terminates the active session associated with the user.
4. The system sets the user's status to "offline."
5. The server logs the user out.
6. The client GUI displays the login prompt screen.

#### Exceptions:
Unable to establish TCP connection

#### Related Use Cases:
1, 2

---

### Use Case ID: UC-11
#### Use Case Name: Join a Conversation

#### Relevant Requirements:
3.1.1.10

3.1.2.21

3.1.3.6

3.1.3.11

3.1.3.13

3.1.3.17

4.1.4

4.1.5

4.1.8

#### Primary Actor:
Admin

#### Pre-conditions:
Admin user logged in

Conversation exists

#### Post-conditions:
Admin is a participant in the conversation.

The conversation is displayed in the Admin user's conversation history.

#### Basic Flow or Main Scenario:
1. Admin clicks “Admin” button in the Directory.
2. AdminConversationWindow is showing up.
3. Admin types the name of the user whom he wants to search for in the conversation searching box in the AdminConversationWindow.
4. Admin GUI displays the list of conversations sorted by conversation ID in AdminConversationWindow.
5. Admin selects the conversation.
6. The server adds the admin to the participant list.
7. The system displays the conversation messages.

#### Extensions or Alternate Flows:
None

#### Exceptions:
Unable to establish TCP connection

User conversation history data not found

User directory data not found

#### Related Use Cases:
1, 2, 3, 4, 5, 6, 7, 9

## 5.4. Use Case Diagrams

![Use Case Diagram](Use%20Case%20Diagram/Use%20Case%20Diagram.png)

## 5.5. Sequence Diagrams

### UC-01 Create Account
![UC-01 Create Account](Sequence%20diagrams/UC-01%20Create%20Account.png)

### UC-02 Log in to the System
![UC-02 Log in to the system](Sequence%20diagrams/UC-02%20Log%20in%20to%20the%20system.png)

### UC-03 Send a Message
![UC-03 Send a message](Sequence%20diagrams/UC-03%20Send%20a%20message.png)

### UC-04 Browse User Directory
![UC-04 Browse User Directory](Sequence%20diagrams/UC-04%20Browse%20User%20Directory.png)

### UC-05 Browse Conversation History
![UC-05 Browse conversation history](Sequence%20diagrams/UC-05%20Browse%20conversation%20history.png)

### UC-06 View a Conversation
![UC-06 View a Conversation](Sequence%20diagrams/UC-06%20View%20a%20Conversation.png)

### UC-07 Create a New Conversation
![UC-07 Create a new conversation](Sequence%20diagrams/UC-07%20Create%20a%20new%20conversation.png)

### UC-08 Add Users to Existing Group Conversation
![UC-08 Add users to existing group conversation](Sequence%20diagrams/UC-08%20Add%20users%20to%20existing%20group%20conversation.png)

### UC-09 Leave a Conversation
![UC-09 Leave a conversation](Sequence%20diagrams/UC-09%20Leave%20a%20conversation.png)

### UC-10 Log Out from Client Application
![UC-10 Log out from client application](Sequence%20diagrams/UC-10%20Log%20out%20from%20client%20application.png)

### UC-11 Join a Conversation
![UC-11 Join a Conversation](Sequence%20diagrams/UC-11%20Join%20a%20Conversation.png)
