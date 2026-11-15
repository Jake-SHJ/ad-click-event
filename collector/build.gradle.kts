// 수집 앱: 클릭 리다이렉트·노출 수신 → Kafka. 초당 1만 건·리다이렉트 1초 목표 때문에 단독 프로세스로 둔다.
plugins {
    id("adclick.spring-service")
    id("adclick.integration-test")
}

dependencies {
    implementation(project(":shared"))
    integrationTestImplementation(testFixtures(project(":shared")))
}
