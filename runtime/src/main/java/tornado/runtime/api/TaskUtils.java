package tornado.runtime.api;

import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import tornado.api.Event;
import tornado.common.DeviceMapping;
import tornado.common.enums.Access;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;
import tornado.runtime.DataMovementTask;
import tornado.runtime.ObjectReference;
import tornado.runtime.TornadoRuntime;
import tornado.runtime.api.TornadoFunctions.Task1;
import tornado.runtime.api.TornadoFunctions.Task10;
import tornado.runtime.api.TornadoFunctions.Task2;
import tornado.runtime.api.TornadoFunctions.Task3;
import tornado.runtime.api.TornadoFunctions.Task4;
import tornado.runtime.api.TornadoFunctions.Task5;
import tornado.runtime.api.TornadoFunctions.Task6;
import tornado.runtime.api.TornadoFunctions.Task7;
import tornado.runtime.api.TornadoFunctions.Task8;
import tornado.runtime.api.TornadoFunctions.Task9;

import com.oracle.graal.api.meta.ConstantPool;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.bytecode.Bytecodes;
import com.oracle.graal.hotspot.meta.HotSpotResolvedJavaMethodImpl;

public class TaskUtils {

    public static CompilableTask scalaTask(Object object, Object... args) {
        Class<?> type = object.getClass();
        // System.out.printf("lambda: type=%s, %s\n", type.getName(), object);
        //
        // for (Object arg : args) {
        // System.out.printf("arg: type=%s, %s\n", arg.getClass()
        // .getSimpleName(), arg);
        // }

        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
            // System.out.printf("m: %s,syn=%s, bridge=%s, public=%s\n",
            // m.getName(), m.isSynthetic(), m.isBridge(),
            // Modifier.isPublic(m.getModifiers()));

            if (m.getName().equals("apply") && !m.isSynthetic()
                    && !m.isBridge()) {
                entryPoint = m;
                break;
            }
        }

        return createTask(entryPoint, object, false, args);
    }

    public static void waitForEvents(List<Event> events) {
        // long start = System.nanoTime();
        // for(Event event : events)
        // event.waitOn();
        // long end = System.nanoTime();
        // System.out.printf("waitForEvents: %d events took %.8f s\n",events.size(),RuntimeUtilities.elapsedTimeInSeconds(start,
        // end));
        // events.clear();
    }

   

    private final static Method resolveMethodHandle(Object task) {
        final Class<?> type = task.getClass();

        /*
         * task should implement one of the TaskX interfaces...
         * ...so we look for the apply function.
         * Note: apply will perform some type casting and then call
         * the function we really want to use, so we need to resolve
         * the nested function.
         */
        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
//            System.out.printf("ep m: %s\n",m.getName());
        	if (m.getName().equals("apply")) {
                entryPoint = m;
//                break;
            }
        }

        guarantee(entryPoint != null, "unable to find entry point");

        /*
         * Fortunately we can do a bit of GRAAL magic to resolve the function
         * to a Method.
         */
        final ResolvedJavaMethod resolvedMethod = TornadoRuntime
                .getVMProviders().getMetaAccess().lookupJavaMethod(entryPoint);
        final ConstantPool cp = resolvedMethod.getConstantPool();
        final byte[] bc = resolvedMethod.getCode();
        
       
        
        for (int i = 0; i < bc.length; i++) {
            if (bc[i] == (byte) Bytecodes.INVOKESTATIC) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKESTATIC);
                HotSpotResolvedJavaMethodImpl jm = (HotSpotResolvedJavaMethodImpl) cp
                        .lookupMethod(bc[i + 2], Bytecodes.INVOKESTATIC);
                
                try {
                    Method toJavaMethod = jm.getClass().getDeclaredMethod(
                            "toJava");
                    toJavaMethod.setAccessible(true);
                    Method m = (Method) toJavaMethod.invoke(jm);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException | SecurityException
                        | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {

                    e.printStackTrace();
                }

                break;
            } else if (bc[i] == (byte) Bytecodes.INVOKEVIRTUAL){
            	cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                HotSpotResolvedJavaMethodImpl jm = (HotSpotResolvedJavaMethodImpl) cp
                        .lookupMethod(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
//                System.out.println(jm.getName());
                
                switch(jm.getName()){
                case "floatValue":
                case "doubleValue":
                case "intValue":
                	continue;
                }
                try {
                    Method toJavaMethod = jm.getClass().getDeclaredMethod(
                            "toJava");
                    toJavaMethod.setAccessible(true);
                    Method m = (Method) toJavaMethod.invoke(jm);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException | SecurityException
                        | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {

                    e.printStackTrace();
                }
                

                break;
            }
        }

        shouldNotReachHere();
        return null;
    }

    public static <T1> CompilableTask createTask(Task1<T1> code, T1 arg) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg);
    }

    public static <T1, T2> CompilableTask createTask(Task2<T1, T2> code,
            T1 arg1, T2 arg2) {
        final Method method = resolveMethodHandle(code);
        System.out.println("method: " + method.getName());
        return createTask(method, code, true, arg1, arg2);
    }

    public static <T1, T2, T3> CompilableTask createTask(
            Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3);
    }

    public static <T1, T2, T3, T4> CompilableTask createTask(
            Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4);
    }

    public static <T1, T2, T3, T4, T5> CompilableTask createTask(
            Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4, arg5);
    }

    public static <T1, T2, T3, T4, T5, T6> CompilableTask createTask(
            Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4, arg5,
                arg6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompilableTask createTask(
            Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompilableTask createTask(
            Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7, arg8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompilableTask createTask(
            Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7, arg8, arg9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompilableTask createTask(
            Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8,
            T9 arg9, T10 arg10) {
        final Method method = resolveMethodHandle(code);
        return createTask(method, code, true, arg1, arg2, arg3, arg4, arg5,
                arg6, arg7, arg8, arg9, arg10);
    }

    public static Object[] extractCapturedVariables(Object code) {
        final Class<?> type = code.getClass();
        
        int count = 0;
        for(Field field : type.getDeclaredFields()){
        	System.out.printf("cv: type=%s, name=%s\n",field.getType().getName(),field.getName());
        	if(!field.getType().getName().contains("$$Lambda$")){
        		count++;
        	}
        }
        
        final Object[] cvs = new Object[count];
        int index = 0;
        for (Field field : type.getDeclaredFields()) {
        	if(!field.getType().getName().contains("$$Lambda$")){
            field.setAccessible(true);
            try {
                cvs[index] = field.get(code);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
            index++;
        	}
        }
        return cvs;
    }

   public static PrebuiltTask createTask(String entryPoint, String filename, Object[] args, Access[] accesses, DeviceMapping device, int[] dims){
	   final DomainTree domain = new DomainTree(dims.length);
	   for(int i=0;i<dims.length;i++){
		   domain.set(i, new IntDomain(0,1,dims[i]));
	   }
	   
	   return new PrebuiltTask(entryPoint,filename,args,accesses,device,domain);
   }

    public static CompilableTask createTask(Runnable runnable) {
        final Method method = resolveRunnable(runnable);
        return createTask(method, runnable, false);
    }
    
 
   	private static CompilableTask createTask(Method method, Object code,
   			boolean extractCVs, Object... args) {
   		 final int numArgs;
   	        final Object[] cvs;

   	        if (extractCVs) {
   	            cvs = TaskUtils.extractCapturedVariables(code);
   	            numArgs = cvs.length + args.length;
   	        } else {
   	            cvs = null;
   	            numArgs = args.length;
   	        }
//   	        final boolean isStatic = Modifier.isStatic(method.getModifiers());

   	        final Object[] parameters = new Object[numArgs];
   	        int index = 0;
   	        if (extractCVs) {
   	            for (Object cv : cvs) {
   	                parameters[index] = cv;
   	                index++;
   	            }
   	        }

   	        for (Object arg : args) {
   	            parameters[index] = arg;
   	            index++;
   	        }

//   	        final Object thisObject = (isStatic) ? null : code;
   	        return new CompilableTask(method, parameters);
   	}

    private static Method resolveRunnable(Runnable runnable) {
        final Class<?> type = runnable.getClass();
        try {
            final Method method = type.getDeclaredMethod("run");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }
}
