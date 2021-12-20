package st;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.AdviceAdapter;
import jdk.internal.org.objectweb.asm.commons.Method;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

class AddTimerMethodAdapter1 extends AdviceAdapter implements Opcodes {

    public AddTimerMethodAdapter1(MethodVisitor mv, int access, String name, String desc) {
        super(ASM5, mv, access, name, desc);
    }

    @Override
    protected void onMethodEnter() {
        // timer = timer - System.currentTimeMillis
        getStatic(Type.getType("st/CTrans3"), "timer", Type.LONG_TYPE);
        invokeStatic(Type.getType(System.class), Method.getMethod("long currentTimeMillis()"));
        visitInsn(LSUB);
        putStatic(Type.getType("st/CTrans3"), "timer", Type.LONG_TYPE);
    }

    @Override
    protected void onMethodExit(int opcode) {
        // timer = timer + System.currentTimeMillis
        getStatic(Type.getType("st/CTrans3"), "timer", Type.LONG_TYPE);
        invokeStatic(Type.getType(System.class), Method.getMethod("long currentTimeMillis()"));
        visitInsn(LADD);
        putStatic(Type.getType("st/CTrans3"), "timer", Type.LONG_TYPE);
    }
}


public class Trans3 implements Opcodes {
    public static void main(String[] args) throws IOException {
        ClassReader cr = new ClassReader("st.C");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor transAdapter = new ClassVisitor(ASM5, cw) {
            boolean isPresent = false;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, "st/CTrans3", signature, superName, interfaces);
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
                // MethodWriter
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (name.equals("m") && desc.equals("()V")) {
                    return new AddTimerMethodAdapter1(mv, access, name, desc);
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

        new ClassReader(bytes).accept(new CheckClassAdapter(new TraceClassVisitor(new PrintWriter(System.out))), 0);

        FileOutputStream fos = new FileOutputStream("transformed/CTrans3.class");
        fos.write(bytes);
        fos.close();
    }
}
