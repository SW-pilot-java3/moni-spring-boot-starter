# moni-spring-boot-starter

Moni(실시간 서버 & 인스턴스 모니터링 SaaS)에 연동하기 위한 Spring Boot 클라이언트 라이브러리입니다. Actuator/Node Exporter로부터 메트릭을 수집해 10초 주기로 모니터링 백엔드에 Outbound Push(HTTPS POST)로 전송합니다. 인바운드 포트를 열 필요가 없습니다.

## 요구 사항

- Java 17 이상
- Spring Boot 3.5.x, 4.x

## 1. 의존성 추가

JitPack 저장소를 추가하고, 라이브러리와 함께 아래 3개 의존성을 **호스트 앱이 직접** 추가해야 합니다. 이 3개는 라이브러리에 `compileOnly`로만 선언돼 있어 jar에 포함되지 않습니다(호스트 앱이 이미 쓰고 있는 버전과 충돌하지 않도록 하기 위함).

### Gradle

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.SW-pilot-java3:moni-spring-boot-starter:0.1.0'

    // moni-spring-boot-starter가 compileOnly로 선언한 의존성 — 직접 추가해야 실제로 동작함
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-core'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.SW-pilot-java3</groupId>
        <artifactId>moni-spring-boot-starter</artifactId>
        <version>0.1.0</version>
    </dependency>

    <!-- compileOnly로 선언된 의존성 — 직접 추가해야 실제로 동작함 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>
</dependencies>
```

## 2. 필수 설정

`moni.api-key`와 `moni.server-url`은 **필수**입니다. 둘 중 하나라도 비어 있으면 애플리케이션이 기동 시점에 `IllegalStateException`을 던지고 시작하지 않습니다(fail-fast).

```yaml
moni:
  api-key: ${MONI_API_KEY}          # Moni에서 발급받은 API Key
  server-url: https://api.moni.example.com   # Moni 백엔드 base URL
```

> **엔드포인트 경로는 직접 지정하지 않습니다.** `moni.server-url`에는 base URL(도메인)만 넣으면 되고, 라이브러리가 내부적으로 `POST {server-url}/api/v1/metrics`로 전송합니다. 인증 헤더(`X-API-KEY: {api-key}`)도 라이브러리가 자동으로 붙입니다.

**위 두 설정만 마치면 끝입니다.** 호스트 앱 코드에서 수집·전송을 호출하는 별도 작업은 필요 없습니다. 애플리케이션이 기동되면 라이브러리가 등록한 전용 데몬 스레드가 `moni.interval` 주기로 알아서 수집(Actuator `MeterRegistry`/Node Exporter)부터 전송까지 자동으로 수행합니다.

## 3. 선택 설정 (기본값)

```yaml
moni:
  enabled: true                              # false로 두면 라이브러리 전체 비활성화(로컬/테스트 프로필에 유용)
  interval: 10s                              # 메트릭 수집·전송 주기
  collect-host-metrics: true                 # false면 Node Exporter 수집(인스턴스 메트릭) 자체를 안 함
  node-exporter-url: http://localhost:9100/metrics  # Node Exporter 스크레이핑 대상
  retry-queue-size: 20                       # 전송 실패 시 재시도 큐 최대 크기(초과 시 가장 오래된 항목부터 drop)
  connect-timeout: 2s                        # Moni 백엔드/Node Exporter 공통 연결 타임아웃
  read-timeout: 5s                           # Moni 백엔드/Node Exporter 공통 응답 타임아웃
```

## 4. 인스턴스(EC2 Host) 메트릭이 필요 없다면

`collect-host-metrics: false`로 두면 Node Exporter 관련 수집기 자체가 비활성화됩니다. `true`(기본값)인데 Node Exporter에 연결이 안 되는 환경(EC2가 아닌 로컬 개발 환경 등)이라면, 에러 없이 `instance` 필드만 `null`로 빠지고 `server` 메트릭은 정상 전송됩니다 — 앱이 죽거나 전체 전송이 스킵되지 않습니다.

## 5. 전송되는 데이터

10초(또는 `moni.interval`)마다 아래 형태로 하나의 요청에 서버·인스턴스 메트릭을 함께 담아 전송합니다. 각 필드가 무엇을 의미하는지는 레포 루트 `DB_설계.pdf`의 `server_realtime_metrics`/`instance_realtime_metrics` 테이블 정의를 참고하세요.

```json
{
  "collectedAt": "2026-08-17T04:57:37.324561Z",
  "server": {
    "jvmHeapUsedBytes": 44549856,
    "jvmHeapMaxBytes": 4213178368,
    "...": "..."
  },
  "instance": {
    "cpuSecondsTotal": 1234.5,
    "...": "..."
  }
}
```

- Node Exporter를 못 쓰거나 수집 실패 시 `instance`는 `null`
- 어떤 메트릭 소스(예: HikariCP, Executor)를 호스트 앱이 아예 안 쓰고 있으면 해당 리스트는 빈 배열, 수집 자체에 실패한 스칼라 필드는 `null`

## 검증 상태

- JitPack 태그 기반 빌드·배포, 실제 호스트 앱에서 의존성 resolve 후 오토컨피그레이션 동작, Moni mock 서버로의 실전송(인증 헤더 + 실제 JVM/HTTP 값)까지 end-to-end로 확인됨.
- Spring Boot 3.5.4 / 4.1.1 두 버전 모두 Docker 컨테이너로 실제 호스트 앱을 기동해 검증 완료 — 두 버전이 동일한 JSON 스키마·포맷으로 정상 전송됨을 확인함.
- **아직 확인 안 됨**: 실제 Moni 운영 백엔드가 이 JSON 스키마를 그대로 받아들이는지(Notion API 명세서가 없어 `DB_설계.pdf` 기준으로 설계됨). 실제 배포 전 반드시 Moni 백엔드와 붙여서 확인이 필요합니다.
