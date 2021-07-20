package ctt.types;

import java.io.Serializable;
import java.util.Objects;

public class Parameter implements Serializable {

  private String type;
  private String id;

  public Parameter(String type, String id) {
    this.type = type;
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Parameter)) {
      return false;
    }
    Parameter parameter = (Parameter) o;
    return Objects.equals(getType(), parameter.getType()) &&
        Objects.equals(getId(), parameter.getId());
  }

  @Override
  public int hashCode() {

    return Objects.hash(getType(), getId());
  }

  @Override
  public String toString() {
    return type + " " + id;
  }
}
