package dev.syrkbuilder.core.session;

public interface Services {
    TemplateStore templates();

    String serverScript(String name);

    long scriptTimeoutMillis();
}
