import uk.ac.manchester.tornado.tc.runtime.*;

/** The headless TC compile as a native image: class bytes from directories, no runtime class loading. */
public class NativeCold {
    public static void main(String[] a) {
        long t0 = System.nanoTime();
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classpath(a[0]);
        int runs = a.length > 3 ? Integer.parseInt(a[3]) : 1;
        for (int r = 0; r < runs; r++) {
            long s = System.nanoTime();
            var c = TcCompiler.compileJava(source, a[1], a[2], uk.ac.manchester.tornado.tc.ir.target.TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            long e = System.nanoTime();
            if (r == 0 || r == runs - 1) {
                System.out.printf("run %d ok=%s wall=%.2f ms tc=%.2f ms %s%n", r, c.succeeded(), (e - s) / 1e6, c.nanos() / 1e6, c.succeeded() ? "" : c.diagnostics());
            }
        }
        System.out.printf("jvm-to-done %.1f ms%n", (System.nanoTime() - t0) / 1e6);
        if (a.length > 4) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(a[4]), TcCompiler.compileJava(source, a[1], a[2], uk.ac.manchester.tornado.tc.ir.target.TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of()).source());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }
}
