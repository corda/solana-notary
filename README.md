# Corda Solana Notary

The Corda Solana [notary](https://docs.r3.com/en/platform/corda/4.12/community/key-concepts-notaries.html) is an
on-chain Solana program that records consumed
[`StateRef`](https://docs.r3.com/en/api-ref/corda/4.12/community/javadoc/net/corda/core/contracts/StateRef.html)s.
An appropriately configured Corda notary node can delegate the tracking of spent states to this program.

## Overview

This repo is a multi-module Gradle project with the following modules. Each module which is published has a Maven
group ID of `net.corda.solana.notary`. It uses the
[axion-release-plugin](https://axion-release-plugin.readthedocs.io/en/latest/) for managing the verion based on git
tags.

### `solana-program`

The on-chain Solana program written using Anchor. It has its own [Gradle build file](solana-program/build.gradle.kts)
which hooks the Cargo/Anchor build process into Gradle's. This means, for example, running `./gradlew test` will
run the tests in all the modules, including the Rust-based tests in this one.

This module also has a Maven publication in the form a Jar file containing the compiled program binary and Anchor IDL
file. The version of the program and IDL is in-sync with the other modules.

### `kotlin-client`

Generated Kotlin client for the program using Solana4j. This module only contains the code generator.

Add this module as an `implementation` dependency if you need to use the generated classes, which can be found under
`build/generated/src/main/kotlin`.  The code generation occurs automatically as part of Kotlin compilation, but if you
find the code is out of sync then run

```shell
./gradlew generateKotlinClient
```

### `admin-cli`

Admin CLI for managing the notary program.

### `common`

Kotlin utilties and helpers when using Solana4j.

### `notary-test`

Testing helper library for when writing JUnit tests which need the Solana notary.

## Build

To test and build the entire project, including the Solana program:

```shell
./gradlew clean build
```
