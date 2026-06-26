package com.transferfile;

public final class Protocol {
    private Protocol() {}

    public static final int COLS = 64;
    public static final int ROWS = 64;

    // Row layout per frame
    public static final int SYNC_ROW        = 0;
    public static final int HEADER_ROW_START = 1;
    public static final int HEADER_ROWS     = 3;   // rows 1-3
    public static final int DATA_ROW_START  = 4;   // rows 4-63
    public static final int DATA_ROWS       = ROWS - DATA_ROW_START; // 60

    public static final int BITS_PER_BLOCK       = 3;
    public static final int HEADER_BYTES         = (HEADER_ROWS * COLS * BITS_PER_BLOCK) / 8; // 72
    public static final int DATA_BYTES_PER_FRAME = (DATA_ROWS  * COLS * BITS_PER_BLOCK) / 8; // 1440

    public static final int TARGET_FPS        = 25;
    public static final int FRAME_DELAY_MS    = 1000 / TARGET_FPS; // 40 ms
    public static final int CALIBRATION_TICKS = TARGET_FPS * 5;    // 5 seconds

    // 8-color palette (3 bits / block) — pure primaries for camera robustness
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

    // Header byte offsets inside the 72-byte header region
    public static final int HDR_FRAME_INDEX  = 0;  // int  (4)
    public static final int HDR_TOTAL_FRAMES = 4;  // int  (4)
    public static final int HDR_FILE_SIZE    = 8;  // long (8)
    public static final int HDR_FRAME_CRC   = 16; // int  (4)
    public static final int HDR_FILE_CRC    = 20; // int  (4)
    public static final int HDR_NAME_LEN    = 24; // byte (1)
    public static final int HDR_NAME        = 25; // 32 bytes
    public static final int HDR_DATA_LEN    = 57; // int  (4)
    // 61 used, 11 reserved — total 72
}
