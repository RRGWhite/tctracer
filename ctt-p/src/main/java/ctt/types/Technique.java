package ctt.types;

// List of techniques.
// All name-similarity techniques are case insensitive.
// Note: When adding/modifying a technique, also update the default threshold value if applicable in Configuration.java.
public enum Technique {
  GROUND_TRUTH("Ground Truth"),
  GROUND_TRUTH_CLASS("Ground Truth - Class"),
  NC("Naming Conventions"),
  NC_CLASS("Naming Conventions - Class"),
  NC_MULTI("Naming Conventions - Multi"),
  NS_CONTAINS("NS - Contains"), // Whether method name is contained in the test name
  NS_CONTAINS_CLASS("NS - Contains - Class"), // Whether method name is contained in the test name
  NS_CONTAINS_MULTI("NS - Contains - Multi"), // Whether method name is contained in the test name
  NS_COMMON_SUBSEQ("NS - LCS-B"), // Name similarity - longest common subsequence
  NS_COMMON_SUBSEQ_CLASS("NS - LCS-B - Class"), // Name similarity - longest common subsequence
  NS_COMMON_SUBSEQ_MULTI("NS - LCS-B - Multi"), // Name similarity - longest common subsequence
  NS_COMMON_SUBSEQ_N("NS - LCS-B (Normalised)"), // Name similarity - longest common subsequence
  NS_COMMON_SUBSEQ_N_CLASS("NS - LCS-B - Class (Normalised)"), // Name similarity - longest common subsequence
  NS_COMMON_SUBSEQ_N_MULTI("NS - LCS-B - Multi (Normalised)"), // Name similarity - longest common subsequence
  NS_COMMON_SUBSEQ_FUZ("NS - LCS-U"), // Longest common subsequence normalised on method name
  NS_COMMON_SUBSEQ_FUZ_CLASS("NS - LCS-U - Class"), // Longest common subsequence normalised on// method name
  NS_COMMON_SUBSEQ_FUZ_MULTI("NS - LCS-U - Multi"), // Longest common subsequence normalised on// method name
  NS_COMMON_SUBSEQ_FUZ_N("NS - LCS-U (Normalised)"), // Longest common subsequence normalised on method name
  NS_COMMON_SUBSEQ_FUZ_N_CLASS("NS - LCS-U - Class (Normalised)"), // Longest common subsequence normalised on// method name
  NS_COMMON_SUBSEQ_FUZ_N_MULTI("NS - LCS-U - Multi (Normalised)"), // Longest common subsequence normalised on// method name
  NS_LEVENSHTEIN("NS - Levenshtein"), // Levenshtein distance
  NS_LEVENSHTEIN_CLASS("NS - Levenshtein - Class"), // Levenshtein distance
  NS_LEVENSHTEIN_MULTI("NS - Levenshtein - Multi"), // Levenshtein distance
  NS_LEVENSHTEIN_N("NS - Levenshtein (Normalised)"), // Scores are normalised across the methods executed by a test.
  NS_LEVENSHTEIN_N_CLASS("NS - Levenshtein (Normalised) - Class"), // Scores are normalised across the
  // methods executed by a test.
  NS_LEVENSHTEIN_N_MULTI("NS - Levenshtein (Normalised) - Multi"), // Scores are normalised across the
  // methods executed by a test.
  LAST_CALL_BEFORE_ASSERT("Last Call Before Assert"),
  LAST_CALL_BEFORE_ASSERT_CLASS("Last Call Before Assert - Class"),
  LAST_CALL_BEFORE_ASSERT_MULTI("Last Call Before Assert - Multi"),
  FAULT_LOC_TARANTULA("Fault Loc - Tarantula"),
  FAULT_LOC_TARANTULA_CLASS("Fault Loc - Tarantula - Class"),
  FAULT_LOC_TARANTULA_MULTI("Fault Loc - Tarantula - Multi"),
  FAULT_LOC_OCHIAI("Fault Loc - Ochiai"), // not implemented
  IR_TFIDF_11("tf-idf - 1_1"),
  IR_TFIDF_11_CLASS("tf-idf - 1_1 - Class"),
  IR_TFIDF_11_MULTI("tf-idf - 1_1 - Multi"),
  IR_TFIDF_12("tf-idf - 1_2"),
  IR_TFIDF_12_CLASS("tf-idf - 1_2 - Class"),
  IR_TFIDF_12_MULTI("tf-idf - 1_2 - Multi"),
  IR_TFIDF_21("tf-idf - 2_1"),
  IR_TFIDF_21_CLASS("tf-idf - 2_1 - Class"),
  IR_TFIDF_21_MULTI("tf-idf - 2_1 - Multi"),
  IR_TFIDF_22("tf-idf - 2_2"),
  IR_TFIDF_22_CLASS("tf-idf - 2_2 - Class"),
  IR_TFIDF_22_MULTI("tf-idf - 2_2 - Multi"),
  IR_TFIDF_31("tf-idf - 3_1"),
  IR_TFIDF_31_CLASS("tf-idf - 3_1 - Class"),
  IR_TFIDF_31_MULTI("tf-idf - 3_1 - Multi"),
  IR_TFIDF_32("tf-idf - 3_2"),
  IR_TFIDF_32_CLASS("tf-idf - 3_2 - Class"),
  IR_TFIDF_32_MULTI("tf-idf - 3_2 - Multi"),
  COVERAGE("Coverage"), // Use coverage scores directly as traceability scores. (normalised)
  COMBINED("Combined"), // Use coverage scores directly as traceability scores. (normalised)
  COMBINED_CLASS("Combined-Class"),
  COMBINED_MULTI("Combined-Multi");

  private final String text;

  Technique(String text) {
    this.text = text;
  }

  @Override
  public String toString() {
    return text;
  }
}
