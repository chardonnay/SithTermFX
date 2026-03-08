package com.sithtermfx.core.emulator.tn5250;

import com.sithtermfx.core.emulator.tn3270.EbcdicCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses IBM 5250 data stream records and applies them to a {@link ScreenBuffer5250}.
 * <p>
 * Each record begins with a 10-byte General Data Stream (GDS) header:
 * <pre>
 *   Bytes 0-1: Record length (big-endian, includes header)
 *   Byte  2:   Record type (always 0x12 for a 5250 data stream)
 *   Byte  3:   Reserved (flags)
 *   Byte  4:   Variable header / flow type
 *   Byte  5:   Reserved
 *   Byte  6:   Opcode (command code from {@link Command5250})
 *   Byte  7:   Reserved
 *   Bytes 8-9: Reserved / escape
 * </pre>
 * After the header, the record body contains orders and displayable data
 * encoded in EBCDIC.
 *
 * @author Daniel Mengel
 */
public final class DataStream5250 {

    private static final Logger logger = LoggerFactory.getLogger(DataStream5250.class);

    private static final int GDS_RECORD_TYPE = 0x12;
    private static final int MIN_HEADER_LENGTH = 10;

    private DataStream5250() {
    }

    /**
     * Processes a complete 5250 data stream record and applies it to the given screen.
     *
     * @param record the raw record bytes (without IAC EOR delimiters)
     * @param screen the screen buffer to update
     */
    public static void processRecord(byte[] record, ScreenBuffer5250 screen) {
        if (record == null || record.length < 2) {
            logger.warn("Record too short to parse");
            return;
        }

        int offset = 0;
        while (offset < record.length) {
            int remaining = record.length - offset;
            if (remaining < 2) {
                break;
            }

            int recordLen = ((record[offset] & 0xFF) << 8) | (record[offset + 1] & 0xFF);
            if (recordLen < 4 || recordLen > remaining) {
                logger.warn("Invalid GDS record length {} at offset {}", recordLen, offset);
                break;
            }

            int recordType = record[offset + 2] & 0xFF;
            if (recordType != GDS_RECORD_TYPE) {
                logger.warn("Unexpected record type 0x{} at offset {}", Integer.toHexString(recordType), offset);
                offset += recordLen;
                continue;
            }

            int opcode = 0;
            if (recordLen >= 7) {
                opcode = record[offset + 6] & 0xFF;
            }

            int dataOffset = Math.min(offset + MIN_HEADER_LENGTH, offset + recordLen);
            int dataLength = (offset + recordLen) - dataOffset;

            processCommand(opcode, record, dataOffset, dataLength, screen);
            offset += recordLen;
        }
    }

    private static void processCommand(int opcode, byte[] record, int dataOffset, int dataLength,
                                        ScreenBuffer5250 screen) {
        if (logger.isDebugEnabled()) {
            logger.debug("Processing 5250 command: {} (0x{}), data length: {}",
                    Command5250.nameOf(opcode), Integer.toHexString(opcode), dataLength);
        }

        switch (opcode) {
            case Command5250.WRITE_TO_DISPLAY:
                processWriteToDisplay(record, dataOffset, dataLength, screen);
                break;
            case Command5250.CLEAR_UNIT:
            case Command5250.CLEAR_UNIT_ALTERNATE:
                screen.clearFormatTable();
                break;
            case Command5250.CLEAR_FORMAT_TABLE:
                screen.clearFormatTable();
                break;
            case Command5250.WRITE_ERROR_CODE:
                processWriteErrorCode(record, dataOffset, dataLength, screen);
                break;
            case Command5250.WRITE_ERROR_CODE_TO_WINDOW:
                processWriteErrorCode(record, dataOffset, dataLength, screen);
                break;
            case Command5250.ROLL:
                processRoll(record, dataOffset, dataLength, screen);
                break;
            case Command5250.SAVE_SCREEN:
                logger.debug("Save Screen command (no-op in this implementation)");
                break;
            case Command5250.RESTORE_SCREEN:
                logger.debug("Restore Screen command (no-op in this implementation)");
                break;
            case Command5250.WRITE_STRUCTURED_FIELD:
                processWriteStructuredField(record, dataOffset, dataLength, screen);
                break;
            case Command5250.READ_IMMEDIATE:
            case Command5250.READ_SCREEN:
            case Command5250.READ_MODIFIED_FIELDS:
                logger.debug("Read command 0x{} acknowledged", Integer.toHexString(opcode));
                break;
            default:
                logger.warn("Unhandled 5250 command: 0x{}", Integer.toHexString(opcode));
                break;
        }
    }

    /**
     * Processes a Write to Display (WTD) command. The data body contains a control-byte pair
     * followed by interleaved orders and displayable EBCDIC data.
     */
    private static void processWriteToDisplay(byte[] data, int offset, int length,
                                               ScreenBuffer5250 screen) {
        if (length < 2) {
            return;
        }

        int cc1 = data[offset] & 0xFF;
        int cc2 = data[offset + 1] & 0xFF;

        if ((cc1 & 0x40) != 0) {
            screen.setKeyboardLocked(false);
        }
        if ((cc1 & 0x20) != 0) {
            screen.resetModifiedFlags();
        }

        int pos = offset + 2;
        int end = offset + length;

        while (pos < end) {
            int b = data[pos] & 0xFF;

            switch (b) {
                case Command5250.ORDER_SBA: {
                    if (pos + 2 >= end) {
                        pos = end;
                        break;
                    }
                    int row = data[pos + 1] & 0xFF;
                    int col = data[pos + 2] & 0xFF;
                    screen.setCursorPosition(row, col);
                    pos += 3;
                    break;
                }

                case Command5250.ORDER_IC: {
                    if (pos + 2 >= end) {
                        pos = end;
                        break;
                    }
                    int icRow = data[pos + 1] & 0xFF;
                    int icCol = data[pos + 2] & 0xFF;
                    screen.setCursorPosition(icRow, icCol);
                    pos += 3;
                    break;
                }

                case Command5250.ORDER_MC: {
                    if (pos + 2 >= end) {
                        pos = end;
                        break;
                    }
                    int mcRow = data[pos + 1] & 0xFF;
                    int mcCol = data[pos + 2] & 0xFF;
                    screen.setCursorPosition(mcRow, mcCol);
                    pos += 3;
                    break;
                }

                case Command5250.ORDER_SF: {
                    pos = processStartField(data, pos, end, screen);
                    break;
                }

                case Command5250.ORDER_SOH: {
                    pos = processStartOfHeader(data, pos, end, screen);
                    break;
                }

                case Command5250.ORDER_RA: {
                    pos = processRepeatToAddress(data, pos, end, screen);
                    break;
                }

                case Command5250.ORDER_WEA: {
                    if (pos + 3 >= end) {
                        pos = end;
                        break;
                    }
                    pos += 4;
                    break;
                }

                case Command5250.ORDER_EA: {
                    if (pos + 3 >= end) {
                        pos = end;
                        break;
                    }
                    pos += 4;
                    break;
                }

                case Command5250.ORDER_TD: {
                    pos = processTransparentData(data, pos, end, screen);
                    break;
                }

                default: {
                    char unicode = EbcdicCodec.ebcdicToUnicode(b);
                    int row = screen.getCursorRow();
                    int col = screen.getCursorCol();
                    screen.setCharAt(row, col, unicode);
                    advanceCursor(screen);
                    pos++;
                    break;
                }
            }
        }
    }

    /**
     * Processes the Start of Field (SF) order, which defines a new input or output field.
     */
    private static int processStartField(byte[] data, int pos, int end, ScreenBuffer5250 screen) {
        pos++;
        if (pos >= end) {
            return end;
        }

        int ffwByte1 = data[pos] & 0xFF;
        int ffwByte2 = 0;
        if (pos + 1 < end) {
            ffwByte2 = data[pos + 1] & 0xFF;
        }
        int ffw = (ffwByte1 << 8) | ffwByte2;
        pos += 2;

        int fcw = 0;
        boolean hasFcw = pos < end && (data[pos] & 0x80) != 0;
        if (hasFcw) {
            int fcwByte1 = data[pos] & 0xFF;
            int fcwByte2 = 0;
            if (pos + 1 < end) {
                fcwByte2 = data[pos + 1] & 0xFF;
            }
            fcw = (fcwByte1 << 8) | fcwByte2;
            pos += 2;
        }

        if (pos + 1 >= end) {
            return end;
        }
        int fieldLen = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;

        int fieldRow = screen.getCursorRow();
        int fieldCol = screen.getCursorCol();

        Field5250 field = new Field5250(fieldRow, fieldCol, fieldLen, ffw, fcw);

        int charsToRead = Math.min(fieldLen, end - pos);
        for (int i = 0; i < charsToRead; i++) {
            char c = EbcdicCodec.ebcdicToUnicode(data[pos + i] & 0xFF);
            field.setCharAt(i, c);
            int r = screen.getCursorRow();
            int col = screen.getCursorCol();
            screen.setCharAt(r, col, c);
            advanceCursor(screen);
        }
        pos += charsToRead;

        screen.addField(field);
        return pos;
    }

    /**
     * Processes the Start of Header (SOH) order, which carries display control information.
     */
    private static int processStartOfHeader(byte[] data, int pos, int end, ScreenBuffer5250 screen) {
        pos++;
        if (pos >= end) {
            return end;
        }

        int headerLen = data[pos] & 0xFF;
        pos++;

        if (headerLen < 1) {
            return pos;
        }

        int headerEnd = Math.min(pos + headerLen - 1, end);

        if (pos < headerEnd) {
            int flags = data[pos] & 0xFF;
            screen.setInputInhibited((flags & 0x20) != 0);
        }

        if (pos + 2 < headerEnd) {
            int errorRow = data[pos + 2] & 0xFF;
            if (errorRow > 0 && errorRow <= screen.getRows()) {
                screen.setErrorLine(errorRow);
            }
        }

        return headerEnd;
    }

    /**
     * Processes the Repeat to Address (RA) order, which fills from the current position
     * to a target address with a given character.
     */
    private static int processRepeatToAddress(byte[] data, int pos, int end, ScreenBuffer5250 screen) {
        pos++;
        if (pos + 2 >= end) {
            return end;
        }

        int targetRow = data[pos] & 0xFF;
        int targetCol = data[pos + 1] & 0xFF;
        int fillByte = data[pos + 2] & 0xFF;
        pos += 3;

        char fillChar = EbcdicCodec.ebcdicToUnicode(fillByte);

        int targetLinear = (targetRow - 1) * screen.getCols() + (targetCol - 1);
        int curLinear = (screen.getCursorRow() - 1) * screen.getCols() + (screen.getCursorCol() - 1);

        int maxCells = screen.getRows() * screen.getCols();
        int count = 0;

        while (curLinear != targetLinear && count < maxCells) {
            int r = (curLinear / screen.getCols()) + 1;
            int c = (curLinear % screen.getCols()) + 1;
            screen.setCharAt(r, c, fillChar);
            curLinear++;
            if (curLinear >= maxCells) {
                curLinear = 0;
            }
            count++;
        }

        screen.setCursorPosition(targetRow, targetCol);
        return pos;
    }

    /**
     * Processes Transparent Data (TD) — raw data following a length byte.
     */
    private static int processTransparentData(byte[] data, int pos, int end, ScreenBuffer5250 screen) {
        pos++;
        if (pos >= end) {
            return end;
        }
        int tdLen = ((data[pos] & 0xFF) << 8);
        if (pos + 1 < end) {
            tdLen |= (data[pos + 1] & 0xFF);
        }
        pos += 2;

        int charsToWrite = Math.min(tdLen, end - pos);
        for (int i = 0; i < charsToWrite; i++) {
            int row = screen.getCursorRow();
            int col = screen.getCursorCol();
            screen.setCharAt(row, col, (char) (data[pos + i] & 0xFF));
            advanceCursor(screen);
        }
        pos += charsToWrite;
        return pos;
    }

    /**
     * Processes a Write Error Code command. The error code bytes are written
     * to the screen's error line.
     */
    private static void processWriteErrorCode(byte[] data, int offset, int length,
                                               ScreenBuffer5250 screen) {
        int errorLine = screen.getErrorLine();
        int col = 1;
        for (int i = offset; i < offset + length && col <= screen.getCols(); i++) {
            char c = EbcdicCodec.ebcdicToUnicode(data[i] & 0xFF);
            screen.setCharAt(errorLine, col, c);
            col++;
        }
        screen.setKeyboardLocked(true);
    }

    /**
     * Processes a Roll command, scrolling the display up or down.
     */
    private static void processRoll(byte[] data, int offset, int length, ScreenBuffer5250 screen) {
        if (length < 2) {
            return;
        }
        int flags = data[offset] & 0xFF;
        int lines = data[offset + 1] & 0xFF;

        boolean rollUp = (flags & 0x01) != 0;
        if (rollUp) {
            screen.scrollUp(lines);
        } else {
            screen.scrollDown(lines);
        }
    }

    /**
     * Processes a Write Structured Field (WSF) command.
     */
    private static void processWriteStructuredField(byte[] data, int offset, int length,
                                                     ScreenBuffer5250 screen) {
        int pos = offset;
        int end = offset + length;

        while (pos + 3 < end) {
            int sfLen = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            int sfClass = data[pos + 2] & 0xFF;
            int sfType = data[pos + 3] & 0xFF;

            if (sfLen < 4 || sfLen > (end - pos)) {
                break;
            }

            if (sfClass == 0xD9 && sfType == 0x70) {
                processQuery5250(screen);
            } else {
                logger.debug("Unhandled structured field: class=0x{}, type=0x{}, len={}",
                        Integer.toHexString(sfClass), Integer.toHexString(sfType), sfLen);
            }

            pos += sfLen;
        }
    }

    private static void processQuery5250(ScreenBuffer5250 screen) {
        logger.debug("5250 Query received — response should be built by emulator");
    }

    private static void advanceCursor(ScreenBuffer5250 screen) {
        int col = screen.getCursorCol() + 1;
        int row = screen.getCursorRow();
        if (col > screen.getCols()) {
            col = 1;
            row++;
            if (row > screen.getRows()) {
                row = 1;
            }
        }
        screen.setCursorPosition(row, col);
    }

    /**
     * Builds a read-response record containing modified field data.
     *
     * @param screen the screen to read from
     * @param aid    the AID key code that triggered the read
     * @param cursorRow current cursor row (1-based)
     * @param cursorCol current cursor column (1-based)
     * @return the response record bytes
     */
    public static byte[] buildReadResponse(ScreenBuffer5250 screen, int aid, int cursorRow, int cursorCol) {
        java.util.List<Field5250> modified = screen.getModifiedFields();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(256);

        int rowAddr = cursorRow;
        int colAddr = cursorCol;
        bos.write(rowAddr & 0xFF);
        bos.write(colAddr & 0xFF);
        bos.write(aid & 0xFF);

        for (Field5250 field : modified) {
            bos.write(Command5250.ORDER_SBA);
            bos.write(field.getRow() & 0xFF);
            bos.write(field.getCol() & 0xFF);

            byte[] fieldData = EbcdicCodec.stringToEbcdic(field.getText());
            bos.write(fieldData, 0, fieldData.length);
        }

        byte[] payload = bos.toByteArray();

        int totalLen = payload.length + 5;
        byte[] record = new byte[totalLen];
        record[0] = (byte) ((totalLen >> 8) & 0xFF);
        record[1] = (byte) (totalLen & 0xFF);
        record[2] = (byte) GDS_RECORD_TYPE;
        record[3] = 0x00;
        record[4] = 0x00;
        System.arraycopy(payload, 0, record, 5, payload.length);

        return record;
    }

    /**
     * Builds the 5250 query reply structured field.
     *
     * @param rows screen rows
     * @param cols screen columns
     * @return the query response bytes
     */
    public static byte[] buildQueryReply(int rows, int cols) {
        byte[] reply = new byte[]{
                0x00, 0x00,
                (byte) 0x88,
                0x00, 0x00,

                (byte) 0xD9, 0x70,
                (byte) 0x06,
                0x00, 0x01,
                0x01, 0x01,
                0x00, 0x00,
                (byte) rows, (byte) cols,
                0x01,
                0x01,

                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x00, 0x00,

                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00
        };

        int totalLen = reply.length;
        reply[0] = (byte) ((totalLen >> 8) & 0xFF);
        reply[1] = (byte) (totalLen & 0xFF);

        return reply;
    }
}
