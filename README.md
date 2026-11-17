# Distributed Hangman RMI

> A cooperative multiplayer Hangman game built with Java RMI and Swing.

## Overview

The project uses a central Java RMI server as the source of truth for the shared game state. Swing clients connect to the server, submit guesses, receive updates, and can request a restart when both players agree.

## Features

- Two-player cooperative game flow
- Shared state managed by a Java RMI server
- Swing desktop client
- Separate client, server, and shared-contract packages
- Integration test that communicates through a real RMI registry
- GUI smoke test that opens two clients and checks turn updates
- Configurable registry and remote-object ports

## Tech Stack

- Java 17 or later
- Java RMI
- Swing
- Standalone Java integration and GUI smoke tests

## Getting Started

The repository does not currently include a Maven or Gradle build file. The following commands are for Windows PowerShell.

Compile the application:

```powershell
$mainSources = Get-ChildItem .\src\main\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force .\out | Out-Null
javac -d .\out $mainSources
```

Start the server in one terminal:

```powershell
java -cp .\out br.edu.ucsal.forca.server.ForcaServerApp
```

The default RMI registry port is `1099`; the remote object uses port `1100`.

Start a client in a second terminal:

```powershell
java -cp .\out br.edu.ucsal.forca.client.ForcaClientApp
```

For a local-network game, start the server with the registry port, the server's LAN address, and the remote-object port. Allow both ports through the server machine's firewall. In each client window, enter the server's LAN address in **Host** and the registry port in **Port** (1099 by default).

```text
ForcaServerApp [registry-port] [advertised-host] [remote-object-port]
```

## Run from the GitHub ZIP

The repository includes the portable Windows app. Download the repository with **Code > Download ZIP**, extract it, and open `dist\ForcaCooperativa\ForcaCooperativa.exe`. The bundled Java runtime means players do not need to install a JDK. Keep the complete `dist\ForcaCooperativa` folder together.

The launcher offers two options:

- **Hospedar partida** asks for this computer's Radmin VPN address, starts the RMI server, and opens the local game client.
- **Entrar em partida** asks for the host's Radmin VPN address and opens the client.

Both computers must be connected to the same Radmin VPN network. The host must allow TCP ports 1099 and 1100 through its firewall.

To rebuild the Windows package from source, run `scripts\package-windows.ps1` on Windows with a JDK 17 or later.

## Tests

`ForcaIntegrationTest` exercises the server through an actual RMI registry. `ForcaGuiSmokeTest` starts two Swing clients and checks that turns update across the network. Both are standalone Java test programs rather than JUnit tests.

Compile the test sources and run the integration test:

```powershell
$testSources = Get-ChildItem .\src\test\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -cp .\out -d .\out $testSources
java -cp .\out br.edu.ucsal.forca.server.ForcaIntegrationTest
```

Run `ForcaGuiSmokeTest` only in a graphical desktop session:

```powershell
java -cp .\out br.edu.ucsal.forca.client.ForcaGuiSmokeTest
```

## Project Structure

```text
src/main/java/br/edu/ucsal/forca/
├── client/   # Swing client and connection helpers
├── server/   # RMI server and game service
└── shared/   # Remote contract and shared state objects

src/test/java/br/edu/ucsal/forca/
├── client/   # GUI smoke test
└── server/   # RMI integration test
```

## Project Status and License

This is an academic group project, not a commercial product. No license has been specified.

## Authors

- Anísio Oliveira Albuquerque Filho ([@Lugarty](https://github.com/Lugarty))
- Carlos Bernardo Góes dos Santos
