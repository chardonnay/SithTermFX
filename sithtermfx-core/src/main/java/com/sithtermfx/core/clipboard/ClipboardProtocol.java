package com.sithtermfx.core.clipboard;

import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * <a href="https://sw.kovidgoyal.net/kitty/clipboard/">OSC 5522</a>, kitty's clipboard protocol: programs read and
 * write the clipboard in any MIME type, the terminal asks the user before a read, and with private mode 5522 a
 * paste reaches the program as a list of MIME types it may then read without a prompt.
 * <p>
 * This class speaks the protocol: it parses {@code OSC 5522 ; metadata ; payload ST}, collects the data of a write,
 * enforces the limits and sends the responses. A {@link TerminalClipboardHandler} decides what is allowed and does
 * the clipboard access; without one every command is ignored and {@link #isSupported()} is false.
 * <p>
 * Commands are handled on the emulator thread; {@link #sendPasteEvent()} is called on the UI thread, and the
 * handler completes its stages on any thread.
 */
public final class ClipboardProtocol {

    /**
     * The most bytes one write may carry, all MIME types together: the minimum the protocol asks terminals to accept.
     */
    public static final int MAX_WRITE_BYTES = 64 * 1024 * 1024;

    /**
     * The most MIME types one request may name or one write may carry, aliases included.
     */
    public static final int MAX_MIME_TYPES = 32;

    /** Read data is sent in chunks of this many bytes before encoding. */
    private static final int CHUNK_SIZE = 4096;

    /** The MIME type of the list of the MIME types on the clipboard. */
    private static final String TARGETS = ".";

    private static final String PASTE_EVENT_NAME = "Paste event";

    /** How many one-time passwords of paste events a program may leave unused. */
    private static final int MAX_PASTE_PASSWORDS = 16;

    private static final int MAX_ID_LENGTH = 256;

    /** A MIME type: a type and a subtype of the chars RFC 6838 allows, optionally with parameters. */
    private static final Pattern MIME_TYPE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]{0,126}/[A-Za-z0-9!#$&^_.+-]{1,127}(;[ -~]{0,255})?");

    private static final Pattern ID_CHARS_TO_STRIP = Pattern.compile("[^a-zA-Z0-9\\-_+.]");

    private final Consumer<String> mySender;

    private volatile @Nullable TerminalClipboardHandler myHandler;

    private volatile boolean myPasteEvents;

    /** One read at a time; another is answered with EBUSY. */
    private final AtomicBoolean myReading = new AtomicBoolean();

    /** The unused one-time passwords of paste events, oldest first. */
    private final Set<String> myPastePasswords = new LinkedHashSet<>();

    /** The write whose data is being sent; emulator thread only. */
    private @Nullable Write myWrite;

    /**
     * @param sender sends a response to the program
     */
    public ClipboardProtocol(@NotNull Consumer<String> sender) {
        mySender = sender;
    }

    /**
     * @param handler gives the programs clipboard access, or null to turn the protocol off
     */
    public void setHandler(@Nullable TerminalClipboardHandler handler) {
        myHandler = handler;
    }

    public @Nullable TerminalClipboardHandler getHandler() {
        return myHandler;
    }

    /**
     * @return whether the protocol is on: a handler is set
     */
    public boolean isSupported() {
        return myHandler != null;
    }

    /**
     * Sets private mode 5522: pastes go to the program as paste events.
     */
    public void setPasteEventsEnabled(boolean enabled) {
        myPasteEvents = enabled;
    }

    public boolean isPasteEventsEnabled() {
        return myPasteEvents;
    }

    /**
     * Forgets the mode, the write in progress and the passwords of paste events, for a terminal reset.
     */
    public void reset() {
        myPasteEvents = false;
        myWrite = null;
        synchronized (myPastePasswords) {
            myPastePasswords.clear();
        }
    }

    /**
     * Handles one command.
     *
     * @param command the body of the OSC after {@code 5522;}: the metadata and, after a {@code ;}, the payload
     */
    public void handle(@NotNull String command) {
        TerminalClipboardHandler handler = myHandler;
        if (handler == null) {
            return;
        }
        int semicolon = command.indexOf(';');
        Map<String, String> keys = parseMetadata(semicolon >= 0 ? command.substring(0, semicolon) : command);
        String payload = semicolon >= 0 ? command.substring(semicolon + 1) : "";
        String type = keys.get("type");
        if (type == null) {
            return;
        }
        switch (type) {
            case "read":
                read(handler, keys, payload);
                break;
            case "write":
                myWrite = new Write(location(keys), credentials(keys), sanitizeId(keys.get("id")));
                break;
            case "wdata":
                writeData(handler, keys, payload);
                break;
            case "walias":
                writeAlias(keys, payload);
                break;
            default:
                break;
        }
    }

    /**
     * Sends a paste event instead of pasting, when the program turned them on: the list of the MIME types on the
     * clipboard with a one-time password, with which the program reads what it wants without a prompt.
     *
     * @return whether a paste event was sent; when not, paste as usual
     */
    public boolean sendPasteEvent() {
        TerminalClipboardHandler handler = myHandler;
        if (handler == null || !myPasteEvents || !myReading.compareAndSet(false, true)) {
            return false;
        }
        String password = UUID.randomUUID().toString();
        synchronized (myPastePasswords) {
            if (myPastePasswords.size() >= MAX_PASTE_PASSWORDS) {
                myPastePasswords.remove(myPastePasswords.iterator().next());
            }
            myPastePasswords.add(password);
        }
        listMimeTypes(handler, ClipboardLocation.CLIPBOARD, null, password);
        return true;
    }

    private void read(@NotNull TerminalClipboardHandler handler, @NotNull Map<String, String> keys,
                      @NotNull String payload) {
        // A read that is not valid is ignored: reads have no error status for that.
        byte[] decoded = decodeBase64(payload);
        String list = decoded != null ? decodeUtf8(decoded) : null;
        if (list == null) {
            return;
        }
        List<String> mimeTypes = new ArrayList<>();
        for (String mimeType : list.trim().split("\\s+")) {
            if (!mimeType.isEmpty() && !mimeTypes.contains(mimeType)) {
                mimeTypes.add(mimeType);
            }
        }
        if (mimeTypes.isEmpty() || mimeTypes.size() > MAX_MIME_TYPES) {
            return;
        }
        boolean listing = mimeTypes.equals(List.of(TARGETS));
        if (!listing && !mimeTypes.stream().allMatch(mimeType -> MIME_TYPE.matcher(mimeType).matches())) {
            return;
        }
        ClipboardLocation location = location(keys);
        String id = sanitizeId(keys.get("id"));
        if (!myReading.compareAndSet(false, true)) {
            send(packet("read", ClipboardError.EBUSY.name(), null, id, null, null, null));
            return;
        }
        if (listing) {
            listMimeTypes(handler, location, id, null);
            return;
        }
        Credentials credentials = credentials(keys);
        String password = decodeText(keys.get("pw"));
        boolean grantedByPaste;
        synchronized (myPastePasswords) {
            grantedByPaste = password != null && myPastePasswords.remove(password);
        }
        ClipboardReadRequest request = new ClipboardReadRequest(location, mimeTypes, credentials.name,
                credentials.password, grantedByPaste);
        stage(() -> handler.read(request)).whenComplete((data, failure) -> {
            try {
                if (failure != null) {
                    send(packet("read", errorOf(failure).name(), null, id, null, null, null));
                    return;
                }
                send(packet("read", "OK", location, id, null, null, null));
                for (String mimeType : mimeTypes) {
                    byte[] bytes = data != null ? data.get(mimeType) : null;
                    if (bytes != null) {
                        sendData(mimeType, bytes, id, null);
                    }
                }
                send(packet("read", "DONE", null, id, null, null, null));
            } finally {
                myReading.set(false);
            }
        });
    }

    private void listMimeTypes(@NotNull TerminalClipboardHandler handler, @NotNull ClipboardLocation location,
                               @Nullable String id, @Nullable String password) {
        stage(() -> handler.getMimeTypes(location)).whenComplete((mimeTypes, failure) -> {
            try {
                if (failure != null) {
                    send(packet("read", errorOf(failure).name(), null, id, null, password, null));
                    return;
                }
                send(packet("read", "OK", location, id, null, password,
                        null, password != null ? PASTE_EVENT_NAME : null));
                String list = mimeTypes == null || mimeTypes.isEmpty() ? "" : String.join(" ", mimeTypes) + "\n";
                sendData(TARGETS, list.getBytes(StandardCharsets.UTF_8), id, password);
                send(packet("read", "DONE", null, id, null, password, null));
            } finally {
                myReading.set(false);
            }
        });
    }

    private void sendData(@NotNull String mimeType, byte @NotNull [] bytes, @Nullable String id,
                          @Nullable String password) {
        for (int offset = 0; offset < bytes.length; offset += CHUNK_SIZE) {
            byte[] chunk = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK_SIZE));
            send(packet("read", "DATA", null, id, mimeType, password, chunk));
        }
    }

    private void writeData(@NotNull TerminalClipboardHandler handler, @NotNull Map<String, String> keys,
                           @NotNull String payload) {
        Write write = myWrite;
        if (write == null || write.failed) {
            return;
        }
        if (!keys.containsKey("mime")) {
            if (!payload.isEmpty()) {
                fail(write, ClipboardError.EINVAL);
            } else {
                finishWrite(handler, write);
            }
            return;
        }
        String mimeType = decodeText(keys.get("mime"));
        if (mimeType == null || !MIME_TYPE.matcher(mimeType).matches()) {
            fail(write, ClipboardError.EINVAL);
            return;
        }
        MimeData data = write.data.get(mimeType);
        if (data == null) {
            if (write.mimeTypeCount() >= MAX_MIME_TYPES) {
                fail(write, ClipboardError.EINVAL);
                return;
            }
            data = new MimeData();
            write.data.put(mimeType, data);
        }
        // Only all the payloads of a type together must be padded: decode whole groups of four and keep the rest.
        String chars = data.base64Rest + payload;
        int whole = chars.length() - chars.length() % 4;
        data.base64Rest = chars.substring(whole);
        if (whole == 0) {
            return;
        }
        if (data.ended) {
            // padding came before more data
            fail(write, ClipboardError.EINVAL);
            return;
        }
        String group = chars.substring(0, whole);
        byte[] bytes = decodeBase64(group);
        if (bytes == null) {
            fail(write, ClipboardError.EINVAL);
            return;
        }
        data.ended = group.endsWith("=");
        if (write.size + bytes.length > MAX_WRITE_BYTES) {
            fail(write, ClipboardError.EFBIG);
            return;
        }
        data.bytes.writeBytes(bytes);
        write.size += bytes.length;
    }

    private void writeAlias(@NotNull Map<String, String> keys, @NotNull String payload) {
        Write write = myWrite;
        if (write == null || write.failed) {
            return;
        }
        String target = decodeText(keys.get("mime"));
        byte[] decoded = decodeBase64(payload);
        String list = decoded != null ? decodeUtf8(decoded) : null;
        if (target == null || !MIME_TYPE.matcher(target).matches() || list == null) {
            fail(write, ClipboardError.EINVAL);
            return;
        }
        List<String> aliases = write.aliases.computeIfAbsent(target, key -> new ArrayList<>());
        for (String alias : list.trim().split("\\s+")) {
            // an X11 target such as UTF8_STRING is no MIME type to offer here: skipped, not an error
            if (alias.isEmpty() || aliases.contains(alias) || !MIME_TYPE.matcher(alias).matches()) {
                continue;
            }
            if (write.mimeTypeCount() >= MAX_MIME_TYPES) {
                fail(write, ClipboardError.EINVAL);
                return;
            }
            aliases.add(alias);
        }
    }

    private void finishWrite(@NotNull TerminalClipboardHandler handler, @NotNull Write write) {
        myWrite = null;
        Map<String, byte[]> data = new LinkedHashMap<>();
        for (Map.Entry<String, MimeData> entry : write.data.entrySet()) {
            if (!entry.getValue().base64Rest.isEmpty()) {
                // the payloads of a type do not add up to padded base64
                fail(write, ClipboardError.EINVAL);
                return;
            }
            data.put(entry.getKey(), entry.getValue().bytes.toByteArray());
        }
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        write.aliases.forEach((target, list) -> {
            if (data.containsKey(target) && !list.isEmpty()) {
                aliases.put(target, List.copyOf(list));
            }
        });
        ClipboardWriteRequest request = new ClipboardWriteRequest(write.location, data, aliases,
                write.credentials.name, write.credentials.password);
        stage(() -> handler.write(request)).whenComplete((ignored, failure) ->
                send(packet("write", failure != null ? errorOf(failure).name() : "DONE", null, write.id, null, null, null)));
    }

    private void fail(@NotNull Write write, @NotNull ClipboardError error) {
        // the data is dropped, and so is every packet until the next write starts
        write.failed = true;
        write.data.clear();
        write.aliases.clear();
        send(packet("write", error.name(), null, write.id, null, null, null));
    }

    private static <T> @NotNull CompletionStage<T> stage(@NotNull StageSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @FunctionalInterface
    private interface StageSupplier<T> {
        @NotNull CompletionStage<T> get();
    }

    private static @NotNull ClipboardError errorOf(@NotNull Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return cause instanceof ClipboardException ? ((ClipboardException) cause).getError() : ClipboardError.EIO;
    }

    private void send(@NotNull String packet) {
        mySender.accept(packet);
    }

    private static @NotNull String packet(@NotNull String type, @NotNull String status,
                                          @Nullable ClipboardLocation location, @Nullable String id,
                                          @Nullable String mimeType, @Nullable String password,
                                          byte @Nullable [] data) {
        return packet(type, status, location, id, mimeType, password, data, null);
    }

    private static @NotNull String packet(@NotNull String type, @NotNull String status,
                                          @Nullable ClipboardLocation location, @Nullable String id,
                                          @Nullable String mimeType, @Nullable String password,
                                          byte @Nullable [] data, @Nullable String name) {
        StringBuilder packet = new StringBuilder().append(Ascii.ESC_CHAR).append("]5522;type=").append(type)
                .append(":status=").append(status);
        if (location == ClipboardLocation.PRIMARY) {
            packet.append(":loc=primary");
        }
        if (id != null && !id.isEmpty()) {
            packet.append(":id=").append(id);
        }
        if (mimeType != null) {
            packet.append(":mime=").append(encodeText(mimeType));
        }
        if (password != null) {
            packet.append(":pw=").append(encodeText(password));
        }
        if (name != null) {
            packet.append(":name=").append(encodeText(name));
        }
        if (data != null) {
            packet.append(';').append(Base64.getEncoder().encodeToString(data));
        }
        return packet.append(Ascii.ESC_CHAR).append('\\').toString();
    }

    private static @NotNull Map<String, String> parseMetadata(@NotNull String metadata) {
        Map<String, String> keys = new HashMap<>();
        for (String record : metadata.split(":")) {
            int equals = record.indexOf('=');
            if (equals > 0) {
                keys.putIfAbsent(record.substring(0, equals), record.substring(equals + 1));
            }
        }
        return keys;
    }

    private static @NotNull ClipboardLocation location(@NotNull Map<String, String> keys) {
        return "primary".equals(keys.get("loc")) ? ClipboardLocation.PRIMARY : ClipboardLocation.CLIPBOARD;
    }

    /**
     * @return the name and password of a request; a password without a name counts as none
     */
    private static @NotNull Credentials credentials(@NotNull Map<String, String> keys) {
        String name = decodeText(keys.get("name"));
        String password = decodeText(keys.get("pw"));
        return name != null && !name.isEmpty() && password != null && !password.isEmpty()
                ? new Credentials(name, password) : new Credentials(name, null);
    }

    /**
     * @return the id with every char removed that the protocol does not allow in one, as it is echoed back
     */
    static @Nullable String sanitizeId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String sanitized = ID_CHARS_TO_STRIP.matcher(id).replaceAll("");
        return sanitized.length() > MAX_ID_LENGTH ? sanitized.substring(0, MAX_ID_LENGTH) : sanitized;
    }

    /**
     * @return strictly decoded base64 (padded, no whitespace), or null when it is not
     */
    private static byte @Nullable [] decodeBase64(@NotNull String base64) {
        if (base64.length() % 4 != 0) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable String decodeText(@Nullable String base64) {
        byte[] bytes = base64 != null ? decodeBase64(base64) : null;
        return bytes != null ? decodeUtf8(bytes) : null;
    }

    private static @Nullable String decodeUtf8(byte @NotNull [] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static @NotNull String encodeText(@NotNull String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Credentials {

        final @Nullable String name;

        final @Nullable String password;

        Credentials(@Nullable String name, @Nullable String password) {
            this.name = name;
            this.password = password;
        }
    }

    private static final class MimeData {

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        /** The base64 chars after the last whole group of four. */
        @NotNull String base64Rest = "";

        /** Whether the data so far ended with padding, after which no more may come. */
        boolean ended;
    }

    private static final class Write {

        final ClipboardLocation location;

        final Credentials credentials;

        final @Nullable String id;

        final Map<String, MimeData> data = new LinkedHashMap<>();

        final Map<String, List<String>> aliases = new LinkedHashMap<>();

        long size;

        boolean failed;

        Write(@NotNull ClipboardLocation location, @NotNull Credentials credentials, @Nullable String id) {
            this.location = location;
            this.credentials = credentials;
            this.id = id;
        }

        int mimeTypeCount() {
            int count = data.size();
            for (List<String> list : aliases.values()) {
                count += list.size();
            }
            return count;
        }
    }
}
