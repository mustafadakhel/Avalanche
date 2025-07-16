# Avalanche Plugin (WIP)

Avalanche is an IntelliJ IDEA plugin that keeps your local branches up to date automatically.

![screenshot](https://github.com/user-attachments/assets/09c3edb0-8cdf-4ab3-8469-4af62396838e)

## Features

- Periodically fetch and merge updates for selected branches.
- Configurable update interval and conflict handling mode.
- Enable or disable auto update for each branch via the Git branches popup.
- Notifications about successes or failures.

## Building from Source

Use Gradle to build the plugin ZIP:

```bash
./gradlew buildPlugin
```

The resulting archive will be located under `build/distributions`. During development you can run a sandbox IDE using:

```bash
./gradlew runIde
```

## Usage

1. Right‑click a branch in the Git branches popup and choose **Enable Auto Update**.
2. Adjust the interval and conflict handling in **Settings | Avalanche**.
3. The plugin will periodically update the branch according to your settings.
