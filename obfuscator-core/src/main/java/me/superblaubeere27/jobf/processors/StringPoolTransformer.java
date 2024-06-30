package me.superblaubeere27.jobf.processors;

import me.superblaubeere27.annotations.ObfuscationTransformer;
import me.superblaubeere27.jobf.IClassTransformer;
import me.superblaubeere27.jobf.ProcessorCallback;
import me.superblaubeere27.jobf.utils.values.DeprecationLevel;
import me.superblaubeere27.jobf.utils.values.EnabledValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;

public class StringPoolTransformer implements IClassTransformer {
    private static final String ARRAY_FIELD_NAME = "arrayOfStrings";
    private static final String PROCESSOR_NAME = "StringPool";
    private EnabledValue enabled = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);

    @Override
    public void process(ProcessorCallback callback, ClassNode node) {
        if (!enabled.getObject()) return;
        if (Modifier.isInterface(node.access)) return;

        List<String> stringList = new ArrayList<>();
        Map<String, Integer> stringToIndexMap = new HashMap<>();

        FieldNode arrayField = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, ARRAY_FIELD_NAME, "[Ljava/lang/String;", null, null);
        node.fields.add(0, arrayField);

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    String stringValue = (String) ((LdcInsnNode) insn).cst;
                    if (!stringToIndexMap.containsKey(stringValue)) {
                        int index = stringList.size();
                        stringList.add(stringValue);
                        stringToIndexMap.put(stringValue, index);
                    }
                }
            }
        }

        for (MethodNode method : node.methods) {
            ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
            while (iterator.hasNext()) {
                AbstractInsnNode insnNode = iterator.next();
                if (insnNode instanceof LdcInsnNode && ((LdcInsnNode) insnNode).cst instanceof String) {
                    String stringValue = (String) ((LdcInsnNode) insnNode).cst;
                    int index = stringToIndexMap.get(stringValue);

                    InsnList replacement = new InsnList();
                    replacement.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, ARRAY_FIELD_NAME, "[Ljava/lang/String;"));
                    replacement.add(new LdcInsnNode(index));
                    replacement.add(new InsnNode(Opcodes.AALOAD));
                    method.instructions.insertBefore(insnNode, replacement);
                    iterator.remove();
                }
            }
        }

        setupClinitMethod(node, stringList, stringToIndexMap);
    }

    private void setupClinitMethod(ClassNode node, List<String> stringList, Map<String, Integer> stringToIndexMap) {
        MethodNode clinit = node.methods.stream()
                .filter(m -> m.name.equals("<clinit>"))
                .findFirst()
                .orElseGet(() -> {
                    MethodNode newClinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    node.methods.add(newClinit);
                    return newClinit;
                });

        InsnList initArray = new InsnList();
        initArray.add(new LdcInsnNode(stringList.size()));
        initArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        for (String str : stringList) {
            int index = stringToIndexMap.get(str);
            initArray.add(new InsnNode(Opcodes.DUP));
            initArray.add(new LdcInsnNode(index));
            initArray.add(new LdcInsnNode(str));
            initArray.add(new InsnNode(Opcodes.AASTORE));
        }
        initArray.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, ARRAY_FIELD_NAME, "[Ljava/lang/String;"));

        if (clinit.instructions.size() == 0) {
            clinit.instructions.add(initArray);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        } else {
            clinit.instructions.insertBefore(clinit.instructions.getFirst(), initArray);
        }
    }

    @Override
    public ObfuscationTransformer getType() {
        return ObfuscationTransformer.STRING_ENCRYPTION;
    }
}


