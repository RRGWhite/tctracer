package ctt;

// List of techniques.
// All name-similarity techniques are case insensitive.
// Note: When adding/modifying a technique, also update the default threshold value if applicable in Configuration.java.
public enum Technique {
    GROUND_TRUTH            ("Ground Truth"),
    NS_CONTAINS             ("NS - Contains"), // Whether method name is contained in the test name
    NS_COMMON_SUBSEQ        ("NS - LCS-B"), // Name similarity - longest common subsequence
    NS_COMMON_SUBSEQ_FUZ    ("NS - LCS-M"), // Longest common subsequence normalised on method name
    NS_LEVENSHTEIN          ("NS - Levenshtein"), // Levenshtein distance
    NS_LEVENSHTEIN_N        ("NS - Levenshtein (Normalised)"), // Scores are normalised across the methods executed by a test.
    LAST_CALL_BEFORE_ASSERT ("Last Call Before Assert"),
    FAULT_LOC_TARANTULA     ("Fault Loc - Tarantula"),
    FAULT_LOC_OCHIAI        ("Fault Loc - Ochiai"), // not implemented
    IR_TFIDF_11             ("tf-idf - 1_1"),
    IR_TFIDF_12             ("tf-idf - 1_2"),
    IR_TFIDF_21             ("tf-idf - 2_1"),
    IR_TFIDF_22             ("tf-idf - 2_2"),
    IR_TFIDF_31             ("tf-idf - 3_1"),
    IR_TFIDF_32             ("tf-idf - 3_2"),
    COVERAGE                ("Coverage"); // Use coverage scores directly as traceability scores. (normalised)

    private final String text;
    Technique(String text) {
        this.text = text;
    }
    @Override public String toString() { return text; }
}
