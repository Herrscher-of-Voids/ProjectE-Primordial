/** Checks optional JEI client hooks against the installed bytecode without loading client-only classes. */
package com.primordial.neoforge.compat.jei;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeJeiClientHookGameTests {
    private static final String MIXINS = "com/primordial/neoforge/mixin/";
    private NeoForgeJeiClientHookGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void jeiCountHooksReachInstalledRendererAndActiveOverlayEntry(GameTestHelper helper) throws IOException {
        if (!ModList.get().isLoaded("jei")) { helper.succeed(); return; }
        ClassNode rendererMixin = read(MIXINS + "NeoForgeJeiCountRendererMixin");
        AnnotationNode targets = annotation(rendererMixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        List<String> targetNames = strings(targets, "targets");
        ClassNode renderer = null;
        for (String target : targetNames) {
            ClassNode candidate = read(target.replace('.', '/'));
            if (candidate != null) renderer = candidate;
        }
        check(renderer != null, "The JEI count hook must target the installed renderer package");
        for (var field : rendererMixin.fields) {
            if (annotation(field.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null
                    && annotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null) continue;
            check(renderer.fields.stream().anyMatch(actual -> actual.name.equals(field.name) && actual.desc.equals(field.desc)),
                    "The JEI renderer must retain shadow field " + field.name);
        }
        check(renderer.methods.stream().anyMatch(method -> method.name.equals("render")
                && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;)V")), "The renderer hook signature must match JEI");
        String slotName = renderer.name.substring(0, renderer.name.lastIndexOf('/') + 1) + "IngredientListSlot";
        ClassNode slot = read(slotName);
        check(hasMethod(slot, "isBlocked", "()Z")
                && hasMethod(slot, "getElement", "()Lmezz/jei/gui/overlay/elements/IElement;")
                && hasMethod(slot, "getRenderArea", "()Lmezz/jei/common/util/ImmutableRect2i;"),
                "The displayed-count reader must match the installed slot API");

        ClassNode overlayMixin = read(MIXINS + "NeoForgeJeiCountOverlayMixin");
        List<String> hookedMethods = new ArrayList<>();
        for (var method : overlayMixin.methods) {
            AnnotationNode injection = annotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (injection == null) injection = annotation(method.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (injection != null) hookedMethods.addAll(strings(injection, "method"));
        }
        ClassNode events = read("mezz/jei/gui/events/GuiEventHandler");
        boolean activeEntryHooked = false;
        for (var method : events.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("mezz/jei/gui/overlay/IngredientListOverlay")
                        && (call.name.equals("drawScreen") || call.name.equals("drawForeground"))
                        && hookedMethods.contains(call.name)) activeEntryHooked = true;
            }
        }
        check(activeEntryHooked, "The EMC toggle must hook the render entry actually called by this JEI version");
        helper.succeed();
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = NeoForgeJeiClientHookGameTests.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) return null;
            var result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static boolean hasMethod(ClassNode type, String name, String descriptor) {
        return type != null && type.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return null;
        return annotations.stream().filter(value -> value.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static List<String> strings(AnnotationNode annotation, String key) {
        if (annotation != null && annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (key.equals(annotation.values.get(index)) && annotation.values.get(index + 1) instanceof List<?> values) {
                    return values.stream().map(String.class::cast).toList();
                }
            }
        }
        return List.of();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
