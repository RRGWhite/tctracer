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
  NCC("Naming Conventions Contains"), // Whether method name is contained in the test name
  NCC_CLASS("Naming Conventions Contains - Class"), // Whether method name is contained in the test name
  NCC_MULTI("Naming Conventions Contains - Multi"), // Whether method name is contained in the test name
  LCS_B_N("LCS-B (Normalised)"), // Name similarity - longest common subsequence
  LCS_B_N_CLASS("LCS-B - Class (Normalised) - Class"), // Name similarity - longest common subsequence
  LCS_B_N_MULTI("LCS-B - Multi (Normalised)"), // Name similarity - longest common subsequence
  LCS_U_N("LCS-U (Normalised)"), // Longest common subsequence normalised on method name
  LCS_U_N_CLASS("LCS-U - Class (Normalised) - Class"), // Longest common subsequence normalised on method name
  LCS_U_N_MULTI("LCS-U - Multi (Normalised)"), // Longest common subsequence normalised on method name
  LEVENSHTEIN_N("NS - Levenshtein (Normalised)"), // Scores are normalised across the methods executed by a test.
  LEVENSHTEIN_N_CLASS("NS - Levenshtein (Normalised) - Class"), // Scores are normalised across
  // the methods executed by a test.
  LEVENSHTEIN_N_MULTI("NS - Levenshtein (Normalised) - Multi"), // Scores are normalised across
  // the methods executed by a test.
  LCBA("Last Call Before Assert"),
  LCBA_CLASS("Last Call Before Assert - Class"),
  LCBA_MULTI("Last Call Before Assert - Multi"),
  TARANTULA("Fault Loc - Tarantula"),
  TARANTULA_CLASS("Fault Loc - Tarantula - Class"),
  TARANTULA_MULTI("Fault Loc - Tarantula - Multi"),
  TFIDF("tf-idf - 3_2"),
  TFIDF_CLASS("tf-idf - 3_2 - Class"),
  TFIDF_MULTI("tf-idf - 3_2 - Multi"),
  COMBINED("Combined"),
  COMBINED_CLASS("Combined - Class"),
  COMBINED_MULTI("Combined - Multi"),
  COMBINED_FFN("Combined - Feed Forward Network"),
  COMBINED_CLASS_FFN("Combined - Feed Forward Network - Class"),
  COMBINED_MULTI_FFN("Combined - Feed Forward Network - Multi"),
  STATIC_NC("Static Naming Conventions"),
  STATIC_NC_CLASS("Static Naming Conventions - Class"),
  STATIC_NC_MULTI("Static Naming Conventions - Multi"),
  STATIC_NCC("Static Naming Conventions Contains"),
  STATIC_NCC_CLASS("Static Naming Conventions Contains - Class"),
  STATIC_NCC_MULTI("Static Naming Conventions Contains - Multi"),
  STATIC_LCS_B_N("Static LCS-B (Normalised)"),
  STATIC_LCS_B_N_CLASS("Static LCS-B - Class (Normalised) - Class"),
  STATIC_LCS_B_N_MULTI("Static LCS-B - Multi (Normalised)"),
  STATIC_LCS_U_N("Static LCS-U (Normalised)"),
  STATIC_LCS_U_N_CLASS("Static LCS-U - Class (Normalised) - Class"),
  STATIC_LCS_U_N_MULTI("Static LCS-U - Multi (Normalised)"),
  STATIC_LEVENSHTEIN_N("Static NS - Levenshtein (Normalised)"),
  STATIC_LEVENSHTEIN_N_CLASS("Static NS - Levenshtein (Normalised) - Class"),
  STATIC_LEVENSHTEIN_N_MULTI("Static NS - Levenshtein (Normalised) - Multi"),
  STATIC_LCBA("Static Last Call Before Assert"),
  STATIC_LCBA_CLASS("Static Last Call Before Assert - Class"),
  STATIC_LCBA_MULTI("Static Last Call Before Assert - Multi");

  private final String text;

  Technique(String text) {
    this.text = text;
  }

  @Override
  public String toString() {
    return text;
  }
}
