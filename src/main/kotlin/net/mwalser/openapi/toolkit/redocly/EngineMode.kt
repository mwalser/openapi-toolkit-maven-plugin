package net.mwalser.openapi.toolkit.redocly

/** How the embedded JavaScript engine executes the Redocly code. */
enum class EngineMode {
    /**
     * Pick the fastest available option: in-process JIT when the host JVM supports it (GraalVM JDK),
     * otherwise a polyglot isolate if one is on the plugin classpath, otherwise the interpreter.
     */
    AUTO,

    /** Plain GraalJS interpreter. Works on any JDK 21+, no extra dependencies, slowest. */
    INTERPRETER,

    /**
     * Run GraalJS as a pre-compiled native image inside the JVM process (polyglot isolate).
     * Requires `org.graalvm.polyglot:js-isolate-<os>-<arch>-community` on the plugin classpath.
     */
    ISOLATE,
}
