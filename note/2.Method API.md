# Method API

在字节码文件中，Method都有着比较复杂的数据结构，所以ASM单独把这部分数据结构抽离出来，做了粒度更细的划分。 

# MethodVisitor

`MethodVisitor` 类的设计和使用，同 `ClassVisitor` 一样，都是没有抽象方法的抽象类，意味着都需要子类扩展其功能。

`ClassReader#accpet` 方法中会调用到`ClassVisitor#visitMethod` 方法，这个方法返回一个 `MethodVisitor` 对象，对于字节码中Method区域的访问，是在 `ClassReader#readMethod` 方法中，在这个方法中，会按照如下的顺序调用 `MethodVisitor#vistXXX` 方法：

```纯文本
visitAnnotationDefault?
( visitAnnotation | visitParameterAnnotation | visitAttribute )* 
(visitCode
  ( visitTryCatchBlock | visitLabel | visitFrame | visitXxxInsn | visitLocalVariable | visitLineNumber )*
visitMaxs )? 
visitEnd
```


这里引申出了 `ClassWriter` 的可选参数：

- `new ClassWriter(0)` ：不自动计算局部变量表和操作数栈的大小。
- `new ClassWriter(ClassWriter.COMPUTE_MAXS)`：自动计算局部变量表和操作数栈的大小，但是仍然需要调用 `visitMaxs`，但是参数可以是任何数（它们会被忽略），使用这个选项仍然需要手动计算每一帧。
- `new ClassWriter(ClassWriter.COMPUTE_FRAMES)`：所有值都被自动计算，无需调用 `visitFrame`，但是也需要调用 `visitMaxs` ，同样参数会被忽略。

# MethodWriter

 `ClassWriter#visitMethod` 方法实际上返回的是一个 `MethodWriter` 对象，在这里只是写入方法的定义（*备注[1]* ），而写入方法体中的逻辑仍需要调用* * `MethodWriter#vistXXX`* * 方法。

![](https://image-1302577725.cos.ap-beijing.myqcloud.com/uPic/image.png)

> 备注[1]：如写入`public int getN()` ，并不会写入方法逻辑。


在 `ClassReader#accept` 方法中被串联调用：

```纯文本
accept --> ... --> readField* --> readMethod* --> ... --> ...
```


把成员变量信息和方法信息写入 `ClassWriter` 所持有的数据结构，然后通过 `ClassWriter#toByteArray` 生成字节码数据。

```java
public class D8 {
    public static void main(String[] args) throws IOException {
        // 自动计算max_locals 和 max_stack
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        // 写入类的基本信息
        cw.visit(Opcodes.ASM5, Opcodes.ACC_PUBLIC, "pkg/Bean", null, "java/lang/Object", null);
        // 写入成员变量 private int f
        cw.visitField(Opcodes.ACC_PRIVATE, "f", "I", null, null).visitEnd();
        // 写入默认构造方法 public Bean() {}
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        // load this
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        // 调用父类构造方法
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // 写入方法定义 public int getF()，但是方法具体逻辑需要在返回的mv中编写
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getF", "()I", null, null);
        // visitCode开始，首先是默认构造方法
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Bean", "f", "I");
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // 写入方法定义 public void setF()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setF", "(I)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "pkg/Bean", "f", "I");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        FileOutputStream fos = new FileOutputStream("Bean.class");
        fos.write(cw.toByteArray());
        fos.close();
    }
}
```


生成的字节码反编译如下：

![](https://image-1302577725.cos.ap-beijing.myqcloud.com/uPic/image_1.png)

# Adapter

同样，我们也可以完成链式调用，达到filter的效果，比如我们想把之前`Bean` 的 `setF` 方法给去掉：

```java
/**
 * Remote setF
 */
public class D9 {
    public static void main(String[] args) throws IOException {
        // Reader --> Adapter --> CheckClassAdapter --> ClassWriter
        ClassReader classReader = new ClassReader(new FileInputStream("Bean.class"));
        ClassWriter classWriter = new ClassWriter(0);
        CheckClassAdapter checkClassAdapter = new CheckClassAdapter(classWriter);

        ClassVisitor removeAdapter = new ClassVisitor(Opcodes.ASM5, checkClassAdapter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                // 捕获访问到 serF 方法的事件
                if (name.equals("setF") && desc.equals("(I)V")) {
                    // 中断后续委派
                    return null;
                }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        };
        classReader.accept(removeAdapter, 0);
        byte[] bytes = classWriter.toByteArray();

        // print readable bytecodes
        new ClassReader(bytes).accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);
    }
}
```


```纯文本
// class version 0.5 (327680)
// access flags 0x1
public class pkg/Bean {


  // access flags 0x2
  private I f

  // access flags 0x1
  public <init>()V
    ALOAD 0
    INVOKESPECIAL java/lang/Object.<init> ()V
    RETURN
    MAXSTACK = 1
    MAXLOCALS = 1

  // access flags 0x1
  public getF()I
    ALOAD 0
    GETFIELD pkg/Bean.f : I
    IRETURN
    MAXSTACK = 1
    MAXLOCALS = 1
}
```




具体的Method Transformation案例，可见[基本使用](https://www.wolai.com/vCHbEA6FpL2z6BE7nFZ4yK)。

