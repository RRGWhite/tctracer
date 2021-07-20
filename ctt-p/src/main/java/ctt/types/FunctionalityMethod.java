package ctt.types;

import java.util.List;

public class FunctionalityMethod extends Method {

  public FunctionalityMethod() {
    super();
  }

  public FunctionalityMethod(String file,
      String methodPackage,
      String className,
      String name,
      String src,
      int startLine,
      int endLine,
      List<Parameter> params,
      String header,
      List<Annotation> annotations) {
    super(file, methodPackage, className, name, src, startLine, endLine, params, header,
        annotations);
  }
}
