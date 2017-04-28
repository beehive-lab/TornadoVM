/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.Def;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryIntrinsic;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;

public class OCLLIRStmt {

    protected static abstract class AbstractInstruction extends LIRInstruction {

        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
        }

        @Override
        public final void emitCode(CompilationResultBuilder crb) {
            emitCode((OCLCompilationResultBuilder) crb, (OCLAssembler) crb.asm);
        }

        public abstract void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm);

    }

    @Opcode("ASSIGN")
    public static class AssignStmt extends AbstractInstruction {

        public static final LIRInstructionClass<AssignStmt> TYPE = LIRInstructionClass.create(AssignStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public AssignStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            if (rhs instanceof OCLLIROp) {
                ((OCLLIROp) rhs).emit(crb, asm);
            } else {
                asm.emitValue(crb, rhs);
            }
            asm.delimiter();
            asm.eol();
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    @Opcode("MOVE")
    public static class MoveStmt extends AbstractInstruction {

        public static final LIRInstructionClass<MoveStmt> TYPE = LIRInstructionClass.create(MoveStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected Value rhs;

        public MoveStmt(AllocatableValue lhs, Value rhs) {
            super(TYPE);
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public Value getExpr() {
            return rhs;
        }
    }

    @Opcode("LOAD")
    public static class LoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<LoadStmt> TYPE = LIRInstructionClass.create(LoadStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected OCLAddressCast cast;
        @Use
        protected MemoryAccess address;

        public LoadStmt(AllocatableValue lhs, OCLAddressCast cast, MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        public AllocatableValue getResult() {
            return lhs;
        }

        public OCLAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("VLOAD")
    public static class VectorLoadStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorLoadStmt> TYPE = LIRInstructionClass.create(VectorLoadStmt.class);

        @Def
        protected AllocatableValue lhs;
        @Use
        protected OCLAddressCast cast;
        @Use
        protected MemoryAccess address;

        @Use
        protected Value index;

        protected OCLBinaryIntrinsic op;

        public VectorLoadStmt(AllocatableValue lhs, OCLBinaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address) {
            super(TYPE);
            this.lhs = lhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            asm.emit(op.toString());
            asm.emit("(");
            asm.emitValue(crb, index);
            asm.emit(", ");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        public Value getResult() {
            return lhs;
        }

        public OCLAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }

        public OCLBinaryIntrinsic getOp() {
            return op;
        }
    }

    @Opcode("STORE")
    public static class StoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<StoreStmt> TYPE = LIRInstructionClass.create(StoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected OCLAddressCast cast;
        @Use
        protected MemoryAccess address;

        public StoreStmt(OCLAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();

            //asm.space();
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
            asm.delimiter();
            asm.eol();
        }

        public Value getRhs() {
            return rhs;
        }

        public OCLAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }
    }

    @Opcode("VSTORE")
    public static class VectorStoreStmt extends AbstractInstruction {

        public static final LIRInstructionClass<VectorStoreStmt> TYPE = LIRInstructionClass.create(VectorStoreStmt.class);

        @Use
        protected Value rhs;
        @Use
        protected OCLAddressCast cast;
        @Use
        protected MemoryAccess address;
        @Use
        protected Value index;

        protected OCLTernaryIntrinsic op;

        public VectorStoreStmt(OCLTernaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address, Value rhs) {
            super(TYPE);
            this.rhs = rhs;
            this.cast = cast;
            this.address = address;
            this.op = op;
            this.index = index;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();

            //asm.space();
            asm.emit(op.toString());
            asm.emit("(");
            asm.emitValue(crb, rhs);
            asm.emit(", ");
            asm.emitValue(crb, index);
            asm.emit(", ");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
//            if (address instanceof MemoryAccess) {
//                ((MemoryAccess) address).emit(crb);
//            } else if (address instanceof Variable) {
//                asm.emitValue(crb, address);
//            }
            asm.emit(")");
            asm.delimiter();
            asm.eol();
        }

        public Value getRhs() {
            return rhs;
        }

        public OCLAddressCast getCast() {
            return cast;
        }

        public MemoryAccess getAddress() {
            return address;
        }

        public Value getIndex() {
            return index;
        }

        public OCLTernaryIntrinsic getOp() {
            return op;
        }
    }

    @Opcode("EXPR")
    public static class ExprStmt extends AbstractInstruction {

        public static final LIRInstructionClass<ExprStmt> TYPE = LIRInstructionClass.create(ExprStmt.class);

        @Use
        protected Value expr;

        public ExprStmt(OCLLIROp expr) {
            super(TYPE);
            this.expr = expr;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            if (expr instanceof OCLLIROp) {
                ((OCLLIROp) expr).emit(crb, asm);
            } else {
                asm.emitValue(crb, expr);
            }
            asm.delimiter();
            asm.eol();
        }

        public Value getExpr() {
            return expr;
        }
    }

}
