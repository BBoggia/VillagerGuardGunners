package com.gamergaming.villagerguardgunners.compat.pointblank;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Verifies the adapter's actual binary calls without loading optional classes during mixin selection. */
public enum PointBlankApi {
    VPB("com.vicmatskiv.pointblank", "vpb/VpbAccess"),
    PBJ("mod.pbj", "pbj/PbjAccess"),
    NONE("", "");

    public static final PointBlankApi INSTALLED = VPB.compatible() ? VPB : PBJ.compatible() ? PBJ : NONE;
    private final String packageName;
    private final String adapter;

    PointBlankApi(String packageName, String adapter) {
        this.packageName = packageName;
        this.adapter = adapter;
    }

    boolean matchesTarget(String target) {
        return this != NONE && target.startsWith(packageName + ".");
    }

    private boolean compatible() {
        String prefix = packageName.replace('.', '/') + "/";
        try {
            Map<String, ClassNode> classes = new HashMap<>();
            if (read(prefix + "item/GunItem", true) == null) return false;
            ClassNode bridge = read(PointBlankApi.class.getPackageName().replace('.', '/') + "/" + adapter, false);
            if (bridge == null) return false;
            for (var method : bridge.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.startsWith(prefix)
                            && !hasMember(classes, prefix, call.owner, call.name, call.desc, true,
                                    call.getOpcode() == Opcodes.INVOKESTATIC)) return false;
                    if (instruction instanceof FieldInsnNode field && field.owner.startsWith(prefix)
                            && !hasMember(classes, prefix, field.owner, field.name, field.desc, false,
                                    field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC)) return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean hasMember(Map<String, ClassNode> classes, String prefix, String owner,
            String name, String descriptor, boolean method, boolean isStatic) throws IOException {
        if (owner == null || !owner.startsWith(prefix)) return false;
        ClassNode type = classes.get(owner);
        if (type == null) {
            type = read(owner, true);
            if (type == null) return false;
            classes.put(owner, type);
        }
        if (method) {
            for (var m : type.methods) {
                if (m.name.equals(name) && m.desc.equals(descriptor) && callable(m.access, isStatic)) return true;
            }
        } else {
            for (var f : type.fields) {
                if (f.name.equals(name) && f.desc.equals(descriptor) && callable(f.access, isStatic)) return true;
            }
        }
        for (String iface : type.interfaces) {
            if (hasMember(classes, prefix, iface, name, descriptor, method, isStatic)) return true;
        }
        return hasMember(classes, prefix, type.superName, name, descriptor, method, isStatic);
    }

    private static boolean callable(int access, boolean isStatic) {
        return (access & Opcodes.ACC_PUBLIC) != 0 && ((access & Opcodes.ACC_STATIC) != 0) == isStatic;
    }

    private static ClassNode read(String name, boolean headersOnly) throws IOException {
        try (InputStream input = PointBlankApi.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
                    | (headersOnly ? ClassReader.SKIP_CODE : 0));
            return node;
        }
    }
}
