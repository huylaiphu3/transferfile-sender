package com.transferfile;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.zip.CRC32;

/**
 * Luby-Transform (LT) encoder with Robust Soliton degree distribution.
 *
 * Each call to nextFrame() produces a new random encoded packet.
 * The receiver only needs any K*(1+epsilon) packets — no specific frame
 * ordering required. Eliminated the "stuck waiting for last few frames" problem.
 *
 * PRNG: Xorshift32 — identical implementation in Java and JS receiver.
 */
public class LTEncoder {

    private final byte[][] sourceBlocks;
    public  final int      K;
    public  final int      lastBlockSize;
    public  final long     fileSize;
    public  final int      fileCRC;
    public  final String   filename;
    private final double[] cdf;          // RSD CDF, index 1..maxDegree
    private final int      blockSize;
    private       int      seedCounter = 1;

    public LTEncoder(byte[] fileData, String filename, int blockSize) {
        this.filename  = filename;
        this.blockSize = blockSize;
        this.fileSize  = fileData.length;
        this.fileCRC   = (int) crc32of(fileData, 0, fileData.length);

        this.K             = (int) Math.ceil((double) fileData.length / blockSize);
        this.lastBlockSize = fileData.length - (K - 1) * blockSize;

        // Split into K zero-padded source blocks
        this.sourceBlocks = new byte[K][blockSize];
        for (int i = 0; i < K; i++) {
            int start = i * blockSize;
            int len   = (i == K - 1) ? lastBlockSize : blockSize;
            System.arraycopy(fileData, start, sourceBlocks[i], 0, len);
        }

        this.cdf = computeRSD(K, 0.03, 0.5);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Encode the next LT packet as a visual frame. */
    public BufferedImage nextFrame() {
        int seed = seedCounter++;
        if (seed == 0) seed = seedCounter++; // xorshift32(0) = 0 — skip zero seed

        int   x1      = xorshift32(seed);
        int   degree  = sampleDegree(x1, cdf);
        int[] chosen  = selectBlocks(x1, degree, K);

        byte[] payload = new byte[blockSize];
        for (int bi : chosen) xorInto(payload, sourceBlocks[bi]);

        int    packetCRC = (int) crc32of(payload, 0, payload.length);
        byte[] header    = buildHeader(seed, packetCRC);

        BufferedImage img = newCanvas();
        drawSyncRow(img);
        drawBytes(img, Protocol.HEADER_ROW_START, header,  Protocol.HEADER_BYTES);
        drawBytes(img, Protocol.DATA_ROW_START,   payload, Protocol.DATA_BYTES_PER_FRAME);
        return img;
    }

    /** Calibration frame shown before transmission starts. */
    public BufferedImage calibrationFrame() {
        BufferedImage img = newCanvas();
        for (int r = 0; r < Protocol.ROWS; r++)
            for (int c = 0; c < Protocol.COLS; c++)
                drawBlock(img, r, c, (r + c) % 8);
        // Corner markers: TL=Red TR=Green BL=Blue BR=Yellow
        fillRect(img, 0,                   0,                   5, 5, 2);
        fillRect(img, 0,                   Protocol.COLS - 5,   5, 5, 3);
        fillRect(img, Protocol.ROWS - 5,   0,                   5, 5, 4);
        fillRect(img, Protocol.ROWS - 5,   Protocol.COLS - 5,   5, 5, 5);
        return img;
    }

    // ── LT math (static — shared logic with JS receiver) ─────────────────────

    /** Xorshift32 PRNG — must match JS receiver exactly. */
    public static int xorshift32(int x) {
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return x;
    }

    /** Sample degree from RSD CDF. Uses first xorshift output from seed. */
    public static int sampleDegree(int x, double[] cdf) {
        double u  = Integer.toUnsignedLong(x) / 4294967296.0; // [0, 1)
        int lo = 1, hi = cdf.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cdf[mid] < u) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Pick `degree` distinct source-block indices using xorshift from startX. */
    public static int[] selectBlocks(int startX, int degree, int K) {
        HashSet<Integer> seen = new HashSet<>(degree * 2);
        int[] result = new int[degree];
        int   count  = 0;
        int   x      = startX;
        while (count < degree) {
            x = xorshift32(x);
            int idx = Integer.remainderUnsigned(x, K);
            if (seen.add(idx)) result[count++] = idx;
        }
        return result;
    }

    /**
     * Robust Soliton Distribution CDF.
     * maxDegree is capped at min(K, 500) to bound table size.
     * Both Java and JS use this identical formula → same degree for same seed.
     */
    public static double[] computeRSD(int K, double c, double delta) {
        int    maxD = Math.min(K, 500);
        double R    = c * Math.sqrt(K) * Math.log((double) K / delta);
        int    KR   = Math.max(1, Math.min((int)(K / R), maxD));

        double[] prob = new double[maxD + 1];
        prob[1] = 1.0 / K;
        for (int d = 2; d <= maxD; d++)
            prob[d] = 1.0 / ((double) d * (d - 1));
        for (int d = 1; d < KR; d++)
            prob[d] += R / ((double) d * K);
        prob[KR] += R * Math.log(R / delta) / K;

        double sum = 0;
        for (int d = 1; d <= maxD; d++) sum += prob[d];

        double[] cdf = new double[maxD + 1]; // cdf[0]=0
        double cumul = 0;
        for (int d = 1; d <= maxD; d++) {
            cumul  += prob[d] / sum;
            cdf[d]  = cumul;
        }
        cdf[maxD] = 1.0;
        return cdf;
    }

    // ── Private image helpers ─────────────────────────────────────────────────

    private byte[] buildHeader(int seed, int packetCRC) {
        byte[] h = new byte[Protocol.HEADER_BYTES];
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.BIG_ENDIAN);
        b.putInt(seed);
        b.putInt(K);
        b.putLong(fileSize);
        b.putInt(fileCRC);
        b.putInt(packetCRC);
        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);
        int    nameLen   = Math.min(nameBytes.length, 32);
        b.put((byte) nameLen);
        b.put(nameBytes, 0, nameLen);
        b.position(Protocol.HDR_LAST_BLOCK_SIZE);
        b.putInt(lastBlockSize);
        return h;
    }

    private void drawSyncRow(BufferedImage img) {
        for (int c = 0; c < Protocol.COLS; c++)
            drawBlock(img, Protocol.SYNC_ROW, c, c % 2);
    }

    private void drawBytes(BufferedImage img, int startRow, byte[] data, int byteCount) {
        int row = startRow, col = 0;
        for (int off = 0; off + 2 < byteCount * 8; off += 3) {
            drawBlock(img, row, col, extractBits(data, off));
            if (++col >= Protocol.COLS) { col = 0; row++; }
            if (row >= Protocol.ROWS) break;
        }
    }

    private static int extractBits(byte[] data, int bitOffset) {
        int result = 0;
        for (int i = 0; i < 3; i++) {
            int byteIdx = (bitOffset + i) / 8;
            int bitIdx  = 7 - ((bitOffset + i) % 8);
            int bit     = byteIdx < data.length ? ((data[byteIdx] >> bitIdx) & 1) : 0;
            result = (result << 1) | bit;
        }
        return result;
    }

    private void drawBlock(BufferedImage img, int row, int col, int colorIdx) {
        int rgb = Protocol.COLOR_RGB[colorIdx & 7];
        int x   = col * blockSize, y = row * blockSize;
        for (int py = y; py < y + blockSize; py++)
            for (int px = x; px < x + blockSize; px++)
                img.setRGB(px, py, rgb);
    }

    private void fillRect(BufferedImage img, int r0, int c0, int rN, int cN, int color) {
        for (int r = r0; r < r0 + rN; r++)
            for (int c = c0; c < c0 + cN; c++)
                drawBlock(img, r, c, color);
    }

    private BufferedImage newCanvas() {
        return new BufferedImage(
            Protocol.COLS * blockSize, Protocol.ROWS * blockSize, BufferedImage.TYPE_INT_RGB);
    }

    private static void xorInto(byte[] dest, byte[] src) {
        for (int i = 0; i < dest.length; i++) dest[i] ^= src[i];
    }

    private static long crc32of(byte[] data, int off, int len) {
        CRC32 crc = new CRC32(); crc.update(data, off, len); return crc.getValue();
    }
}
