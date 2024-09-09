module org.elasticsearch.instrumentation.agent {
    requires java.instrument;
    requires org.objectweb.asm.util;

    exports org.elasticsearch to java.base;
}