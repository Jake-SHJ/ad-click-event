// 처리 앱(Spring Modulith): ingest · realtime · finalize · query · demo 모듈.
// 모듈 경계는 ModularityTest가 검증하고, PostgreSQL 스키마(Flyway)의 주인이다.
plugins {
    id("adclick.spring-service")
    id("adclick.integration-test")
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.spring.modulith.bom))
    implementation(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)
    integrationTestImplementation(testFixtures(project(":shared")))
}
