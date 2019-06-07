package ctt.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utilities {
    private static Map<Executable, String> methodStrCache = new ConcurrentHashMap<>();

    public static String getMethodString(Executable method) {
        String methodStr = methodStrCache.get(method);
        if (methodStr != null) {
            return methodStr;
        } else {
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

            methodStrCache.put(method, methodStr);
            return methodStr;
        }
    }
}
