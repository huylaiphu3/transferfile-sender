package com.transferfile;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VisualModemSender extends JFrame {

    private final FrameEncoder encoder;
    private final int          totalFrames;

    private final ImagePanel imagePanel;
    private final JLabel     lblStatus   = new JLabel("Đang hiệu chỉnh...", SwingConstants.CENTER);
    private final JLabel     lblProgress = new JLabel("",                   SwingConstants.CENTER);
    private final JLabel     lblEta      = new JLabel("",                   SwingConstants.CENTER);
    private final JButton    btnPause    = new JButton("Tạm dừng");

    private Timer   timer;
    private int     currentFrame   = 0;
    private boolean paused         = false;
    private boolean calibrating    = true;
    private int     calibTicks     = 0;
    private long    startTimeMs    = 0;

    public VisualModemSender(byte[] data, String filename) {
        super("TransferFile — Visual Modem");

        Dimension screen    = Toolkit.getDefaultToolkit().getScreenSize();
        int       blockSize = Math.max(10, Math.min(18, (screen.height - 90) / Protocol.ROWS));

        this.encoder     = new FrameEncoder(data, filename, blockSize);
        this.totalFrames = encoder.totalFrames;

        int gridPx  = Protocol.COLS * blockSize;
        imagePanel  = new ImagePanel(gridPx);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        buildLayout();
        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        setBackground(Color.BLACK);
        setLayout(new BorderLayout(0, 0));

        // Grid centered on black background
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(Color.BLACK);
        center.add(imagePanel);
        add(center, BorderLayout.CENTER);

        // Status bar
        Font mono  = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        lblStatus  .setFont(mono); lblStatus  .setForeground(Color.WHITE);
        lblProgress.setFont(mono); lblProgress.setForeground(new Color(0, 220, 220));
        lblEta     .setFont(mono); lblEta     .setForeground(new Color(220, 220, 0));

        btnPause.addActionListener(e -> {
            paused = !paused;
            btnPause.setText(paused ? "Tiếp tục" : "Tạm dừng");
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
        bar.setBackground(new Color(25, 25, 25));
        bar.add(lblStatus);
        bar.add(lblProgress);
        bar.add(lblEta);
        bar.add(btnPause);
        add(bar, BorderLayout.SOUTH);
    }

    public void startTransmission() {
        imagePanel.show(encoder.encodeCalibration());
        lblStatus.setText("Mở receiver/index.html trên điện thoại — hiệu chỉnh 5 giây");
        timer = new Timer(Protocol.FRAME_DELAY_MS, e -> tick());
        timer.start();
    }

    private void tick() {
        if (paused) return;

        if (calibrating) {
            if (++calibTicks >= Protocol.CALIBRATION_TICKS) {
                calibrating  = false;
                startTimeMs  = System.currentTimeMillis();
                currentFrame = 0;
            }
            return;
        }

        int idx = currentFrame % totalFrames;
        imagePanel.show(encoder.encodeFrame(idx));

        int    pass    = currentFrame / totalFrames + 1;
        double pct     = (idx * 100.0) / totalFrames;
        long   elapsed = System.currentTimeMillis() - startTimeMs;
        String eta     = buildEta(idx, elapsed);

        lblStatus  .setText(String.format("Vòng %d  |  Frame %d / %d", pass, idx + 1, totalFrames));
        lblProgress.setText(String.format("%.1f%%", pct));
        lblEta     .setText(eta);

        currentFrame++;
    }

    private String buildEta(int idx, long elapsedMs) {
        if (idx == 0 || elapsedMs < 500) return "";
        double msPerFrame = (double) elapsedMs / currentFrame;
        double remainMs   = msPerFrame * (totalFrames - idx);
        if (remainMs < 60_000)
            return String.format("còn ~%.0f giây", remainMs / 1000);
        return String.format("còn ~%.1f phút", remainMs / 60_000);
    }

    // ── Inner panel ─────────────────────────────────────────────────────────

    private static class ImagePanel extends JPanel {
        private BufferedImage image;
        private final int     size;

        ImagePanel(int size) {
            this.size = size;
            setBackground(Color.BLACK);
        }

        void show(BufferedImage img) { this.image = img; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) g.drawImage(image, 0, 0, null);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
        @Override public Dimension getMinimumSize()   { return getPreferredSize(); }
    }
}
