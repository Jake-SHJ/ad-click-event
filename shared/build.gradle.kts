// collector와 pipeline이 함께 쓰는 것만 둔다: Avro 이벤트 스키마, 지표 이름, UUIDv7, 로깅 설정,
// 그리고 Testcontainers 테스트 픽스처(testFixtures).
plugins {
    id("adclick.kotlin-library")
    `java-test-fixtures`
}
