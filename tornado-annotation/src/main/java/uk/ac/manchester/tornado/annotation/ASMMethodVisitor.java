package uk.ac.manchester.tornado.annotation;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;
import uk.ac.manchester.tornado.runtime.common.ParallelAnnotationProvider;

import java.util.List;

public class ASMMethodVisitor extends MethodVisitor {

    private List<ParallelAnnotationProvider> parallelAnnotations;
    //TODO
    // 1) should dynamically load the @Parallel annotation from the tornado-api module?
    // 2) Can I directly import it?
    // 3) Should we move the @Reduce and @Parallel annotations to this module? What problems would it cause?
    // 4) Hardcoded String for now. Should leave it like this?
//    static Class parallelAnnotationClass = null;
    static String parallelAnnotationClass = "uk.ac.manchester.tornado.api.annotations.Parallel";

    public ASMMethodVisitor(int api, MethodVisitor methodVisitor, List<ParallelAnnotationProvider> parallelAnnotations) {
        super(api, methodVisitor);
        this.parallelAnnotations = parallelAnnotations;
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(
            int typeRef,
            TypePath typePath,
            Label[] start,
            Label[] end,
            int[] index,
            String descriptor,
            boolean visible) {
        String annotationName = descriptor.
                replaceFirst("L", "").
                replaceAll(";", "").
                replaceAll("/", ".");

//        if (Parallel.class.getName().equals(annotationName)) {
        if (parallelAnnotationClass.equals(annotationName)) {
            ParallelAnnotationProvider parallelAnnotation =
                    new ParallelAnnotation(start[0].getOffset(), end[0].getOffset() - start[0].getOffset(), index[0]);
            parallelAnnotations.add(parallelAnnotation);
        }

        return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
    }
}
