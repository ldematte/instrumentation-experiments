module org.elasticsearch.instrumentation.agent {
    requires java.instrument;
    requires org.objectweb.asm.util;
    requires java.compiler;

    exports org.elasticsearch;
}