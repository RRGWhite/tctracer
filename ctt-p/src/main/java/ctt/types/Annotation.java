package ctt.types;

import java.io.Serializable;
import java.util.HashMap;

public class Annotation implements Serializable {

  private String name;
  private HashMap<String, String> pairs;

  public Annotation(String name, HashMap<String, String> pairs) {
    this.name = name;
    this.pairs = pairs;
  }

  public String getName() {
    return name;
  }

  public HashMap<String, String> getPairs() {
    return pairs;
  }
}
