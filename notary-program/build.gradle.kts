plugins {
    java
    id("r3-artifactory")
}

tasks.register<Exec>("solanaClean") {
    commandLine("anchor", "clean")
}

tasks.clean {
    dependsOn("solanaClean")
}

tasks.register<Exec>("solanaBuild") {
    inputs.files("Cargo.toml", "programs/corda-notary/Cargo.toml")
    inputs.dir("programs/corda-notary/src")
    outputs.file("target/deploy/corda_notary.so")
    commandLine("anchor", "build")
}

tasks.register<Exec>("generateIdl") {
    dependsOn("solanaBuild")
    val idlFile = layout.buildDirectory.file("idl.json").get()
    outputs.file(idlFile)
    commandLine("anchor", "idl", "build", "-o", idlFile)
}

tasks.processResources {
    from(tasks.named("solanaBuild")) {
        into("net/corda/solana/notary/program")
    }
    from(tasks.named("generateIdl")) {
        into("net/corda/solana/notary/program")
    }
}

tasks.register<Exec>("solanaTest") {
    commandLine("anchor", "test")
}

tasks.test {
    dependsOn("solanaTest")
}

tasks.register<Exec>("rustfmtCheck") {
    commandLine("cargo", "fmt", "--all", "--check")
}

tasks.check {
    dependsOn("rustfmtCheck")
}
