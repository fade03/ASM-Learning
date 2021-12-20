package st;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileOutputStream;
import java.io.PrintWriter;

public class Trans1 implements Opcodes {
    public static void main(String[] args) throws Exception {
        ClassReader cr = new ClassReader("st.C");
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor transAdapter = new ClassVisitor(ASM5, cw) {
            boolean isPresent = false;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, "st/CTrans1", signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                // private static long timer
                if (name.equals("timer") && desc.equals("J")) {
                    isPresent = true;
                }

                return super.visitField(access, name, desc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                // mv is an instance of MethodWriter
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                // public void m()
                if (name.equals("m") && desc.equals("()V")) {
                    System.out.println("[catch method public void m()]\n\n");
                    // methodVisitor(filter) --> MethodWriter
                    return new MethodVisitor(ASM5, mv) {
                        @Override
                        public void visitCode() {
                            // method start
                            super.visitCode();
                            // timer = timer - System.currentTimMillis()
                            super.visitFieldInsn(GETSTATIC, "st/CTrans1", "timer", "J");
                            super.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimMillis", "()J", false);
                            super.visitInsn(LSUB);
                            super.visitFieldInsn(PUTSTATIC, "st/CTrans1", "timer", "J");
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            // before method exit
                            if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
                                // timer = timer + System.currentTimMillis()
                                super.visitFieldInsn(GETSTATIC, "st/CTrans1", "timer", "J");
                                super.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimMillis", "()J", false);
                                super.visitInsn(LADD);
                                super.visitFieldInsn(PUTSTATIC, "st/CTrans1", "timer", "J");
                            }
                            // the other instruments
                            super.visitInsn(opcode);
                        }
                    };
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

        FileOutputStream fos = new FileOutputStream("transformed/CTrans1.class");
        fos.write(bytes);
        fos.close();
    }
}
