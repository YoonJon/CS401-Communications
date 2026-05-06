# Communication System Application (CSA)

Communication System Application is a Java client/server messaging platform built for CS401.  
It provides a desktop client UI, a TCP server, and shared networking/payload contracts.

## Usage

**Prerequisites:** JDK 17+ on PATH (`java`, `javac`, `jar`, plus `jpackage` for native packaging).

### 1. Compile

Windows PowerShell:
```powershell
New-Item -ItemType Directory -Force out | Out-Null
Copy-Item utils\shared\BuildInfo.java src\shared\BuildInfo.java -Force
Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName | Set-Content -Encoding ascii sources.txt
javac -d out "@sources.txt"
```

macOS / Linux:
```bash
mkdir -p out
cp utils/shared/BuildInfo.java src/shared/BuildInfo.java
find src -name "*.java" > sources.txt
javac -d out @sources.txt
```

### 2. Run from terminal

Server (terminal 1):
```bash
java -cp out server.ServerController                       # binds 0.0.0.0:8080 (LAN-accessible)
java -cp out server.ServerController data                  # binds localhost:8080 (loopback only)
java -cp out server.ServerController data 8080 0.0.0.0     # explicit
```
Args: `[dataRootPath] [port] [ipv4]` — defaults `data`, `8080`. The `ipv4` default depends on arg count: `0.0.0.0` with 0 or 2 args, `localhost` with 1 arg, otherwise the value you pass. Override anytime with `-Dserver.bind.ip=...`.

Client (terminal 2):
```bash
java -cp out client.ClientController                       # auto-discover via UDP
java -cp out client.ClientController 127.0.0.1 8080        # pinned host:port
```
Args: `[host] [port]` — defaults `localhost`, `8080`.

### 3. Build a clickable desktop app

**Windows (`.exe` with bundled JRE)** — after the compile step above:
```powershell
New-Item -ItemType Directory -Force dist | Out-Null
jar --create --file dist\Client.jar --main-class client.ClientController -C out client -C out shared
jpackage --type app-image --input dist --main-jar Client.jar --main-class client.ClientController --name CS401-Client --dest build
```
Output: `build\CS401-Client\CS401-Client.exe`. Double-click to launch. Add `--win-console` to see logs. The whole `build\CS401-Client\` folder is portable — zip it and share.

**macOS (`.app` bundle):**
```bash
./utils/build-client-app.sh
```
Output: `dist/CS401-Client.app/`.

**Cross-platform JAR only:**
```bash
./utils/build-client-jar.sh        # macOS / Linux
```
PowerShell equivalent (after compile):
```powershell
jar --create --file dist\Client.jar --main-class client.ClientController -C out client -C out shared
```
Output: `dist/Client.jar` — double-clicks on any OS with Java 17+.

### 4. Run in Eclipse
Import the repo as an existing Java project (`.classpath` is present), then run `src/server/ServerController.java` followed by `src/client/ClientController.java` in a second launch.

## Table of Contents
- [System Overview](#system-overview)
- [Core Features](#core-features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Repository Layout](#repository-layout)
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
Tests are located under `test/` and use JUnit 5.

## Troubleshooting
- **Client cannot connect**: make sure server is running first and host/port match.
- **Port already in use**: run server on a different port, then launch client with that same port.
- **Login/registration rejects valid data**: verify `authorized_users.txt` format and entries.
- **Admin behavior missing**: confirm the user ID exists in `authorized_admins.txt`.
- **`cannot find symbol: class BuildInfo`** during compile: copy `utils/shared/BuildInfo.java` into `src/shared/` first (the `utils/*.sh` scripts do this automatically on Unix).

## Documentation
Additional design artifacts and reports are in `docs/`, including:
- Sequence diagrams
- Class diagrams
- SRS document
