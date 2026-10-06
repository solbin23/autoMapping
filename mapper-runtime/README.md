# Mapper Runtime

생성 Mapper가 공통으로 사용하는 reflection 경로 접근과 기본 타입 변환 라이브러리입니다.
생성된 Mapper 파일에는 이 구현이 복사되지 않고 다음 Maven 좌표를 의존성으로 사용합니다.

```text
com.example.aimapper:mapper-runtime:1.0.0-SNAPSHOT
```

## 로컬 게시

```powershell
.\gradlew.bat :mapper-runtime:publishToMavenLocal
```

게시 후 로컬 Maven 저장소를 사용하는 프로젝트에서 다음과 같이 참조합니다.

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.example.aimapper:mapper-runtime:1.0.0-SNAPSHOT'
}
```

## 운영 게시

운영에서는 Maven Local 대신 사내 Nexus, Artifactory 또는 사용하는 Maven 저장소에 게시합니다.
생성 ZIP의 `build.gradle`에 해당 저장소 URL을 추가하고 동일한 좌표를 사용합니다.

런타임 동작을 변경할 때는 버전을 올려 게시한 뒤 사용하는 프로젝트의 의존성 버전과 배포본을
갱신합니다. 기존 Mapper 소스를 다시 생성할 필요는 없습니다.

## 공개 API

- `MapperRuntime.readPath`: 중첩 객체와 컬렉션 경로 값 읽기
- `MapperRuntime.writePath`: 대상 경로 생성 및 값 쓰기
- `MapperRuntime.instantiate`: 기본 생성자로 VO 생성
- `MapperRuntime.convert`: 승인된 기본 타입 변환 수행

```powershell
.\gradlew.bat :mapper-runtime:test
```
