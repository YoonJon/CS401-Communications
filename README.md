# Communication System Application (CSA)

CS401 Group Project
by Quan Pham, Harumi Ueda, Jon Yoon

## Overview
This application is intended to be a TCP/IP-based chat application for internal communications in a corporate workplace. It supports text-only synchronous and asynchronous private and group messaging between users of a global directory. Because it is intended for internal communications between company employees, only pre-approved combinations of name and employee ID will be allowed to register as a user. The application also gives the ability for unrestricted surveillance of conversation contents by special administrative users. Approved employees and admins should be authorized by supplying CSV-formatted data as specified below.

## How to run
1. Open the project in Eclipse
2. Set the current working directory to the project root
3. Ensure authorized_users.txt and authorized_admins are in the data/server_data/authorized_ids folder
4. Run src/server/ServerController.java
5. Run src/client/ClientController.java

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
