package com.transferfile;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}

            // Chooser: file hoặc thư mục đều được
            JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
            fc.setDialogTitle("TransferFile — Chọn file hoặc thư mục");
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

            File selected = fc.getSelectedFile();
            if (!selected.exists()) {
                JOptionPane.showMessageDialog(null, "Không tìm thấy!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Hiện dialog loading nếu cần zip
            byte[] data;
            String transmitName;

            if (selected.isDirectory()) {
                JOptionPane pane = new JOptionPane("Đang nén thư mục...", JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION, null, new Object[]{});
                JDialog loadingDlg = pane.createDialog("Vui lòng chờ");
                SwingWorker<byte[], Void> worker = new SwingWorker<>() {
                    @Override protected byte[] doInBackground() throws Exception {
                        return zipDirectory(selected.toPath());
                    }
                };
                worker.execute();
                // Poll until done (simple approach — blocks EDT briefly but acceptable)
                byte[] zipped;
                try {
                    zipped = worker.get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Lỗi nén thư mục:\n" + ex.getMessage());
                    return;
                }
                data         = zipped;
                transmitName = selected.getName() + ".zip";
            } else {
                try (FileInputStream fis = new FileInputStream(selected)) {
                    data = fis.readAllBytes();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, "Lỗi đọc file:\n" + ex.getMessage());
                    return;
                }
                transmitName = selected.getName();
            }

            if (data.length == 0) {
                JOptionPane.showMessageDialog(null, "Dữ liệu rỗng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double mb     = data.length / 1_048_576.0;
            double estSec = data.length / (28.0 * 1024);
            String time   = estSec < 60
                ? String.format("%.0f giây", estSec)
                : String.format("%.1f phút", estSec / 60);

            String label = selected.isDirectory()
                ? String.format("Thư mục : %s\nFile zip : %s\nKích thước nén : %.2f MB\nThời gian  : ~%s",
                    selected.getName(), transmitName, mb, time)
                : String.format("File : %s\nKích thước : %.2f MB\nThời gian  : ~%s",
                    transmitName, mb, time);

            int ok = JOptionPane.showConfirmDialog(null, label + "\n\nBắt đầu truyền?",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;

            VisualModemSender sender = new VisualModemSender(data, transmitName);
            sender.setVisible(true);
            sender.startTransmission();
        });
    }

    /** Zip toàn bộ thư mục vào byte array (giữ nguyên cấu trúc path). */
    private static byte[] zipDirectory(Path root) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setLevel(Deflater.BEST_SPEED);
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Entry path relative to parent of root (so root folder name is preserved)
                    Path entryPath = root.getParent().relativize(file);
                    zos.putNextEntry(new ZipEntry(entryPath.toString().replace('\\', '/')));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(root)) {
                        Path entryPath = root.getParent().relativize(dir);
                        zos.putNextEntry(new ZipEntry(entryPath.toString().replace('\\', '/') + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return baos.toByteArray();
    }
}
