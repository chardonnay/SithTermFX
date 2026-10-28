package com.sithtermfx.core.graphics;

import org.jetbrains.annotations.NotNull;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Turns the data of a transmission into ARGB pixels: raw 24-bit RGB ({@code f=24}), raw 32-bit RGBA
 * ({@code f=32}) or PNG ({@code f=100}), each of them optionally zlib-compressed ({@code o=z}). Every size is
 * checked before anything of it is allocated, so a transmission cannot make the terminal hold more than the
 * limits it is given.
 */
final class KittyImageDecoder {

    static final int FORMAT_RGB = 24;

    static final int FORMAT_RGBA = 32;

    static final int FORMAT_PNG = 100;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private KittyImageDecoder() {
    }

    static final class Decoded {

        final int width;

        final int height;

        final int @NotNull [] pixels;

        Decoded(int width, int height, int @NotNull [] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    /**
     * @param width        the width a raw image was sent with ({@code s}); a PNG image has its own
     * @param height       the height a raw image was sent with ({@code v})
     * @param data         the data, its first {@code length} bytes
     * @param maxDimension the largest width and height accepted
     * @param maxBytes     the most bytes the decoded pixels may take, 4 per pixel
     */
    static @NotNull Decoded decode(int format, boolean compressed, int width, int height, byte @NotNull [] data,
                                   int length, int maxDimension, long maxBytes) throws KittyGraphicsException {
        switch (format) {
            case FORMAT_RGB:
            case FORMAT_RGBA:
                return decodeRaw(format / 8, compressed, width, height, data, length, maxDimension, maxBytes);
            case FORMAT_PNG:
                if (!compressed) {
                    return decodePng(data, length, maxDimension, maxBytes);
                }
                byte[] png = inflate(data, length, maxBytes + 1);
                if (png.length > maxBytes) {
                    throw new KittyGraphicsException("EFBIG", "The PNG data is too large");
                }
                return decodePng(png, png.length, maxDimension, maxBytes);
            default:
                throw new KittyGraphicsException("EINVAL", "Unknown image format: " + format);
        }
    }

    private static @NotNull Decoded decodeRaw(int bytesPerPixel, boolean compressed, int width, int height,
                                              byte @NotNull [] data, int length, int maxDimension, long maxBytes)
            throws KittyGraphicsException {
        checkSize(width, height, maxDimension, maxBytes);
        int expected = width * height * bytesPerPixel;
        byte[] raw = compressed ? inflate(data, length, expected) : data;
        int rawLength = compressed ? raw.length : length;
        if (rawLength < expected) {
            throw new KittyGraphicsException("ENODATA", "Insufficient image data: " + rawLength + " < " + expected);
        }
        int[] pixels = new int[width * height];
        for (int i = 0, offset = 0; i < pixels.length; i++, offset += bytesPerPixel) {
            int alpha = bytesPerPixel == 4 ? raw[offset + 3] & 0xFF : 0xFF;
            pixels[i] = alpha << 24 | (raw[offset] & 0xFF) << 16 | (raw[offset + 1] & 0xFF) << 8 | raw[offset + 2] & 0xFF;
        }
        return new Decoded(width, height, pixels);
    }

    private static @NotNull Decoded decodePng(byte @NotNull [] png, int length, int maxDimension, long maxBytes)
            throws KittyGraphicsException {
        if (length < PNG_SIGNATURE.length
                || !Arrays.equals(png, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            throw new KittyGraphicsException("EBADPNG", "Not a PNG image");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new KittyGraphicsException("EBADPNG", "No PNG decoder is available");
        }
        ImageReader reader = readers.next();
        // a memory cache: the default stream of ImageIO.read() may cache in a temporary file
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(png, 0, length))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            checkSize(width, height, maxDimension, maxBytes);
            BufferedImage image = reader.read(0);
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            return new Decoded(width, height, pixels);
        } catch (IOException | RuntimeException e) {
            throw new KittyGraphicsException("EBADPNG", "Failed to decode the PNG image");
        } finally {
            reader.dispose();
        }
    }

    private static void checkSize(int width, int height, int maxDimension, long maxBytes)
            throws KittyGraphicsException {
        if (width <= 0 || height <= 0) {
            throw new KittyGraphicsException("EINVAL", "The image width and height must be given");
        }
        if (width > maxDimension || height > maxDimension || 4L * width * height > maxBytes) {
            throw new KittyGraphicsException("EFBIG", "The image is too large: " + width + "x" + height);
        }
    }

    /**
     * @return the zlib (RFC 1950) inflated first {@code length} bytes of the data, at most {@code limit} bytes
     */
    private static byte @NotNull [] inflate(byte @NotNull [] data, int length, long limit) throws KittyGraphicsException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data, 0, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(limit, 4L * length + 64));
            byte[] buffer = new byte[64 * 1024];
            while (!inflater.finished() && out.size() < limit) {
                int count = inflater.inflate(buffer, 0, (int) Math.min(buffer.length, limit - out.size()));
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new KittyGraphicsException("EINVAL", "The compressed data is truncated");
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new KittyGraphicsException("EINVAL", "The compressed data is not zlib data");
        } finally {
            inflater.end();
        }
    }
}
