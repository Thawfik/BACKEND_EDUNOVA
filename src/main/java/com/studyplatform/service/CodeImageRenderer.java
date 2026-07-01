package com.studyplatform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Renders a code snippet to a PNG (editor-style: dark background, monospace font,
 * line numbers) and returns it as a base64 data URL. Used for CODE_IMAGE tournament
 * questions so the candidate sees the code as a real, non-copyable image and must
 * read and explain it. Runs headless (Spring sets java.awt.headless=true).
 */
@Service
@Slf4j
public class CodeImageRenderer {

    private static final int FONT_SIZE = 16;
    private static final int LINE_HEIGHT = 24;
    private static final int PADDING = 24;
    private static final int GUTTER_WIDTH = 48;
    private static final int MAX_WIDTH = 1100;

    private static final Color BG = new Color(0x1e, 0x29, 0x3b);       // slate-800
    private static final Color GUTTER_BG = new Color(0x16, 0x20, 0x32);
    private static final Color LINE_NUM = new Color(0x64, 0x74, 0x8b); // slate-500
    private static final Color CODE = new Color(0xe2, 0xe8, 0xf0);     // slate-200

    /**
     * @return a {@code data:image/png;base64,...} URL, or {@code null} if rendering failed
     *         (callers should fall back to keeping the raw snippet in that case).
     */
    public String renderToDataUrl(String code) {
        if (code == null) code = "";
        String[] lines = code.replace("\t", "    ").split("\n", -1);

        try {
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);

            // First pass: measure to size the canvas.
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(font);
            FontMetrics fm = pg.getFontMetrics();
            int maxLineWidth = 0;
            for (String line : lines) {
                maxLineWidth = Math.max(maxLineWidth, fm.stringWidth(line));
            }
            pg.dispose();

            int width = Math.min(MAX_WIDTH, GUTTER_WIDTH + PADDING + maxLineWidth + PADDING);
            width = Math.max(width, 320);
            int height = PADDING * 2 + lines.length * LINE_HEIGHT;

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background + gutter.
            g.setColor(BG);
            g.fillRect(0, 0, width, height);
            g.setColor(GUTTER_BG);
            g.fillRect(0, 0, GUTTER_WIDTH, height);

            g.setFont(font);
            int baseline = PADDING + fm.getAscent();
            for (int i = 0; i < lines.length; i++) {
                int y = baseline + i * LINE_HEIGHT;
                // Line number (right-aligned in the gutter).
                String num = String.valueOf(i + 1);
                g.setColor(LINE_NUM);
                g.drawString(num, GUTTER_WIDTH - 12 - fm.stringWidth(num), y);
                // Code.
                g.setColor(CODE);
                g.drawString(lines[i], GUTTER_WIDTH + PADDING, y);
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            log.warn("Failed to render code image: {}", e.getMessage());
            return null;
        }
    }
}
