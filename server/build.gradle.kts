plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
application { mainClass.set("mx.edu.utez.wallet.server.ApplicationKt") }
dependencies {
    implementation(project(":shared"))
    val ktor = "3.2.3"
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test-junit"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
}
