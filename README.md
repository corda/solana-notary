# Corda Solana Notary
![Maven](https://img.shields.io/maven-metadata/v?metadataUrl=https://download.corda.net/maven/corda-dependencies/net/corda/solana/notary/solana-notary-program/maven-metadata.xml&label=Maven)

The Corda Solana [notary](https://docs.r3.com/en/platform/corda/4.13/community/key-concepts-notaries.html) is an
on-chain Solana program that tracks consumed
[`StateRef`](https://docs.r3.com/en/api-ref/corda/4.13/community/javadoc/net/corda/core/contracts/StateRef.html)s.
An appropriately configured Corda notary can delegate the tracking of spent states to this program.

The program is on both [mainnet](https://solscan.io/account/notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY) and
[devnet](https://solscan.io/account/notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY?cluster=devnet) with the program ID
`notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY`.

## Overview

This repository is a multi-module Gradle project. Each module has a group ID of `net.corda.solana.notary` and an
artifact ID prefixed with`solana-notary-`. For example, the Maven coordinates for `testing` is
`net.corda.solana.notary:solana-notary-testing`.

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
which hooks into the Anchor build process.

The published Maven artifact is a Jar file containing the program .so binary and Anchor IDL file.

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

## Development

To run all the tests, including the Anchor tests in `program`:

```shell
./gradlew check
```

To clean everything, including Rust/Anchor build artifacts:

```shell
./gradlew clean
```

To (re)generate the Kotlin client:

```shell
./gradlew kotlin-client:compileKotlin
```

## Publishing a release

The [axion-release-plugin](https://axion-release-plugin.readthedocs.io/en/latest/) is used for managing the version
via git tags. Run the following to get the current
[SNAPSHOT](https://maven.apache.org/guides/getting-started/#what-is-a-snapshot-version) version:

```shell
./gradlew -q currentVersion
```

> [!NOTE]
> This is the same version across all the modules. Tt is even included in the Anchor IDL of the notary program. This
> is done by [dynamically setting](program/programs/corda-notary/build.rs) the `CARGO_PKG_VERSION` environment variable.

Assuming the version is `0.1.9-SNAPSHOT`. Add a `v` prefix and remove the `-SNAPSHOT` suffix for the next version tag:

```shell
git tag v0.1.9
git push origin v0.1.9
```

Running `./gradlew -q currentVersion` again will print

```
Project version: 0.1.9
```

This is because the current commit is now on a version tag. The CI pipeline will publish this version when run.

The next SNAPSHOT version (`0.1.10-SNAPSHOT` going with our example) will occur automatically when the main branch
advances past this tag.

## Licensing

Each module is released under [difference licenses](LICENSE.md).
