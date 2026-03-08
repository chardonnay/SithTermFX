package com.sithtermfx.core.emulator;

import org.jetbrains.annotations.NotNull;

/**
 * Defines the feature level within the DEC VT terminal family.
 *
 * @author Daniel Mengel
 */
public enum VtEmulationLevel {

    VT100(1, false, 2, 4, false, false, false),
    VT102(1, false, 2, 4, false, false, false),
    VT220(2, true, 4, 20, true, false, false),
    VT320(3, true, 4, 20, true, false, false),
    VT420(4, true, 4, 20, true, true, true),
    VT520(5, true, 4, 20, true, true, true);

    private final int level;
    private final boolean eightBitControls;
    private final int graphicSetCount;
    private final int functionKeyCount;
    private final boolean userDefinedKeys;
    private final boolean rectangularAreaOps;
    private final boolean leftRightMargins;

    VtEmulationLevel(int level, boolean eightBitControls, int graphicSetCount,
                     int functionKeyCount, boolean userDefinedKeys,
                     boolean rectangularAreaOps, boolean leftRightMargins) {
        this.level = level;
        this.eightBitControls = eightBitControls;
        this.graphicSetCount = graphicSetCount;
        this.functionKeyCount = functionKeyCount;
        this.userDefinedKeys = userDefinedKeys;
        this.rectangularAreaOps = rectangularAreaOps;
        this.leftRightMargins = leftRightMargins;
    }

    public int getLevel() {
        return level;
    }

    public boolean supports8BitControls() {
        return eightBitControls;
    }

    public int getGraphicSetCount() {
        return graphicSetCount;
    }

    public int getFunctionKeyCount() {
        return functionKeyCount;
    }

    public boolean supportsUserDefinedKeys() {
        return userDefinedKeys;
    }

    public boolean supportsRectangularAreaOps() {
        return rectangularAreaOps;
    }

    public boolean supportsLeftRightMargins() {
        return leftRightMargins;
    }

    public boolean isAtLeast(@NotNull VtEmulationLevel other) {
        return this.level >= other.level;
    }

    public static @NotNull VtEmulationLevel fromEmulationType(@NotNull EmulationType type) {
        return switch (type) {
            case VT100 -> VT100;
            case VT102 -> VT102;
            case VT220 -> VT220;
            case VT320 -> VT320;
            case VT420 -> VT420;
            case VT520 -> VT520;
            default -> VT100;
        };
    }
}
