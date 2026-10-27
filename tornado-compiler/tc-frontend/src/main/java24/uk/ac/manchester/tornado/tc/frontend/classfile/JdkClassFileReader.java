/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.frontend.classfile;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.BootstrapMethod;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.Code;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.Handler;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.LineNumber;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.LocalVariable;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.Method;
import uk.ac.manchester.tornado.tc.frontend.classfile.TypeAnnotations.LocalAnnotation;

/**
 * {@link ClassFile.Reader#JDK}: reads class files with the JDK's Class-File API
 * ({@code java.lang.classfile}, JEP 484, final in JDK 24). The API does the parsing; this class maps
 * its model onto the one the frontend uses. Instructions come from the API's typed instruction model,
 * and branch targets from its labels. Constant-pool entries keep the class file's own indices, so
 * the instructions refer to the same constants as with TC's own reader.
 *
 * <p>Compiled from {@code src/main/java24} with {@code --release 24}, and loaded by name from
 * {@link ClassFile} only on JDK 24 and newer.
 */
final class JdkClassFileReader implements Function<byte[], ClassFile> {

    @Override
    public ClassFile apply(byte[] bytes) {
        ClassModel model;
        try {
            model = java.lang.classfile.ClassFile.of().parse(bytes);
        } catch (IllegalArgumentException e) {
            throw new ClassFormatException(e.getMessage());
        }
        var pool = new JdkConstantPool(model.constantPool());
        var methods = new ArrayList<Method>(model.methods().size());
        for (var method : model.methods()) {
            methods.add(new Method(method.methodName().stringValue(), method.methodType().stringValue(), method.flags().flagsMask(), method.code().map(code -> code(method, code))));
        }
        var bootstraps = new ArrayList<BootstrapMethod>();
        model.findAttribute(Attributes.bootstrapMethods()).ifPresent(attribute -> {
            for (var entry : attribute.bootstrapMethods()) {
                bootstraps.add(new BootstrapMethod(entry.bootstrapMethod().index(), entry.arguments().stream().map(a -> a.index()).toList()));
            }
        });
        return new ClassFile(model.thisClass().asInternalName(), model.superclass().map(c -> c.asInternalName()), model.majorVersion(), pool, List.copyOf(methods), List.copyOf(bootstraps));
    }

    private static Code code(MethodModel method, CodeModel model) {
        if (!(model instanceof CodeAttribute code)) {
            throw new ClassFormatException("method " + method.methodName().stringValue() + ": code is not backed by a Code attribute");
        }
        var handlers = model.exceptionHandlers().stream() //
                .map(h -> new Handler(code.labelToBci(h.tryStart()), code.labelToBci(h.tryEnd()), code.labelToBci(h.handler()), h.catchType().map(c -> c.index()).orElse(0))) //
                .toList();
        var lines = new ArrayList<LineNumber>();
        for (var table : code.findAttributes(Attributes.lineNumberTable())) {
            table.lineNumbers().forEach(l -> lines.add(new LineNumber(l.startPc(), l.lineNumber())));
        }
        var locals = new ArrayList<LocalVariable>();
        for (var table : code.findAttributes(Attributes.localVariableTable())) {
            table.localVariables().forEach(v -> locals.add(new LocalVariable(v.startPc(), v.length(), v.name().stringValue(), v.type().stringValue(), v.slot())));
        }
        return new Code(code.maxStack(), code.maxLocals(), new Body(code), List.copyOf(handlers), List.copyOf(lines), List.copyOf(locals));
    }

    /** The instructions and local-variable annotations of one method, mapped on first use. */
    private static final class Body implements ClassFile.Body {
        private static final int[] NONE = new int[0];

        private final CodeAttribute code;
        private volatile List<Bytecode.Insn> insns;
        private volatile List<LocalAnnotation> localAnnotations;

        Body(CodeAttribute code) {
            this.code = code;
        }

        @Override
        public List<Bytecode.Insn> insns() {
            var result = insns;
            if (result == null) {
                result = decode();
                insns = result;
            }
            return result;
        }

        @Override
        public List<LocalAnnotation> localAnnotations() {
            var result = localAnnotations;
            if (result == null) {
                var annotations = new ArrayList<LocalAnnotation>();
                for (var attribute : code.findAttributes(Attributes.runtimeVisibleTypeAnnotations())) {
                    for (var annotation : attribute.annotations()) {
                        if (annotation.targetInfo() instanceof TypeAnnotation.LocalVarTarget target) {
                            var descriptor = annotation.annotation().className().stringValue();
                            for (var range : target.table()) {
                                int start = code.labelToBci(range.startLabel());
                                annotations.add(new LocalAnnotation(descriptor, range.index(), start, code.labelToBci(range.endLabel()) - start));
                            }
                        }
                    }
                }
                result = List.copyOf(annotations);
                localAnnotations = result;
            }
            return result;
        }

        private List<Bytecode.Insn> decode() {
            var result = new ArrayList<Bytecode.Insn>();
            int bci = 0;
            for (var element : code) {
                if (element instanceof Instruction instruction) {
                    result.add(insn(bci, instruction));
                    bci += instruction.sizeInBytes();
                }
            }
            return List.copyOf(result);
        }

        /**
         * One instruction in the frontend's form: {@code wide} folded into the instruction it modifies,
         * constant-pool operands as indices, branch targets as bcis.
         */
        private Bytecode.Insn insn(int bci, Instruction instruction) {
            int op = instruction.opcode().bytecode() & 0xff;
            return switch (instruction) {
                case LoadInstruction load -> local(bci, op, load.slot());
                case StoreInstruction store -> local(bci, op, store.slot());
                case IncrementInstruction inc -> new Bytecode.Insn(bci, op, inc.slot(), inc.constant(), NONE, NONE);
                case ConstantInstruction.ArgumentConstantInstruction arg -> new Bytecode.Insn(bci, op, arg.constantValue(), 0, NONE, NONE);
                case ConstantInstruction.LoadConstantInstruction ldc -> new Bytecode.Insn(bci, op, ldc.constantEntry().index(), 0, NONE, NONE);
                case FieldInstruction field -> new Bytecode.Insn(bci, op, field.field().index(), 0, NONE, NONE);
                case InvokeInstruction invoke -> new Bytecode.Insn(bci, op, invoke.method().index(), 0, NONE, NONE);
                case InvokeDynamicInstruction indy -> new Bytecode.Insn(bci, op, indy.invokedynamic().index(), 0, NONE, NONE);
                case NewObjectInstruction create -> new Bytecode.Insn(bci, op, create.className().index(), 0, NONE, NONE);
                case NewReferenceArrayInstruction array -> new Bytecode.Insn(bci, op, array.componentType().index(), 0, NONE, NONE);
                case NewPrimitiveArrayInstruction array -> new Bytecode.Insn(bci, op, 0, array.typeKind().newarrayCode(), NONE, NONE);
                case NewMultiArrayInstruction array -> new Bytecode.Insn(bci, op, array.arrayType().index(), array.dimensions(), NONE, NONE);
                case TypeCheckInstruction check -> new Bytecode.Insn(bci, op, check.type().index(), 0, NONE, NONE);
                case BranchInstruction branch -> new Bytecode.Insn(bci, op, 0, 0, new int[] { bciOf(branch.target()) }, NONE);
                case DiscontinuedInstruction.JsrInstruction jsr -> new Bytecode.Insn(bci, op, 0, 0, new int[] { bciOf(jsr.target()) }, NONE);
                case DiscontinuedInstruction.RetInstruction ret -> new Bytecode.Insn(bci, op, ret.slot(), 0, NONE, NONE);
                case TableSwitchInstruction table -> tableSwitch(bci, op, table);
                case LookupSwitchInstruction lookup -> {
                    var cases = lookup.cases();
                    var targets = new int[cases.size() + 1];
                    var keys = new int[cases.size()];
                    for (int i = 0; i < cases.size(); i++) {
                        keys[i] = cases.get(i).caseValue();
                        targets[i] = bciOf(cases.get(i).target());
                    }
                    targets[cases.size()] = bciOf(lookup.defaultTarget());
                    yield new Bytecode.Insn(bci, op, 0, 0, targets, keys);
                }
                default -> new Bytecode.Insn(bci, op, 0, 0, NONE, NONE);
            };
        }

        /** {@code xload_n}/{@code xstore_n} carry their slot in the opcode, as in TC's own decoding. */
        private static Bytecode.Insn local(int bci, int op, int slot) {
            boolean implicit = (op >= Bytecode.ILOAD_0 && op <= Bytecode.ALOAD_3) || (op >= Bytecode.ISTORE_0 && op <= Bytecode.ASTORE_3);
            return new Bytecode.Insn(bci, op, implicit ? 0 : slot, 0, NONE, NONE);
        }

        /** One target per key from low to high, then the default, as in the class file. */
        private Bytecode.Insn tableSwitch(int bci, int op, TableSwitchInstruction table) {
            var byKey = new HashMap<Integer, Label>();
            table.cases().forEach(c -> byKey.put(c.caseValue(), c.target()));
            int n = table.highValue() - table.lowValue() + 1;
            var targets = new int[n + 1];
            var keys = new int[n];
            for (int i = 0; i < n; i++) {
                keys[i] = table.lowValue() + i;
                targets[i] = bciOf(byKey.getOrDefault(keys[i], table.defaultTarget()));
            }
            targets[n] = bciOf(table.defaultTarget());
            return new Bytecode.Insn(bci, op, 0, 0, targets, keys);
        }

        private int bciOf(Label label) {
            return code.labelToBci(label);
        }
    }

    /** The constant pool through the API's typed entries, looked up by the class file's indices. */
    private record JdkConstantPool(java.lang.classfile.constantpool.ConstantPool pool) implements ConstantPool {

        private java.lang.classfile.constantpool.PoolEntry entry(int index) {
            try {
                return pool.entryByIndex(index);
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new ClassFormatException("constant " + index + ": " + e.getMessage());
            }
        }

        @Override
        public String utf8(int index) {
            if (entry(index) instanceof java.lang.classfile.constantpool.Utf8Entry utf8) {
                return utf8.stringValue();
            }
            throw new ClassFormatException("constant " + index + " is not UTF-8");
        }

        @Override
        public String className(int index) {
            if (entry(index) instanceof java.lang.classfile.constantpool.ClassEntry type) {
                return type.asInternalName();
            }
            throw new ClassFormatException("constant " + index + " is not a class");
        }

        @Override
        public MemberRef member(int index) {
            if (entry(index) instanceof java.lang.classfile.constantpool.MemberRefEntry ref) {
                return new MemberRef(ref.owner().asInternalName(), ref.name().stringValue(), ref.type().stringValue());
            }
            throw new ClassFormatException("constant " + index + " is not a field or method reference");
        }

        @Override
        public Optional<MemberRef> methodHandle(int index) {
            if (entry(index) instanceof java.lang.classfile.constantpool.MethodHandleEntry handle) {
                return Optional.of(member(handle.reference().index()));
            }
            return Optional.empty();
        }

        @Override
        public DynamicSite dynamicSite(int index) {
            if (entry(index) instanceof java.lang.classfile.constantpool.InvokeDynamicEntry site) {
                return new DynamicSite(site.bootstrapMethodIndex(), site.name().stringValue(), site.type().stringValue());
            }
            throw new ClassFormatException("constant " + index + " is not an invokedynamic site");
        }

        @Override
        public Object loadable(int index) {
            return switch (entry(index)) {
                case java.lang.classfile.constantpool.IntegerEntry v -> v.intValue();
                case java.lang.classfile.constantpool.FloatEntry v -> v.floatValue();
                case java.lang.classfile.constantpool.LongEntry v -> v.longValue();
                case java.lang.classfile.constantpool.DoubleEntry v -> v.doubleValue();
                case java.lang.classfile.constantpool.StringEntry v -> v.stringValue();
                default -> throw new ClassFormatException("constant " + index + " is not loadable by ldc in a kernel");
            };
        }
    }
}
