# SithTermFX

SithTermFX is a terminal emulator for JavaFX. The project is a result of porting
[JediTerm](https://github.com/JetBrains/jediterm) (commit 8366f2b) with new features. Therefore, the Terminal Emulator based on this library can be seamlessly integrated into
any JavaFX application. A detailed comparison of terminal libraries is provided below.

## Table of Contents
* [Demo](#demo)
    * [Htop](#demo-htop)
    * [Tig](#demo-tig)
    * [MC](#demo-mc)
    * [Maven](#demo-maven)
    * [Bastet](#demo-bastet)
* [Features](#features)
* [Terminal Comparison](#comparison)
* [Usage](#usage)
    * [Dependencies](#usage-dependencies)
    * [Dark Theme](#usage-dark-theme)
    * [Dynamic Font Size](#usage-font-size)
    * [Split Screen](#usage-split-screen)
    * [Hyperlinks](#usage-hyperlinks)
* [Code building](#code-building)
* [Running the Application](#application)
    * [Using Maven](#application-maven)
    * [Using Distro](#application-distro)
* [Integration Guide](#integration-guide)
* [Public API](#public-api)
* [License](#license)
* [Contributing](#contributing)
* [Support Us](#support-us)

## Features <a name="features"></a>

* Local terminal for Unix, Mac and Windows using Pty4J
* Xterm emulation - passes most of tests from vttest
* Xterm 256 colours
* Scrolling
* Copy/Paste
* Mouse support
* Terminal resizing from client or server side
* Terminal tabs
* **Dynamic font size** - Adjust font size at runtime via Ctrl+Plus/Minus (Cmd+Plus/Minus on Mac) without disconnecting
* **Split screen** - Nested horizontal/vertical splits with independent sessions per pane
* **Reorder split panes** - Drag a split terminal onto another to move it above, below, left, or right; drop zones show possible positions while dragging

## Terminal Comparison <a name="comparison"></a>

Terminal      | SithTermFX  | [JediTerm](https://github.com/JetBrains/jediterm)  | [TerminalFX](https://github.com/javaterminal/TerminalFX) |
:-------------|:----------- |:--------------|:--------------|
GUI Library   | JavaFX      | Swing         | JavaFX        |
Main Component| Canvas      | JComponent    | WebView       |
Languages     | Java        | Java, Kotlin  | Java, JS      |
JPMS Support  | Yes         | No            | Yes           |

## Usage <a name="usage"></a>

It is recommended to start working with SithTermFX by studying and running the
[BasicTerminalShellExample](sithtermfx-app/src/main/java/com/sithtermfx/app/example/BasicTerminalShellExample.java) class.
This class contains the minimal code needed to launch a terminal in a JavaFX application.

### Dependencies <a name="usage-dependencies"></a>

Maven Central publication is prepared and will be used for releases. Until the first Central release is published,
build from source as described below.

```
<dependency>
    <groupId>com.sithtermfx</groupId>
    <artifactId>sithtermfx-core</artifactId>
    <version>${sithtermfx.version}</version>
</dependency>
<dependency>
    <groupId>com.sithtermfx</groupId>
    <artifactId>sithtermfx-ui</artifactId>
    <version>${sithtermfx.version}</version>
</dependency>
```

### Dark Theme <a name="usage-dark-theme"></a>

If you need a dark theme, you should override the `getDefaultForeground()` and `getDefaultBackground()` methods in
`UserSettingsProvider`. To run the demo application in dark theme see [Using Maven](#application-maven).

### Dynamic Font Size <a name="usage-font-size"></a>

Use `DynamicFontSizeSettingsProvider` (or extend it) to enable runtime font resizing without disconnecting the terminal:

* **Ctrl+Plus** / **Ctrl+Minus** (Cmd on Mac) - Increase/decrease font size
* **Ctrl+0** (Cmd+0 on Mac) - Reset to default size

Font size range is configurable via `getMinFontSize()` and `getMaxFontSize()` in `UserSettingsProvider` (default 8–72 pt).

### Split Screen <a name="usage-split-screen"></a>

Use `TerminalSplitPane` for nested horizontal/vertical splits. Right-click in the terminal to access the context menu:

* **Split right (same server)** / **Split right (new connection)** - Split horizontally
* **Split down (same server)** / **Split down (new connection)** - Split vertically
* **Close split** - Close the focused pane

**Reorder panes by drag-and-drop:** Drag a split terminal with the mouse onto another pane. While dragging, four drop zones (above, below, left, right) are shown; the zone under the cursor is highlighted. Release to move the pane to that position.

Implement `SplitConnectorFactory` to provide new `TtyConnector` instances. The `SplitRequest` contains the chosen mode
(SAME_SERVER_NEW_SHELL vs NEW_CONNECTION) and the parent widget for reusing connections.

See [SplitTerminalShellExample](sithtermfx-app/src/main/java/com/sithtermfx/app/example/SplitTerminalShellExample.java) for a demo.

### Hyperlinks <a name="usage-hyperlinks"></a>

SithTermFX provides a wide range of features when working with links. The `HighlightMode` enumeration specifies multiple
modes of working with links and their colors. In the `ALWAYS` modes, links are always underlined and always clickable.
In the `NEVER` modes, links are never underlined and never clickable. In the `HOVER` modes, links become underlined and
clickable only when hovered over. Now let's clarify the difference between the two types of colors. `CUSTOM` colors
are those set by the SithTermFX user in the getHyperlinkColor() method of the settings. `ORIGINAL` colors are those
offered by the program running in the terminal. Thus, links can use either custom colors or the original text colors.

## Public API <a name="public-api"></a>

Public API documentation for integrators is available in [`docs/public-api.md`](docs/public-api.md).
It defines stable packages, recommended entry points, and compatibility rules for upgrades.

## Code Building <a name="code-building"></a>

To build the library use standard Git and Maven commands:

    git clone https://github.com/chardonnay/SithTermFX.git
    cd SithTermFX
    mvn clean install

## Running the Application <a name="application"></a>

The project contains a demo application that shows how to use this library. There are two ways to run the application.

### Using Maven <a name="application-maven"></a>

Build the project from the root first (required so all modules are available on the classpath):

    git clone https://github.com/chardonnay/SithTermFX.git
    cd SithTermFX
    mvn clean install

Then run the main application:

    mvn javafx:run -pl sithtermfx-app

To run the **Split Terminal** demo (nested splits and drag-and-drop reordering):

    mvn javafx:run -pl sithtermfx-app -Psplit

Debugger settings are in `sithtermfx-app/pom.xml`. To try a dark theme, uncomment the following line in the JavaFX plugin configuration:

```
<!--<commandlineArgs>theme=dark</commandlineArgs>-->
```
This will make SithTermFX use the `DarkThemeSettingsProvider`.

### Using Distro <a name="application-distro"></a>

After building the project, you will find a distribution archive named `sithtermfx-app-<version>.tar` in the
`sithtermfx-app/target` directory. Extracting this file will allow you to launch the application
using `.sh` or `.bat` scripts depending on your operating system.

## Integration Guide <a name="integration-guide"></a>

A comprehensive guide for embedding SithTermFX into your own JavaFX application is available
in [`docs/integration-guide.md`](docs/integration-guide.md). It covers dependencies, minimal setup,
SSH connectors, theming, dynamic font size, split terminals, hyperlinks, and advanced patterns
extracted from the [korTTY](https://github.com/chardonnay/korTTY) SSH client.

## License <a name="license"></a>

SithTermFX is licensed under the Apache License 2.0. See `LICENSE-APACHE-2.0.txt`.

## Contributing <a name="contributing"></a>

We welcome all contributions. You can help by reporting bugs, suggesting improvements, or submitting pull requests
with fixes and new features. If you have any questions, feel free to reach out — we’ll be happy to assist you.

## Support Us <a name="support-us"></a>

You can support our open-source work through [GitHub Sponsors](https://github.com/sponsors/chardonnay).
Your contribution helps us maintain projects, develop new features, and provide ongoing improvements.
Multiple sponsorship tiers are available, each offering different levels of recognition and benefits.



