package ctt.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-Link Annotation
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CTTTrace {

  String[] value();
}
