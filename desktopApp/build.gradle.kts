import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The desktop application: window entry point plus the live-server verification harnesses. All shared
// UI and logic lives in :shared, which this module only consumes — matching the KMP default structure
// of one library module and a runnable app module per platform.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.compose.webview)
    implementation(libs.kcef)
}

/** Host/key for the live-server harnesses, read from the workspace's test.env (never committed). */
fun JavaExec.liveServerArgs() {
    // Workspace-root test.env; kodex/.env.test is the pre-move location, kept as a fallback so an
    // older checkout still runs. Not fatal when absent — this runs at configuration time, so
    // throwing here would break every build, not just the verify tasks.
    val env = listOf("../test.env", "../kodex/.env.test")
        .map { rootProject.file(it) }
        .firstOrNull { it.exists() }
    if (env != null) {
        val props = env.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
        }.toMap()
        args(props["HOST"] ?: "http://localhost:26000", props["API_KEY"] ?: "")
    }
}

tasks.register<JavaExec>("verifyApi") {
    group = "verification"
    val main = sourceSets.getByName("main")
    dependsOn(tasks.named("classes"))
    classpath = files(main.output, main.runtimeClasspath)
    mainClass.set("app.kodex.client.VerifyApiKt")
    liveServerArgs()
}

// Exercises the ebook reader's loopback host for real (assets, traversal guards, and — with a live
// server holding an EPUB — the proxied manifest/resource/file routes). See VerifyEbookHost.kt.
tasks.register<JavaExec>("verifyEbookHost") {
    group = "verification"
    val main = sourceSets.getByName("main")
    dependsOn(tasks.named("classes"))
    classpath = files(main.output, main.runtimeClasspath)
    mainClass.set("app.kodex.client.VerifyEbookHostKt")
    liveServerArgs()
}

// Drives a real WebView against the host and waits for foliate to report a rendered book. Downloads
// Chromium on first run. See VerifyEbookRender.kt.
tasks.register<JavaExec>("verifyEbookRender") {
    group = "verification"
    val main = sourceSets.getByName("main")
    dependsOn(tasks.named("classes"))
    classpath = files(main.output, main.runtimeClasspath)
    mainClass.set("app.kodex.client.VerifyEbookRenderKt")
    // KCEF drives Chromium through JCEF, which needs these JVM internals opened up.
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
    liveServerArgs()
}

compose.desktop {
    application {
        mainClass = "app.kodex.client.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "app.kodex.client"
            packageVersion = "1.0.0"
        }
    }
}
