// Gatling 부하 테스트(Kotlin DSL). 시뮬레이션은 src/gatling/kotlin 에 둔다. 실행: ./gradlew :loadtest:gatlingRun
plugins {
    id("adclick.kotlin-library")
    alias(libs.plugins.gatling)
}
