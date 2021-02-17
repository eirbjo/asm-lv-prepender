package com.github.eirbjo;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalVariablesDemo {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{"LocalVariablesDemo"});
    }

    @State(Scope.Benchmark)
    public static class RunState {
        private byte[] bytes;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            bytes = getClass()
                    .getClassLoader()
                    .getResourceAsStream("org/eclipse/jetty/server/Server.class")
                    .readAllBytes();
        }
    }

    @Benchmark
    @Warmup(iterations = 3)
    public void localVariablesPrepender(RunState runState) {
        ClassReader cr = new ClassReader(runState.bytes);

        ClassVisitor visitor = new ClassWriter(cr, 0);

        visitor = new PrependerClassVisitor(visitor);

        cr.accept(visitor, 0);
    }


    @Benchmark
    @Warmup(iterations = 3)
    public void localVariablesSorter(RunState runState) {
        ClassReader cr = new ClassReader(runState.bytes);

        ClassVisitor visitor = new ClassWriter(cr, 0);

        visitor = new SorterClassVisitor(visitor);

        cr.accept(visitor, ClassReader.EXPAND_FRAMES);
    }




    private class LocalVariablesPrepender extends MethodVisitor {
        private final Object[] implicitLocals;
        private final List<Object> prependedLocals = new ArrayList<>();
        private final int first;
        private int next;
        private boolean firstFrame = true;

        public LocalVariablesPrepender(MethodVisitor mv, int access, String className, String descriptor) {
            super(Opcodes.ASM9, mv);
            Type[] argumentTypes = Type.getArgumentTypes(descriptor);

            int first = 0;
            int locals = 0;
            if ((access & Opcodes.ACC_STATIC) == 0) {
                first += 1; // Receiver object
                locals++;
            }

            for (Type type : argumentTypes) {
                first += type.getSize(); // Method parameter
                locals++;
            }
            this.first = first;

            this.implicitLocals = new Object[locals];
            int c = 0;
            if ((access & Opcodes.ACC_STATIC) == 0) {
                implicitLocals[c++] = className;
            }

            for (Type type : argumentTypes) {
                implicitLocals[c++] = getLocalObjectFor(type);
            }

            next = first;
        }

        protected int prependLocal(Type type) {
            int current = next;
            next += type.getSize();
            prependedLocals.add(getLocalObjectFor(type));
            return current;
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack, maxLocals + prependedLocals.size());
        }

        @Override
        public final void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            boolean isFirst = firstFrame;
            firstFrame = false;

            if (Opcodes.F_FULL == type) {
                final int newNumLocals = numLocal + prependedLocals.size();
                final Object[] newLocals = injectPrepended(numLocal, local);
                super.visitFrame(type, newNumLocals, newLocals, numStack, stack);
            } else if (isFirst) {
                Object[] locals = fullLocals(numLocal, local);
                super.visitFrame(Opcodes.F_FULL, locals.length, locals, numStack, stack);
            } else {
                super.visitFrame(type, numLocal, local, numStack, stack);
            }
        }

        private Object[] fullLocals(int numLocal, Object[] local) {
            Object[] locals = new Object[implicitLocals.length + prependedLocals.size() + numLocal];
            int c = 0;
            for (Object implicitLocal : implicitLocals) {
                locals[c++] = implicitLocal;
            }
            for (Object prependedLocal : prependedLocals) {
                locals[c++] = prependedLocal;
            }
            for (int i = 0; i < numLocal; i++) {
                locals[c++] = local[i];
            }
            return locals;
        }


        private Object getLocalObjectFor(Type argumentType) {
            Object t;
            switch (argumentType.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    t = Opcodes.INTEGER;
                    break;
                case Type.FLOAT:
                    t = Opcodes.FLOAT;
                    break;
                case Type.LONG:
                    t = Opcodes.LONG;
                    break;
                case Type.DOUBLE:
                    t = Opcodes.DOUBLE;
                    break;
                case Type.ARRAY:
                    t = argumentType.getDescriptor();
                    break;
                // case Type.OBJECT:
                default:
                    t = argumentType.getInternalName();
                    break;
            }
            return t;
        }

        private Object[] injectPrepended(int numLocal, Object[] local) {
            final Object[] remapped = new Object[numLocal + prependedLocals.size()];
            int c = 0;
            for (int i = 0; i < first; i++) {
                remapped[c++] = local[i];
            }
            for (Object prependedLocal : prependedLocals) {
                remapped[c++] = prependedLocal;
            }

            for (int i = first; i < numLocal; i++) {
                remapped[c++] = local[i];
            }
            return remapped;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            super.visitVarInsn(opcode, remap(var));
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            super.visitIincInsn(remap(var), increment);
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            super.visitLocalVariable(name, descriptor, signature, start, end, remap(index));
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
            int[] remappedIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                remappedIndex[i] = remap(index[i]);

            }
            return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, remappedIndex, descriptor, visible);
        }

        private int remap(int var) {
            return var < first ? var : var + prependedLocals.size();
        }
    }

    private class PrependerInjector extends LocalVariablesPrepender {


        public PrependerInjector(int access, String className, String descriptor, MethodVisitor methodVisitor) {
            super(methodVisitor, access, className, descriptor);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitInsn(Opcodes.ICONST_M1);
            mv.visitVarInsn(Opcodes.ISTORE, prependLocal(Type.INT_TYPE));
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 1), maxLocals);
        }
    }



    private class SorterInjector extends LocalVariablesSorter {
        public SorterInjector(int access, String descriptor, MethodVisitor visitor) {
            super(Opcodes.ASM9, access, descriptor, visitor);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitInsn(Opcodes.ICONST_M1);
            mv.visitVarInsn(Opcodes.ISTORE, newLocal(Type.INT_TYPE));
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 1), maxLocals);
        }
    }
    private class SorterClassVisitor extends ClassVisitor {
        public SorterClassVisitor(ClassVisitor visitor) {
            super(Opcodes.ASM9, visitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new SorterInjector(access, descriptor, super.visitMethod(access, name, descriptor, signature, exceptions));
        }
    }


    private class PrependerClassVisitor extends ClassVisitor {

        private String className;

        public PrependerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public final void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
        }

        @Override
        public final MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            final MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new PrependerInjector(access, className, descriptor, mv);
        }
    }
}
