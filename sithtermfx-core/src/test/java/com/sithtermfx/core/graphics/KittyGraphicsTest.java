package com.sithtermfx.core.graphics;

import com.sithtermfx.core.ArrayTerminalDataStream;
import com.sithtermfx.core.RequestOrigin;
import com.sithtermfx.core.TerminalOutputStream;
import com.sithtermfx.core.emulator.Emulator;
import com.sithtermfx.core.emulator.SithEmulator;
import com.sithtermfx.core.model.SithTerminal;
import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalLine;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.BackBufferDisplay;
import com.sithtermfx.core.util.PixelSize;
import com.sithtermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The kitty graphics protocol end to end: escape codes through {@link SithEmulator} into a {@link SithTerminal}
 * whose display draws images with 10x20 pixel cells.
 */
public class KittyGraphicsTest {

    private static final String ESC = "\u001B";

    private static final String ST = ESC + "\\";

    private TerminalTextBuffer myBuffer;

    private SithTerminal myTerminal;

    private final StringBuilder myResponses = new StringBuilder();

    private boolean myGraphicsEnabled = true;

    @BeforeEach
    public void setUp() {
        StyleState styleState = new StyleState();
        myBuffer = new TerminalTextBuffer(20, 10, styleState);
        myTerminal = new SithTerminal(new GraphicsDisplay(myBuffer), myBuffer, styleState);
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
    }

    private final class GraphicsDisplay extends BackBufferDisplay {

        GraphicsDisplay(TerminalTextBuffer buffer) {
            super(buffer);
        }

        @Override
        public @Nullable PixelSize getCellPixelSize() {
            return new PixelSize(10, 20);
        }

        @Override
        public boolean isKittyGraphicsEnabled() {
            return myGraphicsEnabled;
        }
    }

    private void process(@NotNull String data) throws IOException {
        Emulator emulator = new SithEmulator(new ArrayTerminalDataStream(data.toCharArray()), myTerminal);
        while (emulator.hasNext()) {
            emulator.next();
        }
    }

    private @NotNull String takeResponses() {
        String responses = myResponses.toString();
        myResponses.setLength(0);
        return responses;
    }

    private static @NotNull String graphics(@NotNull String controlData, @NotNull String payload) {
        return ESC + "_G" + controlData + (payload.isEmpty() ? "" : ";" + payload) + ST;
    }

    private static @NotNull String response(@NotNull String keys, @NotNull String message) {
        return ESC + "_G" + keys + ";" + message + ST;
    }

    private static byte @NotNull [] rgba(int width, int height, int r, int g, int b, int a) {
        byte[] bytes = new byte[width * height * 4];
        for (int i = 0; i < bytes.length; i += 4) {
            bytes[i] = (byte) r;
            bytes[i + 1] = (byte) g;
            bytes[i + 2] = (byte) b;
            bytes[i + 3] = (byte) a;
        }
        return bytes;
    }

    private static @NotNull String base64(byte @NotNull [] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** A red RGBA image, sent with the given control data. */
    private static @NotNull String image(@NotNull String controlData, int width, int height) {
        return graphics("f=32,s=" + width + ",v=" + height + "," + controlData, base64(rgba(width, height, 255, 0, 0, 255)));
    }

    private @NotNull List<ImagePlacement> placementsAt(int line) {
        return myBuffer.getLine(line).getImagePlacements();
    }

    private @NotNull List<ImagePlacement> allPlacements() {
        List<ImagePlacement> placements = new ArrayList<>();
        for (int y = -myBuffer.getHistoryLinesCount(); y < myBuffer.getScreenLinesCount(); y++) {
            placements.addAll(myBuffer.getLine(y).getImagePlacements());
        }
        return placements;
    }

    private @NotNull List<Long> storedIds() {
        List<Long> ids = new ArrayList<>();
        for (KittyImage image : myTerminal.getKittyGraphics().getImages()) {
            ids.add(image.getId());
        }
        return ids;
    }

    @Test
    public void aQueryIsAnsweredAndStoresNothing() throws IOException {
        process(graphics("i=31,s=1,v=1,a=q,t=d,f=24", "AAAA") + ESC + "[c");

        assertEquals(response("i=31", "OK") + ESC + "[?6c", takeResponses());
        assertTrue(myTerminal.getKittyGraphics().getImages().isEmpty());
        assertTrue(allPlacements().isEmpty());
    }

    @Test
    public void withGraphicsDisabledACommandIsIgnoredAndNotPrinted() throws IOException {
        myGraphicsEnabled = false;

        process(graphics("i=31,s=1,v=1,a=q,t=d,f=24", "AAAA") + "after");

        assertEquals("", takeResponses());
        assertEquals("after", myBuffer.getLine(0).getText());
    }

    @Test
    public void anotherApplicationProgramCommandIsIgnored() throws IOException {
        process(ESC + "_something else" + ST + "after");

        assertEquals("after", myBuffer.getLine(0).getText());
    }

    @Test
    public void anOverlongCommandIsDroppedAndTheOutputAfterItRenders() throws IOException {
        String payload = "A".repeat(com.sithtermfx.core.emulator.ApplicationProgramCommand.MAX_BODY_LENGTH);

        process(graphics("i=1,a=T,f=24,s=1,v=1", payload) + "after");

        assertEquals("", takeResponses());
        assertEquals("after", myBuffer.getLine(0).getText());
    }

    @Test
    public void anImageIsPlacedAtTheCursorAndTheCursorMovesPastIt() throws IOException {
        process("ab" + image("a=T,i=5", 25, 30));

        assertEquals(response("i=5", "OK"), takeResponses());
        List<ImagePlacement> placements = placementsAt(0);
        assertEquals(1, placements.size());
        ImagePlacement placement = placements.get(0);
        assertEquals(2, placement.getColumn());
        assertEquals(3, placement.getColumns());
        assertEquals(2, placement.getRows());
        assertEquals(2.5, placement.getWidth(), 1e-9);
        assertEquals(1.5, placement.getHeight(), 1e-9);
        // right by the columns, down to the last row of the image
        assertEquals(6, myTerminal.getCursorX());
        assertEquals(2, myTerminal.getCursorY());
        assertEquals(0xFFFF0000, placement.getImage().getPixels()[0]);
    }

    @Test
    public void cursorPolicyOneKeepsTheCursor() throws IOException {
        process("ab" + image("a=T,i=5,C=1", 25, 30));

        assertEquals(3, myTerminal.getCursorX());
        assertEquals(1, myTerminal.getCursorY());
    }

    @Test
    public void anImageAtTheBottomScrollsIntoView() throws IOException {
        process(ESC + "[10;1H" + image("a=T,q=2", 10, 60));

        // 3 rows from the last line: the screen scrolled by 2
        assertEquals(1, placementsAt(7).size());
        assertEquals(2, myBuffer.getHistoryLinesCount());
        assertEquals(10, myTerminal.getCursorY());
    }

    @Test
    public void aChunkedTransmissionIsAnsweredOnceAfterTheLastChunk() throws IOException {
        String payload = base64(rgba(3, 4, 0, 255, 0, 128));
        StringBuilder data = new StringBuilder(graphics("a=T,i=9,f=32,s=3,v=4,m=1", payload.substring(0, 8)));
        for (int i = 8; i < payload.length(); i += 8) {
            String chunk = payload.substring(i, Math.min(i + 8, payload.length()));
            data.append(graphics("m=" + (i + 8 < payload.length() ? 1 : 0), chunk));
        }

        process(data.toString());

        assertEquals(response("i=9", "OK"), takeResponses());
        assertEquals(0x8000FF00, placementsAt(0).get(0).getImage().getPixels()[11]);
    }

    @Test
    public void anotherCommandAbortsAChunkedTransmission() throws IOException {
        process(graphics("a=T,i=9,f=32,s=3,v=4,m=1", "AAAA") + graphics("a=d,d=A", "") + graphics("m=0", "AAAA"));

        assertTrue(storedIds().isEmpty());
        assertTrue(allPlacements().isEmpty());
    }

    @Test
    public void zlibCompressedDataIsInflated() throws IOException {
        byte[] raw = rgba(8, 8, 1, 2, 3, 255);
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[raw.length + 64];
        int length = deflater.deflate(buffer);
        deflater.end();
        byte[] compressed = java.util.Arrays.copyOf(buffer, length);

        process(graphics("a=t,i=3,f=32,o=z,s=8,v=8", base64(compressed)));

        assertEquals(response("i=3", "OK"), takeResponses());
        assertEquals(0xFF010203, myTerminal.getKittyGraphics().getImages().get(0).getPixels()[63]);
    }

    @Test
    public void rgbDataIsOpaque() throws IOException {
        process(graphics("a=t,i=3,f=24,s=1,v=1", base64(new byte[]{10, 20, 30})));

        assertEquals(0xFF0A141E, myTerminal.getKittyGraphics().getImages().get(0).getPixels()[0]);
    }

    @Test
    public void rawDataBeyondTheImageIsDropped() throws IOException {
        process(graphics("a=t,i=3,f=24,s=1,v=1", base64(new byte[]{10, 20, 30, 40, 50, 60})));

        assertEquals(response("i=3", "OK"), takeResponses());
        assertEquals(0xFF0A141E, myTerminal.getKittyGraphics().getImages().get(0).getPixels()[0]);
    }

    @Test
    public void aLineHoldsAtMostSoManyPlacementsTheOldestGo() throws IOException {
        StringBuilder data = new StringBuilder(image("a=t,i=1,q=2", 1, 1));
        for (int i = 1; i <= KittyGraphicsProtocol.MAX_PLACEMENTS_PER_LINE + 10; i++) {
            data.append(graphics("a=p,i=1,C=1,q=2,p=" + i, ""));
        }

        process(data.toString());

        List<ImagePlacement> placements = placementsAt(0);
        assertEquals(KittyGraphicsProtocol.MAX_PLACEMENTS_PER_LINE, placements.size());
        assertEquals(11, placements.get(0).getId());
    }

    @Test
    public void aPngImageIsDecoded() throws IOException {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFF112233);
        source.setRGB(2, 1, 0x80445566);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);

        process(graphics("a=T,i=4,f=100", base64(png.toByteArray())));

        assertEquals(response("i=4", "OK"), takeResponses());
        KittyImage image = placementsAt(0).get(0).getImage();
        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(0xFF112233, image.getPixels()[0]);
        assertEquals(0x80445566, image.getPixels()[5]);
    }

    @Test
    public void dataThatIsNotAPngIsAnError() throws IOException {
        process(graphics("a=t,i=4,f=100", base64(new byte[]{1, 2, 3, 4})));

        assertTrue(takeResponses().startsWith(ESC + "_Gi=4;EBADPNG:"));
        assertTrue(storedIds().isEmpty());
    }

    @Test
    public void anImageNumberIsAnsweredWithTheAssignedId() throws IOException {
        process(image("a=t,I=13", 1, 1));

        String responses = takeResponses();
        long id = myTerminal.getKittyGraphics().getImages().get(0).getId();
        assertTrue(id != 0);
        assertEquals(response("i=" + id + ",I=13", "OK"), responses);

        process(graphics("a=p,I=13,p=7", ""));

        assertEquals(response("i=" + id + ",I=13,p=7", "OK"), takeResponses());
        assertEquals(7, placementsAt(0).get(0).getId());
    }

    @Test
    public void quietModesSuppressResponses() throws IOException {
        process(image("a=t,i=1,q=1", 1, 1) + graphics("a=p,i=99,q=1", ""));

        assertTrue(takeResponses().startsWith(ESC + "_Gi=99;ENOENT:"));

        process(image("a=t,i=2,q=2", 1, 1) + graphics("a=p,i=99,q=2", ""));

        assertEquals("", takeResponses());
    }

    @Test
    public void aCommandWithoutAnIdIsNotAnswered() throws IOException {
        process(image("a=T", 1, 1) + graphics("a=p,x=1", ""));

        assertEquals("", takeResponses());
        assertEquals(1, placementsAt(0).size());
    }

    @Test
    public void invalidCommandsAreErrors() throws IOException {
        process(graphics("a=p,i=1,I=2", ""));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1,I=2;EINVAL:"));

        process(graphics("a=t,i=1,t=f", base64("/etc/passwd".getBytes(StandardCharsets.UTF_8))));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EINVAL:"));

        process(graphics("a=t,i=1,f=32,s=2,v=2", base64(new byte[8])));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;ENODATA:"));

        process(graphics("a=t,i=1,f=24,s=1,v=1", "!!!!"));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EINVAL:"));

        process(graphics("a=t,i=1,f=24,s=20000,v=1", "AAAA"));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EFBIG:"));

        process(graphics("a=t,i=x1", ""));
        assertEquals("", takeResponses());

        assertTrue(storedIds().isEmpty());
    }

    @Test
    public void unsupportedFeaturesAreErrors() throws IOException {
        process(image("a=t,i=1", 1, 1));
        takeResponses();

        process(graphics("a=f,i=1,f=24,s=1,v=1", "AAAA"));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EINVAL:"));

        process(graphics("a=a,i=1,s=3", ""));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EINVAL:"));

        process(graphics("a=p,i=1,U=1,c=2,r=2", ""));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EINVAL:"));

        process(graphics("a=p,i=1,P=1,Q=1", ""));
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;EINVAL:"));

        assertTrue(allPlacements().isEmpty());
    }

    @Test
    public void aResponseNeverEchoesAControlChar() throws IOException {
        process(image("a=t,i=1,q=1", 1, 1) + graphics("a=p,i=1,z=" + ESC + "[31m", ""));

        String responses = takeResponses();
        assertTrue(responses.startsWith(ESC + "_Gi=1;EINVAL:"));
        assertEquals(2, responses.split(ESC, -1).length - 1, responses);
    }

    @Test
    public void anImageIsPlacedAgainWithAnotherLayout() throws IOException {
        process(image("a=t,i=1,q=1", 40, 40) + graphics("a=p,i=1,c=2,q=1", "") + ESC + "[3;1H"
                + graphics("a=p,i=1,r=1,q=1", "") + ESC + "[5;1H"
                + graphics("a=p,i=1,c=4,r=1,q=1", "") + ESC + "[7;1H"
                + graphics("a=p,i=1,x=10,y=20,w=10,h=10,X=3,Y=5,z=-1,q=1", ""));

        ImagePlacement byColumns = placementsAt(0).get(0);
        assertEquals(2, byColumns.getColumns());
        assertEquals(1, byColumns.getRows());
        assertEquals(2.0, byColumns.getWidth(), 1e-9);
        assertEquals(1.0, byColumns.getHeight(), 1e-9);

        ImagePlacement byRows = placementsAt(2).get(0);
        assertEquals(2, byRows.getColumns());
        assertEquals(1, byRows.getRows());
        assertEquals(2.0, byRows.getWidth(), 1e-9);

        // 40x40 pixels in a 40x20 box: 20x20 pixels, centred
        ImagePlacement fitted = placementsAt(4).get(0);
        assertEquals(4, fitted.getColumns());
        assertEquals(1, fitted.getRows());
        assertEquals(2.0, fitted.getWidth(), 1e-9);
        assertEquals(1.0, fitted.getLeft(), 1e-9);

        ImagePlacement cropped = placementsAt(6).get(0);
        assertEquals(10, cropped.getSourceX());
        assertEquals(20, cropped.getSourceY());
        assertEquals(10, cropped.getSourceWidth());
        assertEquals(10, cropped.getSourceHeight());
        assertEquals(0.3, cropped.getLeft(), 1e-9);
        assertEquals(0.25, cropped.getTop(), 1e-9);
        assertEquals(2, cropped.getColumns());
        assertEquals(1, cropped.getRows());
        assertEquals(-1, cropped.getZIndex());
    }

    @Test
    public void aPlacementIdReplacesThePlacementOfTheSameImage() throws IOException {
        process(image("a=T,i=1,p=1,q=2", 10, 20) + ESC + "[5;5H" + graphics("a=p,i=1,p=1,q=2", ""));

        assertTrue(placementsAt(0).isEmpty());
        assertEquals(1, placementsAt(4).size());
        assertEquals(4, placementsAt(4).get(0).getColumn());
    }

    @Test
    public void anImageSentWithTheIdOfAnotherReplacesItAndItsPlacements() throws IOException {
        process(image("a=T,i=1,q=2", 10, 20));
        KittyImage first = placementsAt(0).get(0).getImage();

        process(image("a=t,i=1,q=2", 20, 20));

        assertTrue(first.isDeleted());
        assertTrue(allPlacements().isEmpty());
        assertEquals(List.of(1L), storedIds());
        assertEquals(20, myTerminal.getKittyGraphics().getImages().get(0).getWidth());
    }

    @Test
    public void lowercaseDeleteKeepsTheDataUppercaseFreesIt() throws IOException {
        process(image("a=T,i=1,q=2", 10, 20) + graphics("a=d,d=i,i=1", ""));

        assertTrue(allPlacements().isEmpty());
        assertEquals(List.of(1L), storedIds());

        process(graphics("a=p,i=1", ""));
        assertEquals(response("i=1", "OK"), takeResponses());

        process(graphics("a=d,d=I,i=1", "") + graphics("a=p,i=1", ""));

        assertTrue(allPlacements().isEmpty());
        assertTrue(storedIds().isEmpty());
        assertTrue(takeResponses().startsWith(ESC + "_Gi=1;ENOENT:"));
    }

    @Test
    public void deleteTargetsSelectThePlacements() throws IOException {
        // four 2x2 placements of one image: at (0,0), (5,0), (0,4) and (5,4), the last with z=3
        String placeAll = image("a=t,i=1,q=2", 20, 40)
                + ESC + "[1;1H" + graphics("a=p,i=1,q=2", "")
                + ESC + "[1;6H" + graphics("a=p,i=1,q=2", "")
                + ESC + "[5;1H" + graphics("a=p,i=1,q=2", "")
                + ESC + "[5;6H" + graphics("a=p,i=1,z=3,q=2", "");
        process(placeAll + graphics("a=d,d=p,x=2,y=2", ""));
        assertEquals(3, allPlacements().size());
        assertTrue(placementsAt(0).stream().noneMatch(placement -> placement.getColumn() == 0));

        process(graphics("a=d,d=x,x=7", ""));
        assertEquals(1, allPlacements().size());
        assertEquals(4, myBuffer.findScreenLineIndex(lineHolding(allPlacements().get(0))));

        process(graphics("a=d,d=a", "") + placeAll + graphics("a=d,d=y,y=6", ""));
        assertEquals(2, allPlacements().size());

        process(graphics("a=d,d=a", "") + placeAll + graphics("a=d,d=z,z=3", ""));
        assertEquals(3, allPlacements().size());

        process(graphics("a=d,d=a", "") + placeAll + graphics("a=d,d=q,x=6,y=5,z=0", ""));
        assertEquals(4, allPlacements().size());
        process(graphics("a=d,d=q,x=6,y=5,z=3", ""));
        assertEquals(3, allPlacements().size());

        process(graphics("a=d,d=a", "") + placeAll + ESC + "[6;2H" + graphics("a=d,d=c", ""));
        assertEquals(3, allPlacements().size());

        process(graphics("a=d", ""));
        assertTrue(allPlacements().isEmpty());
        assertEquals(List.of(1L), storedIds());
    }

    private @NotNull TerminalLine lineHolding(@NotNull ImagePlacement placement) {
        for (int y = 0; y < myBuffer.getScreenLinesCount(); y++) {
            if (myBuffer.getLine(y).getImagePlacements().contains(placement)) {
                return myBuffer.getLine(y);
            }
        }
        throw new AssertionError("no line holds " + placement);
    }

    @Test
    public void deleteByIdRangeAndByNumber() throws IOException {
        process(image("a=T,i=3,q=2", 1, 1) + image("a=T,i=5,q=2", 1, 1) + image("a=T,i=9,q=2", 1, 1)
                + image("a=T,I=4,q=2", 1, 1) + graphics("a=d,d=R,x=4,y=9", ""));

        assertEquals(3, storedIds().get(0));
        assertEquals(2, storedIds().size());

        process(graphics("a=d,d=N,I=4", ""));

        assertEquals(List.of(3L), storedIds());
        assertEquals(1, allPlacements().size());
    }

    @Test
    public void anImageScrollsWithTheTextIntoTheHistory() throws IOException {
        process("text" + image("a=T,q=2,C=1", 10, 20) + "\r\n".repeat(15));

        assertTrue(placementsAt(0).isEmpty());
        TerminalLine line = myBuffer.getLine(-6);
        assertEquals("text", line.getText());
        assertEquals(1, line.getImagePlacements().size());
    }

    @Test
    public void anImageAboveTheWindowThatReachesIntoItIsProcessed() throws IOException {
        process(image("a=T,q=2", 10, 100) + "\r\n".repeat(12));
        int historyLineCount = myBuffer.getHistoryLinesCount();
        List<Integer> rows = new ArrayList<>();

        myBuffer.processImagePlacements(-historyLineCount + 3, 10, (placement, row) -> rows.add(row));

        assertEquals(List.of(-3), rows);
    }

    @Test
    public void theAlternateScreenHasItsOwnImages() throws IOException {
        process(image("a=T,i=1,q=2", 10, 20) + ESC + "[?1049h" + image("a=T,i=2,q=2", 10, 20));

        assertEquals(2L, placementsAt(0).get(0).getImage().getId());

        process(ESC + "[?1049l");

        assertEquals(1, placementsAt(0).size());
        assertEquals(1L, placementsAt(0).get(0).getImage().getId());
    }

    @Test
    public void aDeleteByIdReachesTheMainScreenBehindTheAlternateOne() throws IOException {
        process(image("a=T,i=1,q=2", 10, 20) + ESC + "[?1049h" + graphics("a=d,d=I,i=1", "") + ESC + "[?1049l");

        assertTrue(allPlacements().isEmpty());
        assertTrue(storedIds().isEmpty());
    }

    @Test
    public void aNarrowerTerminalRewrapsTheLineAndKeepsTheImageAtItsChar() throws IOException {
        process("0123456" + image("a=T,q=2,C=1", 10, 20) + "789");

        myTerminal.resize(new TermSize(5, 10), RequestOrigin.User);

        TerminalLine line = lineHolding(allPlacements().get(0));
        assertEquals("56789", line.getText());
        assertEquals(2, line.getImagePlacements().get(0).getColumn());
    }

    @Test
    public void eraseInDisplayTwoKeepsTheImageInTheHistoryThreeDeletesIt() throws IOException {
        process("text" + image("a=T,q=2,i=1", 10, 20) + ESC + "[2J");

        assertEquals(1, allPlacements().size());
        assertTrue(myBuffer.getLine(0).getImagePlacements().isEmpty());

        process(ESC + "[1;1H" + graphics("a=p,i=1,q=2", "") + ESC + "[3J");

        assertTrue(allPlacements().isEmpty());
    }

    @Test
    public void aFullResetDeletesEveryImage() throws IOException {
        process(image("a=T,i=1,q=2", 10, 20) + image("a=t,i=2,q=2", 10, 20));
        KittyImage placed = placementsAt(0).get(0).getImage();

        process(ESC + "c");

        assertTrue(allPlacements().isEmpty());
        assertTrue(storedIds().isEmpty());
        assertTrue(placed.isDeleted());
    }

    @Test
    public void cellAndTextAreaSizesAreReported() throws IOException {
        process(ESC + "[14t" + ESC + "[16t" + ESC + "[18t");

        assertEquals(ESC + "[4;200;200t" + ESC + "[6;20;10t" + ESC + "[8;10;20t", takeResponses());
    }

    @Test
    public void imagesThatNoLineShowsAreEvictedFirstThenTheOldest() {
        FakeHost host = new FakeHost();
        // room for two 10x10 images
        KittyGraphicsProtocol protocol = new KittyGraphicsProtocol(host, 1000);
        String pixels = base64(rgba(10, 10, 0, 0, 0, 255));

        protocol.handle("a=T,i=1,q=2,f=32,s=10,v=10;" + pixels);
        protocol.handle("a=t,i=2,q=2,f=32,s=10,v=10;" + pixels);
        protocol.handle("a=t,i=3,q=2,f=32,s=10,v=10;" + pixels);

        assertEquals(List.of(1L, 3L), ids(protocol));

        protocol.handle("a=t,i=4,q=2,f=32,s=20,v=10;" + base64(rgba(20, 10, 0, 0, 0, 255)));

        assertEquals(List.of(4L), ids(protocol));
        assertTrue(host.placements.isEmpty());
        assertEquals(800, protocol.getStoredBytes());

        protocol.handle("a=t,i=5,f=32,s=20,v=20;" + base64(rgba(20, 20, 0, 0, 0, 255)));

        assertTrue(host.responses.get(0).startsWith(ESC + "_Gi=5;EFBIG:"));
        assertEquals(List.of(4L), ids(protocol));
    }

    @Test
    public void atMostSoManyImagesAreStoredHoweverSmall() {
        KittyGraphicsProtocol protocol = new KittyGraphicsProtocol(new FakeHost());
        String pixel = base64(rgba(1, 1, 0, 0, 0, 255));

        for (int id = 1; id <= KittyGraphicsProtocol.MAX_IMAGES + 4; id++) {
            protocol.handle("a=t,q=2,f=32,s=1,v=1,i=" + id + ";" + pixel);
        }

        List<Long> ids = ids(protocol);
        assertEquals(KittyGraphicsProtocol.MAX_IMAGES, ids.size());
        assertEquals(5L, ids.get(0));
    }

    private static @NotNull List<Long> ids(@NotNull KittyGraphicsProtocol protocol) {
        List<Long> ids = new ArrayList<>();
        for (KittyImage image : protocol.getImages()) {
            ids.add(image.getId());
        }
        return ids;
    }

    private static final class FakeHost implements KittyGraphicsProtocol.Host {

        final List<ImagePlacement> placements = new ArrayList<>();

        final List<String> responses = new ArrayList<>();

        @Override
        public @NotNull PixelSize getCellPixelSize() {
            return new PixelSize(10, 20);
        }

        @Override
        public int getCursorColumn() {
            return 0;
        }

        @Override
        public int getCursorRow() {
            return 0;
        }

        @Override
        public void placeAtCursor(@NotNull ImagePlacement placement, boolean moveCursor) {
            placements.add(placement);
        }

        @Override
        public @NotNull List<ImagePlacement> removePlacements(boolean screenOnly,
                                                              @NotNull KittyGraphicsProtocol.PlacementFilter filter) {
            List<ImagePlacement> removed = new ArrayList<>();
            placements.removeIf(placement -> filter.accept(placement, 0) && removed.add(placement));
            return removed;
        }

        @Override
        public @NotNull java.util.Set<KittyImage> getPlacedImages() {
            java.util.Set<KittyImage> images = new java.util.HashSet<>();
            for (ImagePlacement placement : placements) {
                images.add(placement.getImage());
            }
            return images;
        }

        @Override
        public void sendResponse(@NotNull String response) {
            responses.add(response);
        }
    }

    @Test
    public void aDeletedImageHasNoPixels() {
        KittyImage image = new KittyImage(1, 0, 1, 1, new int[]{0}, 0);

        image.delete();

        assertNull(image.getPixels());
        assertTrue(image.isDeleted());
        assertFalse(new KittyImage(2, 0, 1, 1, new int[]{0}, 1).isDeleted());
        assertNotNull(new KittyImage(2, 0, 1, 1, new int[]{0}, 1).getPixels());
    }
}
