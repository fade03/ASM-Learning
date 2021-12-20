package st;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.LocalVariablesSorter;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 * Using LocalVariableSorter
 */
class AddTimerMethodAdapter extends LocalVariablesSorter implements Opcodes {
    private int index;

    public AddTimerMethodAdapter(int access, String desc, MethodVisitor mv) {
        super(ASM5, access, desc, mv);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        // 原来要执行的指令如下，即计算出方法执行的结果，然后手动计算变量存放到局部变量表中的索引
        // INVOKESTATIC java/lang/System.currentTimeMillis ()J
        // LSTORE 1
        super.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimMillis", "()J", false);
        // 使用 newLocal 方法可以直接计算出局部变量要占的索引值 index
        index = this.newLocal(Type.LONG_TYPE);
        super.visitVarInsn(LSTORE, index);
    }

    @Override
    public void visitInsn(int opcode) {
        if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
            // operand stack: System.currentTimMillis |
            super.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimMillis", "()J", false);
            // operand stack: System.currentTimMillis | index |
            super.visitVarInsn(LLOAD, index);
            // operand stack: System.currentTimMillis - index |
            super.visitInsn(LSUB);
            super.visitFieldInsn(GETSTATIC, "st/CTrans2", "timer", "J");
            // timer = timer + (System.currentTimMillis - index)
            super.visitInsn(LADD);
            super.visitFieldInsn(PUTSTATIC, "st/CTrans2", "timer", "J");
        }
        super.visitInsn(opcode);
    }
}

public class Trans2 implements Opcodes {
    public static void main(String[] args) throws Exception {
        ClassReader cr = new ClassReader("st.C");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor transAdapter = new ClassVisitor(ASM5, cw) {
            boolean isPresent = false;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                // ClassWriter#visit
                super.visit(version, access, "st/CTrans2", signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                if (name.equals("timer") && desc.equals("J")) {
                    isPresent = true;
                }

                return super.visitField(access, name, desc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if (name.equals("m") && desc.equals("()V")) {
                    return new AddTimerMethodAdapter(access, desc, mv);
                }

                return mv;
            }

            @Override
            public void visitEnd() {
                if (!isPresent) {
                    super.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "timer", "J", null, null);
                }

                super.visitEnd();
            }
        };

        cr.accept(transAdapter, 0);
        byte[] bytes = cw.toByteArray();

        // check validity
        new ClassReader(bytes).accept(new CheckClassAdapter(new TraceClassVisitor(new PrintWriter(System.out))), 0);

        FileOutputStream fos = new FileOutputStream("transformed/CTrans2.class");
        fos.write(bytes);
        fos.close();
    }
}
