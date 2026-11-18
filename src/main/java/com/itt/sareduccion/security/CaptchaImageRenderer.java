package com.itt.sareduccion.security;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Dibuja el captcha visual y lo codifica como PNG sin usar AWT ni librerias
 * externas. La imagen del backend (JRE Alpine) no incluye fuentes, por lo que
 * {@code java.awt} no es confiable ahi: se usa una fuente de mapa de bits propia
 * y un codificador PNG minimo (zlib + CRC32 del JDK).
 */
final class CaptchaImageRenderer {

    /** Caracteres sin pares confusos (se omiten 0/O, 1/I, 2/Z, 5/S, 8/B, G/6, Q). */
    static final String ALPHABET = "ACDEFHJKLMNPRTUVWXY3479";

    private static final int WIDTH = 220;
    private static final int HEIGHT = 80;
    private static final int SCALE = 6;
    private static final int GLYPH_COLS = 5;
    private static final int GLYPH_ROWS = 7;
    private static final Map<Character, String[]> FONT = new HashMap<>();

    static {
        FONT.put('A', new String[] {"01110", "10001", "10001", "11111", "10001", "10001", "10001"});
        FONT.put('C', new String[] {"01110", "10001", "10000", "10000", "10000", "10001", "01110"});
        FONT.put('D', new String[] {"11110", "10001", "10001", "10001", "10001", "10001", "11110"});
        FONT.put('E', new String[] {"11111", "10000", "10000", "11110", "10000", "10000", "11111"});
        FONT.put('F', new String[] {"11111", "10000", "10000", "11110", "10000", "10000", "10000"});
        FONT.put('H', new String[] {"10001", "10001", "10001", "11111", "10001", "10001", "10001"});
        FONT.put('J', new String[] {"00111", "00010", "00010", "00010", "00010", "10010", "01100"});
        FONT.put('K', new String[] {"10001", "10010", "10100", "11000", "10100", "10010", "10001"});
        FONT.put('L', new String[] {"10000", "10000", "10000", "10000", "10000", "10000", "11111"});
        FONT.put('M', new String[] {"10001", "11011", "10101", "10101", "10001", "10001", "10001"});
        FONT.put('N', new String[] {"10001", "11001", "10101", "10011", "10001", "10001", "10001"});
        FONT.put('P', new String[] {"11110", "10001", "10001", "11110", "10000", "10000", "10000"});
        FONT.put('R', new String[] {"11110", "10001", "10001", "11110", "10100", "10010", "10001"});
        FONT.put('T', new String[] {"11111", "00100", "00100", "00100", "00100", "00100", "00100"});
        FONT.put('U', new String[] {"10001", "10001", "10001", "10001", "10001", "10001", "01110"});
        FONT.put('V', new String[] {"10001", "10001", "10001", "10001", "10001", "01010", "00100"});
        FONT.put('W', new String[] {"10001", "10001", "10001", "10101", "10101", "11011", "10001"});
        FONT.put('X', new String[] {"10001", "10001", "01010", "00100", "01010", "10001", "10001"});
        FONT.put('Y', new String[] {"10001", "10001", "01010", "00100", "00100", "00100", "00100"});
        FONT.put('3', new String[] {"11110", "00001", "00001", "01110", "00001", "00001", "11110"});
        FONT.put('4', new String[] {"00010", "00110", "01010", "10010", "11111", "00010", "00010"});
        FONT.put('7', new String[] {"11111", "00001", "00010", "00100", "01000", "01000", "01000"});
        FONT.put('9', new String[] {"01110", "10001", "10001", "01111", "00001", "00010", "01100"});
    }

    private CaptchaImageRenderer() {
    }

    static byte[] render(String text, Random random) {
        int[] pixels = new int[WIDTH * HEIGHT];
        int background = rgb(244 + random.nextInt(10), 244 + random.nextInt(10), 240 + random.nextInt(12));
        java.util.Arrays.fill(pixels, background);

        // Ruido de fondo: puntos claros que no comprometen el contraste del texto.
        for (int i = 0; i < 260; i++) {
            int shade = 170 + random.nextInt(60);
            set(pixels, random.nextInt(WIDTH), random.nextInt(HEIGHT), rgb(shade, shade, shade));
        }

        int slot = (WIDTH - 20) / text.length();
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = FONT.get(text.charAt(i));
            if (glyph == null) {
                throw new IllegalArgumentException("Caracter sin glifo: " + text.charAt(i));
            }
            int color = rgb(random.nextInt(70), random.nextInt(70), 40 + random.nextInt(90));
            int originX = 10 + i * slot + random.nextInt(Math.max(1, slot - GLYPH_COLS * SCALE));
            int originY = 8 + random.nextInt(HEIGHT - GLYPH_ROWS * SCALE - 16);
            double shear = (random.nextDouble() - 0.5) * 0.5;
            drawGlyph(pixels, glyph, originX, originY, shear, color);
        }

        // Lineas que cruzan el texto para dificultar la segmentacion automatica.
        for (int i = 0; i < 4; i++) {
            int color = rgb(90 + random.nextInt(80), 90 + random.nextInt(80), 110 + random.nextInt(80));
            drawLine(pixels, random.nextInt(WIDTH / 4), random.nextInt(HEIGHT),
                    WIDTH - 1 - random.nextInt(WIDTH / 4), random.nextInt(HEIGHT), color);
        }
        return encodePng(pixels, WIDTH, HEIGHT);
    }

    private static void drawGlyph(int[] pixels, String[] glyph, int originX, int originY, double shear, int color) {
        int glyphHeight = GLYPH_ROWS * SCALE;
        for (int row = 0; row < glyphHeight; row++) {
            String bits = glyph[row / SCALE];
            int shift = (int) Math.round(shear * (glyphHeight / 2.0 - row));
            for (int col = 0; col < GLYPH_COLS * SCALE; col++) {
                if (bits.charAt(col / SCALE) == '1') {
                    set(pixels, originX + col + shift, originY + row, color);
                }
            }
        }
    }

    private static void drawLine(int[] pixels, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            set(pixels, x, y, color);
            set(pixels, x, y + 1, color);
        }
    }

    private static void set(int[] pixels, int x, int y, int color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            pixels[y * WIDTH + x] = color;
        }
    }

    private static int rgb(int red, int green, int blue) {
        return (Math.min(255, red) << 16) | (Math.min(255, green) << 8) | Math.min(255, blue);
    }

    /** PNG de 8 bits RGB sin entrelazado. */
    static byte[] encodePng(int[] pixels, int width, int height) {
        byte[] raw = new byte[height * (1 + width * 3)];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            raw[offset++] = 0; // filtro "None"
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                raw[offset++] = (byte) (pixel >> 16);
                raw[offset++] = (byte) (pixel >> 8);
                raw[offset++] = (byte) pixel;
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.length / 4);
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();

        ByteArrayOutputStream png = new ByteArrayOutputStream(compressed.size() + 64);
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        png.write(signature, 0, signature.length);
        byte[] header = new byte[13];
        writeInt(header, 0, width);
        writeInt(header, 4, height);
        header[8] = 8;  // profundidad de bits
        header[9] = 2;  // color verdadero RGB
        writeChunk(png, "IHDR", header);
        writeChunk(png, "IDAT", compressed.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] length = new byte[4];
        writeInt(length, 0, data.length);
        out.write(length, 0, 4);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeInt(crcBytes, 0, (int) crc.getValue());
        out.write(crcBytes, 0, 4);
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
