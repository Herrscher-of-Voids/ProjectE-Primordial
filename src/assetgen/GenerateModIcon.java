/**
 * 【文件职责】生成原初的独立像素徽记、模组列表图标及发布平台上传图，不进入游戏运行时。
 * 【变更记录 #1】2026-09-17 00:16:55（第 1 次备注 · 1.0.0-mod-icon）
 * - 删除：无；现有 16×16 物品和方块贴图保持原样。
 * - 新增：黑洞核心、炼金金属环、红青能量轨迹组成的 64×64 原始图案，透明 RGBA PNG 输出。
 * - 修改：模组内输出 256×256 logo.png，平台版输出 512×512；使用最近邻缩放保持像素边缘清晰。
 * - 用途：Forge/Prism 从 mods.toml 的 logoFile 读取图案；artwork 中的高清版可上传发布平台。
 *   build/artwork 下另存深浅背景及小尺寸预览，检查透明边缘和模组列表中的辨识度。
 */
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** 从项目根目录以 Java 17 源码模式运行，全部资源由同一份固定几何和调色板生成。 */
public final class GenerateModIcon {
    private static final int SIZE = 64;
    private static final int INK = 0xFF101319;
    private static final int METAL_DARK = 0xFF564536;
    private static final int GOLD = 0xFFCCAA66;
    private static final int GOLD_LIGHT = 0xFFF7E1A0;
    private static final int CORE = 0xFF080B10;
    private static final int RED_DARK = 0xFF852F4B;
    private static final int RED = 0xFFE6586A;
    private static final int RED_LIGHT = 0xFFFFB39B;
    private static final int CYAN_DARK = 0xFF206877;
    private static final int CYAN = 0xFF4ED0CE;
    private static final int CYAN_LIGHT = 0xFFCDFFF2;

    private GenerateModIcon() { }

    public static void main(String[] args) throws IOException {
        BufferedImage original = draw();
        write(scale(original, 256), Path.of("src/main/resources/logo.png"));
        write(scale(original, 512), Path.of("artwork/primordial-icon-512.png"));
        write(original, Path.of("build/artwork/primordial-icon-64.png"));
        write(preview(original), Path.of("build/artwork/primordial-icon-preview.png"));
    }

    private static BufferedImage draw() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            // 金属八角环提供稳定外轮廓；左上高光、右下阴影和无字刻度保持炼金器具的风格。
            octagon(g, INK, 4, 4, 56, 17);
            octagon(g, METAL_DARK, 6, 6, 52, 15);
            octagon(g, GOLD, 7, 7, 49, 14);
            polygon(g, GOLD_LIGHT, 21, 7, 42, 7, 49, 14, 46, 15, 40, 10, 22, 10, 10, 22, 10, 38, 7, 41, 7, 21);
            polygon(g, 0xFF8E7049, 55, 23, 56, 41, 41, 56, 23, 56, 21, 53, 40, 53, 53, 40, 53, 25);
            octagon(g, INK, 12, 12, 40, 11);
            octagon(g, 0xFF292B2D, 14, 14, 36, 10);
            octagon(g, 0xFF14191E, 16, 16, 32, 9);
            rect(g, GOLD_LIGHT, 30, 7, 4, 7);
            rect(g, GOLD, 30, 50, 4, 7);
            rect(g, GOLD_LIGHT, 7, 30, 7, 4);
            rect(g, GOLD, 50, 30, 7, 4);

            // 黑洞后侧轨迹先画，核心遮住中段，前侧轨迹最后覆盖，形成清楚的穿插层次。
            orbit(image, false);
            octagon(g, INK, 16, 16, 32, 9);
            octagon(g, CYAN_DARK, 18, 16, 29, 8);
            polygon(g, CYAN, 25, 17, 37, 17, 43, 23, 42, 25, 35, 20, 26, 20, 21, 25, 19, 25, 19, 23);
            polygon(g, CYAN_LIGHT, 26, 17, 34, 17, 34, 19, 26, 19, 22, 23, 20, 23);
            polygon(g, RED_DARK, 45, 26, 47, 28, 47, 36, 38, 45, 26, 45, 19, 38, 22, 35, 39, 25);
            polygon(g, RED, 44, 29, 46, 29, 46, 35, 37, 44, 28, 44, 26, 42, 36, 42, 44, 34);
            octagon(g, CORE, 22, 21, 20, 6);
            orbit(image, true);

            // 两端的方形光点是能量轨迹本身的一部分，缩小后仍保留红青方向信息。
            rect(g, INK, 7, 39, 6, 6);
            rect(g, CYAN, 8, 40, 4, 4);
            rect(g, CYAN_LIGHT, 8, 40, 2, 2);
            rect(g, INK, 51, 18, 6, 6);
            rect(g, RED, 52, 19, 4, 4);
            rect(g, RED_LIGHT, 52, 19, 2, 2);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** 在像素中心采样倾斜椭圆，分开前后半环，避免抗锯齿带来缩略图杂边。 */
    private static void orbit(BufferedImage image, boolean front) {
        double angle = Math.toRadians(-27);
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        for (int y = 8; y < 56; y++) for (int x = 4; x < 60; x++) {
            double dx = x + .5 - 32;
            double dy = y + .5 - 32;
            double u = cosine * dx + sine * dy;
            double v = -sine * dx + cosine * dy;
            if ((v >= 0) != front) continue;
            double outer = u * u / (28 * 28) + v * v / (10 * 10);
            double inner = u * u / (23 * 23) + v * v / (5 * 5);
            if (outer > 1 || inner < 1) continue;
            double rim = u * u / (27 * 27) + v * v / (9 * 9);
            boolean blue = u < 3;
            int color = rim > 1 ? INK : blue ? CYAN_DARK : RED_DARK;
            if (rim <= .92 && inner >= 1.6) color = blue ? CYAN : RED;
            if (front && v > 6 && v < 8 && Math.abs(u) < 20) color = blue ? CYAN_LIGHT : RED_LIGHT;
            image.setRGB(x, y, color);
        }
    }

    private static void octagon(Graphics2D g, int color, int x, int y, int size, int corner) {
        polygon(g, color, x + corner, y, x + size - corner, y, x + size, y + corner,
                x + size, y + size - corner, x + size - corner, y + size,
                x + corner, y + size, x, y + size - corner, x, y + corner);
    }

    private static void polygon(Graphics2D g, int color, int... points) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < points.length; i += 2) polygon.addPoint(points[i], points[i + 1]);
        g.setColor(new Color(color, true));
        g.fillPolygon(polygon);
    }

    private static void rect(Graphics2D g, int color, int x, int y, int width, int height) {
        g.setColor(new Color(color, true));
        g.fillRect(x, y, width, height);
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, size, size, null);
        } finally { g.dispose(); }
        return output;
    }

    private static BufferedImage preview(BufferedImage icon) {
        BufferedImage sheet = new BufferedImage(880, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            for (int row = 0; row < 2; row++) {
                int y = row * 200;
                rect(g, row == 0 ? 0xFF232326 : 0xFFF0F1F2, 0, y, 880, 200);
                int[] sizes = {160, 96, 64, 32, 24, 16};
                int[] positions = {28, 244, 420, 554, 658, 752};
                for (int i = 0; i < sizes.length; i++) {
                    int size = sizes[i];
                    g.drawImage(scale(icon, size), positions[i], y + 12 + (160 - size) / 2, null);
                    g.setColor(row == 0 ? new Color(0xC4C6CD) : new Color(0x41464F));
                    g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                    g.drawString(size + " px", positions[i], y + 187);
                }
            }
        } finally { g.dispose(); }
        return sheet;
    }

    private static void write(BufferedImage image, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        if (!ImageIO.write(image, "png", output.toFile())) throw new IOException("PNG writer unavailable: " + output);
        System.out.println(output + " (" + image.getWidth() + " x " + image.getHeight() + ")");
    }
}
