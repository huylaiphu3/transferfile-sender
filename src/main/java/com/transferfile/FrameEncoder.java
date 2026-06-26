package com.transferfile;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class FrameEncoder {

    private final byte[] fileData;
    private final String filename;
    public  final int    totalFrames;
    public  final int    fileCRC;
    private final int    blockSize;

    public FrameEncoder(byte[] fileData, String filename, int blockSize) {
        this.fileData    = fileData;
        this.filename    = filename;
        this.blockSize   = blockSize;
        this.totalFrames = (int) Math.ceil((double) fileData.length / Protocol.DATA_BYTES_PER_FRAME);
        this.fileCRC     = (int) crc32of(fileData, 0, fileData.length);
    }

    /** Encode one data frame as a BufferedImage. */
    public BufferedImage encodeFrame(int frameIndex) {
        int dataStart = frameIndex * Protocol.DATA_BYTES_PER_FRAME;
        int dataEnd   = Math.min(dataStart + Protocol.DATA_BYTES_PER_FRAME, fileData.length);
        int dataLen   = dataEnd - dataStart;

        byte[] frameData = new byte[Protocol.DATA_BYTES_PER_FRAME];
        System.arraycopy(fileData, dataStart, frameData, 0, dataLen);

        int frameCRC = (int) crc32of(frameData, 0, dataLen);
        byte[] header = buildHeader(frameIndex, dataLen, frameCRC);

        BufferedImage img = newCanvas();
        drawSyncRow(img);
        drawBytes(img, Protocol.HEADER_ROW_START, header,   Protocol.HEADER_BYTES);
        drawBytes(img, Protocol.DATA_ROW_START,   frameData, Protocol.DATA_BYTES_PER_FRAME);
        return img;
    }

    /** Calibration frame: checkerboard + solid corner markers. */
    public BufferedImage encodeCalibration() {
        BufferedImage img = newCanvas();
        for (int r = 0; r < Protocol.ROWS; r++)
            for (int c = 0; c < Protocol.COLS; c++)
                drawBlock(img, r, c, (r + c) % 8);

        // Corner markers 5×5 — TL=Red, TR=Green, BL=Blue, BR=Yellow
        fillRect(img, 0,                   0,                   5, 5, 2);
        fillRect(img, 0,                   Protocol.COLS - 5,   5, 5, 3);
        fillRect(img, Protocol.ROWS - 5,   0,                   5, 5, 4);
        fillRect(img, Protocol.ROWS - 5,   Protocol.COLS - 5,   5, 5, 5);
        return img;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private byte[] buildHeader(int frameIndex, int dataLen, int frameCRC) {
        byte[] h   = new byte[Protocol.HEADER_BYTES];
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.BIG_ENDIAN);
        b.putInt(frameIndex);
        b.putInt(totalFrames);
        b.putLong(fileData.length);
        b.putInt(frameCRC);
        b.putInt(fileCRC);
        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);
        int    nameLen   = Math.min(nameBytes.length, 32);
        b.put((byte) nameLen);
        b.put(nameBytes, 0, nameLen);
        b.position(Protocol.HDR_DATA_LEN);
        b.putInt(dataLen);
        return h;
    }

    private void drawSyncRow(BufferedImage img) {
        for (int c = 0; c < Protocol.COLS; c++)
            drawBlock(img, Protocol.SYNC_ROW, c, c % 2);
    }

    private void drawBytes(BufferedImage img, int startRow, byte[] data, int byteCount) {
        int row = startRow, col = 0;
        int totalBits = byteCount * 8;
        for (int off = 0; off + 2 < totalBits; off += 3) {
            drawBlock(img, row, col, extractBits(data, off));
            if (++col >= Protocol.COLS) { col = 0; row++; }
            if (row >= Protocol.ROWS) break;
        }
    }

    /** Extract 3 bits MSB-first from a byte array at bitOffset. */
    private static int extractBits(byte[] data, int bitOffset) {
        int result = 0;
        for (int i = 0; i < 3; i++) {
            int byteIdx = (bitOffset + i) / 8;
            int bitIdx  = 7 - ((bitOffset + i) % 8);
            int bit     = (byteIdx < data.length) ? ((data[byteIdx] >> bitIdx) & 1) : 0;
            result      = (result << 1) | bit;
        }
        return result;
    }

    private void drawBlock(BufferedImage img, int row, int col, int colorIdx) {
        int rgb = Protocol.COLOR_RGB[colorIdx & 7];
        int x   = col * blockSize;
        int y   = row * blockSize;
        for (int py = y; py < y + blockSize; py++)
            for (int px = x; px < x + blockSize; px++)
                img.setRGB(px, py, rgb);
    }

    private void fillRect(BufferedImage img, int r0, int c0, int rCount, int cCount, int color) {
        for (int r = r0; r < r0 + rCount; r++)
            for (int c = c0; c < c0 + cCount; c++)
                drawBlock(img, r, c, color);
    }

    private BufferedImage newCanvas() {
        return new BufferedImage(
            Protocol.COLS * blockSize,
            Protocol.ROWS * blockSize,
            BufferedImage.TYPE_INT_RGB
        );
    }

    private static long crc32of(byte[] data, int off, int len) {
        CRC32 crc = new CRC32();
        crc.update(data, off, len);
        return crc.getValue();
    }
}
