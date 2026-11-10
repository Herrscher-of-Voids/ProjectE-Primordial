package com.primordial.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in real-JEI contract checks; reads bytecode without initializing Minecraft client classes. */
class JeiClientHookCompatibilityTest {
    @Test void countAndPurchaseHooksMatchProvidedJeiRuntimes() throws IOException {
        String supplied = System.getenv("PRIMORDIAL_TEST_JEI_JARS");
        assumeTrue(supplied != null && !supplied.isBlank(), "Provide JEI runtime jars via PRIMORDIAL_TEST_JEI_JARS");
        for (String path : supplied.split(Pattern.quote(java.io.File.pathSeparator))) {
            try (var jar = new JarFile(Path.of(path).toFile())) { checkRuntime(jar); }
        }
    }

    private static void checkRuntime(JarFile jar) throws IOException {
        ClassNode rendererMixin = ownClass("com/primordial/mixin/JeiCountRendererMixin");
        List<String> targets = strings(annotation(rendererMixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;"), "targets");
        ClassNode renderer = null;
        for (String target : targets) {
            var entry = jar.getJarEntry(target.replace('.', '/') + ".class");
            if (entry != null) try (var stream = jar.getInputStream(entry)) { renderer = read(stream); }
        }
        assertNotNull(renderer, jar.getName() + " has no renderer matching our mixin");
        for (var field : rendererMixin.fields) {
            if (annotation(field.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null
                    && annotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null) continue;
            assertTrue(renderer.fields.stream().anyMatch(actual -> actual.name.equals(field.name) && actual.desc.equals(field.desc)),
                    "JEI renderer must retain shadow field " + field.name);
        }
        assertMethod(renderer, "render", "(Lnet/minecraft/client/gui/GuiGraphics;)V");
        String slotPackage = renderer.name.substring(0, renderer.name.lastIndexOf('/') + 1);
        ClassNode slot = jarClass(jar, slotPackage + "IngredientListSlot");
        assertMethod(slot, "isBlocked", "()Z");
        assertMethod(slot, "getElement", "()Lmezz/jei/gui/overlay/elements/IElement;");
        assertMethod(slot, "getRenderArea", "()Lmezz/jei/common/util/ImmutableRect2i;");
        ClassNode rect = jarClass(jar, "mezz/jei/common/util/ImmutableRect2i");
        assertMethod(rect, "getX", "()I");
        assertMethod(rect, "getY", "()I");
        assertMethod(jarClass(jar, "mezz/jei/gui/overlay/elements/IElement"), "getTypedIngredient",
                "()Lmezz/jei/api/ingredients/ITypedIngredient;");
        ClassNode overlay = jarClass(jar, "mezz/jei/gui/overlay/IngredientListOverlay");
        assertMethod(overlay, "isListDisplayed", "()Z");
        assertMethod(overlay, "getIngredientUnderMouse", "()Ljava/util/Optional;");

        ClassNode overlayMixin = ownClass("com/primordial/mixin/JeiCountOverlayMixin");
        var selectors = new java.util.ArrayList<String>();
        for (var method : overlayMixin.methods) {
            var inject = annotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (inject == null) inject = annotation(method.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            selectors.addAll(strings(inject, "method"));
        }
        boolean hooked = false;
        for (var method : jarClass(jar, "mezz/jei/gui/events/GuiEventHandler").methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(overlay.name)
                        && (call.name.equals("drawScreen") || call.name.equals("drawForeground"))
                        && selectors.contains(call.name)) hooked = true;
            }
        }
        assertTrue(hooked, jar.getName() + " must actually call a hooked overlay render entry");
    }

    private static ClassNode ownClass(String name) throws IOException {
        try (var stream = JeiClientHookCompatibilityTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            return read(stream);
        }
    }

    private static ClassNode jarClass(JarFile jar, String name) throws IOException {
        var entry = jar.getJarEntry(name + ".class");
        assertNotNull(entry, name);
        try (var stream = jar.getInputStream(entry)) { return read(stream); }
    }

    private static ClassNode read(java.io.InputStream stream) throws IOException {
        var node = new ClassNode();
        new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static void assertMethod(ClassNode node, String name, String descriptor) {
        assertTrue(node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)),
                node.name + " must retain " + name + descriptor);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream().filter(node -> node.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static List<String> strings(AnnotationNode annotation, String key) {
        if (annotation != null && annotation.values != null) for (int index = 0; index < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index)) && annotation.values.get(index + 1) instanceof List<?> values)
                return values.stream().map(String.class::cast).toList();
        }
        return List.of();
    }
}
