package com.sithtermfx.core.emulator.tn3270;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses and processes IBM 3270 data stream records.
 * <p>
 * Supports all standard 3270 commands (Write, Erase/Write, Erase/Write Alternate,
 * Read Buffer, Read Modified, Read Modified All) and 3270 orders (SBA, SF, SFE,
 * SA, IC, PT, RA, EUA, MF). Also handles Write Structured Field (WSF) for
 * query replies.
 *
 * @author Daniel Mengel
 */
public final class DataStream3270 {

    private static final Logger LOG = LoggerFactory.getLogger(DataStream3270.class);

    // 3270 data stream commands
    public static final int CMD_WRITE = 0xF1;
    public static final int CMD_WRITE_SNA = 0x01;
    public static final int CMD_ERASE_WRITE = 0xF5;
    public static final int CMD_ERASE_WRITE_SNA = 0x05;
    public static final int CMD_ERASE_WRITE_ALTERNATE = 0x7E;
    public static final int CMD_ERASE_WRITE_ALTERNATE_SNA = 0x0D;
    public static final int CMD_READ_BUFFER = 0xF2;
    public static final int CMD_READ_BUFFER_SNA = 0x02;
    public static final int CMD_READ_MODIFIED = 0xF6;
    public static final int CMD_READ_MODIFIED_SNA = 0x06;
    public static final int CMD_READ_MODIFIED_ALL = 0x6E;
    public static final int CMD_WSF = 0xF3;
    public static final int CMD_WSF_SNA = 0x11;

    // 3270 orders
    public static final int ORDER_SBA = 0x11;
    public static final int ORDER_SF = 0x1D;
    public static final int ORDER_SFE = 0x29;
    public static final int ORDER_SA = 0x28;
    public static final int ORDER_IC = 0x13;
    public static final int ORDER_PT = 0x05;
    public static final int ORDER_RA = 0x3C;
    public static final int ORDER_EUA = 0x12;
    public static final int ORDER_MF = 0x2C;

    // WCC bits
    private static final int WCC_RESET_MDT = 0x02;
    private static final int WCC_KEYBOARD_RESTORE = 0x01;
    private static final int WCC_SOUND_ALARM = 0x04;

    // WSF structured field IDs
    private static final int SF_READ_PARTITION = 0x01;
    private static final int SF_QUERY_REPLY = 0x81;
    private static final int SF_QUERY_LIST = 0x01;

    private DataStream3270() {
    }

    /**
     * Result of processing a record, communicating cursor position and
     * whether the keyboard should be unlocked.
     */
    public static class ProcessResult {
        private int myCursorAddress = -1;
        private boolean myKeyboardRestore = false;
        private boolean mySoundAlarm = false;
        private boolean myErasePerformed = false;
        private int myCommandType;

        public int getCursorAddress() { return myCursorAddress; }
        public boolean isKeyboardRestore() { return myKeyboardRestore; }
        public boolean isSoundAlarm() { return mySoundAlarm; }
        public boolean isErasePerformed() { return myErasePerformed; }
        public int getCommandType() { return myCommandType; }
    }

    /**
     * Main entry point: processes a single 3270 data stream record.
     *
     * @param record the raw 3270 record bytes (without IAC EOR framing)
     * @param screen the screen buffer to update
     * @return processing result with cursor address and keyboard state
     */
    public static ProcessResult processRecord(byte[] record, ScreenBuffer3270 screen) {
        ProcessResult result = new ProcessResult();
        if (record == null || record.length == 0) return result;

        int command = record[0] & 0xFF;
        result.myCommandType = command;

        switch (command) {
            case CMD_WRITE, CMD_WRITE_SNA ->
                    processWrite(record, screen, result, false, false);
            case CMD_ERASE_WRITE, CMD_ERASE_WRITE_SNA ->
                    processWrite(record, screen, result, true, false);
            case CMD_ERASE_WRITE_ALTERNATE, CMD_ERASE_WRITE_ALTERNATE_SNA ->
                    processWrite(record, screen, result, true, true);
            case CMD_READ_BUFFER, CMD_READ_BUFFER_SNA ->
                    result.myKeyboardRestore = true;
            case CMD_READ_MODIFIED, CMD_READ_MODIFIED_SNA ->
                    result.myKeyboardRestore = true;
            case CMD_READ_MODIFIED_ALL ->
                    result.myKeyboardRestore = true;
            case CMD_WSF, CMD_WSF_SNA ->
                    processWsf(record, screen, result);
            default ->
                    LOG.warn("Unknown 3270 command: 0x{}", Integer.toHexString(command));
        }
        return result;
    }

    private static void processWrite(byte[] record, ScreenBuffer3270 screen,
                                     ProcessResult result, boolean erase, boolean alternate) {
        if (erase) {
            screen.clearScreen();
            result.myErasePerformed = true;
        }

        if (record.length < 2) return;

        int wcc = record[1] & 0xFF;
        processWcc(wcc, screen, result);

        int bufferAddress = 0;
        int pos = 2;

        while (pos < record.length) {
            int b = record[pos] & 0xFF;

            switch (b) {
                case ORDER_SBA -> {
                    if (pos + 2 < record.length) {
                        bufferAddress = decodeBufferAddress(record[pos + 1] & 0xFF,
                                record[pos + 2] & 0xFF, screen.getSize());
                        pos += 3;
                    } else {
                        pos = record.length;
                    }
                }
                case ORDER_SF -> {
                    if (pos + 1 < record.length) {
                        int attrByte = record[pos + 1] & 0xFF;
                        FieldAttribute3270 attr = FieldAttribute3270.decode(attrByte);
                        screen.addField(bufferAddress, attr);
                        screen.setCharAt(bufferAddress, ' ');
                        bufferAddress = (bufferAddress + 1) % screen.getSize();
                        pos += 2;
                    } else {
                        pos = record.length;
                    }
                }
                case ORDER_SFE -> {
                    pos = processSfe(record, pos, screen, bufferAddress);
                    bufferAddress = (bufferAddress + 1) % screen.getSize();
                }
                case ORDER_SA -> {
                    if (pos + 2 < record.length) {
                        pos += 3;
                    } else {
                        pos = record.length;
                    }
                }
                case ORDER_IC -> {
                    result.myCursorAddress = bufferAddress;
                    pos++;
                }
                case ORDER_PT -> {
                    int next = screen.nextUnprotectedField(bufferAddress);
                    if (next >= 0) bufferAddress = next;
                    pos++;
                }
                case ORDER_RA -> {
                    if (pos + 3 < record.length) {
                        int targetAddr = decodeBufferAddress(record[pos + 1] & 0xFF,
                                record[pos + 2] & 0xFF, screen.getSize());
                        int fillByte = record[pos + 3] & 0xFF;
                        char fillChar = EbcdicCodec.ebcdicToUnicode(fillByte);

                        int current = bufferAddress;
                        while (current != targetAddr) {
                            screen.setCharAt(current, fillChar);
                            current = (current + 1) % screen.getSize();
                        }
                        bufferAddress = targetAddr;
                        pos += 4;
                    } else {
                        pos = record.length;
                    }
                }
                case ORDER_EUA -> {
                    if (pos + 2 < record.length) {
                        int targetAddr = decodeBufferAddress(record[pos + 1] & 0xFF,
                                record[pos + 2] & 0xFF, screen.getSize());
                        int current = bufferAddress;
                        while (current != targetAddr) {
                            FieldAttribute3270 effAttr = screen.getEffectiveAttribute(current);
                            if (effAttr == null || !effAttr.isProtected()) {
                                screen.setCharAt(current, ' ');
                            }
                            current = (current + 1) % screen.getSize();
                        }
                        bufferAddress = targetAddr;
                        pos += 3;
                    } else {
                        pos = record.length;
                    }
                }
                case ORDER_MF -> {
                    pos = processModifyField(record, pos, screen, bufferAddress);
                }
                default -> {
                    if (isWritableCharacter(b)) {
                        char unicode = EbcdicCodec.ebcdicToUnicode(b);
                        screen.setCharAt(bufferAddress, unicode);

                        Field3270 field = screen.getFieldAt(bufferAddress);
                        if (field != null) {
                            int offset = (bufferAddress - field.getFirstDataPosition()
                                    + screen.getSize()) % screen.getSize();
                            if (offset >= 0 && offset < field.getLength()) {
                                field.setCharAt(offset, unicode);
                            }
                        }

                        bufferAddress = (bufferAddress + 1) % screen.getSize();
                    }
                    pos++;
                }
            }
        }
    }

    private static void processWcc(int wcc, ScreenBuffer3270 screen, ProcessResult result) {
        if ((wcc & WCC_RESET_MDT) != 0) {
            for (Field3270 field : screen.getFields()) {
                field.getAttribute().setMdt(false);
            }
        }
        result.myKeyboardRestore = (wcc & WCC_KEYBOARD_RESTORE) != 0;
        result.mySoundAlarm = (wcc & WCC_SOUND_ALARM) != 0;
    }

    private static int processSfe(byte[] record, int pos, ScreenBuffer3270 screen, int bufferAddress) {
        if (pos + 1 >= record.length) return record.length;
        int pairCount = record[pos + 1] & 0xFF;
        int offset = pos + 2;

        FieldAttribute3270 attr = new FieldAttribute3270(0);
        for (int i = 0; i < pairCount && offset + 1 < record.length; i++) {
            int type = record[offset] & 0xFF;
            int value = record[offset + 1] & 0xFF;
            if (type == 0xC0) {
                attr = FieldAttribute3270.decode(value);
            }
            offset += 2;
        }

        screen.addField(bufferAddress, attr);
        screen.setCharAt(bufferAddress, ' ');
        return offset;
    }

    private static int processModifyField(byte[] record, int pos, ScreenBuffer3270 screen, int bufferAddress) {
        if (pos + 1 >= record.length) return record.length;
        int pairCount = record[pos + 1] & 0xFF;
        int offset = pos + 2;

        Field3270 field = screen.getFieldStartingAt(bufferAddress);
        for (int i = 0; i < pairCount && offset + 1 < record.length; i++) {
            int type = record[offset] & 0xFF;
            int value = record[offset + 1] & 0xFF;
            if (type == 0xC0 && field != null) {
                FieldAttribute3270 newAttr = FieldAttribute3270.decode(value);
                screen.getRawAttributes()[bufferAddress] = newAttr;
            }
            offset += 2;
        }
        return offset;
    }

    /**
     * Decodes a 3270 buffer address from two bytes.
     * Supports 12-bit (standard), 14-bit, and 16-bit addressing.
     */
    public static int decodeBufferAddress(int byte1, int byte2, int screenSize) {
        if ((byte1 & 0xC0) == 0x00) {
            return ((byte1 & 0x3F) << 8) | byte2;
        }
        return ((byte1 & 0x3F) << 6) | (byte2 & 0x3F);
    }

    /**
     * Encodes a buffer address into 2 bytes (12-bit format).
     */
    public static byte[] encodeBufferAddress(int address) {
        byte[] result = new byte[2];
        result[0] = ADDR_TABLE[(address >> 6) & 0x3F];
        result[1] = ADDR_TABLE[address & 0x3F];
        return result;
    }

    private static final byte[] ADDR_TABLE = {
            0x40, (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5,
            (byte) 0xC6, (byte) 0xC7, (byte) 0xC8, (byte) 0xC9, 0x4A, 0x4B, 0x4C,
            0x4D, 0x4E, 0x4F, 0x50, (byte) 0xD1, (byte) 0xD2, (byte) 0xD3,
            (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7, (byte) 0xD8,
            (byte) 0xD9, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F, 0x60,
            (byte) 0x61, (byte) 0xE2, (byte) 0xE3, (byte) 0xE4, (byte) 0xE5,
            (byte) 0xE6, (byte) 0xE7, (byte) 0xE8, (byte) 0xE9, 0x6A, 0x6B,
            0x6C, 0x6D, 0x6E, 0x6F, (byte) 0xF0, (byte) 0xF1, (byte) 0xF2,
            (byte) 0xF3, (byte) 0xF4, (byte) 0xF5, (byte) 0xF6, (byte) 0xF7,
            (byte) 0xF8, (byte) 0xF9, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F
    };

    private static boolean isWritableCharacter(int b) {
        return b >= 0x40 && b <= 0xFE && !isOrder(b);
    }

    private static boolean isOrder(int b) {
        return b == ORDER_SBA || b == ORDER_SF || b == ORDER_SFE || b == ORDER_SA
                || b == ORDER_IC || b == ORDER_PT || b == ORDER_RA || b == ORDER_EUA
                || b == ORDER_MF;
    }

    private static boolean isGraphicEscape(int b) {
        return b == 0x08;
    }

    private static void processWsf(byte[] record, ScreenBuffer3270 screen, ProcessResult result) {
        int pos = 1;
        while (pos < record.length) {
            if (pos + 1 >= record.length) break;

            int sfLen = ((record[pos] & 0xFF) << 8) | (record[pos + 1] & 0xFF);
            if (sfLen == 0) sfLen = record.length - pos;

            if (pos + 2 < record.length) {
                int sfId = record[pos + 2] & 0xFF;
                if (sfId == SF_READ_PARTITION) {
                    processReadPartition(record, pos, sfLen, result);
                }
            }

            pos += sfLen;
        }
    }

    private static void processReadPartition(byte[] record, int offset, int length, ProcessResult result) {
        if (offset + 3 < record.length) {
            int partId = record[offset + 3] & 0xFF;
            if (partId == 0xFF && offset + 4 < record.length) {
                int queryType = record[offset + 4] & 0xFF;
                if (queryType == SF_QUERY_LIST || queryType == SF_QUERY_REPLY) {
                    LOG.debug("Query reply requested (type=0x{})", Integer.toHexString(queryType));
                }
                result.myKeyboardRestore = true;
            }
        }
    }

    /**
     * Builds a Read Modified response for the given AID and screen.
     *
     * @param aid    the AID byte that triggered the read
     * @param cursor the current cursor address
     * @param screen the screen buffer
     * @return the response bytes to send to the host
     */
    public static byte[] buildReadModifiedResponse(int aid, int cursor, ScreenBuffer3270 screen) {
        if (Aid3270.isShortRead(aid)) {
            byte[] cursorAddr = encodeBufferAddress(cursor);
            return new byte[]{(byte) aid, cursorAddr[0], cursorAddr[1]};
        }

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        baos.write(aid);
        byte[] cursorAddr = encodeBufferAddress(cursor);
        baos.write(cursorAddr[0]);
        baos.write(cursorAddr[1]);

        for (Field3270 field : screen.getAllModifiedFields()) {
            if (field.isProtected()) continue;

            byte[] sba = encodeBufferAddress(field.getFirstDataPosition());
            baos.write(ORDER_SBA);
            baos.write(sba[0]);
            baos.write(sba[1]);

            String text = field.getText();
            String trimmed = trimTrailingSpaces(text);
            byte[] ebcdic = EbcdicCodec.stringToEbcdic(trimmed);
            baos.write(ebcdic, 0, ebcdic.length);
        }

        return baos.toByteArray();
    }

    /**
     * Builds a Read Buffer response for the entire screen.
     */
    public static byte[] buildReadBufferResponse(int aid, int cursor, ScreenBuffer3270 screen) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        baos.write(aid);
        byte[] cursorAddr = encodeBufferAddress(cursor);
        baos.write(cursorAddr[0]);
        baos.write(cursorAddr[1]);

        for (int i = 0; i < screen.getSize(); i++) {
            FieldAttribute3270 attr = screen.getAttributeAt(i);
            if (attr != null) {
                baos.write(ORDER_SF);
                baos.write(encodeFieldAttribute(attr));
            } else {
                char ch = screen.getCharAt(i);
                int ebcdic = EbcdicCodec.unicodeToEbcdic(ch);
                baos.write(ebcdic);
            }
        }

        return baos.toByteArray();
    }

    private static int encodeFieldAttribute(FieldAttribute3270 attr) {
        int raw = attr.getRawByte();
        return ((raw & 0x30) << 2) | (raw & 0x0F);
    }

    private static String trimTrailingSpaces(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') end--;
        return s.substring(0, end);
    }
}
