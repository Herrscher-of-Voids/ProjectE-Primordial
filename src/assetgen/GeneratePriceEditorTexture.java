import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Hand-drawn pixel price tag and stylus; standalone generator, excluded from the mod JAR. */
public final class GeneratePriceEditorTexture {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // Purple suspension cord behind the brass tag.
        polygon(g, 0x251D36, new int[]{19,23,28,31,31,28,24,21}, new int[]{5,1,1,4,8,11,9,7});
        polygon(g, 0xA88AD2, new int[]{21,24,27,29,29,27,24,23}, new int[]{5,3,3,5,7,9,7,6});
        polygon(g, 0x000000, new int[]{24,26,27,27,26,25}, new int[]{5,4,5,7,7,6});
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(25, 5, 3, 2);
        g.setComposite(java.awt.AlphaComposite.SrcOver);

        // Clipped tag silhouette, with a bright upper-left bevel and dark lower edge.
        polygon(g, 0x201C2A, new int[]{2,7,20,25,25,21,6,2}, new int[]{8,3,3,8,26,30,30,26});
        polygon(g, 0x83502E, new int[]{3,8,19,24,24,20,7,3}, new int[]{8,4,4,9,25,29,29,25});
        polygon(g, 0xEDC777, new int[]{3,8,19,23,23,19,7,3}, new int[]{8,4,4,8,24,28,28,24});
        polygon(g, 0xFFF0BA, new int[]{3,8,19,20,9,4}, new int[]{8,4,4,5,5,10});
        polygon(g, 0xAF793C, new int[]{4,8,19,22,22,19,8,4}, new int[]{10,6,6,9,23,26,26,23});
        polygon(g, 0x292D43, new int[]{5,9,18,21,21,18,9,5}, new int[]{10,7,7,10,22,25,25,22});
        g.setColor(new Color(0x41475F));
        g.fillRect(6, 11, 1, 10);
        g.fillRect(9, 8, 8, 1);
        g.setColor(new Color(0x83502E));
        g.fillRect(17, 5, 4, 4);
        g.setColor(new Color(0x201C2A));
        g.fillRect(18, 6, 2, 2);

        // Faceted EMC gem above two price-entry lines.
        polygon(g, 0x0B596C, new int[]{8,11,15,18,13}, new int[]{12,10,10,12,19});
        polygon(g, 0x25BEC8, new int[]{9,12,14,17,13}, new int[]{12,11,11,12,17});
        polygon(g, 0xA7F9E8, new int[]{9,12,14,13}, new int[]{12,11,11,14});
        polygon(g, 0x6EE5DF, new int[]{9,13,13}, new int[]{12,14,17});
        polygon(g, 0x158398, new int[]{13,17,13}, new int[]{14,12,17});
        g.setColor(new Color(0xF3D68D));
        g.fillRect(8, 21, 3, 2);
        g.fillRect(13, 21, 5, 1);
        g.setColor(new Color(0xAC8F66));
        g.fillRect(13, 23, 5, 1);

        // Separate diagonal pricing stylus with a pale metal nib.
        polygon(g, 0x201C2A, new int[]{17,18,27,29,31,29,20}, new int[]{30,25,14,14,16,19,30});
        polygon(g, 0x765294, new int[]{19,20,28,30,28,21}, new int[]{27,24,15,16,19,28});
        polygon(g, 0xC4A0E6, new int[]{20,28,29,21}, new int[]{24,15,16,25});
        polygon(g, 0xE8D29E, new int[]{18,20,22,20}, new int[]{29,25,27,29});
        g.setColor(new Color(0xFFF4D3));
        g.fillRect(19, 27, 1, 2);
        g.dispose();

        Path output = Path.of("src/main/resources/assets/primordial/textures/item/price_editor.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
    }

    private static void polygon(Graphics2D g, int color, int[] x, int[] y) {
        g.setColor(new Color(color));
        g.fillPolygon(x, y, x.length);
    }
}
