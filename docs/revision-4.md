<!--4차수정: 기존 V0.2 코드에 이번 변경을 적용하기 위한 파일·블록 목록. 이 문서 전체가 새로 추가됨.-->
# 4차 수정 적용 목록 — V0.3.0까지 통합

기준은 기존 커밋 `8a557c1`의 V0.2.0이다. Java 8, IDEA 2018.3 SDK, 외부 라이브러리 없는 구성은 유지한다.
Java 코드에서 `//4차수정`을 검색하면 이번에 바뀐 블록 바로 위에 표시가 있다.
앞선 JSP 오류 수정과 이번 변수 탐색을 **모두 4차수정**으로 적용한다.
JSP 오류 수정은 아래 기존 Java 파일 **3개, 5개 블록**이며, 변수 탐색은 **새 제품 Java 파일 7개**와
기존 그래프/등록/빌드 파일 수정이 추가된다. 신규 파일은 첫 줄의 `//4차수정: 신규 파일 전체` 표시부터 파일 전체를 추가한다.
이미 V0.2.1(`75baedf`)까지 적용했다면 1~3번 JSP 수정은 완료된 상태이며, 아래 4번부터 적용하면 된다.

## 1. LocalValue.java — 1개 블록

파일: `src/main/java/com/github/youngjoone/legacybreaker/jsp/LocalValue.java`

`origin()`의 기존 `for`문과 그 아래 `if`문을 다음으로 교체한다.

```java
//4차수정: 람다 내부는 재대입 검사가 생략되므로 초기값으로 연결하지 않고 경계에서 중단한다.
for (PsiElement parent = use; parent != null && parent != method; parent = parent.getParent())
    if (parent instanceof PsiLoopStatement || parent instanceof PsiLambdaExpression) return null;
```

람다 안의 로컬 변수는 초기값으로 연결하지 않고 중단한다. 중첩 람다도 같은 방식으로 처리한다.

## 2. RequestWriterFinder.java — 3개 블록

파일: `src/main/java/com/github/youngjoone/legacybreaker/jsp/RequestWriterFinder.java`

### A. addWriter() 시작 부분

다음 줄은 유지하고, 바로 아래 기존 `if (evidence == null) return;` 줄을 **삭제**한다.

```java
//4차수정: 화면 힌트가 달라도 후보를 유지한다. 기존 evidence == null 제외문은 삭제한다.
String evidence = viewEvidence(jsp, method);
```

### B. viewEvidence()의 마지막 반환문

이 메서드 마지막의 `return null;` 한 줄을 다음으로 교체한다.

```java
//4차수정: 일반 반환 문자열을 화면명으로 단정하지 않고 목적지 미확인 후보로 남긴다.
return "No matching literal view/path hint; destination unconfirmed. Return strings may be data rather than view names.";
```

예를 들어 `setAttribute()` 뒤에 `return "OK"`가 있어도 후보에서 빠지지 않는다.
화면 경로가 다른 후보도 함께 표시할 수 있으며, 점선과 미확인 설명을 보고 구분한다.

### C. traceJava()의 JAVA_VARIABLE 노드 생성

`origin`을 구한 다음의 `CodeNode node = ...` 두 줄을 교체한다.

```java
//4차수정: 선언 위치 대신 사용 위치로 노드와 소스 이동을 구분하여 재대입 전후의 경로 혼합을 막는다.
CodeNode node = result.node(file, expression.getTextOffset(), "JAVA_VARIABLE", ((PsiLocalVariable) variable).getName(),
        "Java local value", origin == null ? "Conditional/reassigned, loop/lambda or unknown origin; stopped here." : "Direct initializer or straight-line assignment.", expression);
```

같은 변수를 두 번 사용해도 별도 노드가 된다. 초기값이 `FIRST`였다가 `SECOND`로 바뀐 경우,
두 번째 사용처는 경계에서 중단하며 첫 번째 사용처의 `FIRST` 경로를 공유하지 않는다.
변수 노드 소스 이동은 선언 위치에서 해당 사용 위치로 바뀐다.

## 3. JspValueAnalyzer.java — 1개 블록

파일: `src/main/java/com/github/youngjoone/legacybreaker/jsp/JspValueAnalyzer.java`

`traceLocal()`에서 `origin`을 구한 다음의 노드 생성 부분을 교체한다.

```java
//4차수정: 루프/람다 경계에서 중단되는 경우도 노드 설명에 표시한다.
CodeNode node = result.node(file, declaration, "JSP_VARIABLE", variable.getName(), "JSP Java variable",
        origin == null ? "Reassigned, conditional, loop/lambda or missing initializer: reaching value is not determined."
                : "Direct local value source; casts and parentheses are unwrapped.", null);
```

## 4. 변수 탐색 신규 제품 파일 — 7개 전체 추가

`src/main/java/com/github/youngjoone/legacybreaker/` 아래에 다음 파일을 추가한다.

| 파일 | 역할 |
| --- | --- |
| `action/ExploreVariableRelationsAction.java` | 변수 탐색 우클릭 메뉴 |
| `variable/VariableAtCaret.java` | 커서의 변수 선언/참조 판별 |
| `variable/VariableAnalysis.java` | 그래프 결과·스마트 포인터·집계 |
| `variable/VariableAnalyzer.java` | 초기값·읽기·쓰기·증감 위치 검색 |
| `variable/VariableSession.java` | 비동기 검색·필터·이동·새로고침·취소 |
| `variable/VariablePanel.java` | 변수 탐색 화면 |
| `variable/VariableToolWindowFactory.java` | 변수 탐색 창 생성 |

각 파일 전체가 이번 추가분이며 새 라이브러리는 필요 없다.

## 5. GraphCanvas.java — 간선 라벨 1개 블록

`src/main/java/com/github/youngjoone/legacybreaker/ui/GraphCanvas.java`의 `paintEdge()`에서
기존 `String label = ...` 한 줄을 다음으로 교체한다.

```java
//4차수정: 변수 관계는 읽기/쓰기/초기값 간선을 구분하여 표시한다.
String label = candidate ? "candidate" : edge.type.equals("OUTPUTS") ? "output"
        : edge.type.equals("READS") ? "read" : edge.type.equals("WRITES") ? "write"
        : edge.type.equals("INITIALIZES") ? "init" : "value";
```

## 6. 등록·버전·빌드 파일

`src/main/resources/META-INF/plugin.xml`의 4차수정 블록을 적용한다.
버전은 `0.3.0`이며 `<extensions>` 안에 `VariableSession` 서비스와 `Variable Relations` 창,
`<actions>` 안에 `LegacyBreaker.ExploreVariableRelations` Action을 추가한다. 기존 등록은 유지한다.
설명 문구도 변수 탐색을 포함하도록 갱신했다.

`build.sh`, `build.bat`의 javac 명령에 새 `ExploreVariableRelationsAction.java`를 추가해야
신규 제품 클래스들이 결과 JAR에 포함된다. 여러 줄인 셸 명령의 `\` 줄 연결을 함께 적용한다.

## 버전과 빌드 경로

| 파일 | 적용 내용 | 주석 형식 |
| --- | --- | --- |
| `src/main/resources/META-INF/plugin.xml` | `<version>0.3.0</version>` | `<!--4차수정 ...-->` |
| `build.bat` | JAR 경로 3곳을 `legacybreaker-0.3.0.jar`로 변경 | `rem 4차수정 ...` |
| `build.sh` | JAR 경로 3곳을 `legacybreaker-0.3.0.jar`로 변경 | `#4차수정 ...` |
| `test.sh` | 테스트 설치 JAR 경로 1곳을 `legacybreaker-0.3.0.jar`로 변경 | `#4차수정 ...` |

Java 이외 파일은 해당 파일에서 유효한 주석 문법을 사용한다.
`build.bat` 또는 `build.sh`로 다시 빌드하면 `build/distributions/legacybreaker-0.3.0.jar`가 나온다.
호환되는 IntelliJ의 **Install Plugin from Disk**에서 이 파일을 설치하고 재시작한다.

## 테스트와 문서

추가된 `samples/VariableExample.java`, `src/test/java/com/github/youngjoone/legacybreaker/VariableIntegrationTest.java`는
각각 실습·검증용 파일이다. 테스트를 실행할 경우 `IntegrationTestStarter.java`의 4차수정 두 블록도 적용한다.
제품 JAR에 테스트 파일을 넣을 필요는 없다. 변수 탐색 사용법은 [variable-relations.md](variable-relations.md)에 있다.


`src/test/java/com/github/youngjoone/legacybreaker/JspIntegrationTest.java`에도 변경·추가 블록마다
`//4차수정` 표시를 붙였다. 재대입 전후의 그래프, 사용 위치 포인터, JSP/자바 람다,
일반 반환값과 불일치 경로 후보를 실제 IDEA PSI/인덱스에서 검사한다.

문서 변경은 `README.md`, `docs/jsp-tracing.md`, `docs/architecture.md`,
`docs/verification.md`, `docs/variable-relations.md` 및 이 문서다. 제품 JAR에는 테스트와 문서가 포함되지 않는다.
