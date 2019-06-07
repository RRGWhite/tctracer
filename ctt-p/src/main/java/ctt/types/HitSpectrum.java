package ctt.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * [Used for JSON (de)/serialization]
 * The set of methods that were hit in a test.
 */
public class HitSpectrum {
    public String cls;
    public String func;
    public Map<String, Integer> hitSet = new HashMap<>(); // map of method to lowest call depth
    public Set<String> callsBeforeAssert = new HashSet<>();
    public Set<String> groundTruth = new HashSet<>();

    public String getTestName() {
        return this.cls + "." + this.func;
    }
}
