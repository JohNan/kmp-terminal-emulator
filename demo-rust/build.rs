use std::env;
use std::path::PathBuf;

fn main() {
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    let target_arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap_or_default();

    let target_dir_name = match (target_os.as_str(), target_arch.as_str()) {
        ("linux", "x86_64") => "linuxX64",
        ("macos", "aarch64") => "macosArm64",
        ("macos", "x86_64") => "macosX64",
        (os, arch) => {
            eprintln!("Warning: Unsupported target OS '{}' and arch '{}', defaulting to linuxX64", os, arch);
            "linuxX64"
        }
    };

    let manifest_dir = PathBuf::from(
        env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR not set"),
    );
    let release_path = manifest_dir.join(format!(
        "../terminal-core/build/bin/{}/releaseShared",
        target_dir_name
    ));
    let debug_path = manifest_dir.join(format!(
        "../terminal-core/build/bin/{}/debugShared",
        target_dir_name
    ));

    let lib_dir = if release_path.exists() {
        std::fs::canonicalize(&release_path).unwrap_or(release_path)
    } else if debug_path.exists() {
        std::fs::canonicalize(&debug_path).unwrap_or(debug_path)
    } else {
        std::fs::canonicalize(&release_path).unwrap_or(release_path)
    };

    println!("cargo:rustc-link-search=native={}", lib_dir.display());
    println!("cargo:rustc-link-lib=dylib=terminal_core");
    println!("cargo:rustc-link-arg=-Wl,-rpath,{}", lib_dir.display());
    println!("cargo:rerun-if-changed=../terminal-core/src/nativeMain/kotlin/com/johnan/terminal/core/ffi/TerminalNativeBridge.kt");
}
