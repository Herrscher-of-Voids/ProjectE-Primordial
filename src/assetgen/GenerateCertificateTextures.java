/**
 * 【文件职责】用固定像素调色板生成九种资源凭证的 16×16 PNG。
 *
 * 【变更记录 #1】2026-09-16 16:32:00（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无；凭证尚未有独立图标资源。
 * - 新增：统一的金属凭证底板、边框、EMC 核心和九种资源颜色标记。
 * - 修改：所有像素由代码写入透明 PNG，不依赖外部绘图工具，确保每个物品分辨率固定为 16×16。
 * - 用途：为资源凭证提供可重复生成的物品贴图；本文件只在资源生成时运行，不进入模组运行时。
 */
/**
 * 【变更记录 #2】2026-09-16 19:46:19（第 2 次备注 · 1.0.0-review-ready）
 * - 删除：长度不一致的整张字符底图和仅靠颜色区分类型的标记。
 * - 新增：固定边框与九个独立7×7资源符号，使用16×16透明PNG输出。
 * - 修改：逐像素绘制底板及水滴、气流、能量等符号，避免字符行裁切导致图标不完整。
 * - 用途：模型引用各自图标，生成器可复现全部资源且不进入运行时。
 */
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

public final class GenerateCertificateTextures {
    private static final String[] IDS = {
            "fluid", "gas", "energy", "mana", "pressure", "aura", "source", "speed", "stress"
    };
    private static final int[] COLORS = {
            0xFF3D8DFF, 0xFFB77CFF, 0xFFFFC44D, 0xFF5BCB75, 0xFF7BD7FF,
            0xFFB4E36A, 0xFFB06CFF, 0xFFE0E0E0, 0xFFFF8A45
    };

    private GenerateCertificateTextures() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of("src/main/resources/assets/primordial/textures/item");
        Files.createDirectories(directory);
        for (int i = 0; i < IDS.length; i++) write(directory.resolve(IDS[i] + "_certificate.png"), COLORS[i], i);
    }

    private static void write(Path output, int accent, int symbol) throws Exception {
        String[][] icons = {
                {"...#...", "..###..", "..###..", ".#####.", "#######", ".#####.", "..###.."},
                {"..##...", ".#.....", "..###..", ".....#.", "..###..", ".#.....", "..##..."},
                {"...##..", "..##...", ".#####.", "...##..", "..##...", ".##....", ".#....."},
                {"...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#..."},
                {"..####.", ".#....#", "#...#.#", "#..#..#", "#.....#", ".#...#.", "..###.."},
                {".....#.", "...###.", ".#####.", ".####..", ".###...", "..#....", ".#....."},
                {"...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."},
                {".#..#..", "..#..#.", "...#..#", "...#..#", "...#..#", "..#..#.", ".#..#.."},
                {"..#.#..", ".#####.", "##...##", ".#.#.#.", "##...##", ".#####.", "..#.#.."}
        };
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 15; y++) for (int x = 2; x < 14; x++) {
            int color = x == 2 || x == 13 || y == 1 || y == 14 ? 0xFF62552F : 0xFF29282D;
            if (x == 3 || y == 2) color = 0xFFD9C67B;
            if (y == 12 && x >= 5 && x <= 10) color = 0xFF899598;
            image.setRGB(x, y, color);
        }
        for (int y = 0; y < 7; y++) for (int x = 0; x < 7; x++)
            if (icons[symbol][y].charAt(x) == '#') image.setRGB(x + 5, y + 4, accent);
        ImageIO.write(image, "png", output.toFile());
    }
}
