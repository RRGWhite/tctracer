package ctt.types;

import ctt.MethodParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Method implements Serializable {

  private String file;
  private String methodPackage;
  private String className;
  private String name;
  private String header;
  private String src;
  private int startLine;
  private int endLine;
  private List<Parameter> params;
  private List<Annotation> annotations;

  public Method() {
    /* create a blank method */
    this.methodPackage = "";
    this.name = "";
    this.className = "";
    this.params = new ArrayList<>();
  }

  public Method(String file,
      String methodPackage,
      String className,
      String name,
      String src,
      int startLine,
      int endLine,
      List<Parameter> params,
      String header,
      List<Annotation> annotations) {
    this.file = file;
    this.methodPackage = methodPackage;
    this.className = className;
    this.name = name;
    this.src = src;
    this.startLine = startLine;
    this.endLine = endLine;
    this.params = params;
    this.header = header;
    this.annotations = annotations;
  }

  public String getHeader() {
    return header;
  }

  public void setHeader(String header) {
    this.header = header;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public String getName() {
    return name;
  }

  public String getFullyQualifiedMethodName() {

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < params.size(); i++) {
      Parameter p = params.get(i);
      if (i == 0) {
        sb.append(p.getType());
        sb.append(" ");
        sb.append(p.getId());
      } else {
        sb.append(", ");
        sb.append(p.getType());
        sb.append(" ");
        sb.append(p.getId());
      }
    }
    String paramsStr = sb.toString();

    return this.getMethodPackage() + "."
        + this.getClassName() + "#"
        + this.getName() + "("
        + paramsStr + ")";
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSrc() {
    return src;
  }

  public void setSrc(String src) {
    this.src = src;
  }

  public int getStartLine() {
    return startLine;
  }

  public int getEndLine() {
    return endLine;
  }

  public List<Parameter> getParams() {
    return params;
  }

  public void setStartLine(int startLine) {
    this.startLine = startLine;
  }

  public void setEndLine(int endLine) {
    this.endLine = endLine;
  }

  public void setParams(List<Parameter> params) {
    this.params = params;
  }

  public String getMethodPackage() {
    return methodPackage;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getClassName() {
    return this.className;
  }

  public void setMethodPackage(String methodPackage) {
    this.methodPackage = methodPackage;
  }

  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public String toString() {
    return methodPackage + "." + className + "." + name + "," + params;
  }

  @Override
  public int hashCode() {

    // adapted from https://www.mkyong.com/java/java-how-to-overrides-equals-and-hashcode/

    int result = 17;
    result = 31 * result + methodPackage.hashCode();
    result = 31 * result + className.hashCode();
    result = 31 * result + name.hashCode();

    for (Parameter p : params) {
      result = 31 * result + p.hashCode();
    }

    return result;
  }

  public boolean equals(Object o) {
    if (o == null || !(o instanceof Method)) {
      return false;
    }

    Method m = (Method) o;

    if (MethodParser.normaliseSrc(this.getSrc()).equals(MethodParser.normaliseSrc(m.getSrc()))
        && this.getClassName().equals(m.getClassName())) {
      //Logger.get().logAndPrintLn("DEBUGGING METHOD COMPARES");
      return true;
    }

    return false;
    /*int countParamsMatches = 0;

    // if the no. of params is different, they're not equal
    if (params.size() != m.getParams().size()) {
      return false;
    } else {
      for (int i = 0; i < params.size(); i++) {
        if (params.get(i).equals(m.getParams().get(i))) {
          countParamsMatches++;
        }
      }

      return methodPackage.equals(m.getMethodPackage())
          && className.equals(m.getClassName())
          && name.equals(m.getName())
          && countParamsMatches == params.size();
    }*/
  }
}
