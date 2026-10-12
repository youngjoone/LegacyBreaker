# LegacyBreaker — Code Relationship Explorer

Java 8로 만드는 IntelliJ IDEA용 Java 메서드 관계 탐색 플러그인.
회사 IDE 버전은 아직 미확인이므로 **2018.3 (빌드 183)을 임시 개발 기준**으로 사용한다.
2018.3보다 오래된 버전이나 다른 버전의 지원은 아직 보장하지 않는다.

## 현재 구현 범위

- Java 에디터 우클릭의 `Explore Code Relations`
- 커서를 포함하는 메서드의 클래스명, 메서드명, 매개변수 타입 표시
- 메서드 밖에서는 안내 표시, 생성자는 현재 제외
- 익명/지역 클래스의 필드 초기화 코드를 바깥 메서드로 오인하지 않도록 경계 처리

**Caller/Callee 분석, 그래프, 소스 이동, Lazy Expansion은 다음 단계다.**
우선 이 작은 기능으로 실제 회사 IDE에서 플러그인 실행 가능 여부를 확인한다.

## 개발 환경

필수는 기존 **JDK 8**과 **IntelliJ IDEA 2018.3 설치 디렉터리**다.
JRE만으로는 컴파일할 수 없다. Gradle, Maven, Kotlin, Python, 추가 외부 JAR는 필요 없다.
빌드 스크립트는 인터넷에 접근하지 않는다.

IntelliJ API는 설치 디렉터리의 `lib`에서 참조한다. 해당 JAR들을 결과물에 복사하지 않는다.
화면은 추후 JDK Swing/Java2D로 구현하며 별도 그래프 라이브러리를 사용하지 않는다.
프로젝트가 Java 7/8인 것과 플러그인 SDK의 Java 요구사항은 별개다.
Java 8 문법 설정만으로 Java 11 기반의 IntelliJ SDK를 JDK 8에서 사용할 수는 없다.

## Windows에서 빌드

명령 프롬프트(cmd)에서 실제 설치 경로로 지정한다.

```bat
set "LEGACYBREAKER_JDK=C:\Program Files\Java\jdk1.8.0_202"
set "LEGACYBREAKER_IDEA=C:\Program Files\JetBrains\IntelliJ IDEA 2018.3.6"
build.bat
```

## macOS / Linux에서 빌드

```sh
export LEGACYBREAKER_JDK="/path/to/jdk8"
export LEGACYBREAKER_IDEA="/path/to/intellij"
./build.sh
```

macOS 앱의 IDE 경로는 `/Applications/IntelliJ IDEA.app/Contents` 형태다.
JDK 경로도 macOS에서는 해당 JDK의 `Contents/Home`을 지정한다.

결과물: `build/distributions/legacybreaker-0.1.0-dev.jar`

호환되는 테스트 IDE의 `Settings → Plugins → Install plugin from disk`에서
이 JAR를 선택하고 재시작한다. 테스트에는 공개 예제나 직접 작성한 샘플 코드를 사용한다.

## IDE에서 편집

기존 Plugin DevKit이 있으면 IntelliJ Platform Plugin 프로젝트를 만들고, 기존 IDEA
설치 경로를 SDK로, JDK 8을 내부 Java SDK로 지정할 수 있다. DevKit이 없어도 위 스크립트로
컴파일·패키징할 수 있다. IDE 메뉴 이름은 설치 버전에 따라 다르다.

소스 루트는 `src/main/java`, 리소스 루트는 `src/main/resources`다.
현재는 진입점 Action을 컴파일하면 참조하는 소스도 함께 컴파일한다. 이후 XML에만 등록하는
Tool Window 같은 진입점이 생기면 양쪽 빌드 스크립트의 컴파일 대상도 추가해야 한다.

## 확인 순서

1. `Help → About`에서 정확한 IDE 버전과 빌드 번호를 확인한다.
2. 현재 설정은 183 계열만 허용한다. 다른 버전은 SDK/API와 의존성 구성을 확인하고
   빌드 스크립트와 `plugin.xml`을 함께 조정한다. 버전 범위만 넓혀 호환된다고 간주하지 않는다.
3. Java 메서드 선언과 본문에서 우클릭해 정확한 메서드가 표시되는지 확인한다.
4. 오버로드 메서드, 내부 클래스 메서드, 저장하지 않은 편집 내용에서도 확인한다.
5. 클래스 필드/초기화 블록/생성자에서는 메서드 선택 안내가 나오는지 확인한다.
6. Java가 아닌 파일에서는 Action이 나타나지 않는지 확인한다.

## 현재 검증 결과

- macOS에서 실제 JDK `1.8.0_202` + IDEA Community `2018.3.6` SDK로 컴파일·JAR 생성 성공.
- 생성된 두 클래스의 Java 8 바이트코드 버전(52), Action 등록과 클래스 포함 확인.
- SDK/외부 라이브러리가 결과물에 포함되지 않는 것 확인.
- JDK 설정 누락, JDK 11, 다른 버전의 IntelliJ SDK를 빌드 전에 거부하는 것 확인.
- Windows `build.bat` 실행과 IDE 내부 메뉴/대화상자 동작은 아직 직접 검증하지 않음.
- 회사 환경 호환성은 정확한 버전을 확인한 후 검증해야 함.

개인 개발 PC에서 검증용으로 받은 SDK는 `.local-sdk/`에만 있으며 Git에서 제외된다.
회사에서 이 SDK를 추가로 다운로드해야 한다는 뜻은 아니다. 회사의 기존 IDE를 기준으로
맞추는 작업이 필요하다.

설계와 단계별 API/성능 설명은 [architecture.md](docs/architecture.md)에 정리한다.
