# Communication System Application (CSA)

Communication System Application is a Java client/server messaging platform built for CS401.  
It provides a desktop client UI, a TCP server, and shared networking/payload contracts.

## Table of Contents
- [System Overview](#system-overview)
- [Core Features](#core-features)
- [Architecture](#architecture)
- [Repository Layout](#repository-layout)
- [Prerequisites](#prerequisites)
- [Quick Start (Eclipse)](#quick-start-eclipse)
- [Run from Command Line](#run-from-command-line)
- [Runtime Configuration](#runtime-configuration)
- [IP Finder Utility (JAR)](#ip-finder-utility-jar)
- [Data and Persistence](#data-and-persistence)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Documentation](#documentation)

## System Overview
Based on `docs/SRS.md` section 2, this project is a standalone internal communications platform for a large organization using a centralized client-server model.

- The **server** is the authoritative source of truth for user accounts, sessions, conversations, and message logs.
- The **client** is a desktop GUI interface for authentication, conversation management, and text-based messaging.
- Communication is done over TCP/IP using serialized request/response objects.
- Persistence is server-managed and file-based (text/config + serialized data), with no external database or third-party service dependencies.
- Administrative users are granted elevated oversight privileges aligned with organizational requirements.

## Core Features
- Account registration and login
- User directory search
- One-to-one and group conversation support
- Message viewing and conversation history
- Leave conversation support
- Admin support for joining existing conversations

## Architecture
- **Client Layer**: `ClientController` and `ClientUI` manage GUI events and server requests.
- **Server Layer**: `ServerController` and `DataManager` handle request processing, validation, and persistence.
- **Shared Layer**: enums, payload objects, and networking classes in `src/shared`.
- **Transport**: TCP/IP sockets with request/response objects.

## Tech Stack
- Java
- Java Swing (GUI)
- TCP/IP socket communication
- MVC-style separation (client controller/UI, server controller, shared payloads)

## Repository Layout
- `src/client`: client UI and controller
- `src/server`: server-side controller and data management
- `src/shared`: shared enums, payloads, and networking contracts
- `test`: JUnit tests
- `data`: server-side data files
- `docs`: design and analysis documents

## Prerequisites
- Java 17+ (recommended)
- Eclipse IDE (optional, but easiest for this project)
- Terminal access for command-line execution (optional)

## Quick Start (Eclipse)
1. Import the repository as a Java project.
2. Run `src/server/ServerController.java`.
3. Run `src/client/ClientController.java` in a second launch.
4. Use the client UI to register/login and start messaging.

## Run from Command Line
From repository root:

1. Compile all source files:
   ```bash
   mkdir -p out
   find src -name "*.java" > sources.txt
   javac -d out @sources.txt
   ```
2. Start the server (default: data root `./data`, port `8080`, bind `localhost`):
   ```bash
   java -cp out server.ServerController
   ```
3. Start client(s) in separate terminal(s):
   ```bash
   java -cp out client.ClientController
   ```
4. Optional: connect to a custom host/port:
   ```bash
   java -cp out client.ClientController 127.0.0.1 8080
   ```

## Runtime Configuration
### Server
`ServerController` supports:
```text
java -cp out server.ServerController [dataRootPath] [port] [ipv4]
```
- `dataRootPath` (optional): root directory for persistence (default `data`)
- `port` (optional): listening port (default `8080`)
- `ipv4` (optional): bind address (default `localhost` in local mode)

Example:
```bash
java -cp out server.ServerController data 9090 0.0.0.0
```

### Client
`ClientController` supports:
```text
java -cp out client.ClientController [host] [port]
```
- `host` (optional): server host (default `localhost`)
- `port` (optional): server port (default `8080`)

## IP Finder Utility (JAR)
The project includes an IP helper utility source at `utils/IP_Finder.java`.  
When packaged as a JAR, launch it with:

```bash
java -jar IP_Finder.jar
```

What it does:
- Prints the primary non-loopback IPv4 address.
- Lists all non-loopback IPv4 addresses grouped by interface.
- Helps identify the host IP that clients should use when connecting to a remote server.

If you only have source code (no prebuilt JAR), run:
```bash
javac -d out utils/IP_Finder.java
java -cp out IP_Finder
```

## Data and Persistence
Important data files live under `data/`:
- `data/server_data/server_config.txt`: server counters
- `data/server_data/authorized_ids/authorized_users.txt`: valid user IDs + names
- `data/server_data/authorized_ids/authorized_admins.txt`: IDs with admin privileges

The server reads/writes these files through `DataManager`.

### Externally Supplied Management Files
The authorization `.txt` files are **externally supplied by company management** (not generated by clients):
- `authorized_users.txt` is the managed employee roster used for account ownership verification.
- `authorized_admins.txt` is the managed list of employee IDs granted admin privileges.

These files should be placed under the server data root exactly as:

```text
<dataRootPath>/
  server_data/
    authorized_ids/
      authorized_users.txt
      authorized_admins.txt
```

For default startup (`dataRootPath = data`), place them at:
- `data/server_data/authorized_ids/authorized_users.txt`
- `data/server_data/authorized_ids/authorized_admins.txt`

Expected content format:
- `authorized_users.txt`: one `userId,name` record per line (CSV-style)
- `authorized_admins.txt`: one `userId` per line
- Employee ID format is not hard-restricted by the system; however, company management should standardize on **alphanumeric IDs of 8-10 characters** for consistency.
- Blank lines and lines beginning with `#` are ignored

## Testing
Tests are located under `test/` and use JUnit.

If you are running in Eclipse, use **Run As > JUnit Test** on specific test classes or the test package.

## Troubleshooting
- **Client cannot connect**: make sure server is running first and host/port match.
- **Port already in use**: run server on a different port, then launch client with that same port.
- **Login/registration rejects valid data**: verify `authorized_users.txt` format and entries.
- **Admin behavior missing**: confirm the user ID exists in `authorized_admins.txt`.

## Documentation
Additional design artifacts and reports are in `docs/`, including:
- Sequence diagrams
- Class diagrams
- SRS document
