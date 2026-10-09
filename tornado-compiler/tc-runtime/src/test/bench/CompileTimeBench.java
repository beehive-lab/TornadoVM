import java.nio.file.*;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.*;
/** Warm compile-time microbenchmark of the C3 kernels (decision review, C3). Run: java -cp <tc classes>:. CompileTimeBench <dir with .tcir>. */
public class CompileTimeBench {
    public static void main(String[] a) throws Exception {
        var dir = Path.of(a[0]);
        var texts = new String[] { Files.readString(dir.resolve("vector-add.tcir")), Files.readString(dir.resolve("branchy.tcir")), Files.readString(dir.resolve("block-reduce.tcir")) };
        for (var report : new boolean[] { false, true, false, true }) {
            var opts = TcOptions.DEFAULTS.withReport(report, Path.of("unused"));
            for (int w = 0; w < 2000; w++) for (var t : texts) TcCompiler.compile(IrParser.parseFunction(t), TargetCaps.CUDA_SM89, opts);
            long total = 0; int n = 500;
            for (int i = 0; i < n; i++) for (var t : texts) total += TcCompiler.compile(IrParser.parseFunction(t), TargetCaps.CUDA_SM89, opts).nanos();
            System.out.printf("textCapture=%-5s mean per kernel %.1f us%n", report, total / 1e3 / (n * texts.length));
        }
    }
}
