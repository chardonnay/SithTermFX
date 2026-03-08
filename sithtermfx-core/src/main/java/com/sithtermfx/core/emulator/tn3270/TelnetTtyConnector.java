package com.sithtermfx.core.emulator.tn3270;

import com.sithtermfx.core.TtyConnector;
import com.sithtermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static com.sithtermfx.core.emulator.tn3270.TelnetConstants.*;

/**
 * Base telnet connector implementing RFC 854/855 option negotiation.
 * <p>
 * Handles IAC DO/DONT/WILL/WONT negotiation for BINARY, SGA, TERMINAL-TYPE,
 * EOR, and NAWS options. The {@link #read(char[], int, int)} method transparently
 * filters IAC sequences from the data stream.
 * <p>
 * <strong>Security note:</strong> By default, connections are unencrypted (plaintext TCP).
 * Use {@link #connect(boolean)} with {@code useTls=true} to create a TLS-secured
 * connection via {@link SSLSocketFactory}. When TLS is not available, consider using
 * an SSH tunnel or VPN.
 *
 * @author Daniel Mengel
 */
public class TelnetTtyConnector implements TtyConnector {

    private static final Logger LOG = LoggerFactory.getLogger(TelnetTtyConnector.class);

    private final String myHost;
    private final int myPort;
    private final String myTerminalType;

    private Socket mySocket;
    private InputStream myInputStream;
    private OutputStream myOutputStream;

    private final AtomicBoolean myConnected = new AtomicBoolean(false);

    private final Object myWriteLock = new Object();
    private final Object myReadLock = new Object();

    private int myColumns = 80;
    private int myRows = 24;

    private static final int DEFAULT_SO_TIMEOUT_MS = 30_000;

    private final boolean[] myLocalOptions = new boolean[256];
    private final boolean[] myRemoteOptions = new boolean[256];

    public TelnetTtyConnector(String host, int port, String terminalType) {
        myHost = host;
        myPort = port;
        myTerminalType = terminalType;
    }

    /**
     * Establishes an unencrypted TCP connection and performs telnet negotiation.
     */
    public void connect() throws IOException {
        connect(false);
    }

    /**
     * Establishes a TCP connection (optionally TLS-secured) and performs telnet negotiation.
     *
     * @param useTls if {@code true}, creates an SSL/TLS socket via {@link SSLSocketFactory}
     */
    public void connect(boolean useTls) throws IOException {
        if (useTls) {
            SSLSocket sslSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(myHost, myPort);
            SSLParameters sslParams = sslSocket.getSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParams);
            mySocket = sslSocket;
        } else {
            mySocket = new Socket(myHost, myPort);
        }
        mySocket.setKeepAlive(true);
        mySocket.setTcpNoDelay(true);
        mySocket.setSoTimeout(DEFAULT_SO_TIMEOUT_MS);
        myInputStream = mySocket.getInputStream();
        myOutputStream = mySocket.getOutputStream();
        myConnected.set(true);
        negotiate();
    }

    /**
     * Performs initial telnet option negotiation. Subclasses may override to
     * extend the handshake (e.g. TN3270E).
     */
    protected void negotiate() throws IOException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (myInputStream.available() > 0) {
                processAvailableInput();
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processAvailableInput() throws IOException {
        while (myInputStream.available() > 0) {
            int b = myInputStream.read();
            if (b == -1) {
                myConnected.set(false);
                return;
            }
            if (b == IAC) {
                processIac();
            }
        }
    }

    private void processIac() throws IOException {
        int command = myInputStream.read();
        if (command == -1) {
            myConnected.set(false);
            return;
        }
        switch (command) {
            case DO -> handleDo(myInputStream.read());
            case DONT -> handleDont(myInputStream.read());
            case WILL -> handleWill(myInputStream.read());
            case WONT -> handleWont(myInputStream.read());
            case SB -> handleSubnegotiation();
            case IAC -> { /* escaped 0xFF data byte — ignored during negotiation */ }
            default -> LOG.debug("Unknown IAC command: {}", command);
        }
    }

    private void handleDo(int option) throws IOException {
        if (option == -1) return;
        LOG.debug("Received DO {}", option);
        switch (option) {
            case OPT_BINARY, OPT_SGA, OPT_EOR -> {
                myLocalOptions[option] = true;
                sendCommand(WILL, option);
            }
            case OPT_TERMINAL_TYPE -> {
                myLocalOptions[option] = true;
                sendCommand(WILL, option);
            }
            case OPT_NAWS -> {
                myLocalOptions[option] = true;
                sendCommand(WILL, option);
                sendNaws();
            }
            case OPT_TN3270E -> handleDoTn3270e();
            default -> sendCommand(WONT, option);
        }
    }

    /**
     * Hook for subclasses to handle DO TN3270E. Default refuses.
     */
    protected void handleDoTn3270e() throws IOException {
        sendCommand(WONT, OPT_TN3270E);
    }

    private void handleDont(int option) throws IOException {
        if (option == -1) return;
        LOG.debug("Received DONT {}", option);
        myLocalOptions[option] = false;
        sendCommand(WONT, option);
    }

    private void handleWill(int option) throws IOException {
        if (option == -1) return;
        LOG.debug("Received WILL {}", option);
        switch (option) {
            case OPT_BINARY, OPT_SGA, OPT_EOR -> {
                myRemoteOptions[option] = true;
                sendCommand(DO, option);
            }
            default -> sendCommand(DONT, option);
        }
    }

    private void handleWont(int option) throws IOException {
        if (option == -1) return;
        LOG.debug("Received WONT {}", option);
        myRemoteOptions[option] = false;
        sendCommand(DONT, option);
    }

    private void handleSubnegotiation() throws IOException {
        int option = myInputStream.read();
        if (option == -1) return;

        byte[] data = readUntilIacSe();
        processSubnegotiation(option, data);
    }

    /**
     * Processes a subnegotiation payload. Override to extend.
     */
    protected void processSubnegotiation(int option, byte[] data) throws IOException {
        if (option == OPT_TERMINAL_TYPE && data.length > 0 && data[0] == TERMINAL_TYPE_SEND) {
            sendTerminalType();
        }
    }

    private void sendTerminalType() throws IOException {
        byte[] typeBytes = myTerminalType.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[typeBytes.length + 1];
        payload[0] = (byte) TERMINAL_TYPE_IS;
        System.arraycopy(typeBytes, 0, payload, 1, typeBytes.length);
        sendSubnegotiation(OPT_TERMINAL_TYPE, payload);
    }

    private void sendNaws() throws IOException {
        byte[] payload = new byte[]{
                (byte) (myColumns >> 8), (byte) (myColumns & 0xFF),
                (byte) (myRows >> 8), (byte) (myRows & 0xFF)
        };
        sendSubnegotiation(OPT_NAWS, payload);
    }

    /**
     * Reads raw bytes until IAC SE, un-stuffing doubled IACs.
     */
    protected byte[] readUntilIacSe() throws IOException {
        byte[] buf = new byte[4096];
        int len = 0;
        boolean prevIac = false;
        while (true) {
            int b = myInputStream.read();
            if (b == -1) break;
            if (prevIac) {
                if (b == SE) break;
                if (b == IAC) {
                    if (len < buf.length) buf[len++] = (byte) IAC;
                    prevIac = false;
                    continue;
                }
                prevIac = false;
                continue;
            }
            if (b == IAC) {
                prevIac = true;
                continue;
            }
            if (len < buf.length) buf[len++] = (byte) b;
        }
        byte[] result = new byte[len];
        System.arraycopy(buf, 0, result, 0, len);
        return result;
    }

    protected void sendCommand(int command, int option) throws IOException {
        synchronized (myWriteLock) {
            myOutputStream.write(new byte[]{(byte) IAC, (byte) command, (byte) option});
            myOutputStream.flush();
        }
    }

    /**
     * Sends a subnegotiation: IAC SB option data IAC SE.
     * Doubles any 0xFF bytes inside data.
     */
    protected void sendSubnegotiation(int option, byte[] data) throws IOException {
        synchronized (myWriteLock) {
            myOutputStream.write(new byte[]{(byte) IAC, (byte) SB, (byte) option});
            for (byte b : data) {
                myOutputStream.write(b & 0xFF);
                if ((b & 0xFF) == IAC) myOutputStream.write(IAC);
            }
            myOutputStream.write(new byte[]{(byte) IAC, (byte) SE});
            myOutputStream.flush();
        }
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        synchronized (myReadLock) {
            int count = 0;
            while (count < length) {
                int b;
                try {
                    b = myInputStream.read();
                } catch (SocketTimeoutException e) {
                    return count > 0 ? count : 0;
                }
                if (b == -1) {
                    myConnected.set(false);
                    return count > 0 ? count : -1;
                }
                if (b == IAC) {
                    int cmd;
                    try {
                        cmd = myInputStream.read();
                    } catch (SocketTimeoutException e) {
                        return count > 0 ? count : 0;
                    }
                    if (cmd == -1) {
                        myConnected.set(false);
                        return count > 0 ? count : -1;
                    }
                    if (cmd == IAC) {
                        buf[offset + count++] = (char) 0xFF;
                    } else if (cmd == SB) {
                        handleSubnegotiation();
                    } else if (cmd == EOR) {
                        if (count > 0) return count;
                    } else if (cmd == DO || cmd == DONT || cmd == WILL || cmd == WONT) {
                        int opt = myInputStream.read();
                        if (opt == -1) {
                            myConnected.set(false);
                            return count > 0 ? count : -1;
                        }
                        switch (cmd) {
                            case DO -> handleDo(opt);
                            case DONT -> handleDont(opt);
                            case WILL -> handleWill(opt);
                            case WONT -> handleWont(opt);
                        }
                    }
                } else {
                    buf[offset + count++] = (char) b;
                }
            }
            return count;
        }
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        synchronized (myWriteLock) {
            myOutputStream.write(bytes);
            myOutputStream.flush();
        }
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return myConnected.get() && mySocket != null && !mySocket.isClosed();
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        myColumns = termSize.getColumns();
        myRows = termSize.getRows();
        if (myLocalOptions[OPT_NAWS] && isConnected()) {
            try {
                sendNaws();
            } catch (IOException e) {
                LOG.warn("Failed to send NAWS update", e);
            }
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected()) {
            Thread.sleep(100);
        }
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        synchronized (myReadLock) {
            return isConnected() && myInputStream.available() > 0;
        }
    }

    @Override
    public String getName() {
        return "Telnet " + myHost + ":" + myPort;
    }

    @Override
    public void close() {
        myConnected.set(false);
        try {
            if (mySocket != null) mySocket.close();
        } catch (IOException e) {
            LOG.debug("Error closing socket", e);
        }
    }

    protected InputStream getInputStream() {
        return myInputStream;
    }

    protected OutputStream getOutputStream() {
        return myOutputStream;
    }

    protected String getTerminalType() {
        return myTerminalType;
    }

    protected boolean isLocalOptionEnabled(int option) {
        return myLocalOptions[option];
    }

    protected boolean isRemoteOptionEnabled(int option) {
        return myRemoteOptions[option];
    }

    protected void setLocalOption(int option, boolean enabled) {
        myLocalOptions[option] = enabled;
    }

    protected void setRemoteOption(int option, boolean enabled) {
        myRemoteOptions[option] = enabled;
    }
}
