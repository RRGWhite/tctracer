package ctt;

import com.google.common.collect.Table;

import java.util.function.Supplier;

public class Utilities {
    // Utility function that implements a function similar to Java 8's computeIfAbsent for Guava tables
    public static <R, C, V> V createIfAbsent(Table<R, C, V> table, R rowKey, C columnKey, Supplier<? extends V> mappingFunction) {
        V value = table.get(rowKey, columnKey);
        if (value == null) {
            value = mappingFunction.get();
            table.put(rowKey, columnKey, value);
        }
        return value;
    }
}
