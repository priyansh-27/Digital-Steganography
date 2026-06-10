package steg;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 
 * - Embed text or file (with optional AES encryption).
 * - Extract back (auto detect text/file).
 *
 * Slightly hardened version: safer validation when extracting to avoid
 * BufferUnderflow and clearer errors for malformed/no-data images.
 */
public class Steganography {

    // =====================
    // Extraction result
    // =====================
    public static class ExtractionResult {
        public boolean isFile;
        public String filename;
        public byte[] data;
    }

    // =====================
    // Public API
    // =====================

    /**
     * Get max payload (in bytes) possible in this image.
     */
    public static long getMaxPayloadBytes(BufferedImage img) {
        long w = img.getWidth();
        long h = img.getHeight();
        long bits = w * h * 3L; // 3 color channels per pixel
        return bits / 8L;
    }

    /**
     * Embed secret data into image.
     *
     * @param cover       Cover image
     * @param secretBytes Secret data
     * @param hiddenFile  Filename if file (path or name), otherwise null
     * @param usePassword Use AES
     * @param password    Password
     * @return Stego image
     */
    public static BufferedImage embed(BufferedImage cover,
                                      byte[] secretBytes,
                                      String hiddenFile,
                                      boolean usePassword,
                                      String password) throws Exception {

        // If it's a file, compress into zip (we replace secretBytes with zip bytes)
        String filenameOnly = null;
        if (hiddenFile != null) {
            File f = new File(hiddenFile);
            // keep only file name (not full path)
            filenameOnly = f.getName();
            secretBytes = compressToZip(f);
        }

        // Encrypt if password
        if (usePassword && password != null) {
            secretBytes = AESUtil.encrypt(secretBytes, password);
        }

        // Header format:
        // [4 bytes length][1 byte flag][4 bytes filenameLen][filenameBytes...][data]
        // flag: 0=text, 1=file, +128 if encrypted
        byte flag = (byte) (filenameOnly != null ? 1 : 0);
        if (usePassword && password != null) flag |= 0x80;

        byte[] filenameBytes = (filenameOnly != null)
                ? filenameOnly.getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        ByteBuffer header = ByteBuffer.allocate(4 + 1 + 4 + filenameBytes.length);
        header.putInt(secretBytes.length);
        header.put(flag);
        header.putInt(filenameBytes.length);
        header.put(filenameBytes);

        byte[] headerBytes = header.array();
        byte[] payload = new byte[headerBytes.length + secretBytes.length];
        System.arraycopy(headerBytes, 0, payload, 0, headerBytes.length);
        System.arraycopy(secretBytes, 0, payload, headerBytes.length, secretBytes.length);

        if (payload.length > getMaxPayloadBytes(cover)) {
            throw new IllegalArgumentException("Payload too large for this image");
        }

        BufferedImage stego = new BufferedImage(cover.getWidth(), cover.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        int w = cover.getWidth();
        int h = cover.getHeight();

        int byteIndex = 0;
        int bitIndex = 0;

        outer:
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = cover.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb) & 0xFF;

                int[] channels = {r, g, b};
                for (int i = 0; i < 3; i++) {
                    if (byteIndex >= payload.length) break;
                    int bit = (payload[byteIndex] >> (7 - bitIndex)) & 1;
                    channels[i] = (channels[i] & 0xFE) | bit;
                    bitIndex++;
                    if (bitIndex == 8) {
                        bitIndex = 0;
                        byteIndex++;
                    }
                    if (byteIndex >= payload.length) break;
                }

                int newRgb = (a << 24) | (channels[0] << 16) | (channels[1] << 8) | channels[2];
                stego.setRGB(x, y, newRgb);

                if (byteIndex >= payload.length) {
                    // copy remaining pixels unchanged
                    for (int yy = y; yy < h; yy++) {
                        for (int xx = (yy == y ? x + 1 : 0); xx < w; xx++) {
                            stego.setRGB(xx, yy, cover.getRGB(xx, yy));
                        }
                    }
                    break outer;
                }
            }
        }

        return stego;
    }

    /**
     * Extract secret from stego image.
     */
    public static ExtractionResult extract(BufferedImage stego, String password) throws Exception {
        int w = stego.getWidth();
        int h = stego.getHeight();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int byteVal = 0;
        int bitCount = 0;

        // Read raw bits back into bos
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = stego.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb) & 0xFF;

                int[] channels = {r, g, b};
                for (int c : channels) {
                    int bit = c & 1;
                    byteVal = (byteVal << 1) | bit;
                    bitCount++;
                    if (bitCount == 8) {
                        bos.write(byteVal);
                        byteVal = 0;
                        bitCount = 0;
                    }
                }
            }
        }

        byte[] all = bos.toByteArray();

        // Basic validation: must have at least header base size: 4 (len) +1 (flag) +4 (filenameLen)
        int minHeader = 4 + 1 + 4;
        if (all.length < minHeader) {
            throw new IllegalArgumentException("No hidden data found (stream too short).");
        }

        ByteBuffer buffer = ByteBuffer.wrap(all);

        // Safely read header fields with validation
        int length;
        byte flag;
        int filenameLen;
        try {
            length = buffer.getInt();
            flag = buffer.get();
            filenameLen = buffer.getInt();
        } catch (Exception e) {
            throw new IllegalArgumentException("No valid hidden header found.");
        }

        if (length < 0) {
            throw new IllegalArgumentException("Invalid payload length in hidden data.");
        }
        if (filenameLen < 0 || filenameLen > 10_000) { // arbitrary upper cap for sanity
            throw new IllegalArgumentException("Invalid filename length in hidden data.");
        }

        // Now ensure there's enough bytes left for filename + payload
        if (buffer.remaining() < filenameLen + length) {
            throw new IllegalArgumentException("Hidden data is truncated or malformed.");
        }

        byte[] filenameBytes = new byte[filenameLen];
        if (filenameLen > 0) buffer.get(filenameBytes);
        String filename = (filenameLen > 0 ? new String(filenameBytes, StandardCharsets.UTF_8) : null);

        byte[] payload = new byte[length];
        if (length > 0) buffer.get(payload);

        boolean isFile = (flag & 0x01) != 0;
        boolean encrypted = (flag & 0x80) != 0;

        if (encrypted) {
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("Data is encrypted. Please provide password.");
            }
            payload = AESUtil.decrypt(payload, password);
        }

        if (isFile) {
            return decompressFromZip(payload);
        } else {
            ExtractionResult res = new ExtractionResult();
            res.isFile = false;
            res.filename = null;
            res.data = payload;
            return res;
        }
    }

    // =====================
    // ZIP helpers
    // =====================
    private static byte[] compressToZip(File file) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);
            byte[] data = Files.readAllBytes(file.toPath());
            zos.write(data);
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        }
    }

    private static ExtractionResult decompressFromZip(byte[] zipBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes);
             ZipInputStream zis = new ZipInputStream(bais)) {

            ZipEntry entry = zis.getNextEntry();
            if (entry == null) throw new IOException("ZIP archive is empty");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = zis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            zis.closeEntry();

            ExtractionResult res = new ExtractionResult();
            res.isFile = true;
            res.filename = entry.getName();
            res.data = baos.toByteArray();
            return res;
        }
    }
}
