package ctt;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains utilities to interact with RW's traceability ground truth corpus.
 */
public class RWUtilities {

    public static class RWMethod {
        public String className;
        public String methodName;
        public List<String> argTypes;

        public RWMethod(String className, String methodName, List<String> argTypes) {
            this.className = className;
            this.methodName = methodName;
            this.argTypes = argTypes;
        }

      @Override
      public String toString() {
        String fqn = className + '.' + methodName + '(';
        boolean first = true;
        for (String argType : argTypes) {
          if (first) {
            fqn = fqn + argType;
            first = false;
          } else {
            fqn = fqn + "," + argType;
          }
        }

        fqn = fqn + ")";
        return fqn;
      }
    }

    public static RWMethod parseRWMethodName(String str) {
      try {
        int idx_hash = str.indexOf( '#' );
        int idx_firstOpenBracket = str.indexOf( '(' );
        int idx_lastCloseBracket = str.lastIndexOf( ')' );

        String className = str.substring( 0, idx_hash );
        String methodName = str.substring( idx_hash + 1, idx_firstOpenBracket );
        String signatureStr = str.substring( idx_firstOpenBracket + 1, idx_lastCloseBracket );

        signatureStr = signatureStr.replaceAll( "<.*>", "" ); // drop generic specifiers
        signatureStr = signatureStr.replace( "final ", "" ); // drop 'final' specifiers
        signatureStr = signatureStr.replace( "...", "[]" ); // replace variable arity (...) with array []

        // Parse method signature
        List<String> args = new ArrayList<>();
        String[] arguments = signatureStr.split( "," ); // [String arg1, boolean arg2, ...]
        for (String argument : arguments)
        {
          if (argument.trim().length() > 0)
          {
            int idx_space = argument.lastIndexOf( ' ' );
            String argType = idx_space == -1 ? argument : argument.substring( 0, idx_space );
            args.add( argType.trim() );
          }
        }

        return new RWMethod( className, methodName, args );
      } catch (Exception e) {
        return null;
      }
    }

    // Given a class, method name and list of argument types (simple names), returns the method.
    public static Executable getClassMethod(Class cls, String methodName, List<String> argTypes) {
        // For each method in the class
        LinkedHashSet<Executable> methods = new LinkedHashSet<>(); // must be LinkedHashSet to preserve insertion order

        // Including non-public methods, but excluding inherited methods
        methods.addAll(Arrays.asList(cls.getDeclaredMethods()));
        methods.addAll(Arrays.asList(cls.getDeclaredConstructors()));

        // Including inherited methods, but excluding non-public methods
        methods.addAll(Arrays.asList(cls.getMethods()));
        methods.addAll(Arrays.asList(cls.getConstructors()));

        if (cls.getSimpleName().equals(methodName)) {
            // Looking for constructor (class name == method name)
            // Set method to class name (this is how ctors are named)
            methodName = cls.getCanonicalName();
        }

        methodLoop:
        for (Executable method : methods) {
            // Check for name match
            // System.out.println("Method: " + method.getName() + " Target: " + methodName);

            if (method.getName().equals(methodName)) {
                // Name matches, now check if parameters match

                Class<?>[] paramTypes = method.getParameterTypes();
                Type[] genericTypes = method.getGenericParameterTypes();

                if (paramTypes.length == argTypes.size()) {
                    // Loop through all method parameters
                    for (int i = 0; i < paramTypes.length; i++) {
                        // Move onto next method if any parameter does not match
                        String simpleName = paramTypes[i].getSimpleName();
                        String genericName = genericTypes[i].getTypeName();
                        // System.out.println(simpleName);
                        String argType = argTypes.get(i);
                        if (!simpleName.equals(argType) && !genericName.equals(argType)) {
                            continue methodLoop;
                        }
                    }
                    return method; // all parameters match
                }
            }
        }
        return null;
    }

  public static String getMethodString(Executable method) {
    String methodStr;
    Type[] trueMethodParameterTypes = method.getGenericParameterTypes();

    String trueParamsStr = Stream.of(trueMethodParameterTypes)
        .map(Type::getTypeName)
        .collect(Collectors.joining(", "));

    if (method instanceof Constructor) {
      // methodStr = String.format("%s(%s)", method.getName(), trueParamsStr);
      methodStr = String.format("%s.<init>(%s)", method.getDeclaringClass().getCanonicalName(), trueParamsStr);
    } else {
      methodStr = String.format("%s.%s(%s)", method.getDeclaringClass().getCanonicalName(), method.getName(), trueParamsStr);
    }
    return methodStr;
  }
}
