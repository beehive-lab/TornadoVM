package uk.ac.manchester.tornado.runtime;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.common.ParallelAnnotationProvider;

public interface ASMClassVisitorProvider {
    ParallelAnnotationProvider[] getParallelAnnotations(ResolvedJavaMethod method);
}
