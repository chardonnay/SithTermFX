package com.sithtermfx.core.clipboard;

import com.sithtermfx.core.ArrayTerminalDataStream;
import com.sithtermfx.core.TerminalOutputStream;
import com.sithtermfx.core.emulator.Emulator;
import com.sithtermfx.core.emulator.SithEmulator;
import com.sithtermfx.core.model.SithTerminal;
import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.BackBufferDisplay;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OSC 5522 end to end: escape codes through {@link SithEmulator} into a {@link SithTerminal} with a handler whose
 * answers the test completes.
 */
public class ClipboardProtocolTest {

    private static final String ESC = "\u001B";

    private static final String ST = ESC + "\\";

    private TerminalTextBuffer myBuffer;

    private SithTerminal myTerminal;

    private final StringBuilder myResponses = new StringBuilder();

    private final FakeHandler myHandler = new FakeHandler();

    @BeforeEach
    public void setUp() {
        StyleState styleState = new StyleState();
        myBuffer = new TerminalTextBuffer(20, 5, styleState);
        myTerminal = new SithTerminal(new BackBufferDisplay(myBuffer), myBuffer, styleState);
        myTerminal.setTerminalOutput(new TerminalOutputStream() {
            @Override
            public void sendBytes(byte @NotNull [] response, boolean userInput) {
                myResponses.append(new String(response, StandardCharsets.UTF_8));
            }

            @Override
            public void sendString(@NotNull String string, boolean userInput) {
                myResponses.append(string);
            }
        });
        myTerminal.setClipboardHandler(myHandler);
    }

    private static final class FakeHandler implements TerminalClipboardHandler {

        final List<ClipboardReadRequest> reads = new ArrayList<>();

        final List<CompletableFuture<Map<String, byte[]>>> readResults = new ArrayList<>();

        final List<ClipboardWriteRequest> writes = new ArrayList<>();

        CompletionStage<Void> writeResult = CompletableFuture.completedFuture(null);

        CompletionStage<List<String>> mimeTypes = CompletableFuture.completedFuture(List.of("text/plain", "image/png"));

        @Override
        public @NotNull CompletionStage<List<String>> getMimeTypes(@NotNull ClipboardLocation location) {
            return mimeTypes;
        }

        @Override
        public @NotNull CompletionStage<Map<String, byte[]>> read(@NotNull ClipboardReadRequest request) {
            reads.add(request);
            CompletableFuture<Map<String, byte[]>> result = new CompletableFuture<>();
            readResults.add(result);
            return result;
        }

        @Override
        public @NotNull CompletionStage<Void> write(@NotNull ClipboardWriteRequest request) {
            writes.add(request);
            return writeResult;
        }
    }

    private void process(@NotNull String data) throws IOException {
        Emulator emulator = new SithEmulator(new ArrayTerminalDataStream(data.toCharArray()), myTerminal);
        while (emulator.hasNext()) {
            emulator.next();
        }
    }

    private @NotNull List<String> takePackets() {
        List<String> packets = new ArrayList<>();
        for (String packet : myResponses.toString().split(ESC)) {
            // the ESC of each response and the backslash after it, of the ST of an OSC
            if (!packet.isEmpty() && !packet.equals("\\")) {
                packets.add(packet);
            }
        }
        myResponses.setLength(0);
        return packets;
    }

    private static @NotNull String osc(@NotNull String metadata, @NotNull String payload) {
        return ESC + "]5522;" + metadata + (payload.isEmpty() ? "" : ";" + payload) + ST;
    }

    private static @NotNull String b64(@NotNull String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static @NotNull String text(byte @NotNull [] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    public void withoutAHandlerCommandsAreIgnoredAndTheModeIsReportedUnsupported() throws IOException {
        myTerminal.setClipboardHandler(null);

        process(osc("type=read", b64(".")) + ESC + "[?5522$p");

        assertEquals(List.of("[?5522;0$y"), takePackets());
        assertEquals("", myBuffer.getLine(0).getText());
    }

    @Test
    public void decrqmReportsThePasteEventMode() throws IOException {
        process(ESC + "[?5522$p" + ESC + "[?5522h" + ESC + "[?5522$p" + ESC + "[?5522l" + ESC + "[?5522$p");

        assertEquals(List.of("[?5522;2$y", "[?5522;1$y", "[?5522;2$y"), takePackets());
        assertEquals("", myBuffer.getLine(0).getText());
    }

    @Test
    public void anotherDollarSequenceIsIgnoredNotRunWithoutItsDollar() throws IOException {
        // CSI 2;3 $ r is DECCARA; run without its $ it would be DECSTBM and move the cursor home
        process("ab" + ESC + "[2;3$r");

        assertEquals("ab", myBuffer.getLine(0).getText());
        assertEquals(3, myTerminal.getCursorX());
    }

    @Test
    public void aWriteCollectsChunksAndAliasesAndIsAnsweredWithTheId() throws IOException {
        String data = b64("hello clipboard");
        process(osc("type=write:id=a b!c", "")
                + osc("type=wdata:mime=" + b64("text/plain"), data.substring(0, 6))
                + osc("type=walias:mime=" + b64("text/plain"), b64("UTF8_STRING text/x-plain"))
                + osc("type=wdata:mime=" + b64("text/plain"), data.substring(6))
                + osc("type=wdata:mime=" + b64("text/html"), b64("<b>hi</b>"))
                + osc("type=wdata", ""));

        assertEquals(List.of("]5522;type=write:status=DONE:id=abc"), takePackets());
        ClipboardWriteRequest write = myHandler.writes.get(0);
        assertEquals(List.of("text/plain", "text/html"), new ArrayList<>(write.getData().keySet()));
        assertEquals("hello clipboard", text(write.getData().get("text/plain")));
        assertEquals("<b>hi</b>", text(write.getData().get("text/html")));
        // UTF8_STRING, an X11 target, is no MIME type and is skipped
        assertEquals(Map.of("text/plain", List.of("text/x-plain")), write.getAliases());
    }

    @Test
    public void aliasesAndCredentialsReachTheHandler() throws IOException {
        process(osc("type=write:loc=primary:name=" + b64("nvim") + ":pw=" + b64("secret"), "")
                + osc("type=walias:mime=" + b64("text/plain"), b64("text/x-plain"))
                + osc("type=wdata:mime=" + b64("text/plain"), b64("x"))
                + osc("type=wdata", ""));

        ClipboardWriteRequest write = myHandler.writes.get(0);
        assertEquals(ClipboardLocation.PRIMARY, write.getLocation());
        assertEquals(Map.of("text/plain", List.of("text/x-plain")), write.getAliases());
        assertEquals("nvim", write.getName());
        assertEquals("secret", write.getPassword());
    }

    @Test
    public void aPasswordWithoutANameCountsAsNone() throws IOException {
        process(osc("type=write:pw=" + b64("secret"), "") + osc("type=wdata", ""));

        assertNull(myHandler.writes.get(0).getPassword());
    }

    @Test
    public void anInvalidWriteFailsOnceAndIgnoresTheRestUntilTheNextWrite() throws IOException {
        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("text/plain"), "!!!!")
                + osc("type=wdata:mime=" + b64("text/plain"), b64("x")) + osc("type=wdata", ""));

        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());
        assertTrue(myHandler.writes.isEmpty());

        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("text/plain"), b64("ok")) + osc("type=wdata", ""));

        assertEquals(List.of("]5522;type=write:status=DONE"), takePackets());
        assertEquals("ok", text(myHandler.writes.get(0).getData().get("text/plain")));
    }

    @Test
    public void badBase64IsRejectedNotRepaired() throws IOException {
        // data after padding
        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("text/plain"), "QQ==")
                + osc("type=wdata:mime=" + b64("text/plain"), "QUJD") + osc("type=wdata", ""));
        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());

        // the payloads do not add up to padded base64
        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("text/plain"), "QUJDR") + osc("type=wdata", ""));
        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());

        // a line break inside
        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("text/plain"), "QUJD\nQUJD") + osc("type=wdata", ""));
        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());

        // data without a MIME type, and a MIME type that is none
        process(osc("type=write", "") + osc("type=wdata", "QUJD"));
        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());
        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("plain"), "QUJD"));
        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());

        assertTrue(myHandler.writes.isEmpty());
    }

    @Test
    public void tooManyMimeTypesAreAnError() throws IOException {
        StringBuilder data = new StringBuilder(osc("type=write", ""));
        for (int i = 0; i <= ClipboardProtocol.MAX_MIME_TYPES; i++) {
            data.append(osc("type=wdata:mime=" + b64("application/x-" + i), b64("x")));
        }
        process(data + osc("type=wdata", ""));

        assertEquals(List.of("]5522;type=write:status=EINVAL"), takePackets());
        assertTrue(myHandler.writes.isEmpty());
    }

    @Test
    public void theHandlersRefusalIsSent() throws IOException {
        myHandler.writeResult = CompletableFuture.failedFuture(new ClipboardException(ClipboardError.EPERM));

        process(osc("type=write", "") + osc("type=wdata", ""));

        assertEquals(List.of("]5522;type=write:status=EPERM"), takePackets());
    }

    @Test
    public void aReadIsAnsweredInChunksOfTheTypesTheClipboardHas() throws IOException {
        process(osc("type=read:id=r1:name=" + b64("app") + ":pw=" + b64("pw1"), b64("text/plain image/png")));

        ClipboardReadRequest request = myHandler.reads.get(0);
        assertEquals(List.of("text/plain", "image/png"), request.getMimeTypes());
        assertEquals("app", request.getName());
        assertEquals("pw1", request.getPassword());
        assertFalse(request.isGrantedByPaste());
        assertTrue(takePackets().isEmpty());

        byte[] text = new byte[10_000];
        Arrays.fill(text, (byte) 'x');
        Map<String, byte[]> data = new LinkedHashMap<>();
        data.put("text/plain", text);
        myHandler.readResults.get(0).complete(data);

        List<String> packets = takePackets();
        String dataPrefix = "]5522;type=read:status=DATA:id=r1:mime=" + b64("text/plain") + ";";
        assertEquals("]5522;type=read:status=OK:id=r1", packets.get(0));
        assertEquals(5, packets.size());
        StringBuilder received = new StringBuilder();
        for (String packet : packets.subList(1, 4)) {
            assertTrue(packet.startsWith(dataPrefix), packet);
            received.append(text(Base64.getDecoder().decode(packet.substring(dataPrefix.length()))));
        }
        assertEquals("x".repeat(10_000), received.toString());
        assertEquals("]5522;type=read:status=DONE:id=r1", packets.get(4));
    }

    @Test
    public void aSecondReadWhileOneIsInProgressIsBusy() throws IOException {
        process(osc("type=read", b64("text/plain")) + osc("type=read:id=2", b64("text/plain")));

        assertEquals(List.of("]5522;type=read:status=EBUSY:id=2"), takePackets());

        myHandler.readResults.get(0).complete(Map.of());
        takePackets();
        process(osc("type=read", b64("text/plain")));

        assertEquals(2, myHandler.reads.size());
    }

    @Test
    public void aDeniedReadIsAnsweredWithItsError() throws IOException {
        process(osc("type=read:loc=primary", b64("text/plain")));
        assertEquals(ClipboardLocation.PRIMARY, myHandler.reads.get(0).getLocation());

        myHandler.readResults.get(0).completeExceptionally(new ClipboardException(ClipboardError.EPERM));

        assertEquals(List.of("]5522;type=read:status=EPERM"), takePackets());
    }

    @Test
    public void listingTheMimeTypesNeedsNoReadAndMayNameThePrimarySelection() throws IOException {
        process(osc("type=read:loc=primary", b64(".")));

        assertEquals(List.of("]5522;type=read:status=OK:loc=primary",
                "]5522;type=read:status=DATA:mime=" + b64(".") + ";" + b64("text/plain image/png\n"),
                "]5522;type=read:status=DONE"), takePackets());
        assertTrue(myHandler.reads.isEmpty());
    }

    @Test
    public void anInvalidReadIsIgnored() throws IOException {
        process(osc("type=read", "!!!!") + osc("type=read", b64("not a mime type")) + osc("type=read", ""));

        assertTrue(takePackets().isEmpty());
        assertTrue(myHandler.reads.isEmpty());
    }

    @Test
    public void aPasteEventGrantsOneReadWithItsPassword() throws IOException {
        assertFalse(myTerminal.getClipboardProtocol().sendPasteEvent());

        process(ESC + "[?5522h");
        assertTrue(myTerminal.getClipboardProtocol().sendPasteEvent());

        List<String> packets = takePackets();
        assertEquals(3, packets.size());
        String ok = packets.get(0);
        assertTrue(ok.startsWith("]5522;type=read:status=OK:pw="), ok);
        assertTrue(ok.endsWith(":name=" + b64("Paste event")), ok);
        String password = text(Base64.getDecoder().decode(ok.substring(ok.indexOf(":pw=") + 4, ok.indexOf(":name="))));
        assertTrue(packets.get(1).contains(":mime=" + b64(".")), packets.get(1));

        process(osc("type=read:pw=" + b64(password), b64("text/plain")));
        assertTrue(myHandler.reads.get(0).isGrantedByPaste());
        myHandler.readResults.get(0).complete(Map.of());

        process(osc("type=read:pw=" + b64(password), b64("text/plain")));
        assertFalse(myHandler.reads.get(1).isGrantedByPaste());
    }

    @Test
    public void aFullResetTurnsPasteEventsOff() throws IOException {
        process(ESC + "[?5522h" + ESC + "c" + ESC + "[?5522$p");

        assertEquals(List.of("[?5522;2$y"), takePackets());
    }

    @Test
    public void anIdIsEchoedWithoutTheCharsTheProtocolForbids() {
        assertEquals("ab-_+.9", ClipboardProtocol.sanitizeId("a\u001Bb-_+.9;:"));
        assertNull(ClipboardProtocol.sanitizeId(null));
        assertEquals(256, ClipboardProtocol.sanitizeId("x".repeat(1000)).length());
    }

    @Test
    public void writtenBytesArriveUnchanged() throws IOException {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF};

        process(osc("type=write", "") + osc("type=wdata:mime=" + b64("image/png"), Base64.getEncoder().encodeToString(png))
                + osc("type=wdata", ""));

        assertArrayEquals(png, myHandler.writes.get(0).getData().get("image/png"));
    }
}
