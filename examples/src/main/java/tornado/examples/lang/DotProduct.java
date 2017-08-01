package tornado.examples.lang;

import tornado.collections.types.Float3;
import tornado.runtime.api.TaskSchedule;

public class DotProduct {

    public static final void main(String[] args) {
        Float3 a = new Float3(1, 1, 1);
        Float3 b = new Float3(2, 2, 2);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", Float3::dot, a, b);

        s0.warmup();
        s0.schedule();

        System.out.printf("result = 0x%x\n", s0.getReturnValue("t0"));

    }

}
