// 실행 가능한 Spring Boot 서비스: Actuator + Prometheus 지표 + JSON 구조화 로그.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("adclick.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("spring-boot-starter-actuator").get())
    implementation(libs.findLibrary("micrometer-prometheus").get())
    implementation(libs.findLibrary("logstash-logback-encoder").get())
    implementation(libs.findLibrary("kotlin-reflect").get())
    implementation(libs.findLibrary("jackson-kotlin").get())
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testImplementation(libs.findLibrary("kotest-spring").get())
}
