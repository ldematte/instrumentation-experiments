package org.elasticsearch;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@SupportedAnnotationTypes({
        "org.elasticsearch.DelegateChecks"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class DelegateChecksAnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<Element> annotatedElements = new ArrayList<>(roundEnv.getElementsAnnotatedWith(DelegateChecks.class));

        for (var element: annotatedElements) {
            var className = ((ExecutableElement)element).getReturnType().toString();
            try {
                writeClassFile(className, annotatedElements);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private void writeClassFile(
            String className, List<Element> annotatedElements)
            throws IOException {

        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);

        String delegatingSimpleClassName = "Delegating" + simpleClassName;
        String delegatingClassName = packageName + "." + delegatingSimpleClassName;

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(delegatingClassName);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

            out.println("package " + packageName + ".generated;");
            out.println();

            out.println("import " + packageName + ".*;");
            out.println("import java.util.function.Supplier;");

            out.println(String.format("""
                    public class %s extends %s {
                        public %1$s() {
                            super(new Delegator() {
                                @Override
                                public <T> T delegate(Supplier<T> supplier) {
                                    return supplier.get();
                                }
                            });
                        }
                    }
                    """,
                    delegatingSimpleClassName,
                    simpleClassName));
        }
    }
}
