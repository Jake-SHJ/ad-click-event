// 모든 Kotlin 모듈 공통: JDK 21 툴체인, ktlint, Spring Boot BOM, Kotest + MockK 단위 테스트.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-bom").get()))
    testImplementation(platform(libs.findLibrary("spring-boot-bom").get()))
    testImplementation(libs.findLibrary("kotest-runner").get())
    testImplementation(libs.findLibrary("kotest-assertions").get())
    testImplementation(libs.findLibrary("kotest-property").get())
    testImplementation(libs.findLibrary("mockk").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
