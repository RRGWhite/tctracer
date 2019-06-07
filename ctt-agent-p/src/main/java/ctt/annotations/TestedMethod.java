package ctt.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Author: Robert White @ UCL
 *
 * Note from Raymond:
 * I have added @Retention(RetentionPolicy.RUNTIME) to this annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TestedMethods.class)
public @interface TestedMethod {
    String judges() default "";
    String fqMethodName() default "";
}
