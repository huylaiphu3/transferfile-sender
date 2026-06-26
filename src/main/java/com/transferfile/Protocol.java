package com.transferfile;

public final class Protocol {
    private Protocol() {}

    public static final int COLS = 80;
    public static final int ROWS = 80;

    public static final int SYNC_ROW         = 0;
    public static final int HEADER_ROW_START = 1;
    public static final int HEADER_ROWS      = 3;   // rows 1-3  → 72 bytes
    public static final int DATA_ROW_START   = 4;   // rows 4-63
    public static final int DATA_ROWS        = ROWS - DATA_ROW_START; // 60

    public static final int BITS_PER_BLOCK       = 3;
    public static final int HEADER_BYTES         = (HEADER_ROWS * COLS * BITS_PER_BLOCK) / 8; // 72
    public static final int DATA_BYTES_PER_FRAME = (DATA_ROWS  * COLS * BITS_PER_BLOCK) / 8; // 1440

    public static final int TARGET_FPS        = 30;
    public static final int FRAME_DELAY_MS    = 1000 / TARGET_FPS; // 33 ms
    public static final int CALIBRATION_TICKS = TARGET_FPS * 5;    // 5 seconds

    public static final int[] COLOR_RGB = {
        0x000000, // 0 Black
        0xFFFFFF, // 1 White
        0xFF0000, // 2 Red
        0x00CC00, // 3 Green
        0x0000FF, // 4 Blue
        0xFFFF00, // 5 Yellow
        0x00FFFF, // 6 Cyan
        0xFF00FF, // 7 Magenta
    };

    // ── LT-codes header layout (72 bytes) ─────────────────────────────────────
    public static final int HDR_SEED            = 0;  // int  (4) — packet seed
    public static final int HDR_K               = 4;  // int  (4) — total source blocks
    public static final int HDR_FILE_SIZE       = 8;  // long (8)
    public static final int HDR_FILE_CRC        = 16; // int  (4)
    public static final int HDR_PACKET_CRC      = 20; // int  (4) — CRC32 of XOR payload
    public static final int HDR_NAME_LEN        = 24; // byte (1)
    public static final int HDR_NAME            = 25; // 32 bytes
    public static final int HDR_LAST_BLOCK_SIZE = 57; // int  (4) — bytes in last source block
    // bytes 61-71: reserved
}
