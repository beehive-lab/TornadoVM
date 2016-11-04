package tornado.common;

public class StackTracer {
    
    public static void printStack(){
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.println("Stack Trace:");
        for(int i=2;i<st.length;i++){
            StackTraceElement e = st[i];
            System.out.printf("\t at %s.%s(%s:%d)\n",e.getClassName(),e.getMethodName(),e.getFileName(),e.getLineNumber());
        }
    }
}
