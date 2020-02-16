package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import uk.ac.manchester.tornado.drivers.cuda.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;

public class PTXCompiler {
    public static PTXCompilationResult compileSketchForDevice(Sketch sketch, CompilableTask executable, PTXProviders providers, PTXBackend backend) {
        return superSketchyHardcodedKernel();
    }

    private static PTXCompilationResult superSketchyHardcodedKernel() {
        return new PTXCompilationResult("add", ".version 6.1\n" + ".target sm_30\n" + ".address_size 64\n" + "\n" + ".visible .entry add(\n" + "\t.param .u64 add_param0,\n"
                + "\t.param .u64 add_param1,\n" + "\t.param .u64 add_param2,\n" + "\t.param .u64 add_param3\n" + ")\n" + "{\n" + "\t.reg .pred \t%p<3>;\n" + "\t.reg .b32 \t%r<10>;\n"
                + "\t.reg .b64 \t%rd<17>;\n" + "\n" + "\n" + "\tld.param.u64 \t%rd8, [add_param0];\n" + "\tld.param.u64 \t%rd9, [add_param1];\n" + "\tld.param.u64 \t%rd10, [add_param2];\n"
                + "\tld.param.u64 \t%rd11, [add_param3];\n" + "\tmov.u32 \t%r1, %ntid.x;\n" + "\tmov.u32 \t%r2, %ctaid.x;\n" + "\tmov.u32 \t%r3, %tid.x;\n" + "\tmad.lo.s32 \t%r4, %r1, %r2, %r3;\n"
                + "\tcvt.s64.s32\t%rd16, %r4;\n" + "\tsetp.ge.s64\t%p1, %rd16, %rd8;\n" + "\t@%p1 bra \tBB0_3;\n" + "\n" + "\tmov.u32 \t%r5, %nctaid.x;\n" + "\tmul.lo.s32 \t%r6, %r5, %r1;\n"
                + "\tcvt.s64.s32\t%rd2, %r6;\n" + "\tcvta.to.global.u64 \t%rd3, %rd9;\n" + "\tcvta.to.global.u64 \t%rd4, %rd10;\n" + "\tcvta.to.global.u64 \t%rd5, %rd11;\n" + "\n" + "BB0_2:\n"
                + "\tshl.b64 \t%rd12, %rd16, 2;\n" + "\tadd.s64 \t%rd13, %rd4, %rd12;\n" + "\tld.global.u32 \t%r7, [%rd13];\n" + "\tadd.s64 \t%rd14, %rd3, %rd12;\n"
                + "\tld.global.u32 \t%r8, [%rd14];\n" + "\tadd.s32 \t%r9, %r7, %r8;\n" + "\tadd.s64 \t%rd15, %rd5, %rd12;\n" + "\tst.global.u32 \t[%rd15], %r9;\n" + "\tadd.s64 \t%rd16, %rd16, %rd2;\n"
                + "\tsetp.lt.s64\t%p2, %rd16, %rd8;\n" + "\t@%p2 bra \tBB0_2;\n" + "\n" + "BB0_3:\n" + "\tret;\n" + "}");
    }
}
