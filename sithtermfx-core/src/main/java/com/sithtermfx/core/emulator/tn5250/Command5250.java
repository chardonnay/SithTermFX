package com.sithtermfx.core.emulator.tn5250;

/**
 * 5250 data stream command codes as defined in the IBM 5250 Data Stream Reference (SA21-9247).
 * <p>
 * Each constant represents a single-byte command opcode that appears in the GDS header
 * of a 5250 data stream record.
 *
 * @author Daniel Mengel
 */
public final class Command5250 {

    private Command5250() {
    }

    // Write commands
    public static final int WRITE_TO_DISPLAY = 0x11;
    public static final int WRITE_ERROR_CODE = 0x21;
    public static final int WRITE_ERROR_CODE_TO_WINDOW = 0x22;

    // Clear commands
    public static final int CLEAR_UNIT = 0x40;
    public static final int CLEAR_UNIT_ALTERNATE = 0x20;
    public static final int CLEAR_FORMAT_TABLE = 0x50;

    // Structured field
    public static final int WRITE_STRUCTURED_FIELD = 0xF3;

    // Screen save / restore
    public static final int SAVE_SCREEN = 0x02;
    public static final int RESTORE_SCREEN = 0x12;

    // Scroll
    public static final int ROLL = 0x23;

    // Read commands
    public static final int READ_IMMEDIATE = 0x72;
    public static final int READ_SCREEN = 0x62;
    public static final int READ_MODIFIED_FIELDS = 0x52;

    // 5250 order codes (used inside Write to Display data)
    public static final int ORDER_SOH = 0x01;
    public static final int ORDER_RA = 0x02;
    public static final int ORDER_SBA = 0x11;
    public static final int ORDER_SF = 0x1D;
    public static final int ORDER_MC = 0x14;
    public static final int ORDER_IC = 0x13;
    public static final int ORDER_WEA = 0x15;
    public static final int ORDER_TD = 0x10;
    public static final int ORDER_EA = 0x12;

    // AID codes (attention identifier)
    public static final int AID_ENTER = 0xF1;
    public static final int AID_F1 = 0x31;
    public static final int AID_F2 = 0x32;
    public static final int AID_F3 = 0x33;
    public static final int AID_F4 = 0x34;
    public static final int AID_F5 = 0x35;
    public static final int AID_F6 = 0x36;
    public static final int AID_F7 = 0x37;
    public static final int AID_F8 = 0x38;
    public static final int AID_F9 = 0x39;
    public static final int AID_F10 = 0x3A;
    public static final int AID_F11 = 0x3B;
    public static final int AID_F12 = 0x3C;
    public static final int AID_F13 = 0xB1;
    public static final int AID_F14 = 0xB2;
    public static final int AID_F15 = 0xB3;
    public static final int AID_F16 = 0xB4;
    public static final int AID_F17 = 0xB5;
    public static final int AID_F18 = 0xB6;
    public static final int AID_F19 = 0xB7;
    public static final int AID_F20 = 0xB8;
    public static final int AID_F21 = 0xB9;
    public static final int AID_F22 = 0xBA;
    public static final int AID_F23 = 0xBB;
    public static final int AID_F24 = 0xBC;
    public static final int AID_HELP = 0xF3;
    public static final int AID_ROLL_UP = 0xF5;
    public static final int AID_ROLL_DOWN = 0xF4;
    public static final int AID_PRINT = 0xF6;
    public static final int AID_RECORD_BACKSPACE = 0xF8;
    public static final int AID_AUTO_ENTER = 0x3F;
    public static final int AID_CLEAR = 0xBD;
    public static final int AID_ATTN = 0x70;
    public static final int AID_SYSREQ = 0x71;

    /**
     * Returns {@code true} if the given opcode is a write-type command that updates the screen.
     */
    public static boolean isWriteCommand(int opcode) {
        return opcode == WRITE_TO_DISPLAY
                || opcode == WRITE_ERROR_CODE
                || opcode == WRITE_ERROR_CODE_TO_WINDOW
                || opcode == WRITE_STRUCTURED_FIELD;
    }

    /**
     * Returns {@code true} if the given opcode is a clear-type command.
     */
    public static boolean isClearCommand(int opcode) {
        return opcode == CLEAR_UNIT
                || opcode == CLEAR_UNIT_ALTERNATE
                || opcode == CLEAR_FORMAT_TABLE;
    }

    /**
     * Returns {@code true} if the given opcode is a read-type command.
     */
    public static boolean isReadCommand(int opcode) {
        return opcode == READ_IMMEDIATE
                || opcode == READ_SCREEN
                || opcode == READ_MODIFIED_FIELDS;
    }

    /**
     * Returns {@code true} if the given opcode is a screen save/restore or roll command.
     */
    public static boolean isScreenManagementCommand(int opcode) {
        return opcode == SAVE_SCREEN
                || opcode == RESTORE_SCREEN
                || opcode == ROLL;
    }

    /**
     * Returns a human-readable name for the given 5250 command opcode.
     */
    public static String nameOf(int opcode) {
        switch (opcode) {
            case WRITE_TO_DISPLAY:           return "Write to Display";
            case WRITE_ERROR_CODE:           return "Write Error Code";
            case WRITE_ERROR_CODE_TO_WINDOW: return "Write Error Code to Window";
            case CLEAR_UNIT:                 return "Clear Unit";
            case CLEAR_UNIT_ALTERNATE:       return "Clear Unit Alternate";
            case CLEAR_FORMAT_TABLE:         return "Clear Format Table";
            case WRITE_STRUCTURED_FIELD:     return "Write Structured Field";
            case SAVE_SCREEN:                return "Save Screen";
            case RESTORE_SCREEN:             return "Restore Screen";
            case ROLL:                       return "Roll";
            case READ_IMMEDIATE:             return "Read Immediate";
            case READ_SCREEN:                return "Read Screen";
            case READ_MODIFIED_FIELDS:       return "Read Modified Fields";
            default:                         return "Unknown(0x" + Integer.toHexString(opcode) + ")";
        }
    }
}
