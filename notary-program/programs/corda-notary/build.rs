fn main() {
    // Set the Cargo package version to the one from Gradle. This means the version in the program
    // IDL is in-sync with the rest of the project.
    if let Some(gradle_version) = option_env!("GRADLE_VERSION") {
        println!("cargo::rustc-env=CARGO_PKG_VERSION={gradle_version}")
    } else {
        eprintln!("GRADLE_VERSION env not set")
    }
}
