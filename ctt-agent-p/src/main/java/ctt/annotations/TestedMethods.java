package ctt.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Author: Robert White @ UCL
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TestedMethods {
    TestedMethod[] value() default {};
}
