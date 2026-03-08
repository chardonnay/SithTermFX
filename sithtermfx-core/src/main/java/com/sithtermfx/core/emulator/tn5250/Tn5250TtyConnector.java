package com.sithtermfx.core.emulator.tn5250;

import com.sithtermfx.core.emulator.tn3270.TelnetConstants;
import com.sithtermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocketFactory;

/**
 * TN5250 telnet connector implementing the TN5250E protocol (RFC 4777).
 * <p>
 * Handles telnet option negotiation (BINARY, EOR, TERMINAL-TYPE, NEW-ENVIRON),
 * 5250 record framing with IAC EOR delimiters, and NEW-ENVIRON exchange for
 * IBM-specific user variables (IBMRSEED, KBDTYPE, CODEPAGE, CHARSET, DEVNAME).
 * <p>
 * <strong>Security note:</strong> By default, connections are unencrypted (plaintext TCP).
 * Use {@link #connect(boolean)} with {@code useSsl=true} to create a TLS-secured
 * connection. When TLS is not available, consider using an SSH tunnel or VPN.
 *
 * @author Daniel Mengel
 */
public class Tn5250TtyConnector implements com.sithtermfx.core.TtyConnector {

    private static final Logger logger = LoggerFactory.getLogger(Tn5250TtyConnector.class);

    private static final String DEFAULT_TERMINAL_TYPE = "IBM-3179-2";
    private static final String DEFAULT_CODE_PAGE = "37";

    private final String host;
    private final int port;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    private volatile boolean connected;

    private String terminalType = DEFAULT_TERMINAL_TYPE;
    private String deviceName = "";
    private String codePage = DEFAULT_CODE_PAGE;

    private static final int DEFAULT_SO_TIMEOUT_MS = 30_000;

    private boolean optionBinary;
    private boolean optionEor;
    private boolean optionTerminalType;
    private boolean optionNewEnviron;

    private byte[] pendingBuffer;
    private int pendingOffset;

    public Tn5250TtyConnector(@NotNull String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Opens an unencrypted TCP connection and performs TN5250E negotiation.
     */
    public void connect() throws IOException {
        connect(false);
    }

    /**
     * Opens a TCP connection (optionally TLS-secured) and performs TN5250E negotiation.
     *
     * @param useSsl if {@code true}, creates an SSL/TLS socket via {@link SSLSocketFactory}
     */
    public void connect(boolean useSsl) throws IOException {
        if (useSsl) {
            socket = SSLSocketFactory.getDefault().createSocket(host, port);
        } else {
            socket = new Socket(host, port);
        }
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(DEFAULT_SO_TIMEOUT_MS);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        connected = true;

        negotiate();
        logger.info("Connected to {}:{} as {}", host, port, terminalType);
    }

    public void setDeviceName(@NotNull String deviceName) {
        this.deviceName = deviceName;
    }

    public void setCodePage(@NotNull String codePage) {
        this.codePage = codePage;
    }

    public void setTerminalType(@NotNull String terminalType) {
        this.terminalType = terminalType;
    }

    /**
     * Reads a complete 5250 record delimited by IAC EOR.
     *
     * @return the record bytes (IAC EOR stripped), or {@code null} on disconnect
     * @throws IOException on I/O error
     */
    public byte[] readRecord() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        boolean prevIac = false;

        while (connected) {
            int b;
            try {
                b = inputStream.read();
            } catch (SocketTimeoutException e) {
                continue;
            }
            if (b == -1) {
                connected = false;
                return null;
            }

            if (prevIac) {
                prevIac = false;
                if (b == TelnetConstants.EOR) {
                    return bos.toByteArray();
                } else if (b == TelnetConstants.IAC) {
                    bos.write(TelnetConstants.IAC);
                } else if (b == TelnetConstants.WILL || b == TelnetConstants.WONT
                        || b == TelnetConstants.DO || b == TelnetConstants.DONT) {
                    int option = readByte();
                    handleTelnetNegotiation(b, option);
                } else if (b == TelnetConstants.SB) {
                    handleSubNegotiation();
                } else {
                    logger.debug("Ignoring telnet command during record read: 0x{}", Integer.toHexString(b));
                }
            } else if (b == TelnetConstants.IAC) {
                prevIac = true;
            } else {
                bos.write(b);
            }
        }
        return null;
    }

    /**
     * Sends a 5250 record framed with IAC EOR.
     */
    public void writeRecord(byte[] record) throws IOException {
        if (!connected) {
            throw new IOException("Not connected");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(record.length + 10);
        for (byte b : record) {
            bos.write(b & 0xFF);
            if ((b & 0xFF) == TelnetConstants.IAC) {
                bos.write(TelnetConstants.IAC);
            }
        }
        bos.write(TelnetConstants.IAC);
        bos.write(TelnetConstants.EOR);

        synchronized (outputStream) {
            outputStream.write(bos.toByteArray());
            outputStream.flush();
        }
    }

    // TtyConnector interface

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        int copied = 0;

        if (pendingBuffer != null && pendingOffset < pendingBuffer.length) {
            int available = pendingBuffer.length - pendingOffset;
            int toCopy = Math.min(available, length);
            for (int i = 0; i < toCopy; i++) {
                buf[offset + i] = (char) (pendingBuffer[pendingOffset + i] & 0xFF);
            }
            pendingOffset += toCopy;
            copied += toCopy;
            if (pendingOffset >= pendingBuffer.length) {
                pendingBuffer = null;
                pendingOffset = 0;
            }
            if (copied >= length) {
                return copied;
            }
        }

        byte[] record = readRecord();
        if (record == null) {
            return copied > 0 ? copied : -1;
        }

        int remaining = length - copied;
        int toCopy = Math.min(record.length, remaining);
        for (int i = 0; i < toCopy; i++) {
            buf[offset + copied + i] = (char) (record[i] & 0xFF);
        }
        copied += toCopy;

        if (toCopy < record.length) {
            pendingBuffer = record;
            pendingOffset = toCopy;
        }

        return copied;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        writeRecord(bytes);
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        // 5250 is block-mode; resize is not supported mid-session
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected()) {
            Thread.sleep(500);
        }
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return connected && inputStream.available() > 0;
    }

    @Override
    public String getName() {
        return "TN5250:" + host + ":" + port;
    }

    @Override
    public void close() {
        connected = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            logger.debug("Error closing socket", e);
        }
    }

    // Telnet negotiation

    private void negotiate() throws IOException {
        sendWill(TelnetConstants.OPT_BINARY);
        sendDo(TelnetConstants.OPT_BINARY);
        sendWill(TelnetConstants.OPT_EOR);
        sendDo(TelnetConstants.OPT_EOR);
        sendWill(TelnetConstants.OPT_TERMINAL_TYPE);
        sendWill(TelnetConstants.OPT_NEW_ENVIRON);

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (inputStream.available() > 0) {
                int b = readByte();
                if (b == TelnetConstants.IAC) {
                    int cmd = readByte();
                    if (cmd == TelnetConstants.DO || cmd == TelnetConstants.DONT
                            || cmd == TelnetConstants.WILL || cmd == TelnetConstants.WONT) {
                        int option = readByte();
                        handleTelnetNegotiation(cmd, option);
                    } else if (cmd == TelnetConstants.SB) {
                        handleSubNegotiation();
                    }
                }
            } else {
                if (optionBinary && optionEor) {
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleTelnetNegotiation(int command, int option) throws IOException {
        switch (command) {
            case TelnetConstants.DO:
                handleDo(option);
                break;
            case TelnetConstants.DONT:
                handleDont(option);
                break;
            case TelnetConstants.WILL:
                handleWill(option);
                break;
            case TelnetConstants.WONT:
                handleWont(option);
                break;
        }
    }

    private void handleDo(int option) throws IOException {
        switch (option) {
            case TelnetConstants.OPT_BINARY:
                optionBinary = true;
                sendWill(option);
                break;
            case TelnetConstants.OPT_EOR:
                optionEor = true;
                sendWill(option);
                break;
            case TelnetConstants.OPT_TERMINAL_TYPE:
                optionTerminalType = true;
                sendWill(option);
                break;
            case TelnetConstants.OPT_NEW_ENVIRON:
                optionNewEnviron = true;
                sendWill(option);
                break;
            default:
                sendWont(option);
                break;
        }
    }

    private void handleDont(int option) throws IOException {
        sendWont(option);
    }

    private void handleWill(int option) throws IOException {
        switch (option) {
            case TelnetConstants.OPT_BINARY:
                optionBinary = true;
                sendDo(option);
                break;
            case TelnetConstants.OPT_EOR:
                optionEor = true;
                sendDo(option);
                break;
            case TelnetConstants.OPT_NEW_ENVIRON:
                sendDo(option);
                break;
            default:
                sendDont(option);
                break;
        }
    }

    private void handleWont(int option) throws IOException {
        sendDont(option);
    }

    private void handleSubNegotiation() throws IOException {
        ByteArrayOutputStream sbData = new ByteArrayOutputStream(64);
        boolean prevIac = false;

        while (true) {
            int b = readByte();
            if (b == -1) {
                return;
            }
            if (prevIac) {
                prevIac = false;
                if (b == TelnetConstants.SE) {
                    break;
                } else if (b == TelnetConstants.IAC) {
                    sbData.write(b);
                } else {
                    sbData.write(TelnetConstants.IAC);
                    sbData.write(b);
                }
            } else if (b == TelnetConstants.IAC) {
                prevIac = true;
            } else {
                sbData.write(b);
            }
        }

        byte[] sb = sbData.toByteArray();
        if (sb.length == 0) {
            return;
        }

        int option = sb[0] & 0xFF;
        switch (option) {
            case TelnetConstants.OPT_TERMINAL_TYPE:
                if (sb.length > 1 && sb[1] == TelnetConstants.TERMINAL_TYPE_SEND) {
                    sendTerminalType();
                }
                break;
            case TelnetConstants.OPT_NEW_ENVIRON:
                if (sb.length > 1 && sb[1] == TelnetConstants.ENVIRON_SEND) {
                    sendNewEnviron();
                }
                break;
            default:
                logger.debug("Unhandled sub-negotiation for option 0x{}", Integer.toHexString(option));
                break;
        }
    }

    private void sendTerminalType() throws IOException {
        byte[] typeBytes = terminalType.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(typeBytes.length + 6);
        bos.write(TelnetConstants.IAC);
        bos.write(TelnetConstants.SB);
        bos.write(TelnetConstants.OPT_TERMINAL_TYPE);
        bos.write(TelnetConstants.TERMINAL_TYPE_IS);
        bos.write(typeBytes);
        bos.write(TelnetConstants.IAC);
        bos.write(TelnetConstants.SE);

        synchronized (outputStream) {
            outputStream.write(bos.toByteArray());
            outputStream.flush();
        }
        logger.debug("Sent terminal type: {}", terminalType);
    }

    /**
     * Sends the NEW-ENVIRON IS response with IBM 5250-specific user variables.
     */
    private void sendNewEnviron() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(128);
        bos.write(TelnetConstants.IAC);
        bos.write(TelnetConstants.SB);
        bos.write(TelnetConstants.OPT_NEW_ENVIRON);
        bos.write(TelnetConstants.ENVIRON_IS);

        addUserVar(bos, "IBMRSEED", generateRandomSeed());
        addUserVar(bos, "KBDTYPE", "USB");
        addUserVar(bos, "CODEPAGE", codePage);
        addUserVar(bos, "CHARSET", "697");

        if (deviceName != null && !deviceName.isEmpty()) {
            addUserVar(bos, "DEVNAME", deviceName);
        }

        bos.write(TelnetConstants.IAC);
        bos.write(TelnetConstants.SE);

        synchronized (outputStream) {
            outputStream.write(bos.toByteArray());
            outputStream.flush();
        }
        logger.debug("Sent NEW-ENVIRON response (codepage={}, device={})", codePage, deviceName);
    }

    private void addUserVar(ByteArrayOutputStream bos, String name, String value) {
        bos.write(TelnetConstants.ENVIRON_USERVAR);
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        bos.write(nameBytes, 0, nameBytes.length);
        bos.write(TelnetConstants.ENVIRON_VALUE);
        byte[] valueBytes = value.getBytes(StandardCharsets.US_ASCII);
        bos.write(valueBytes, 0, valueBytes.length);
    }

    private String generateRandomSeed() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append((char) ('A' + (int) (Math.random() * 26)));
        }
        return sb.toString();
    }

    private void sendCommand(int... bytes) throws IOException {
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            buf[i] = (byte) bytes[i];
        }
        synchronized (outputStream) {
            outputStream.write(buf);
            outputStream.flush();
        }
    }

    private void sendWill(int option) throws IOException {
        sendCommand(TelnetConstants.IAC, TelnetConstants.WILL, option);
    }

    private void sendWont(int option) throws IOException {
        sendCommand(TelnetConstants.IAC, TelnetConstants.WONT, option);
    }

    private void sendDo(int option) throws IOException {
        sendCommand(TelnetConstants.IAC, TelnetConstants.DO, option);
    }

    private void sendDont(int option) throws IOException {
        sendCommand(TelnetConstants.IAC, TelnetConstants.DONT, option);
    }

    private int readByte() throws IOException {
        int b = inputStream.read();
        if (b == -1) {
            connected = false;
            throw new IOException("Connection closed by remote host");
        }
        return b;
    }
}
