# Communication System Application (CSA)

CS401 Group Project
by Quan Pham, Harumi Ueda, Jon Yoon

## Overview
This application is intended to be a TCP/IP-based chat application for internal communications in a corporate workplace. It supports text-only synchronous and asynchronous private and group messaging between users of a global directory. Because it is intended for internal communications between company employees, only pre-approved combinations of name and employee ID will be allowed to register as a user. The application also gives the ability for unrestricted surveillance of conversation contents by special administrative users. Approved employees and admins should be authorized by supplying CSV-formatted data as specified below.

## How to run
1. Open the project in Eclipse
2. Set the current working directory to the project root
3. Ensure authorized_users.txt and authorized_admins are in the data/server_data/authorized_ids folder
4. Run src/server/ServerController.java to start the server
5. Run src/client/ClientController.java to launch individual client applications
6. Register as a new user using the GUI

For testing purposes, if you want to register as a user, put your desired ID + Name combination into the authorized_users.txt file.

## Externally-supplied CSV file formats:
# authorized_users.txt format:
<employee ID>,<first name> <last name>

# authorized_admins.txt format:
<admin ID>

The host organization has the choice of what format ID numbers should be, although alphanumeric strings of 8-10 characters are recommended.

## Features
- Create an account
- Log in to the account
- Search users
- Create conversations
- View messages
- Select the conversation
- Leave the conversation
- For Admin, join existing conversations

## Technologies
- Java
- Swing (GUI)
- TCP/IP connection
- OOP (MVC architecture)
