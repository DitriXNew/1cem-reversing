import java.util.*

plugins {
    id("base")
    id("com.novoda.build-properties") version "0.4.1"
}

buildProperties {
    create("local") {
        using(project.file("local.properties"))
    }
}
val localProperties = buildProperties["local"].asMap()

val fileSrcApk = layout.projectDirectory.dir("sourceApk").asFileTree.singleFile
val apkFilePath = fileSrcApk.path
val apkName = fileSrcApk.nameWithoutExtension
val packageName = "com.e1c.mobile"

val androidSdkDir = localProperties["sdk.dir"]?.string?.trim() ?: error("Need define path to android sdk")

val keystorePath = localProperties["keystore.path"]?.string?.trim() ?: error("Need define path to keystore")
val keystorePassword = localProperties["keystore.password"]?.string?.trim() ?: error("Need define keystore password")
val keyAlias = localProperties["key.alias"]?.string?.trim() ?: error("Need define key alias")
val keyPassword = localProperties["key.password"]?.string?.trim() ?: error("Need define key password")

val buildToolsVersion = localProperties["buildToolsVersion"]?.string?.trim()
        ?: error("Need define sdk build tools version")

val runOnWindows = runOnWindows()

tasks {
    task("print") {
        doLast {
            println(apkName)
        }
    }

    val apktoolUnpackDestinationDir = "${layout.buildDirectory.dir("unpacked").get().asFile.path}"
    val unpack = task("unpack", type = Exec::class) {
        doFirst {
            if (runOnWindows) {
                commandLine("PowerShell", "apktool.bat", "-f", "d $apkFilePath", "-o $apktoolUnpackDestinationDir")
            } else {
                executable("sh")
                args("-c", "apktool -f d $apkFilePath -o $apktoolUnpackDestinationDir")
            }
        }
    }

    val copySmaliFiles = task("copySmaliFiles", type = Copy::class) {
        dependsOn(unpack)

        from(layout.projectDirectory.dir("src/smali").asFile.path) {
            include("**/*.smali")
        }

        destinationDir = layout.buildDirectory.dir("unpacked/smali").get().asFile
    }

    val copyResFiles = task("copyResFiles", type = Copy::class) {
        dependsOn(unpack)

        from(layout.projectDirectory.dir("src/res").asFile.path) {
            include("**/*")
        }

        destinationDir = layout.buildDirectory.dir("unpacked/res").get().asFile
    }

    val apktoolPackDestinationDir = "${layout.buildDirectory.get().asFile.path}${File.separator}$apkName.apk"
    val pack = task("pack", type = Exec::class) {
        dependsOn(copySmaliFiles)
        dependsOn(copyResFiles)

        if (runOnWindows) {
            commandLine("PowerShell", "apktool.bat  b $apktoolUnpackDestinationDir -o $apktoolPackDestinationDir")
        } else {
            executable("sh")
            args("-c", "apktool b $apktoolUnpackDestinationDir -o $apktoolPackDestinationDir")
        }

    }

    val sign = task("sign", type = Exec::class) {
        dependsOn(pack)
        val separator = File.separator
        if (runOnWindows) {
            commandLine(
                    "PowerShell",
                    "${androidSdkDir}${separator}build-tools$separator$buildToolsVersion${separator}apksigner.bat sign"
                            + " --ks $keystorePath "
                            + " --ks-key-alias $keyAlias "
                            + " --ks-pass pass:$keystorePassword "
                            + " --key-pass pass:$keyPassword "
                            + apktoolPackDestinationDir
            )
        } else {
            executable("sh")
            args("-c", "${androidSdkDir}${separator}build-tools$separator$buildToolsVersion${separator}apksigner sign"
                    + " --ks $keystorePath "
                    + " --ks-key-alias $keyAlias "
                    + " --ks-pass pass:$keystorePassword "
                    + " --key-pass pass:$keyPassword "
                    + apktoolPackDestinationDir
            )
        }

    }
    assemble.get().dependsOn(sign)

    val installApk = task("installApk", type = Exec::class) {
        //        dependsOn(assemble.get())
        if (runOnWindows) {
            commandLine(
                    "PowerShell",
                    "adb install ${layout.buildDirectory.file("$apkName.apk").get().asFile.path}"
            )
        } else {
            executable("sh")
            args("-c", "")
            TODO("Пока не реализовал")
        }
    }

    val grantPermissionReadStorage = task("grantPermissionReadStorage", type = Exec::class) {
        dependsOn(installApk)
        commandLine(
                "PowerShell",
                "adb shell pm grant $packageName android.permission.READ_EXTERNAL_STORAGE"
        )
    }

    val pathToCf = "/storage/emulated/0/Download"
    val push1cCf = task("push1cCf", type = Exec::class) {
        dependsOn(grantPermissionReadStorage)
        commandLine(
                "PowerShell",
                "adb push ${layout.projectDirectory.dir("src/1cConfiguration").asFile.path} $pathToCf"
        )
    }

    val run1cApk = task("run1cApk", type = Exec::class) {
        dependsOn(push1cCf)
        commandLine(
                "PowerShell",
                "adb shell am start -n com.e1c.mobile/.App -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
        )

    }

    val runOnDevice = task("runOnDevice", type = Exec::class) {
        dependsOn(run1cApk)
        commandLine(
                "PowerShell",
                "adb shell am broadcast -n com.e1c.mobile/.Starter -a com.e1c.mobile.START_TEMPLATE -e templatepath $pathToCf/1cConfiguration/1cema.xml "
        )
    }
    
}

fun runOnWindows(): Boolean = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
