plugins {
    id("default-java")
}

// The only the main classes need to be Java 17 compatible. The tests can use the default JDK.
tasks.compileJava {
    options.release = 17
}
