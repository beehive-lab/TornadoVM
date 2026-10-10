import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;
import jdk.incubator.code.CodeTransformer;
import jdk.incubator.code.Op;
import jdk.incubator.code.Reflect;
import jdk.incubator.code.dialect.core.CoreOp;
import jdk.incubator.code.dialect.core.SSA;

/** What a TC frontend from Babylon code models would receive: ops after lowering and SSA. */
public class Probe {
    @Reflect
    public static void saxpy(float alpha, float[] x, float[] y, int n) {
        for (int i = 0; i < n; i++) {
            float v = alpha * x[i] + y[i];
            y[i] = v > 0 ? v : 0;
        }
    }

    @Reflect
    public static float rowSum(float[] m, int rows, int cols, int r) {
        float s = 0;
        for (int i = 0; i < rows; i++) {
            if (i != r) continue;
            for (int j = 0; j < cols; j++) {
                s += Math.max(m[i * cols + j], 0f);
            }
        }
        return s;
    }

    @Reflect
    public static int helperCall(int[] a, int i) {
        return clamp(a[i], 0, 255) + (a.length > i ? 1 : 0);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    public static void main(String[] args) throws Exception {
        for (var name : new String[] { "saxpy", "rowSum", "helperCall" }) {
            Method m = null;
            for (var c : Probe.class.getDeclaredMethods()) if (c.getName().equals(name)) m = c;
            CoreOp.FuncOp f = Op.ofMethod(m).orElseThrow();
            Map<String, Integer> before = histogram(f);
            CoreOp.FuncOp ssa = SSA.transform(f.transform(CodeTransformer.LOWERING_TRANSFORMER));
            Map<String, Integer> after = histogram(ssa);
            System.out.println("== " + name + "\n  high-level ops: " + before + "\n  lowered SSA ops: " + after);
            if (name.equals("saxpy")) System.out.println(ssa.toText());
        }
    }

    static Map<String, Integer> histogram(Op op) {
        var h = new TreeMap<String, Integer>();
        op.elements().forEach(e -> { if (e instanceof Op o && o != op) h.merge(o.getClass().getSimpleName(), 1, Integer::sum); });
        return h;
    }
}
