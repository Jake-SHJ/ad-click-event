package dev.jakeshj.adclick.pipeline

import io.kotest.core.spec.style.FunSpec
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest :
    FunSpec({
        val modules = ApplicationModules.of(PipelineApplication::class.java)

        test("모듈 사이에 허용되지 않은 의존과 순환이 없다") {
            modules.verify()
        }

        test("모듈 구조 문서를 생성한다") {
            Documenter(modules).writeDocumentation()
        }
    })
