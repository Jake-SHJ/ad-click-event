package dev.jakeshj.adclick.pipeline

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 처리 앱. 하위 패키지 하나가 Spring Modulith 모듈 하나다.
 *
 * - `ingest`   : Kafka → PostgreSQL 사실 저장, 격리 저장
 * - `realtime` : Kafka Streams 1분 집계(잠정 판정)
 * - `finalize` : 24시간 유예 후 확정 판정·일별 리포트·개정본
 * - `query`    : 광고주 조회 API
 * - `demo`     : 시각화 스트림·시나리오 제어·합성 부하(demo 프로필)
 * - `rules`    : 판정 규칙 파라미터(realtime·finalize 공용)
 */
@SpringBootApplication
class PipelineApplication

fun main(args: Array<String>) {
    runApplication<PipelineApplication>(*args)
}
