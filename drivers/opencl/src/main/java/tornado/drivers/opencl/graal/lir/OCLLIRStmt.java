/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.lir;

import java.util.HashMap;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLTernaryIntrinsic;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;

public class OCLLIRStmt {

    protected static abstract class AbstractInstruction extends LIRInstruction {
        
        protected static HashMap<Value, OCLKind> phiVectorTypes; 
        protected static HashMap<Value, String> phiVectorNames;
        protected static HashMap<Value, OCLKind> vectorType;

        protected AbstractInstruction(LIRInstructionClass<? extends AbstractInstruction> c) {
            super(c);
            phiVectorNames = new HashMap<>();
            phiVectorTypes = new HashMap<>();
            vectorType = new HashMap<>();
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
            
            /*
             * During the ASSIGN operation, we introduce the type and vector type in 
             * a hash table if the left side is a phi value node of type vector.
             */
            OCLKind kind = (OCLKind) rhs.getPlatformKind();
            if (!(rhs instanceof OCLLIROp) && kind.isVector()) {
                phiVectorTypes.put(lhs, kind);
                phiVectorNames.put(lhs, lhs.toString());
            }
            
            asm.emitValue(crb, lhs);
            asm.space();
            asm.assign();
            asm.space();
            if (rhs instanceof OCLLIROp) {
                ((OCLLIROp) rhs).emit(crb, asm);
                
                /* Is the type to be assigned is Vector, then we save the left side into a hash table.
                 * If the left side is used again for a store operation, the store has to be in vector format 
                 * (vstore)  
                 */
                if (kind.isVector()) {
                    vectorType.put(lhs, (OCLKind)lhs.getPlatformKind());
                }
                
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
        
        /*
         * This method emit the OpenCL vstore of a vector given the input vector and the address.
         * This is because, when having PhiNodes that update a vector value, the update has to be 
         * performed using vstores rather than pointer assignation.
         */
        private void emitVectorPhiStore(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            OCLKind kind = phiVectorTypes.get(rhs);
            int vlength = kind.getVectorLength();
            asm.emit("vstore");
            asm.emit(vlength + OCLAssemblerConstants.BRACKET_OPEN);
            String namePhi=  phiVectorNames.get(rhs);
            asm.emit(namePhi);
            asm.emit(", 0,");
            
            String typeVector  = kind.name().substring(0, kind.name().length()-1).toLowerCase();
            asm.emit("(" + OCLAssemblerConstants.GLOBAL_MEM_MODIFIER +  " " + typeVector + " *" + OCLAssemblerConstants.BRACKET_CLOSE);
            asm.space();
            address.emit(crb, asm);
            asm.emit(OCLAssemblerConstants.BRACKET_CLOSE);
        }
        
        private void emitStore(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit("*(");
            cast.emit(crb, asm);
            asm.space();
            address.emit(crb, asm);
            asm.emit(")");
            asm.space();
            asm.assign();
            asm.space();
            asm.emitValue(crb, rhs);
        }
        
        private void emitVectorStore(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.emit("vstore");
            OCLKind kind = vectorType.get(rhs);
            int vlength = kind.getVectorLength();
            asm.emit(vlength + OCLAssemblerConstants.BRACKET_OPEN);
            String vectorName =  rhs.toString();
            asm.emit(vectorName);
            asm.emit(", 0,");

            String typeVector  = kind.name().substring(0, kind.name().length()-1).toLowerCase();
            asm.emit("(" + OCLAssemblerConstants.GLOBAL_MEM_MODIFIER +  " " + typeVector + " *" + OCLAssemblerConstants.BRACKET_CLOSE);
            asm.space();
            address.emit(crb, asm);
            asm.emit(OCLAssemblerConstants.BRACKET_CLOSE);
        }
        
        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            if (vectorType.containsKey(rhs)) {
                emitVectorStore(crb, asm);
            } else if (phiVectorNames.containsKey(rhs)) {
                emitVectorPhiStore(crb, asm);
            } else {
                emitStore(crb, asm);
            }
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
