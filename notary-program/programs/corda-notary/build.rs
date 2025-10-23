fn main() {
    // Set the Cargo package version to the one from Gradle. This means the version in the program
    // IDL is in-sync with the rest of the project.
    let gradle_version = option_env!("GRADLE_VERSION").expect("GRADLE_VERSION not set");
    println!("cargo::rustc-env=CARGO_PKG_VERSION={gradle_version}");
}
