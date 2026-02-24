# Corda Solana Notary

The Corda Solana [notary](https://docs.r3.com/en/platform/corda/4.12/community/key-concepts-notaries.html) is an
on-chain Solana program that records consumed
[`StateRef`](https://docs.r3.com/en/api-ref/corda/4.12/community/javadoc/net/corda/core/contracts/StateRef.html)s.
An appropriately configured Corda notary node can delegate the tracking of spent states to this program.

## Overview

This repo is a multi-module Gradle project with the following modules. Each module which is published has a Maven
group ID of `net.corda.solana.notary`. It uses the
[axion-release-plugin](https://axion-release-plugin.readthedocs.io/en/latest/) for managing the version based on git
tags.

### `program`

The on-chain Solana program written using Anchor. It has its own [Gradle build file](program/build.gradle.kts)
which hooks the Cargo/Anchor build process into Gradle's. This means, for example, running `./gradlew test` will
run the tests in all the modules, including the Rust-based tests in this one.

This module also has a Maven publication in the form a Jar file containing the compiled program binary and Anchor IDL
file. The version of the program and IDL is in-sync with the other modules.

### `kotlin-client`

Generated Kotlin client for the program targeting a forked version of [Sava](https://github.com/corda/sava).

In terms of source code, this module only contains an Anchor IDL code generator. It is invoked before the Kotlin
compilation phase, generating the client source code.

### `testing`

Testing library for writing JUnit tests which need the Solana notary. In contains
[`SolanaNotaryExtension`](testing/src/main/kotlin/net/corda/solana/notary/testing/SolanaNotaryExtension.kt) which
will automatically spin up a `solana-test-validator` configured with the notary program and ready to use.

### `admin-cli`

Admin CLI for managing the notary program.

## Build

To test and build the entire project, including the Solana program:

```shell
./gradlew clean build
```

The projects are build and published with `solana-notary-` prefix,
so for example the program artifact is `net.corda.solana.notary:solana-notary-program:<VERSION>>`.
