# Notary Program

This module has the [Rust notary program](programs/corda-notary) and an Anchor IDL [code generator](src/codegen) for Kotlin.

## Generated Code

Add this module as an `implementation` dependency if you need to use the generated classes, which can be found under
`build/generated/src/main/kotlin`.  The code generation occurs automatically as part of Kotlin compilation, but if you find the code is
out of sync then run

```shell
./gradlew generateAnchorIdl
```

## Formatting

CI checks the Rust code is [correctly formatted](../../rustfmt.toml) using [Rustfmt](https://rust-lang.github.io/rustfmt). You can apply the
correct format by running

```shell
cargo fmt --all
```

You may want to [setup your IDE](https://github.com/rust-lang/rustfmt?tab=readme-ov-file#running-rustfmt-from-your-editor) to automatically
run this, or a Git hook.
