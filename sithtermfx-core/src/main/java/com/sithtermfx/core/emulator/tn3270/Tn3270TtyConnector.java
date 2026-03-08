package com.sithtermfx.core.emulator.tn3270;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.sithtermfx.core.emulator.tn3270.TelnetConstants.*;

/**
 * TN3270E-aware telnet connector (RFC 2355).
 * <p>
 * Extends {@link TelnetTtyConnector} with TN3270E device-type and function
 * negotiation. When the host supports TN3270E, the connector switches to
 * TN3270E mode and frames 3270 data records using IAC EOR delimiters.
 * <p>
 * If TN3270E negotiation is rejected or unsupported by the host, the connector
 * falls back to plain TN3270 (RFC 1576) mode.
 *
 * @author Daniel Mengel
 */
public class Tn3270TtyConnector extends TelnetTtyConnector {

    private static final Logger LOG = LoggerFactory.getLogger(Tn3270TtyConnector.class);

    private boolean myTn3270eMode = false;
    private String myDeviceType = "IBM-3278-2-E";
    private String myLuName;

    public Tn3270TtyConnector(String host, int port, String terminalType) {
        super(host, port, terminalType);
    }

    public void setDeviceType(String deviceType) {
        myDeviceType = deviceType;
    }

    public void setLuName(String luName) {
        myLuName = luName;
    }

    public boolean isTn3270eMode() {
        return myTn3270eMode;
    }

    @Override
    protected void handleDoTn3270e() throws IOException {
        setLocalOption(OPT_TN3270E, true);
        sendCommand(WILL, OPT_TN3270E);
        sendDeviceTypeRequest();
    }

    private void sendDeviceTypeRequest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(TN3270E_DEVICE_TYPE);
        baos.write(TN3270E_REQUEST);
        byte[] devBytes = myDeviceType.getBytes(StandardCharsets.US_ASCII);
        baos.write(devBytes);

        if (myLuName != null && !myLuName.isEmpty()) {
            baos.write(TN3270E_CONNECT);
            baos.write(myLuName.getBytes(StandardCharsets.US_ASCII));
        }

        sendSubnegotiation(OPT_TN3270E, baos.toByteArray());
        LOG.debug("Sent TN3270E device-type request: {} LU: {}", myDeviceType, myLuName);
    }

    @Override
    protected void processSubnegotiation(int option, byte[] data) throws IOException {
        if (option == OPT_TN3270E && data.length > 0) {
            processTn3270eSubnegotiation(data);
        } else {
            super.processSubnegotiation(option, data);
        }
    }

    private void processTn3270eSubnegotiation(byte[] data) throws IOException {
        if (data.length < 2) return;

        int subCommand = data[0];
        int action = data[1];

        if (subCommand == TN3270E_DEVICE_TYPE) {
            if (action == TN3270E_IS) {
                handleDeviceTypeIs(data);
            } else if (action == TN3270E_REJECT) {
                LOG.warn("TN3270E device type rejected, falling back to plain TN3270");
                myTn3270eMode = false;
            }
        } else if (subCommand == TN3270E_FUNCTIONS) {
            if (action == TN3270E_IS) {
                handleFunctionsIs(data);
            } else if (action == TN3270E_REQUEST) {
                handleFunctionsRequest(data);
            }
        }
    }

    private void handleDeviceTypeIs(byte[] data) throws IOException {
        int nameStart = 2;
        int nameEnd = data.length;
        for (int i = 2; i < data.length; i++) {
            if (data[i] == TN3270E_CONNECT) {
                nameEnd = i;
                break;
            }
        }
        String deviceName = new String(data, nameStart, nameEnd - nameStart, StandardCharsets.US_ASCII);
        LOG.info("TN3270E device type accepted: {}", deviceName);

        sendFunctionsRequest();
    }

    private void sendFunctionsRequest() throws IOException {
        sendSubnegotiation(OPT_TN3270E, new byte[]{
                (byte) TN3270E_FUNCTIONS, (byte) TN3270E_REQUEST
        });
    }

    private void handleFunctionsIs(byte[] data) {
        LOG.info("TN3270E functions negotiated, entering TN3270E mode");
        myTn3270eMode = true;
    }

    private void handleFunctionsRequest(byte[] data) throws IOException {
        sendSubnegotiation(OPT_TN3270E, new byte[]{
                (byte) TN3270E_FUNCTIONS, (byte) TN3270E_IS
        });
        myTn3270eMode = true;
        LOG.info("TN3270E functions accepted, entering TN3270E mode");
    }

    /**
     * Reads one complete 3270 record from the stream, delimited by IAC EOR.
     * In TN3270E mode, strips the 5-byte TN3270E header.
     *
     * @return the raw 3270 data record, or {@code null} on disconnect
     */
    public byte[] readRecord() throws IOException {
        InputStream in = getInputStream();
        if (in == null) return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        boolean prevIac = false;

        while (true) {
            int b = in.read();
            if (b == -1) {
                close();
                return null;
            }
            if (prevIac) {
                prevIac = false;
                if (b == EOR) {
                    break;
                } else if (b == IAC) {
                    baos.write(IAC);
                } else if (b == DO || b == DONT || b == WILL || b == WONT) {
                    int opt = in.read();
                    if (opt != -1) {
                        switch (b) {
                            case DO -> handleInlineNegotiation(DO, opt);
                            case DONT -> handleInlineNegotiation(DONT, opt);
                            case WILL -> handleInlineNegotiation(WILL, opt);
                            case WONT -> handleInlineNegotiation(WONT, opt);
                        }
                    }
                } else if (b == SB) {
                    int option = in.read();
                    if (option != -1) {
                        byte[] subData = readUntilIacSe();
                        processSubnegotiation(option, subData);
                    }
                }
                continue;
            }
            if (b == IAC) {
                prevIac = true;
                continue;
            }
            baos.write(b);
        }

        byte[] record = baos.toByteArray();
        if (myTn3270eMode && record.length >= 5) {
            byte[] stripped = new byte[record.length - 5];
            System.arraycopy(record, 5, stripped, 0, stripped.length);
            return stripped;
        }
        return record;
    }

    private void handleInlineNegotiation(int command, int option) throws IOException {
        switch (command) {
            case DO -> {
                if (option == OPT_BINARY || option == OPT_SGA || option == OPT_EOR
                        || option == OPT_TERMINAL_TYPE || option == OPT_TN3270E) {
                    sendCommand(WILL, option);
                } else {
                    sendCommand(WONT, option);
                }
            }
            case DONT -> sendCommand(WONT, option);
            case WILL -> {
                if (option == OPT_BINARY || option == OPT_SGA || option == OPT_EOR) {
                    sendCommand(DO, option);
                } else {
                    sendCommand(DONT, option);
                }
            }
            case WONT -> sendCommand(DONT, option);
        }
    }

    /**
     * Writes a complete 3270 record to the host, terminated by IAC EOR.
     * In TN3270E mode, prepends the 5-byte TN3270E header.
     */
    public void writeRecord(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length + 16);

        if (myTn3270eMode) {
            baos.write(TN3270E_DT_3270_DATA);
            baos.write(0x00); // request flag
            baos.write(0x00); // response flag
            baos.write(0x00); // sequence number high
            baos.write(0x00); // sequence number low
        }

        for (byte b : data) {
            baos.write(b & 0xFF);
            if ((b & 0xFF) == IAC) {
                baos.write(IAC);
            }
        }

        baos.write(IAC);
        baos.write(EOR);

        write(baos.toByteArray());
    }
}
