# LegacyBreaker — Code Relationship Explorer

Java 8로 구현한 IntelliJ IDEA용 오프라인 Java 메서드 관계 및 JSP 값 출처 탐색 플러그인.
**V0.2는 기존 Caller/Callee 그래프에 JSP → request 속성 → 자바 작성자 후보 역추적을 추가한다.**

<!--4차수정: 수정판 안내와 수동 적용 위치.-->
V0.2.1은 재대입 경로 혼합, 람다 초기값 오판, 작성자 후보 누락을 수정한다.
수정 코드에는 `//4차수정` 표시가 있다. [4차 수정 적용 목록](docs/revision-4.md)을 참고한다.

회사 IDE 버전은 아직 미확인이므로 **IntelliJ IDEA 2018.3 (빌드 183)만 임시 지원**한다.
그보다 오래되거나 새로운 IDE는 정확한 버전을 확인하고 별도로 맞춰야 한다.

## 사용 방법

1. 호환되는 IDEA의 `Settings → Plugins → Install Plugin from Disk`에서
   `build/distributions/legacybreaker-0.2.1.jar`를 설치하고 IDE를 재시작한다.
   이 JAR는 독립 실행 프로그램이 아니므로 더블클릭/`java -jar`로 실행하지 않는다.
2. Java 프로젝트를 열고 메서드 선언 또는 본문에 커서를 둔다.
3. 에디터 우클릭 → **Explore Code Relations**를 선택한다.
4. **Code Relations** Tool Window에 선택한 메서드와 직접 Caller/Callee가 표시된다.
   인덱싱 중이면 검색을 기다린다.
5. 노드를 클릭해 선택한 뒤 **Expand Callers / Expand Callees**를 누른다.
   노드 우클릭 메뉴에서도 같은 작업을 할 수 있다.
6. 노드를 더블클릭하거나 **Go To Source**를 누르면 소스가 열린다. 선택 후 Enter도 가능하다.
   에디터로 이동하거나 Tool Window를 숨겨도 현재 그래프는 유지된다.

화살표는 항상 **호출하는 메서드 → 호출되는 메서드**다.
Root는 녹색 테두리, 선택 노드는 파란색 테두리다. 노드를 끌어서 배치할 수 있고,
스크롤바로 큰 그래프를 탐색한다. **Show Root**는 Root를 화면에 보이게 한다.

- **Cancel Search**: 진행/대기 중인 검색만 취소하고 현재 그래프를 유지한다.
- **Refresh Root**: 현재 Root에서 다시 시작한다. 기존 확장은 초기화한다.
- 코드 또는 프로젝트 구성이 바뀌면 기존 결과에 **snapshot** 안내가 표시된다.
  이때 확장은 중지되고, Refresh Root로 현재 코드를 다시 분석할 수 있다.
- 다른 메서드에서 Explore를 실행하면 새 탐색으로 바뀐다.
- IDE/프로젝트를 종료한 뒤 그래프를 복원하는 기능은 없다.

실습 코드는 [samples/Example.java](samples/Example.java)에 있다. `samples`를 Java source root로
지정하고 `save(int)`에서 시작하면 오버로드·재귀·라이브러리 호출을 볼 수 있다.
`Store.write()`를 확장하면 `audit()`가 추가된다.

## JSP의 JS 변수에서 역추적하기

JSP의 `var status = "<%= userStatus %>";`에서 변수 선언이나 `<%= ... %>`에 커서를 두고
우클릭 → **Trace JSP Value Origin**을 실행한다. **JSP Value Origins** 창에
JS 변수 → JSP 출력 → 자바 변수 → `request.getAttribute("키")` → `setAttribute("키", 값)` 후보가 나타난다.

노드 더블클릭으로 소스에 이동하고, Java 노드의 **Explore Method Calls**로 기존 메서드 그래프를 이어서 탐색한다.
점선은 작성자 후보이며 해당 요청의 실제 실행 경로를 보장하지 않는다. 조건부 재대입 등은 경계에서 멈춘다.
이번 단계는 스크립틀릿과 문자열 키의 request 속성을 지원한다. JSTL/EL, Model/ModelAndView,
include, 동적 키, JS 임의 사용 위치 및 실행 중 실제 값은 지원하지 않는다.

예제와 정확한 범위: [JSP 역추적 사용법](docs/jsp-tracing.md).

## Java 메서드 분석 범위와 한계

- Java의 직접적인 메서드 호출을 PSI와 참조 인덱스로 분석한다. 프로젝트 소스 루트 설정이 필요하다.
- Caller는 프로젝트 소스에서 검색한다. 테스트 소스도 포함한다.
- 같은 메서드를 여러 번 호출해도 노드/간선은 하나로 합친다. 호출 횟수는 집계하지 않는다.
- 오버로드는 파일·클래스·매개변수 타입으로 구분한다.
- 인터페이스/상속 호출은 **컴파일 시 해석된 선언**에 연결한다. 가능한 모든 런타임 구현체를 추정하지 않는다.
- 생성자, 초기화 블록의 호출, 람다 내부 호출, `Type::method` 참조는 범위 밖이다.
  중첩 클래스 메서드의 호출은 해당 메서드의 관계로 분석한다.
- 라이브러리 메서드는 terminal node로 표시한다. 소스가 연결되어 있으면 소스로,
  그렇지 않으면 IDE가 제공하는 디컴파일 화면으로 이동할 수 있다. 추가 확장은 하지 않는다.
- 누락된 의존성 등으로 해석하지 못한 Callee는 임의 연결하지 않고 개수로 알린다.
- 한 번의 방향별 확장은 최대 100개 메서드, 그래프는 최대 400개 노드/2,000개 간선이다.
  검색은 방문 20,000회와 약 5초의 협력적 제한도 적용한다. 플랫폼 내부 인덱스 작업 중에는
  이 시간을 넘을 수 있다. 제한에 걸리면 **partial**로 표시한다. 전체 결과 페이지 기능은 없다.
- AI, Spring 전용 분석, MyBatis/SQL, 일반적인 변수 데이터 흐름, 전체 프로젝트 그래프는 구현하지 않는다.
  JSP 값 추적의 제한된 지원 범위는 위 별도 사용법을 따른다.

## 네트워크 없는 빌드

필수는 기존 **JDK 8**과 **IntelliJ IDEA 2018.3 설치 디렉터리**다. JRE만으로는 컴파일할 수 없다.
Gradle, Maven, Kotlin, Python, 추가 외부 라이브러리는 필요 없다.
빌드 스크립트는 인터넷에 접근하지 않으며, 설치된 IDE의 `lib`를 컴파일에만 참조한다.
SDK JAR와 테스트 코드는 플러그인 결과물에 포함하지 않는다.

Windows 명령 프롬프트(cmd):

```bat
set "LEGACYBREAKER_JDK=C:\Program Files\Java\jdk1.8.0_202"
set "LEGACYBREAKER_IDEA=C:\Program Files\JetBrains\IntelliJ IDEA 2018.3.6"
build.bat
```

macOS / Linux:

```sh
export LEGACYBREAKER_JDK="/path/to/jdk8"
export LEGACYBREAKER_IDEA="/path/to/intellij"
./build.sh
```

macOS IDE 경로는 앱의 `Contents`, JDK 경로는 JDK의 `Contents/Home`을 지정한다.
결과물은 `build/distributions/legacybreaker-0.2.1.jar`다.

IDE 편집 시 소스 루트는 `src/main/java`, 리소스 루트는 `src/main/resources`다.
기존 Plugin DevKit이 있으면 설치된 IDEA를 SDK로 지정해 편집할 수 있다.
DevKit이 없어도 스크립트로 빌드·패키징할 수 있다.

Java 7/8 프로젝트를 분석하는 것과 플러그인 SDK의 Java 요구사항은 별개다.
Java 8 문법 설정만으로 Java 11 기반 SDK를 JDK 8에서 사용할 수는 없다.
회사 버전이 다르면 SDK/API, 빌드 스크립트, `plugin.xml`을 함께 검토해야 한다.
버전 범위만 넓혀 호환된다고 간주하지 않는다.

## 검증

같은 환경 변수로 macOS/Linux에서 `./test.sh`를 실행한다.

- 순수 Java 테스트: 간선 방향, 중복, 재귀/순환, 제한, 초기화, Swing 그래프 렌더링.
- 실제 IDEA 2018.3 플랫폼 테스트: 샘플 프로젝트와 Java 8 SDK를 인덱싱하고 PSI/참조 검색을 실행한다.
  탐색 상태, 방향별 확장 재사용, 소스 이동, 변경 감지, 새로고침, 취소도 검사한다.
- 통합 테스트는 `build/test-sandbox`에 별도 IDE 설정/인덱스를 만든다. 평소 IDE 설정은 변경하지 않는다.
  테스트 전용 starter와 테스트 JAR는 배포물에 포함하지 않는다.
- JSP 추출, 자바 작성자 후보, 잘못된 연결 배제, 소스 이동, 변경 감지, 새로고침, 취소를 검사한다.
- 그래프 렌더링 결과: `build/graph-preview.png`, `build/integration-preview.png`, `build/jsp-preview.png`.

정확한 실행 결과와 수동 확인 목록은 [docs/verification.md](docs/verification.md)에 기록한다.
회사 버전/Windows에서의 실제 설치와 클릭 동작은 해당 환경에서 별도 확인이 필요하다.

설계: [docs/architecture.md](docs/architecture.md).
개인 PC 검증용 `.local-sdk/`와 `build/`는 Git에서 제외한다.
