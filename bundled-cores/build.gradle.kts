plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid.cores"
}

dependencies {
    implementation(kotlin(deps.libs.kotlin.stdlib))
}

// Materializes per-core jniLibs into this module's jniLibs before the Android build
// merges them into the APK.
//
// The checked-in .so files in bundled-cores/src/main/jniLibs/ are Unix symlinks
// pointing to the real binaries in the sibling lemuroid_core_<name> modules. On
// Windows without `core.symlinks = true`, git checks them out as ~90-byte text
// stubs containing the link target path. The Android build then bundles those
// stubs into the APK and the runtime native loader fails with "Núcleo do
// Libretro não foi carregado" — silently, with no useful error.
//
// This task overwrites any small (< 10KB) .so file in our jniLibs with the real
// ELF binary from the per-core source-of-truth module. It is a no-op on Linux/Mac
// (where the symlinks resolve correctly) and on Windows checkouts that already
// have the materialized binaries.
val materializeNativeLibs = tasks.register("materializeNativeLibs") {
    group = "build"
    description = "Repairs broken text-symlink .so stubs in bundled-cores/jniLibs by " +
        "copying real binaries from per-core lemuroid_core_<name> modules."

    val coresRoot = projectDir.parentFile
    val targetDir = file("src/main/jniLibs")

    doLast {
        var copied = 0
        var skippedExisting = 0
        coresRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("lemuroid_core_") }
            ?.forEach { coreDir ->
                val sourceJniLibs = File(coreDir, "src/main/jniLibs")
                if (!sourceJniLibs.isDirectory) return@forEach
                sourceJniLibs.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".so") }
                    .forEach { src ->
                        if (src.length() < 10_000L) return@forEach
                        val rel = src.relativeTo(sourceJniLibs)
                        val dst = File(targetDir, rel.path)
                        if (!dst.exists() || dst.length() < 10_000L) {
                            dst.parentFile.mkdirs()
                            src.copyTo(dst, overwrite = true)
                            copied++
                        } else {
                            skippedExisting++
                        }
                    }
            }
        logger.lifecycle(
            "materializeNativeLibs: copied=$copied skippedAlreadyMaterialized=$skippedExisting",
        )
    }
}

afterEvaluate {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(materializeNativeLibs)
    }
}
