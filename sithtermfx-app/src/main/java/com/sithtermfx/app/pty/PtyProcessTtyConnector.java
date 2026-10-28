package com.sithtermfx.app.pty;

import com.sithtermfx.core.util.PixelSize;
import com.sithtermfx.core.util.TermSize;
import com.sithtermfx.core.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Daniel Mengel
 */
public class PtyProcessTtyConnector extends ProcessTtyConnector {

    private final PtyProcess myProcess;

    public PtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset) {
        this(process, charset, null);
    }

    public PtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset, @Nullable List<String> commandLine) {
        super(process, charset, commandLine);
        myProcess = process;
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        if (isConnected()) {
            myProcess.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
        }
    }

    @Override
    public void resize(@NotNull TermSize termSize, @NotNull PixelSize pixelSize) {
        if (isConnected()) {
            // ws_xpixel and ws_ypixel are shorts
            myProcess.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows(),
                    Math.min(pixelSize.getWidth(), Short.MAX_VALUE), Math.min(pixelSize.getHeight(), Short.MAX_VALUE)));
        }
    }

    @Override
    public boolean isConnected() {
        return myProcess.isAlive();
    }

    @Override
    public String getName() {
        return "Local";
    }
}
