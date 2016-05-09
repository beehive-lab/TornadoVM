package tornado.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER,ElementType.LOCAL_VARIABLE,ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Atomic {

}
