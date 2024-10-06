# SFE4 Happy

Welcome to **SFE4 Happy**! This application is designed to manage multiple subprocesses (such as servers and FRP), providing features for starting, monitoring, and safely shutting down these processes.

## Table of Contents

- [Project Overview](#project-overview)
- [Usage Guide](#usage-guide)
- [Instructions](#instructions)
- [Stopping the Application](#stopping-the-application)
- [Contributing](#contributing)
- [License](#license)

## Project Overview

**SFE4 Happy** is a Java-based application aimed at simplifying the management of multiple subprocesses, such as game servers and FRP. It allows you to start multiple subprocesses concurrently, monitor their output, and safely shut them down when necessary.

### Key Features

- **Multi-process management**: Supports simultaneous launching and management of multiple subprocesses.
- **Signal capture**: Captures `Ctrl+C` and other signals to ensure the application shuts down safely.
- **Command listener**: Allows you to safely stop all subprocesses by typing the `stop` command in the console.
- **Log recording**: Uses Log4j2 to log the status of the application and subprocesses for easier debugging and monitoring.
- **Timeout mechanism**: Sets a timeout when shutting down subprocesses to prevent them from hanging and not exiting.

### System Requirements

- **Java 17** or later
- **Maven** (for building the project)
- Operating System: Windows, Linux, or macOS

## Usage Guide

### Directory Structure

```perl
📦 SFE4-Happy-0.0.1.jar  # The executable JAR file for the application
📂 package               # Configuration and subprocess files
 ┣ 📜 info.json          # Configuration file that defines the startup commands for each subprocess
 ┣ 📂 frp                # FRP directory, containing the FRP executable
 ┣ 📂 server1            # Directory for the first server
 ┗ 📂 server2            # Directory for the second server
```

### Prepare the Configuration File

Within the `package` directory, create an `info.json` file that defines the subprocesses to be launched. For example:

```json
{
  "frp": [
    "frpc_linux_amd64",
    "-n", 
    "-u", "0dd7b43bdf696969b682ce4cc9a86f61",
    "-p", "504494,504495"
  ],
  "server1": [
    "java", 
    "-jar", 
    "server1.jar"
  ],
  "server2": [
    "start.sh"
  ]
}
```

Ensure that the paths for `frp`, `server1`, and `server2` point to the correct executable files or scripts.

### Running the Application

```bash
java -jar SFE4-Happy-0.0.1.jar
```

Once the application starts, it will sequentially launch the subprocesses defined in the configuration and begin listening for console input.

## Instructions

- **Starting subprocesses**: When the application starts, it will launch all subprocesses as defined in the `info.json` file.
- **Monitoring subprocess output**: The application will display the output logs of the subprocesses in the main console for real-time monitoring.
- **Manually stopping subprocesses**: To stop all subprocesses safely, type `stop` in the console.

## Stopping the Application

While the application is running, simply type the following command in the console:

```bash
stop
```

The application will then:

1. Print the log message `Received stop command`.
2. Begin calling the `shutdownProcesses()` method to shut down each subprocess one by one.
3. Print logs related to the shutdown process.
4. Finally, print `All subprocesses terminated` and `Application stopped`, then exit.

## Contributing

We welcome issues and pull requests to help improve this project.

## License

This project is open-source under the [MIT License](LICENSE). You are free to use, modify, and distribute this software.