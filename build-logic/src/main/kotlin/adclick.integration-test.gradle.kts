// Testcontainers 통합 테스트용 `integrationTest` 소스 세트. `check`가 단위 테스트 다음에 실행한다.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("adclick.kotlin-library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

@Suppress("UnstableApiUsage")
testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(platform(libs.findLibrary("spring-boot-bom").get()))
                implementation(libs.findLibrary("kotest-runner").get())
                implementation(libs.findLibrary("kotest-assertions").get())
                implementation(libs.findLibrary("kotest-property").get())
                implementation(libs.findLibrary("testcontainers-kafka").get())
                implementation(libs.findLibrary("testcontainers-postgresql").get())
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.named("test"))
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}
