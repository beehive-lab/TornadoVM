package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getVMConfig;

public class PTXCompiler {
    public static PTXCompilationResult compileSketchForDevice(Sketch sketch, CompilableTask executable, PTXProviders providers, PTXBackend backend) {
        return littleLessSketchyHardcodedKernel();
    }

    private static PTXCompilationResult littleLessSketchyHardcodedKernel() {
        return new PTXCompilationResult("add",
                ".version 6.1\n" + ".target sm_30\n" + ".address_size 64\n" + "\n" + ".visible .entry add(\n"
                + "\t.param .u64 _Z3addyyh_param_0,\n" + "\t.param .u64 _Z3addyyh_param_1,\n"
                + "\t.param .u8 _Z3addyyh_param_2\n" + ")\n" + "{\n" + "\t.reg .pred \t%p<3>;\n"
                + "\t.reg .b16 \t%rs<2>;\n" + "\t.reg .b32 \t%r<11>;\n" + "\t.reg .b64 \t%rd<21>;\n" + "\n"
                + "\n" + "\tld.param.u64 \t%rd10, [_Z3addyyh_param_1];\n"
                + "\tld.param.u8 \t%rs1, [_Z3addyyh_param_2];\n" + "\tcvt.u64.u16\t%rd11, %rs1;\n"
                + "\tadd.s64 \t%rd1, %rd11, %rd10;\n" + "\tld.u64 \t%rd12, [%rd1];\n"
                + "\tcvta.to.global.u64 \t%rd2, %rd12;\n" + "\tmov.u32 \t%r1, %ntid.x;\n"
                + "\tmov.u32 \t%r4, %ctaid.x;\n" + "\tmov.u32 \t%r5, %tid.x;\n"
                + "\tmad.lo.s32 \t%r2, %r1, %r4, %r5;\n" + "\tld.global.u32 \t%r3, [%rd2+" + getVMConfig().arrayOopDescLengthOffset() + "];\n"
                + "\tsetp.ge.u32\t%p1, %r2, %r3;\n" + "\t@%p1 bra \tBB0_3;\n" + "\n"
                + "\tld.u64 \t%rd13, [%rd1+8];\n" + "\tld.u64 \t%rd14, [%rd1+16];\n"
                + "\tcvt.s64.s32\t%rd20, %r2;\n" + "\tmov.u32 \t%r6, %nctaid.x;\n"
                + "\tmul.lo.s32 \t%r7, %r6, %r1;\n" + "\tcvt.s64.s32\t%rd4, %r7;\n"
                + "\tcvt.s64.s32\t%rd5, %r3;\n" + "\tcvta.to.global.u64 \t%rd6, %rd14;\n"
                + "\tcvta.to.global.u64 \t%rd7, %rd13;\n" + "\n" + "BB0_2:\n" + "\tshl.b64 \t%rd15, %rd20, 2;\n"
                + "\tadd.s64 \t%rd16, %rd15, "+ getVMConfig().getArrayBaseOffset(JavaKind.Int) +";\n" + "\tadd.s64 \t%rd17, %rd2, %rd16;\n"
                + "\tadd.s64 \t%rd18, %rd7, %rd16;\n" + "\tld.global.u32 \t%r8, [%rd18];\n"
                + "\tld.global.u32 \t%r9, [%rd17];\n" + "\tadd.s32 \t%r10, %r8, %r9;\n"
                + "\tadd.s64 \t%rd19, %rd6, %rd16;\n" + "\tst.global.u32 \t[%rd19], %r10;\n"
                + "\tadd.s64 \t%rd20, %rd20, %rd4;\n" + "\tsetp.lt.u64\t%p2, %rd20, %rd5;\n"
                + "\t@%p2 bra \tBB0_2;\n" + "\n" + "BB0_3:\n" + "\tret;\n" + "}"
        );
    }
}
