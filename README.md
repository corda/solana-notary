# Corda Solana Notary
![Maven](https://img.shields.io/maven-metadata/v?metadataUrl=https://download.corda.net/maven/corda-dependencies/net/corda/solana/notary/solana-notary-program/maven-metadata.xml&label=Maven)

The Corda Solana [notary](https://docs.r3.com/en/platform/corda/4.13/community/key-concepts-notaries.html) is an
on-chain Solana program that tracks consumed
[`StateRef`](https://docs.r3.com/en/api-ref/corda/4.13/community/javadoc/net/corda/core/contracts/StateRef.html)s.
An appropriately configured Corda notary can delegate the tracking of spent states to this program. It's deployed to
both [mainnet](https://solscan.io/account/notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY) and
[devnet](https://solscan.io/account/notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY?cluster=devnet) with the program ID
`notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY`.

## Overview

This repository is a multi-module Gradle project. Each (published) module has a group ID of `net.corda.solana.notary`
and an artifact ID prefixed with`solana-notary-`. For example `testing` is available at
`net.corda.solana.notary:solana-notary-testing:<VERSION>`.

The artifacts are available at the following Maven repo:

```kotlin
repositories {
    maven {
        url = uri("https://download.corda.net/maven/corda-dependencies")
    }
}
```

### [`program`](program/README.md)
[![License](https://img.shields.io/badge/License-BUSL%201.1-orange.svg)](program/LICENSE)

The on-chain Solana program written using Anchor. It has its own [Gradle build file](program/build.gradle.kts)
which hooks the Cargo/Anchor build process into Gradle's. This means, for example, running `./gradlew test` will
run the tests in all the modules, including the Rust-based tests in this one.

This module also has a Maven publication in the form a Jar file containing the compiled program binary and Anchor IDL
file. The version of the program and IDL is in-sync with the other modules.

### `kotlin-client`
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](kotlin-client/LICENSE)

Generated Kotlin client for the program targeting a forked version of [Sava](https://github.com/corda/sava).

In terms of source code, this module only contains an Anchor IDL code generator. It is invoked before the Kotlin
compilation phase, generating the client source code.

### `testing`
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](testing/LICENSE)

Testing library for writing JUnit tests which need the Solana notary. In contains
[`SolanaNotaryExtension`](testing/src/main/kotlin/net/corda/solana/notary/testing/SolanaNotaryExtension.kt) which
will automatically spin up a `solana-test-validator` configured with the notary program and ready to use.

### [`admin-cli`](admin-cli/README.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](admin-cli/LICENSE)

Admin CLI for managing the notary program.

## Build

To test and build the entire project, including the Solana program:

```shell
./gradlew clean build
```

This project uses the [axion-release-plugin](https://axion-release-plugin.readthedocs.io/en/latest/) for managing the version based on git tags.

## Licensing

Each module is released under [difference licenses](LICENSE.md).
