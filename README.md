# Store Ledger 매출·마진 관리

네이버 스마트스토어 판매자를 위한 단가·마진 계산과
일 / 주 / 월 / 년 매출 그래프 플랫폼. 백엔드는 Java 21 + Spring Boot 4.1, 데이터는 H2 파일 DB,
화면은 Spring이 서빙하는 정적 HTML + Chart.js(로컬 번들)로 별도 프런트 빌드가 없습니다.

## 실행

```bash
./gradlew bootRun
```

- 화면: http://localhost:8080
- DB 파일: `./data/storeledger.mv.db` (재시작해도 데이터 유지, git 제외)
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/storeledger`, 사용자 `sa`, 비밀번호 없음)

테스트:

```bash
./gradlew test
```

## 배포 (PostgreSQL)

로컬은 H2 파일 DB, 배포 서버는 `prod` 프로필로 PostgreSQL을 씁니다. 접속 정보는 환경변수로 받습니다.

```sql
-- 클라우드 서버의 PostgreSQL에서 한 번만
CREATE USER ledger WITH PASSWORD '비밀번호';
CREATE DATABASE storeledger OWNER ledger;
```

```bash
./gradlew bootJar
DB_HOST=localhost DB_USER=ledger DB_PASSWORD='비밀번호' SPRING_PROFILES_ACTIVE=prod \
  java -jar build/libs/storeledger-0.0.1-SNAPSHOT.jar
```

- 첫 실행 때 `ddl-auto=update`가 테이블을 만듭니다.
- 관리형 DB가 아니므로 백업은 직접 합니다. 예: `pg_dump -U ledger storeledger > backup.sql` 을 cron으로 매일.
- DB 포트 5432는 앱 서버 또는 본인 IP에서만 접근하도록 방화벽/보안 그룹을 제한하세요.

## 마진 계산식

```
수수료  = 판매가 × 수수료율        (원 단위 반올림)
마진    = 판매가 − 원가 − 개당 배송비 − 개당 기타비용 − 수수료
마진율  = 마진 ÷ 판매가 × 100     (소수 첫째 자리)
```

- 수수료율 기본값 5.63% = 네이버 주문관리 수수료(신용카드 3.63%) + 네이버쇼핑 매출연동 수수료 2%. 상품마다 조정할 수 있습니다.
- 판매 기록의 이익은 실제 판매 단가(할인 반영)와 **현재** 상품 원가 구조로 계산합니다. 상품 원가를 바꾸면 과거 기록의 이익도 다시 계산됩니다.
- 배송비·기타 비용은 개당 금액입니다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET / POST | `/api/products` | 상품 목록(수수료·마진 포함) / 등록 |
| GET / PUT / DELETE | `/api/products/{id}` | 조회 / 수정 / 삭제 (판매 기록이 있으면 409) |
| POST | `/api/margin` | 저장 없이 마진만 계산 |
| GET / POST | `/api/sales?from=&to=` | 판매 기록 목록(기본 최근 30일) / 등록 (`unitPrice` 생략 시 상품 판매가) |
| PUT / DELETE | `/api/sales/{id}` | 수정 / 삭제 |
| GET | `/api/sales/summary?period=DAILY\|WEEKLY\|MONTHLY\|YEARLY&from=&to=` | 기간별 매출·이익·수량 집계 (빈 구간은 0으로 채움) |

`from`/`to` 생략 시 `to`는 오늘(KST), `from`은 단위별 기본 범위(최근 30일 / 12주 / 12개월 / 5년)입니다. 주는 월요일 시작입니다.
오류는 RFC 9457 ProblemDetail JSON(`detail` 필드)으로 내려갑니다.

## 구조

```
src/main/java/com/storeledger
├── margin/   MarginResult(계산식), MarginRequest, MarginController(/api/margin)
├── product/  Product 엔티티, 리포지토리, 서비스, 컨트롤러, 요청/응답 DTO
├── sale/     Sale 엔티티, Period(일/주/월/년 구간), SaleService.summary(집계), 컨트롤러
└── common/   GlobalExceptionHandler
src/main/resources/static   index.html, app.js, style.css, vendor/chart.umd.min.js
```
