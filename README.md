# Techsenger JediTermFX

Techsenger JediTermFX is a Terminal Emulator for JavaFX. The project is a result of porting
[JediTerm](https://github.com/JetBrains/jediterm) (commit 8366f2b) from Swing to JavaFX. JediTermFX exclusively
utilizes JavaFX components. Therefore, the Terminal Emulator based on this library can be seamlessly integrated into
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
* [License](#license)
* [Contributing](#contributing)
* [Support Us](#support-us)

## Demo <a name="demo"></a>

### Htop <a name="demo-htop"></a>

![htop-dark](https://github.com/user-attachments/assets/b390eb08-3897-4ad4-b77f-c55ed395c2a3)

### Tig <a name="demo-tig"></a>

![tig](https://github.com/user-attachments/assets/7e1ace65-bf49-4e2c-bb0e-adb7c89b5857)

### MC <a name="demo-mc"></a>

![mc](https://github.com/user-attachments/assets/f2189273-24ca-4420-908b-fa6d1d4d0df0)

### Maven <a name="demo-maven"></a>

![mvn](https://github.com/user-attachments/assets/51bf7b95-09e4-4d62-877b-b214d1a1d7e7)

### Bastet <a name="demo-bastet"></a>

![bastet](https://github.com/user-attachments/assets/d399109b-87c5-4bf3-a860-51341efe85d6)

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

## Terminal Comparison <a name="comparison"></a>

Terminal      | JediTermFX  | [JediTerm](https://github.com/JetBrains/jediterm)  | [TerminalFX](https://github.com/javaterminal/TerminalFX) |
:-------------|:----------- |:--------------|:--------------|
GUI Library   | JavaFX      | Swing         | JavaFX        |
Main Component| Canvas      | JComponent    | WebView       |
Languages     | Java        | Java, Kotlin  | Java, JS      |
JPMS Support  | Yes         | No            | Yes           |

## Usage <a name="usage"></a>

It is recommended to start working with JediTermFX by studying and running the
[BasicTerminalShellExample](jeditermfx-app/src/main/java/com/techsenger/jeditermfx/app/example/BasicTerminalShellExample.java) class.
This class contains the minimal code needed to launch a terminal in a JavaFX application.

### Dependencies <a name="usage-dependencies"></a>

This project is available on Maven Central:

```
<dependency>
    <groupId>com.techsenger.jeditermfx</groupId>
    <artifactId>jeditermfx-core</artifactId>
    <version>${jeditermfx.version}</version>
</dependency>
<dependency>
    <groupId>com.techsenger.jeditermfx</groupId>
    <artifactId>jeditermfx-ui</artifactId>
    <version>${jeditermfx.version}</version>
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

Implement `SplitConnectorFactory` to provide new `TtyConnector` instances. The `SplitRequest` contains the chosen mode
(SAME_SERVER_NEW_SHELL vs NEW_CONNECTION) and the parent widget for reusing connections.

See [SplitTerminalShellExample](jeditermfx-app/src/main/java/com/techsenger/jeditermfx/app/example/SplitTerminalShellExample.java) for a demo.

### Hyperlinks <a name="usage-hyperlinks"></a>

JediTermFX provides a wide range of features when working with links. The `HighlightMode` enumeration specifies multiple
modes of working with links and their colors. In the `ALWAYS` modes, links are always underlined and always clickable.
In the `NEVER` modes, links are never underlined and never clickable. In the `HOVER` modes, links become underlined and
clickable only when hovered over. Now let's clarify the difference between the two types of colors. `CUSTOM` colors
are those set by the JediTermFX user in the getHyperlinkColor() method of the settings. `ORIGINAL` colors are those
offered by the program running in the terminal. Thus, links can use either custom colors or the original text colors.

## Code Building <a name="code-building"></a>

To build the library use standard Git and Maven commands:

    git clone https://github.com/techsenger/jeditermfx
    cd jeditermfx
    mvn clean install

## Running the Application <a name="application"></a>

The project contains a demo application that shows how to use this library. There are two ways to run the application.

### Using Maven <a name="application-maven"></a>

To run application using maven plugin execute the following commands in the root of the project:

    cd jeditermfx-app
    mvn javafx:run

Please note, that debugger settings are in `jeditermfx-app/pom.xml` file. If you want to try a dark theme,
uncomment the following line in the JavaFX plugin configuration in the pom.xml file:

```
<!--<commandlineArgs>theme=dark</commandlineArgs>-->
```
This will make JediTermFX use the `DarkThemeSettingsProvider`.

### Using Distro <a name="application-distro"></a>

After building the project, you will find a distribution archive named `jeditermfx-app-<version>.tar` in the
`jeditermfx-app/target` directory. Extracting this file will allow you to launch the application
using `.sh` or `.bat` scripts depending on your operating system.

## License <a name="license"></a>

JediTermFX is dual-licensed under both the LGPLv3 (found in the LICENSE-LGPL-3.txt file in the root directory) and
Apache 2.0 License (found in the LICENSE-APACHE-2.0.txt file in the root directory). You may select, at your option,
one of the above-listed licenses.

## Contributing <a name="contributing"></a>

We welcome all contributions. You can help by reporting bugs, suggesting improvements, or submitting pull requests
with fixes and new features. If you have any questions, feel free to reach out — we’ll be happy to assist you.

## Support Us <a name="support-us"></a>

You can support our open-source work through [GitHub Sponsors](https://github.com/sponsors/techsenger).
Your contribution helps us maintain projects, develop new features, and provide ongoing improvements.
Multiple sponsorship tiers are available, each offering different levels of recognition and benefits.



