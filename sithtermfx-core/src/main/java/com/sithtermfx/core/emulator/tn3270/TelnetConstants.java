package com.sithtermfx.core.emulator.tn3270;

/**
 * Telnet protocol constants (RFC 854/855) and TN3270E extensions (RFC 2355).
 *
 * @author Daniel Mengel
 */
public final class TelnetConstants {

    private TelnetConstants() {
    }

    // Telnet commands
    public static final int IAC = 255;
    public static final int DONT = 254;
    public static final int DO = 253;
    public static final int WONT = 252;
    public static final int WILL = 251;
    public static final int SB = 250;
    public static final int GA = 249;
    public static final int EL = 248;
    public static final int EC = 247;
    public static final int AYT = 246;
    public static final int AO = 245;
    public static final int IP = 244;
    public static final int BRK = 243;
    public static final int DM = 242;
    public static final int NOP = 241;
    public static final int SE = 240;
    public static final int EOR = 239;

    // Telnet options
    public static final int OPT_BINARY = 0;
    public static final int OPT_ECHO = 1;
    public static final int OPT_SGA = 3;
    public static final int OPT_TERMINAL_TYPE = 24;
    public static final int OPT_EOR = 25;
    public static final int OPT_NAWS = 31;
    public static final int OPT_NEW_ENVIRON = 39;
    public static final int OPT_TN3270E = 40;

    // Terminal-Type sub-option
    public static final int TERMINAL_TYPE_IS = 0;
    public static final int TERMINAL_TYPE_SEND = 1;

    // TN3270E sub-option commands
    public static final int TN3270E_SEND = 8;
    public static final int TN3270E_IS = 4;
    public static final int TN3270E_DEVICE_TYPE = 2;
    public static final int TN3270E_REQUEST = 7;
    public static final int TN3270E_FUNCTIONS = 3;
    public static final int TN3270E_CONNECT = 1;
    public static final int TN3270E_REJECT = 6;

    // TN3270E data types
    public static final int TN3270E_DT_3270_DATA = 0;
    public static final int TN3270E_DT_SCS_DATA = 1;
    public static final int TN3270E_DT_NVT_DATA = 2;
    public static final int TN3270E_DT_SSCP_LU_DATA = 3;
    public static final int TN3270E_DT_RESPONSE = 5;
    public static final int TN3270E_DT_BIND_IMAGE = 7;
    public static final int TN3270E_DT_UNBIND = 8;

    // NEW-ENVIRON sub-option
    public static final int ENVIRON_IS = 0;
    public static final int ENVIRON_SEND = 1;
    public static final int ENVIRON_INFO = 2;
    public static final int ENVIRON_VAR = 0;
    public static final int ENVIRON_VALUE = 1;
    public static final int ENVIRON_USERVAR = 3;
}
