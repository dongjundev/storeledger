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

## 화면에 표시할 상호

저장소에는 기본 이름 "Store Ledger"만 들어 있습니다. 실제 상호는 git에 제외된 `config/` 폴더에 적으면
화면 제목과 상단 로고에 표시됩니다. 프로젝트 루트에서 실행하면 Spring Boot가 자동으로 읽습니다.

상호에 한글이 있으면 `config/application.yml`을 쓰세요. `.properties` 파일은 Spring Boot가 ISO-8859-1로 읽어서
한글이 깨집니다. 영문 상호라면 `config/application.properties`에 `app.store-name=My Store`로 적어도 됩니다.

```yaml
# config/application.yml
app:
  store-name: 우리 가게 이름
```

이 폴더에는 상호만 적고 DB 설정은 넣지 마세요. Mac 앱은 빌드할 때 준 이름을 쓰므로 이 파일이 필요 없습니다.

## Mac 앱으로 만들기 (Java 설치 없이 더블클릭 실행)

컴퓨터에 익숙하지 않은 사람이 쓸 수 있도록, Java 실행 환경을 품은 Mac 앱(.app)을 만듭니다.
만드는 Mac에 JDK 21이 있어야 하고, 결과물은 같은 종류의 칩(Apple Silicon / Intel)을 쓰는 Mac에서 실행됩니다.

```bash
packaging/build-mac-app.sh "가게 이름"     # 이름 생략 시 "Store Ledger"
```

- 결과: `build/mac-app/가게 이름.app` (다른 Mac으로 옮길 때 쓰는 zip도 함께). 응용 프로그램 폴더에 넣고 더블클릭하면
  서버가 뜨고 브라우저가 열립니다. 앱 이름이 화면 제목의 상호로도 쓰이며, 바꾸려면 다시 만듭니다.
- 데이터는 `~/StoreLedger/data`, 로그는 `~/StoreLedger/logs`. 백업은 `~/StoreLedger` 폴더를 복사하면 됩니다.
- 앱의 장부는 `./gradlew bootRun`으로 쓰던 장부(`./data/storeledger.mv.db`)와 별개입니다. 기존 장부를 앱에서 계속 쓰려면
  앱에 아무것도 입력하기 전에, 앱을 종료한 상태에서 그 파일을 `~/StoreLedger/data/storeledger.mv.db`로 복사하세요.
  앱에 이미 입력한 내용이 있으면 덮어써져 사라지고, 두 장부를 나눠 쓰면 나중에 합칠 수 없습니다.
- Dock 아이콘 클릭 → 화면 다시 열기. Cmd+Q 또는 Dock에서 종료 → 서버 정리 후 종료. 부팅 시 자동 시작은 하지 않습니다.
- 접속은 같은 Mac에서만 됩니다 (127.0.0.1:28080). 개발용 `bootRun`(8080)과 동시에 실행해도 겹치지 않습니다.
  다른 사이트가 이 앱에 요청하지 못하게 localhost 주소로 온 요청만 받고, H2 콘솔은 앱에서 꺼져 있습니다.
- 다른 Mac으로 옮길 때는 앱 폴더가 아니라 **zip 파일**을 옮기세요. 앱 안의 심볼릭 링크가 exFAT USB나 일부 클라우드
  드라이브를 거치면 깨져서 실제로 손상될 수 있습니다.
- 인터넷·AirDrop·메신저로 받은 앱은 "손상되었기 때문에 열 수 없습니다"라고 나옵니다. 파일은 정상이고, Apple 개발자
  인증서 서명과 공증이 없어서 macOS가 막는 것입니다. 받은 Mac에서 앱을 응용 프로그램 폴더로 옮긴 뒤 터미널에서 한 번만
  실행하면 이후로는 더블클릭으로 열립니다.

  ```bash
  xattr -dr com.apple.quarantine "/Applications/가게 이름.app"
  ```

  zip을 USB로 옮겨서 풀면 이 표시가 붙지 않아 명령 없이 열립니다. 경고 없이 배포하려면 Apple Developer Program
  (연 129,000원)에 가입해 Developer ID 서명과 공증을 받아야 합니다.

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
  java -jar build/libs/storeledger-1.0.0.jar
```

- 첫 실행 때 `ddl-auto=update`가 테이블을 만듭니다.
- 관리형 DB가 아니므로 백업은 직접 합니다. 예: `pg_dump -U ledger storeledger > backup.sql` 을 cron으로 매일.
- DB 포트 5432는 앱 서버 또는 본인 IP에서만 접근하도록 방화벽/보안 그룹을 제한하세요.

## 카테고리

처음 실행할 때 기본 카테고리 11개(Kitchen, 가구, Living, Bathroom, Bedding, Pet, 케이스, 패션, 차량용품 및 기타용품,
캐릭터 상품, Christmas)가 DB마다 한 번만 들어갑니다. 이후에는 화면의 **카테고리** 탭에서 추가·이름 수정·삭제하며,
지운 기본 카테고리는 재시작해도 다시 생기지 않습니다. 상품은 카테고리를 하나 고르거나 미분류로 둘 수 있고,
카테고리를 지우면 그 카테고리의 상품은 미분류가 됩니다.

## 마진 계산식

```
수수료  = 판매가 × (주문관리 + 판매 수수료율) + 구매자 배송비 × 주문관리 수수료율   (각각 원 단위 반올림)
마진    = 판매가 + 구매자 배송비 − 원가 − 택배비 − 기타비용 − 수수료
마진율  = 마진 ÷ 판매가 × 100     (소수 첫째 자리)
```

- 구매자 배송비는 구매자가 결제하는 배송비(판매자 수입), 택배비는 판매자가 택배사에 내는 실제 비용입니다.
  네이버는 구매자 배송비에 주문관리 수수료만 매기고 판매 수수료는 매기지 않습니다.
- 수수료율 기본값: 주문관리 1.95%(네이버페이 영세 등급 1.77% + VAT, 2025년 10월 인하 후), 판매 3.00%(스마트스토어 2.73% + VAT,
  2025년 6월부터 네이버쇼핑 매출연동 수수료 2%를 대체). 마케팅 링크 유입 판매 수수료는 1.00%이고, 주문관리 수수료는
  등급이 오르면 중소1 2.56% · 중소2 2.73% · 중소3 3.00% · 일반 3.63%입니다(모두 VAT 포함, 카드 결제 기준).
  내 수수료율은 스마트스토어센터 › 정산관리 › 나의 수수료 정보에서 확인해 상품마다 조정하세요.
- 수수료율이 하나였던 이전 버전의 상품은 합계가 그대로 유지되도록 주문관리 3.63% + 판매(나머지)로 나뉩니다.
  합계가 3.63%보다 작았던 상품은 주문관리 = 합계, 판매 = 0으로 나뉩니다. 상품 목록의 수수료율 열에서 확인해 고치세요.
- 이전 버전의 "개당 배송비 · 판매자 부담"은 이제 **택배비**, 즉 판매자가 택배사에 내는 실제 비용으로 계산됩니다.
  구매자가 배송비를 내는 상품을 예전에 배송비 0으로 적어 두었다면 택배비와 구매자 배송비를 다시 입력하세요.
- 대시보드의 매출은 상품 판매액(수량 × 판매 단가)이고, 구매자 배송비는 매출에 넣지 않고 이익에만 반영합니다.
- 판매 기록의 이익은 실제 판매 단가(할인 반영)와 **현재** 상품 원가 구조로 계산합니다. 상품 원가를 바꾸면 과거 기록의 이익도 다시 계산됩니다.
- 택배비·구매자 배송비·기타 비용은 개당 금액입니다. 여러 개를 한 상자로 보내는 주문도 개당으로 계산합니다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET / POST | `/api/products` | 상품 목록(수수료·마진·카테고리 포함) / 등록 (`categoryId` 생략 시 미분류) |
| GET / PUT / DELETE | `/api/products/{id}` | 조회 / 수정 / 삭제 (판매 기록이 있으면 409) |
| POST | `/api/margin` | 저장 없이 마진만 계산 |
| GET / POST | `/api/categories` | 카테고리 목록(상품 수 포함, 등록 순) / 추가 (이름은 대소문자 무시하고 중복 불가, 409) |
| PUT / DELETE | `/api/categories/{id}` | 이름 수정 / 삭제 (그 카테고리의 상품은 미분류가 됨) |
| GET / POST | `/api/sales?from=&to=` | 판매 기록 목록(기본 최근 30일) / 등록 (`unitPrice` 생략 시 상품 판매가) |
| PUT / DELETE | `/api/sales/{id}` | 수정 / 삭제 |
| GET | `/api/sales/summary?period=DAILY\|WEEKLY\|MONTHLY\|YEARLY&from=&to=` | 기간별 매출·이익·수량 집계 (빈 구간은 0으로 채움, 한 번에 최대 1,000개 구간) |

`from`/`to` 생략 시 `to`는 오늘(KST), `from`은 단위별 기본 범위(최근 30일 / 12주 / 12개월 / 5년)입니다. 주는 월요일 시작입니다.
오류는 RFC 9457 ProblemDetail JSON(`detail` 필드)으로 내려갑니다.

## 구조

```
src/main/java/com/storeledger
├── margin/   MarginResult(계산식), MarginRequest, MarginController(/api/margin)
├── category/ Category 엔티티, 서비스, 컨트롤러, DefaultCategorySeeder(기본 카테고리 1회 입력)
├── product/  Product 엔티티, 리포지토리, 서비스, 컨트롤러, 요청/응답 DTO
├── sale/     Sale 엔티티, Period(일/주/월/년 구간), SaleService.summary(집계), 컨트롤러
└── common/   GlobalExceptionHandler, SettingsController, DataSeed(초기 데이터 입력 기록)
src/main/resources/static   index.html, app.js, style.css, vendor/chart.umd.min.js
```
