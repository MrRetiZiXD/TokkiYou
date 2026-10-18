plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

// What runs the engine on a computer, to check a patch before it is handed out.
val cli: Configuration by configurations.creating

dependencies {
    // the two libraries a patch is read with; both plain Java, so the same
    // code runs on a phone and on the computer that checks the patch
    api("org.tukaani:xz:1.10")
    api("com.jcraft:jzlib:1.1.3")
    // Android has this one built in
    compileOnly("org.json:json:20240303")
    cli("org.json:json:20240303")
}

tasks.register<JavaExec>("patch") {
    description = "Apply a patch on this computer: -Pbase=… -Ppatch=… -Pout=…"
    mainClass.set("cat.narezany.patcher.core.CliKt")
    classpath = sourceSets["main"].runtimeClasspath + cli
    jvmArgs("-Xmx1g")
    args(listOfNotNull(
        project.findProperty("base") as String?,
        project.findProperty("patch") as String?,
        project.findProperty("out") as String?,
    ))
}
