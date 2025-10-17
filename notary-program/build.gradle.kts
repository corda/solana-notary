plugins {
    java
//    id "corda.common-publishing"
}

tasks.register<Exec>("solanaClean") {
    commandLine("anchor", "clean")
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

tasks.clean {
    dependsOn("solanaClean")
}

tasks.test {
    dependsOn("solanaTest")
}

tasks.processResources {
    from(tasks.named("solanaBuild")) {
        into("net/corda/solana/notary/program")
    }
    from(tasks.named("generateIdl")) {
        into("net/corda/solana/notary/program")
    }
}

tasks.jar {
    archiveBaseName = "corda-solana-notary-program"
}

tasks.register<Exec>("solanaTest") {
    commandLine("anchor", "test")
}

tasks.register<Exec>("rustfmtCheck") {
    commandLine("cargo", "fmt", "--all", "--check")
}

//publishing {
//    publications {
//        maven(MavenPublication) {
//            artifactId jar.baseName
//            from components.java
//        }
//    }
//}
