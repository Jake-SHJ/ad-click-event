// 모듈: shared(공유 계약·유틸), collector(수집 앱), pipeline(Spring Modulith 앱), loadtest(Gatling)
// 공통 설정은 build-logic/의 컨벤션 플러그인에 있다:
// - adclick.kotlin-library  : Kotlin + JDK 21 + ktlint + Spring Boot BOM + Kotest/MockK
// - adclick.spring-service  : 실행 가능한 Spring Boot 서비스(Actuator, Prometheus, JSON 로그)
// - adclick.integration-test: Testcontainers 통합 테스트용 integrationTest 소스 세트

group = "dev.jakeshj.adclick"
version = "0.1.0-SNAPSHOT"
