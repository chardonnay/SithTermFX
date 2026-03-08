package com.sithtermfx.core.emulator.tn3270;

/**
 * AID (Attention Identifier) key codes for the 3270 data stream.
 * <p>
 * When the operator presses an AID key (Enter, PF key, PA key, Clear, etc.),
 * the terminal sends the corresponding AID byte as the first byte of its
 * response to the host.
 *
 * @author Daniel Mengel
 */
public final class Aid3270 {

    private Aid3270() {
    }

    // Basic AID keys
    public static final int ENTER = 0x7D;
    public static final int CLEAR = 0x6D;

    // PA keys (Program Attention — short read, no field data)
    public static final int PA1 = 0x6C;
    public static final int PA2 = 0x6E;
    public static final int PA3 = 0x6B;

    // PF keys 1–12
    public static final int PF1 = 0xF1;
    public static final int PF2 = 0xF2;
    public static final int PF3 = 0xF3;
    public static final int PF4 = 0xF4;
    public static final int PF5 = 0xF5;
    public static final int PF6 = 0xF6;
    public static final int PF7 = 0xF7;
    public static final int PF8 = 0xF8;
    public static final int PF9 = 0xF9;
    public static final int PF10 = 0x7A;
    public static final int PF11 = 0x7B;
    public static final int PF12 = 0x7C;

    // PF keys 13–24
    public static final int PF13 = 0xC1;
    public static final int PF14 = 0xC2;
    public static final int PF15 = 0xC3;
    public static final int PF16 = 0xC4;
    public static final int PF17 = 0xC5;
    public static final int PF18 = 0xC6;
    public static final int PF19 = 0xC7;
    public static final int PF20 = 0xC8;
    public static final int PF21 = 0xC9;
    public static final int PF22 = 0x4A;
    public static final int PF23 = 0x4B;
    public static final int PF24 = 0x4C;

    // Special AID keys
    public static final int SYSREQ = 0xF0;
    public static final int ATTN = 0xFF;

    /**
     * Determines whether the given AID produces a short read (no field data).
     * PA keys and CLEAR send only the AID byte and cursor address, without
     * any modified field data.
     *
     * @param aid the AID byte
     * @return {@code true} if this AID triggers a short read
     */
    public static boolean isShortRead(int aid) {
        return aid == PA1 || aid == PA2 || aid == PA3 || aid == CLEAR;
    }

    /**
     * @return the PF key AID for a given PF number (1–24), or -1 if out of range
     */
    public static int pfKey(int number) {
        switch (number) {
            case 1: return PF1;
            case 2: return PF2;
            case 3: return PF3;
            case 4: return PF4;
            case 5: return PF5;
            case 6: return PF6;
            case 7: return PF7;
            case 8: return PF8;
            case 9: return PF9;
            case 10: return PF10;
            case 11: return PF11;
            case 12: return PF12;
            case 13: return PF13;
            case 14: return PF14;
            case 15: return PF15;
            case 16: return PF16;
            case 17: return PF17;
            case 18: return PF18;
            case 19: return PF19;
            case 20: return PF20;
            case 21: return PF21;
            case 22: return PF22;
            case 23: return PF23;
            case 24: return PF24;
            default: return -1;
        }
    }
}
