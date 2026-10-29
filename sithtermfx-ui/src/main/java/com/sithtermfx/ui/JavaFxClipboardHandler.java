package com.sithtermfx.ui;

import com.sithtermfx.core.clipboard.ClipboardError;
import com.sithtermfx.core.clipboard.ClipboardException;
import com.sithtermfx.core.clipboard.ClipboardLocation;
import com.sithtermfx.core.clipboard.ClipboardReadRequest;
import com.sithtermfx.core.clipboard.ClipboardWriteRequest;
import com.sithtermfx.core.clipboard.TerminalClipboardHandler;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Gives programs in the terminal the system clipboard through JavaFX, for OSC 5522 (set it with
 * {@link SithTermFxWidget#setClipboardHandler}). Writes are allowed or not as a whole; a read is allowed only when
 * the {@link ReadPermission} says so, unless the user pasted (paste events) or allowed the program before by its
 * name and password. JavaFX has no primary selection: requests for it are answered with {@code ENOSYS}.
 * <p>
 * Text ({@code text/plain}), HTML, RTF and URI lists are exchanged as the clipboard holds them, images as
 * {@code image/png}; any other MIME type as raw bytes under a JavaFX {@link DataFormat} of that name, which other
 * applications may or may not see.
 */
public class JavaFxClipboardHandler implements TerminalClipboardHandler {

    /**
     * What the user decided about a read.
     */
    public enum ReadDecision {
        ALLOW_ONCE,
        /** Also allow later reads of the program with the same name and password, without asking. */
        ALLOW_FOR_PROGRAM,
        DENY
    }

    /**
     * Decides whether a program may read the clipboard, typically by asking the user.
     */
    @FunctionalInterface
    public interface ReadPermission {

        @NotNull CompletionStage<ReadDecision> decide(@NotNull ClipboardReadRequest request);
    }

    /** Refuses every read: programs may only list the MIME types and, if allowed, write. */
    public static final ReadPermission DENY_READS = request -> CompletableFuture.completedFuture(ReadDecision.DENY);

    /** At most this many {@link DataFormat}s are made for MIME types JavaFX does not know: JavaFX never frees them. */
    private static final int MAX_CUSTOM_FORMATS = 64;

    private static final AtomicInteger ourCustomFormats = new AtomicInteger();

    private static final int MAX_PROMPT_NAME_LENGTH = 80;

    private final boolean myWritesAllowed;

    private final ReadPermission myReadPermission;

    /** The programs allowed to read for the session: name and password. */
    private final Set<String> myAllowedPrograms = ConcurrentHashMap.newKeySet();

    /**
     * @param writesAllowed  whether programs may replace the clipboard
     * @param readPermission decides about reads; {@link #askUser(Supplier)} asks the user
     */
    public JavaFxClipboardHandler(boolean writesAllowed, @NotNull ReadPermission readPermission) {
        myWritesAllowed = writesAllowed;
        myReadPermission = readPermission;
    }

    /**
     * @param owner the window the prompt belongs to, or a supplier of null
     * @return a permission that asks the user in a dialog, naming the program and the MIME types it wants
     */
    public static @NotNull ReadPermission askUser(@NotNull Supplier<@Nullable Window> owner) {
        return request -> onFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(owner.get());
            alert.setTitle("Clipboard access");
            String name = request.getName();
            alert.setHeaderText((name != null ? "“" + displayName(name) + "”" : "A program in the terminal")
                    + " wants to read the clipboard.");
            alert.setContentText("Requested: " + String.join(", ", request.getMimeTypes()));
            ButtonType once = new ButtonType("Allow Once", ButtonBar.ButtonData.OK_DONE);
            ButtonType always = new ButtonType("Allow for This Program", ButtonBar.ButtonData.OTHER);
            ButtonType deny = new ButtonType("Deny", ButtonBar.ButtonData.CANCEL_CLOSE);
            if (request.getPassword() != null) {
                alert.getButtonTypes().setAll(deny, always, once);
            } else {
                alert.getButtonTypes().setAll(deny, once);
            }
            Optional<ButtonType> answer = alert.showAndWait();
            if (answer.isEmpty() || answer.get() == deny) {
                return ReadDecision.DENY;
            }
            return answer.get() == always ? ReadDecision.ALLOW_FOR_PROGRAM : ReadDecision.ALLOW_ONCE;
        });
    }

    @Override
    public @NotNull CompletionStage<List<String>> getMimeTypes(@NotNull ClipboardLocation location) {
        if (location != ClipboardLocation.CLIPBOARD) {
            return CompletableFuture.failedFuture(new ClipboardException(ClipboardError.ENOSYS));
        }
        return onFxThread(() -> {
            List<String> mimeTypes = new ArrayList<>();
            for (DataFormat format : Clipboard.getSystemClipboard().getContentTypes()) {
                for (String mimeType : mimeTypesOf(format)) {
                    if (!mimeTypes.contains(mimeType)) {
                        mimeTypes.add(mimeType);
                    }
                }
            }
            return mimeTypes;
        });
    }

    @Override
    public @NotNull CompletionStage<Map<String, byte[]>> read(@NotNull ClipboardReadRequest request) {
        if (request.getLocation() != ClipboardLocation.CLIPBOARD) {
            return CompletableFuture.failedFuture(new ClipboardException(ClipboardError.ENOSYS));
        }
        String program = programKey(request.getName(), request.getPassword());
        CompletionStage<Boolean> allowed;
        if (request.isGrantedByPaste() || (program != null && myAllowedPrograms.contains(program))) {
            allowed = CompletableFuture.completedFuture(true);
        } else {
            allowed = myReadPermission.decide(request).thenApply(decision -> {
                if (decision == ReadDecision.ALLOW_FOR_PROGRAM && program != null) {
                    myAllowedPrograms.add(program);
                }
                return decision != ReadDecision.DENY;
            });
        }
        return allowed.thenCompose(ok -> {
            if (!ok) {
                return CompletableFuture.failedFuture(new ClipboardException(ClipboardError.EPERM));
            }
            return onFxThread(() -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                Map<String, byte[]> data = new LinkedHashMap<>();
                for (String mimeType : request.getMimeTypes()) {
                    byte[] bytes = readType(clipboard, mimeType);
                    if (bytes != null) {
                        data.put(mimeType, bytes);
                    }
                }
                return data;
            });
        });
    }

    @Override
    public @NotNull CompletionStage<Void> write(@NotNull ClipboardWriteRequest request) {
        if (!myWritesAllowed) {
            return CompletableFuture.failedFuture(new ClipboardException(ClipboardError.EPERM));
        }
        if (request.getLocation() != ClipboardLocation.CLIPBOARD) {
            return CompletableFuture.failedFuture(new ClipboardException(ClipboardError.ENOSYS));
        }
        return onFxThread(() -> {
            ClipboardContent content = new ClipboardContent();
            for (Map.Entry<String, byte[]> entry : request.getData().entrySet()) {
                putType(content, entry.getKey(), entry.getValue());
                for (String alias : request.getAliases().getOrDefault(entry.getKey(), List.of())) {
                    putType(content, alias, entry.getValue());
                }
            }
            if (!Clipboard.getSystemClipboard().setContent(content)) {
                throw new ClipboardException(ClipboardError.EIO);
            }
            return null;
        });
    }

    /**
     * @return the MIME types a format of the clipboard is offered as
     */
    static @NotNull List<String> mimeTypesOf(@NotNull DataFormat format) {
        if (format.equals(DataFormat.PLAIN_TEXT)) {
            return List.of("text/plain");
        }
        if (format.equals(DataFormat.HTML)) {
            return List.of("text/html");
        }
        if (format.equals(DataFormat.RTF)) {
            return List.of("text/rtf");
        }
        if (format.equals(DataFormat.URL)) {
            return List.of("text/uri-list");
        }
        if (format.equals(DataFormat.IMAGE)) {
            return List.of("image/png");
        }
        if (format.equals(DataFormat.FILES)) {
            return List.of();
        }
        List<String> mimeTypes = new ArrayList<>();
        for (String identifier : format.getIdentifiers()) {
            if (identifier.matches("[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9!#$&^_.+-]+")) {
                mimeTypes.add(identifier);
            }
        }
        return mimeTypes;
    }

    /**
     * @return the type and subtype of a MIME type, lower case, without parameters
     */
    static @NotNull String baseType(@NotNull String mimeType) {
        int semicolon = mimeType.indexOf(';');
        return (semicolon >= 0 ? mimeType.substring(0, semicolon) : mimeType).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @return a name a program gave itself, safe to show: without control and bidi chars, shortened
     */
    static @NotNull String displayName(@NotNull String name) {
        StringBuilder shown = new StringBuilder();
        name.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT)
                .limit(MAX_PROMPT_NAME_LENGTH)
                .forEach(shown::appendCodePoint);
        return shown.toString();
    }

    private static @Nullable String programKey(@Nullable String name, @Nullable String password) {
        return name != null && password != null ? name + '\0' + password : null;
    }

    private static byte @Nullable [] readType(@NotNull Clipboard clipboard, @NotNull String mimeType) throws IOException {
        String text;
        switch (baseType(mimeType)) {
            case "text/plain":
                text = clipboard.getString();
                break;
            case "text/html":
                text = clipboard.getHtml();
                break;
            case "text/rtf":
                text = clipboard.getRtf();
                break;
            case "text/uri-list":
                text = clipboard.getUrl();
                break;
            case "image/png":
                Image image = clipboard.getImage();
                return image != null ? toPng(image) : null;
            default:
                DataFormat format = DataFormat.lookupMimeType(mimeType);
                Object content = format != null && clipboard.hasContent(format) ? clipboard.getContent(format) : null;
                if (content instanceof ByteBuffer) {
                    ByteBuffer buffer = ((ByteBuffer) content).duplicate();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    return bytes;
                }
                if (content instanceof byte[]) {
                    return ((byte[]) content).clone();
                }
                text = content instanceof String ? (String) content : null;
                break;
        }
        return text != null ? text.getBytes(StandardCharsets.UTF_8) : null;
    }

    private static void putType(@NotNull ClipboardContent content, @NotNull String mimeType, byte @NotNull [] bytes) {
        String base = baseType(mimeType);
        switch (base) {
            case "text/plain":
                content.putString(new String(bytes, StandardCharsets.UTF_8));
                return;
            case "text/html":
                content.putHtml(new String(bytes, StandardCharsets.UTF_8));
                return;
            case "text/rtf":
                content.putRtf(new String(bytes, StandardCharsets.UTF_8));
                return;
            case "text/uri-list":
                content.putUrl(new String(bytes, StandardCharsets.UTF_8).trim());
                return;
            default:
                break;
        }
        if (base.startsWith("image/") && !content.hasImage()) {
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (!image.isError()) {
                content.putImage(image);
                return;
            }
        }
        DataFormat format = DataFormat.lookupMimeType(mimeType);
        if (format == null) {
            if (ourCustomFormats.incrementAndGet() > MAX_CUSTOM_FORMATS) {
                return;
            }
            format = new DataFormat(mimeType);
        }
        content.put(format, ByteBuffer.wrap(bytes));
    }

    private static byte @NotNull [] toPng(@NotNull Image image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", png);
        return png.toByteArray();
    }

    private static <T> @NotNull CompletionStage<T> onFxThread(@NotNull Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable run = () -> {
            try {
                future.complete(action.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (Platform.isFxApplicationThread()) {
            run.run();
        } else {
            Platform.runLater(run);
        }
        return future;
    }
}
