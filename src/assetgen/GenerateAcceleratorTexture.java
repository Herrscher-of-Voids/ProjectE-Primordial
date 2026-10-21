/**
 * 【文件职责】以固定像素和调色板生成 EMC 加速器的 16×16 PNG，不使用抗锯齿或外部绘图库。
 * 【变更记录 #1】2026-09-16 13:42:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无；资源模型不再复用黑洞护符图标。
 * - 新增：深色表盘、金属边框、浅色指针和 EMC 核心的透明背景像素贴图。
 * - 修改：输出位置由项目根目录固定解析，ImageIO 写入标准 RGBA PNG。
 * - 用途：保持原生 16×16 风格并可通过同一生成代码复现资源；本类不进入模组运行时。
 */
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

public final class GenerateAcceleratorTexture {
    public static void main(String[] args) throws Exception {
        String[] pixels = {
            "......gggg......",
            "......gHHg......",
            "....gggHHggg....",
            "...gHHHHHHHHg...",
            "..gHHggggggHHg..",
            ".gHHgddddddgHHg.",
            ".gHgdddWddddgHg.",
            ".gHgdddWddddgHg.",
            ".gHgdddWddddgHg.",
            ".gHgdddCCCWWgHg.",
            ".gHgddddCdddgHg.",
            ".gHHgddddddgHHg.",
            "..gHHggggggHHg..",
            "...gHHHHHHHHg...",
            "....gggggggg....",
            "................"
        };
        Map<Character, Integer> palette = Map.of('.', 0x00000000, 'g', 0xFF62522D,
                'H', 0xFFE1C77C, 'd', 0xFF171421, 'W', 0xFFE2EDF3, 'C', 0xFFB05277);
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            if (pixels[y].length() != 16) throw new IllegalArgumentException("Invalid pixel row " + y);
            for (int x = 0; x < 16; x++) image.setRGB(x, y, palette.get(pixels[y].charAt(x)));
        }
        Path output = Path.of("src/main/resources/assets/primordial/textures/item/emc_accelerator.png");
        ImageIO.write(image, "png", output.toFile());
    }
}
