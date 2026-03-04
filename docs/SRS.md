# Software Requirements Specification

# Revision History
## Date | Version | Description | Author
-  02/14/2026 | 1.0 | Initiate the document | Jon


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


# 1. Purpose
## 1.1. Scope
This project encompasses the design and implementation of a Java-based client–server internal messaging system operating over TCP/IP.

The system includes user authentication, private and group-based text messaging, synchronous and asynchronous communication support, persistent conversation logging, and administrative oversight capabilities.

The server functions as the authoritative source of truth, managing user accounts, sessions, message distribution, and data synchronization.

All data must be stored using text-based file persistence without the use of external databases, frameworks, libraries, or web technologies.

The client application provides a graphical interface for authentication and message interaction.

The project deliverables include complete Java source code, JUnit-based unit testing, formal documentation, version-controlled repository management, and a structured project presentation.
 
## 1.2. Definition, Acronyms, Abbreviations
### TCP/IP (Transmission Control Protocol / Internet Protocol)
A standardized suite of communication protocols used to establish reliable network communication between client and server applications.

### Client Application
The Java-based graphical user interface (GUI) program used by employees to access the communications system, authenticate, and exchange messages.

### Server Application
The centralized Java application responsible for managing user authentication, message storage, synchronization, session tracking, and persistent logging.  
It acts as the system’s authoritative source of truth.

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
The server’s authoritative role in maintaining and validating all official system data, including user credentials, conversation records, and login states.

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
[Use Case Specification document](#use-case-specification)

Use Cases Diagram

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

All data storage is performed through structured text-based files managed by the server. Administrators are granted elevated privileges that allow oversight access to all communications, aligning with the requirement to minimize privacy within the organizational environment. 

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
- Unique username and password selection during registration
- User login with username and password
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
- Enforcement of unique Employee ID and username
- Authentication of login username and password
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
Client-side storage shall be runtime-only; the client shall not persist user, conversation, or message records beyond program execution.
- Server-stored communication data (e.g., messages and logs) shall be immutable once recorded, meaning sent messages cannot be edited or deleted.
- The server shall be the authoritative source of truth for all user identity data, session state, and message synchronization; the client shall not make independent state decisions.
- The system shall support role-based interfaces, requiring a distinct GUI experience for administrative users versus standard users.
- Conversations shall be private by default, with access restricted to participants unless overridden by authorized administrative privileges.
- The system shall use unique numeric identifiers internally to streamline storage, indexing, and retrieval operations.


## 2.5. Assumptions and Dependencies
### Assumptions
- The organization issues unique Employee IDs in advance; Employee ID values required for account creation are available to the server for verification.
- Username and Employee ID uniqueness will be enforced by the server at account-creation time.
- The target deployment is a single authoritative server (host device) on the organizational network; the server machine provides sufficient CPU, memory, and disk resources for the expected load.
- Typical message payloads are text-only. The system will not be required to support file attachments, images, or multimedia.
- Network connectivity between clients and the server is reliable LAN/Wi-Fi with low latency for synchronous delivery; occasional disconnections are expected and handled via server-side buffering for offline delivery.
- There will be one active session per user enforced by the server (concurrent logins from multiple devices are either rejected or will cause the earlier session to be invalidated).
- Administrative users (IT) and the list of admin accounts are predetermined or authorized and available to the server for role assignment at account creation or initial configuration.
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
- Administrator provisioning mechanism — a simple configuration file or server-side list used to define accounts that are granted administrative privileges at initialization or account creation


# 3. Specific Requirements
## 3.1. Functional Requirements
### 3.1.1. Common Requirements:
3.1.1.1 The system shall follow a client–server architecture using TCP/IP socket communication.

3.1.1.2 The system shall be implemented entirely in Java using only standard Java SE libraries.

3.1.1.3 The system shall not use external libraries, frameworks, databases, or web technologies.

3.1.1.4 The Server shall act as the authoritative source of truth for all user, session, conversation, and message data.

3.1.1.5 All persistent data shall be stored in structured text-based files on the Server.

3.1.1.6 All internal entities (users, conversations) shall use unique numeric identifiers generated by the Server.

3.1.1.7 Employee ID and username values shall be unique within the system.

3.1.1.8 Once a message is sent and stored by the Server, it shall be immutable.

3.1.1.9 The system shall support both synchronous (real-time) and asynchronous (buffered) message delivery.

3.1.1.10 The system shall enforce role-based access control with two roles: User and Administrator.

3.1.1.11 All user interactions shall be performed through a graphical user interface on the Client side.

### 3.1.2 Client Module Requirements:
3.1.2.1 The Client shall provide a graphical user interface (GUI) for all user interactions.

3.1.2.2 The Client shall allow a user to create a new account using a valid Employee ID.

3.1.2.3 The Client shall allow verification of Employee ID ownership through name confirmation.

3.1.2.4 The Client shall allow the user to select a unique username and password during registration.

3.1.2.5 The Client shall allow login using username and password.

3.1.2.6 The Client shall allow users to log out of the system.

3.1.2.7 The Client shall establish and maintain a TCP/IP connection to the Server.

3.1.2.8 The Client shall provide exactly one active message input buffer at any time.

3.1.2.9 The Client shall allow modification of text within the input field prior to sending.

3.1.2.10 The Client shall transmit message content to the Server for processing and storage.

3.1.2.11 The Client shall display conversations ordered by most recent activity.

3.1.2.12 The Client shall display the X most recent conversations.

3.1.2.13 The Client shall display message history within a selected conversation.

3.1.2.14 The Client shall visually indicate unread messages within conversations.

3.1.2.15 The Client shall allow users to search the directory by name.

3.1.2.16 The Client shall allow users to start a new private conversation.

3.1.2.17 The Client shall allow users to create a new group conversation.

3.1.2.18 The Client shall allow users to add participants to an existing group conversation.

3.1.2.19 The Client shall allow users to leave a conversation.

3.1.2.20 The Client shall provide a distinct interface for administrative users.

### 3.1.3. Server Module Requirements:
3.1.3.1 The Server shall accept and manage concurrent TCP/IP connections from multiple Clients.

3.1.3.2 The Server shall create, store, and manage User accounts, including role assignment (User or Administrator).

3.1.3.3 The Server shall verify Employee ID ownership during account creation and enforce uniqueness of username.

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

## 3.2. External Interface Requirements
3.2.1 The system shall provide a network interface using TCP/IP socket communication between the Client and Server modules.

3.2.2 The Server shall listen on a configurable TCP port and accept connection requests from authorized Client applications.

3.2.3 The Client shall establish a socket connection to the Server using a specified IP address (or hostname) and port number.

3.2.4 The system shall interface with the host operating system’s file system to persist user data, conversation records, and message logs in structured text files.

3.2.5 The system shall operate within a Java SE runtime environment and shall not interface with external databases, third-party APIs, or enterprise systems.

3.2.6 The Client shall provide a desktop graphical user interface for all user interactions.

## 3.3 Internal Interface Requirements
3.3.1 The Client module shall communicate with the Server module using a structured request–response protocol defined by the system.

3.3.2 The communication protocol shall define standardized message formats for operations including user registration, authentication, message transmission, conversation management, search operations, and logout.

3.3.3 The Server shall ensure that internal data structures representing Users, Conversations, Messages, and Sessions are consistently accessed through controlled interfaces to maintain data integrity.

3.3.4 The Server shall manage synchronization between internal components to ensure thread-safe operation during concurrent client interactions.


# 4. Non-Functional Requirements
## 4.1. Security and Privacy Requirements
4.1.1 The system shall require user authentication(Login) before granting access to messaging features.

4.1.2 The system shall enforce uniqueness of Employee ID and username during account creation.

4.1.3 The system shall securely store user passwords.

4.1.4 The system shall enforce role-based access control with at least two roles: User and Administrator.

4.1.5 The system shall restrict access to conversations to participating users, except for authorized administrative users.

4.1.6 The system shall enforce a single active session per non-administrative user account.

4.1.7 The system shall validate all client requests before processing them.

4.1.8 The system shall log administrative access to conversations for audit purposes.

4.1.9 The system shall ensure that stored messages are immutable and cannot be modified or deleted after persistence.

4.1.10 The system shall maintain data integrity during concurrent access and file storage operations.

4.1.11 The system shall ensure that only authenticated clients may establish active messaging sessions with the Server.

## 4.2. Environmental Requirements
4.2.1 The system shall operate in environments that support Java SE (compatible JDK installed on both Client and Server machines).

4.2.2 The Client application shall run on desktop operating systems capable of supporting a Java-based graphical user interface.

4.2.3 The Server application shall run on a host machine with continuous network connectivity.

4.2.4 The Server shall be deployable on standard desktop or Linux-based server infrastructure.

4.2.5 The system shall operate within an internal organizational network environment (LAN or Wi-Fi).

4.2.6 The Server host machine shall provide sufficient disk storage for persistent text-based data files.

4.2.7 The system shall not require installation of external database management systems, web servers, or third-party frameworks.

4.2.8 The system shall not depend on cloud infrastructure or external enterprise services.

## 4.3. Performance Requirements.
4.3.1. The system shall support at least 1000 concurrent active client connections without performance degradation under standard deployment conditions.

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


# Use Case Specification
## Use Case ID: UC-01
### Use Case Name: Create an Account

### Relevant Requirements: 
3.1.1.4

3.1.1.5

3.1.1.6

3.1.1.7

3.1.1.10

3.1.2.2.

3.1.2.3

3.1.2.4

3.1.3.2 
### Primary Actor:
User
### Pre-conditions: 
Client is connected to the server

User has a valid Employee ID already issued

### Post-conditions:
A new user account is created

### Basic Flow or Main Scenario: 
1. User enters the correct real name and employee ID
2. Server verified that Employee ID exists and matches the name. If not, repeat step 1.
3. User enters a username and password.
4. Server checks that the username is unique. If not unique, repeat step 4 with a new username
5. The system creates the account with the username and password
6. The system assigns the role based on the information
### Extensions or Alternate Flows:
None. 

### Exceptions: 
User enters an invalid real name or employeeID

User enters an existing username
Unable to establish TCP connection

User credentials data not found
### Related Use Cases:
None

## Use Case ID: UC-02
### Use Case Name: Log in to the System
### Relevant Requirements:
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

### Primary Actor:
User

### Pre-conditions: 
User has an existing registered account
Client connects to the server

There is no existing active session associated with the user’s ID

### Post-conditions: 

User has an online status.

A session is created on the server.
Conversation history imported from the server

### Basic Flow or Main Scenario: 
1. User enters a valid username and password
2. Clients sends request to server
3. The server verifies the username and password.
4. The system displays the main conversation interface
### Extensions or Alternate Flows: 
None.
### Exceptions: 
User enters an invalid username or password

There is a preexisting active session associated with the user’s ID

Unable to establish TCP connection

User credentials data not found

### Related Use Cases: 
2, 3, 4, 5, 6, 7, 8, 9, 10, 11

## Use Case ID: UC-03
### Use Case Name: Send a Message to a conversation
### Relevant Requirements: 
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

### Primary Actor:
User

### Pre-conditions: 
User is logged in
User has at least one existing conversation
### Post-conditions: 
Conversation is updated on the client and server.
### Basic Flow or Main Scenario: 
User selects an existing conversation
System displays recent messages.
User enters text.
User presses the send button
### Extensions or Alternate Flows:
None
### Exceptions: 
Empty message
Unable to establish TCP connection
Conversation history data not found
### Related Use Cases: 1,2,6,7,11

Use Case ID: UC-04
Use Case Name: Browse User Directory
Relevant Requirements: 
3.1.2.15
3.1.3.8
3.1.3.13
4.3.4
Primary Actor: User
Pre-conditions: 
User logged in
Post-conditions: 
The client GUI displays matching user or “no result found” if there is no matching user
Basic Flow or Main Scenario:
Select the directory search box
Enter the name of the desired employee
The system returns the result
Extensions or Alternate Flows: 
Select the “add to conversation” option in a conversation window
Enter the name of the desired employee
Exceptions:
Unable to establish TCP connection.
Directory data not found
Related Use Cases: 1,2,7,8,11

Use Case ID: UC-05
Use Case Name: Browse Conversation History
Relevant Requirements: 
3.1.2.11
3.1.2.12
3.1.2.13
3.1.3.8
3.1.3.13
4.3.4
Primary Actor: User
Pre-conditions: 
User logged in
Post-conditions: 
Results are displayed by order of most recent activity. 
User is not a participant of any conversation, the GUI displays an empty conversation history.
Basic Flow or Main Scenario:
User enters the name in the conversation history search box
Client GUI displays conversations involving the searched user.
User selects the desired conversation for viewing
Extensions or Alternate Flows: 
None	
Exceptions:
Unable to establish TCP connection
Conversation history data not found
Related Use Cases: 1,2,3,6,7,8,9,11

## Use Case ID: UC-06
### Use Case Name: View a conversation
### Relevant Requirements: 
3.1.2.13

3.1.2.14

3.1.3.7

3.1.3.12

3.1.3.15

4.3.3

### Primary Actor:
User
### Pre-conditions:
User logged in

User is a participant of a conversation
### Post-conditions: 

The client GUI displays the message history of the selected conversation.

Unread messages are indicated when viewing a conversation for the first time since changes were made to the conversation.

### Basic Flow or Main Scenario: 
1. User selects a conversation
2. The client GUI displays the message history of the selected conversation.
3. If the conversation is too large to be displayed in the GUI, the user should be able to scroll up or down to view more of the conversation history.

### Extensions or Alternate Flows:
None
### Exceptions: 

Unable to establish TCP connection

Conversation history data not found
### Related Use Cases:
1, 2, 3, 5, 7, 8, 9, 11

## Use Case ID: UC-07
### Use Case Name: Create a New Conversation
### Relevant Requirements: 
3.1.2.16

3.1.2.17

3.1.3.6

3.1.3.11.3

3.1.3.15

3.1.1.6
4.1.5
### Primary Actor:
User

### Pre-conditions: 
Client GUI is working

User is logged in and connected to the server

User exists in the system

At least one valid user is available to select

### Post-conditions: 
New conversation is created

Conversation stored on the server with a unique conversation ID

The participant is updated 

### Basic Flow or Main Scenario: 
1. User selects a user to create a conversation
2. System checks if the conversation already exists
3. If not, the system create the new conversation with the user
4. The system assigns a unique conversation ID
5. The system stores the conversations
6. GUI created conversation box for each user
### Extensions or Alternate Flows:
 Create multi-user conversation from scratch
1. User selects multiple users.
2. User use the create group function
3. Sever creates new group conversation 
4. Server assigns a unique conversation id
5. Serve stores the conversation with participants list
6. GUI created

### Exceptions: 

User is not found

The same conversation group exists

Unable to establish TCP connection

User directory data not found

User conversation history data not found

### Related Use Cases:
1, 2, 3, 5, 6, 8, 9, 11

## Use Case ID: UC-08
### Use Case Name: Add user to existing group conversation
### Relevant Requirements: 
3.1.2.18

3.1.3.6

3.1.3.11.2

3.1.3.15

4.1.5
### Primary Actor:
User 

### Pre-conditions: 
User is logged in

The group conversation exists

User is a participant in the conversation

The added user isn’t a participant in the conversation

### Post-conditions: 

The new user has a new conversation.

The system added the user to the existing conversation.

Conversation remains a part of the new user’s conversation history.

### Basic Flow or Main Scenario: 
1. User selects a group conversation
2. User selects the “add participant” search box.
3. User enters the name of the desired participant.
4. The system adds the user to the conversation.
5. GUI displays the conversation for the added user.

### Extensions or Alternate Flows: 
None
### Exceptions: 
No group conversation under the user’s conversation history.

Unable to establish TCP connection

User directory data not found

User conversation history not found

### Related Use Cases:
1, 2, 3, 5, 6, 7, 9, 11

## Use Case ID: UC-09
### Use Case Name: Leave a Conversation
### Relevant Requirements: 
3.1.2.19

3.1.3.6

3.1.3.11

3.1.3.15

4.1.5

### Primary Actor:
User

### Pre-conditions: 
User logged in

User is a participant in the conversation

### Post-conditions: 
User is not a participant of the conversation.

The conversation is no longer displayed on the client GUI for the user that left.

### Basic Flow or Main Scenario:
1. User selects the conversation
2. User clicks the “leave conversation” button.
3. The system removes the user from the conversation

### Extensions or Alternate Flows:
None

### Exceptions: 
Unable to establish TCP connection

### Related Use Cases:
1, 2, 3, 5, 6, 7, 11

## Use Case ID: UC-10
### Use Case Name: Log Out from Client Application
### Relevant Requirements: 
3.1.2.6

3.1.3.4

3.1.3.5

4.1.6

### Primary Actor:
User

### Pre-conditions:
User has logged in status.

User has an active session.

Post-conditions: 

The session is terminated

The user’s status is set to offline.

The client GUI displays the login prompt screen.

### Basic Flow or Main Scenario: 
1. User clicks the “Log Out” button.
2. The GUI displays login screen.

### Extensions or Alternate Flows: 
Case 1: Client application inactivity
1. Client application does not detect user input for a certain time threshold.
2. Client application automatically logs the user out.
3. The client GUI displays the login prompt screen

Case 2: Networking issues
1. The client loses connection to the server
2. The server detects the lost connection.
3. The server terminates the active session associated with the user.
4. The system set the user’s status to “offline.”
5. The server logs the user out.
6. The client GUI displays the login prompt screen.

### Exceptions: 
Unable to establish TCP connection

### Related Use Cases:
1, 2

## Use Case ID: UC-11
### Use Case Name: Join a Conversation
### Relevant Requirements: 
3.1.1.10

3.1.2.21

3.1.3.6

3.1.3.11

3.1.3.13

3.1.3.17

4.1.4

4.1.5

4.1.8

### Primary Actor:
Admin

### Pre-conditions: 
Admin user logged in

Conversation exists

### Post-conditions: 
Admin is a participant in the conversation.

The conversation is displayed in the Admin user’s conversation history.

### Basic Flow or Main Scenario: 
1. Admin types the name of the user whom he wants to search for in the conversation searching box.
2. Admin GUI displays the list of conversations, which contains the user sorted first by number of participants, and then by alphabetical order of participants.
3. Admin selects the conversation.
4. The server adds the admin to the participant list.
5. The system displays the conversation messages.

### Extensions or Alternate Flows: 
None

### Exceptions: 
Unable to establish TCP connection

User conversation history data not found

User directory data not found

### Related Use Cases:
1, 2, 3, 4, 5, 6, 7, 9
