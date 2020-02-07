package uk.ac.manchester.tornado.examples.compute;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

public class Julia extends JPanel {
    private final int maxIter = 10;
    private final double zoom = 2;
    private double cY,cX;

    public Julia() {
        setPreferredSize(new Dimension(819, 819));
        setBackground(Color.white);
    }

    void drawJuliaSet(Graphics2D g) {
        int w = getWidth();
        int h = getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        cX = -0.7;
        cY = 0.27015;
        double moveX = 0,moveY = 0;
        double zx,zy;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                zx = 1.5 * (x - w / 2) / (0.5 * zoom * w) + moveX;
                zy = (y - h / 2) / (0.5 * zoom * h) + moveY;
                float i = maxIter;
                while (zx * zx + zy * zy < 4 && i > 0) {
                    double tmp = zx * zx - zy * zy + cX;
                    zy = 2.0 * zx * zy + cY;
                    zx = tmp;
                    i--;
                }
                int c = Color.HSBtoRGB((maxIter / i) % 1, 1, i > 0 ? 1 : 0);
                image.setRGB(x, y, c);
            }
        }
        g.drawImage(image, 0, 0, null);
    }

    @Override
    public void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawJuliaSet(g);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setTitle("Julia Set");
            f.setResizable(false);
            f.add(new Julia(), BorderLayout.CENTER);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
