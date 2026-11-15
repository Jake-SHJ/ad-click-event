# 로컬 데모 실행 (FR-033: 명령 하나로 띄우고 내린다)
COMPOSE := docker compose --env-file .env.demo

.PHONY: demo-up demo-down demo-clean demo-logs infra-up

## 데모 전체를 띄우고, 모든 서비스가 healthy가 될 때까지 기다린다
demo-up:
	$(COMPOSE) up -d --build --wait
	@echo "web: http://localhost:5173  pipeline(조회 API): http://localhost:8082  prometheus: http://localhost:9090"

## 데모를 내린다(데이터는 유지)
demo-down:
	$(COMPOSE) down

## 데모를 내리고 Kafka·PostgreSQL 데이터까지 지운다
demo-clean:
	$(COMPOSE) down -v --remove-orphans

## 로그 보기
demo-logs:
	$(COMPOSE) logs -f --tail=100

## 인프라만 띄운다(IDE에서 서비스를 직접 실행할 때)
infra-up:
	$(COMPOSE) up -d --wait kafka kafka-init apicurio apicurio-init postgres prometheus
