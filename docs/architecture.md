# Java 8 / 오프라인 V0.3 설계

## 조건

- Java 8, 외부 런타임 라이브러리 없음, 네트워크 호출 없음.
- SDK 기준: IDEA Community 2018.3.6 (183.6156.11).
- 사용자가 회사 IDE 버전 일치를 확인했다. Windows 설치/GUI 실행 검증은 남아 있다.
- 일반 데이터 흐름/Spring 전용/MyBatis/AI는 범위 밖이다. 제한된 JSP 값 추적은 아래 별도 경로로 구현한다.

## 단계 1~4: 진입점과 로컬 빌드

`AnAction`, `EditorPopupMenu`, `CommonDataKeys`로 Java 에디터에 Action을 등록한다.
메뉴 갱신은 파일/에디터 유무만 검사하고, Action 실행 시 `PsiDocumentManager.commitDocument()`로
현재 문서를 동기화한 뒤 커서의 `PsiMethod`를 찾는다. 클래스 경계와 생성자를 구분한다.
호출 표현식에 커서를 둬도 Root는 그 표현식을 **포함하는 메서드**다.

JDK 8의 `javac`와 `jar`로 빌드하며 IDE JAR는 컴파일 시에만 참조한다.
실제 JDK 8을 사용하여 최신 Java API가 섞이는 것을 방지한다.
`com.intellij.modules.java`로 구버전 Java 기능 의존성을 선언한다.
2019.2 이후 SDK로 옮길 때는 Java 플러그인의 별도 클래스패스도 확인해야 한다.

## 단계 5: Caller

`ReferencesSearch` + `GlobalSearchScope.projectScope()`로 해당 메서드 참조만 검색한다.
그중 실제 `PsiMethodCallExpression`의 메서드 참조만 사용한다.
`resolveMethod()`와 `areElementsEquivalent()`로 정적 대상이 같은 호출만 연결한다.
람다/초기화 블록은 제외하고 중첩 클래스 메서드는 독립적인 호출자로 취급한다.
이 방식은 문자열 검색보다 오버로드를 정확히 구분하고 기존 인덱스를 활용한다.

## 단계 6: Callee

`JavaRecursiveElementWalkingVisitor`로 선택한 본문만 방문한다.
메서드 호출을 `resolveMethod()`로 해석하며 중첩 클래스와 람다 본문에는 들어가지 않는다.
재귀 호출은 일반 간선으로 표현하고, 해석 실패는 별도 개수로 표시한다.
방향별 100개 메서드, 방문 20,000회, 약 5초의 협력적 검색 제한을 적용한다.
플랫폼 내부에서 작업하는 시간까지 강제로 끊는 deadline은 아니므로 엄격한 5초 상한은 아니다.

## 단계 7: 그래프 모델

`CodeNode`, `CodeEdge`, `CodeGraph`, `Direction`은 JDK에만 의존한다.
노드에는 식별자, 타입, 메서드 시그니처, 클래스명, 파일 URL, 줄 번호, 외부 소스 여부가 있다.
식별자는 파일 URL + 클래스 + 매개변수 타입을 포함한다. 익명/지역 클래스에는 위치도 포함한다.
소스 변경 후 식별자가 달라질 수 있어 기존 그래프를 snapshot으로 표시하고 Root에서 새로고침한다.

간선은 source/target/type으로 식별한다. 타입은 확장 가능한 문자열이며 Java 관계에서는 `CALLS`를 생성한다.
Java 관계 방향은 caller → callee다. 중복·재귀·순환을 허용하고 노드/간선 수에 상한을 둔다.
스마트 PSI 포인터는 `AnalysisResult`와 프로젝트 세션이 소유하며 그래프 모델에는 넣지 않는다.

## 단계 8: UI와 탐색 상태

`CodeRelationsToolWindowFactory`는 Swing `CodeRelationsPanel`과 `GraphCanvas`를 만든다.
화면은 PSI를 읽거나 검색하지 않고 모델을 렌더링한다. 초기 노드는 열별로 배치하고,
기존 노드 위치를 유지한 채 새 노드를 추가한다. 스크롤, 선택, 노드 이동, 우클릭을 지원한다.

프로젝트 `ExplorationSessionService`가 Root·그래프·선택·완료된 확장·진행 작업을 소유한다.
그래서 소스 이동/Tool Window 숨김으로 탐색 상태가 사라지지 않는다.
프로젝트/IDE 재시작 후 복원, 여러 탐색 탭은 V0.1에 없다.

## 단계 9: 소스 이동

`SmartPsiElementPointer<PsiMethod>`를 다시 해석한 뒤 `Navigatable`로 이동한다.
기존 파일 오프셋만 저장해 이동하지 않으므로 앞부분에 줄이 추가돼도 메서드를 추적한다.
라이브러리는 IDE의 소스 연결/디컴파일 지원을 사용한다.
메서드가 삭제되어 포인터가 무효해지면 잘못된 줄로 이동하지 않고 안내한다.

## 단계 10: Lazy Expansion과 작업 수명

선택 시 직접 Caller/Callee 두 방향만 실행한다. 이후 각 노드/방향은 사용자 요청으로 확장한다.
완료한 방향은 그래프와 요약을 재사용한다. 실패/취소한 방향은 다시 실행할 수 있다.

실제 183 SDK에서 `ReadAction.nonBlocking()`과 `inSmartMode()` 지원을 확인했다.
2개의 백그라운드 작업으로 제한하고 인덱싱 완료를 기다린다. 방문마다 취소를 확인한다.
쓰기 작업이 들어오면 읽기 액션을 취소·재시작할 수 있어 긴 읽기 잠금으로 편집을 막지 않는다.
재시작 가능한 계산은 세션을 직접 변경하지 않으며 결과 DTO만 반환한다.

결과 병합은 `finishOnUiThread()`로 EDT에서 수행한다. 세대 번호를 검사해 취소·Root 교체·
프로젝트 종료 후 도착한 결과를 버린다. 실제 183 API에 맞춰 `ModalityState.NON_MODAL`을 사용한다.

PSI 변경과 프로젝트 루트 변경을 구독한다. 변화가 있으면 대기 작업과 확장 캐시를 무효화하고
현재 그래프는 snapshot으로 남긴다. 이 상태에서는 확장 버튼을 막고 Refresh Root를 안내한다.
자동으로 전체 프로젝트를 재분석하지 않는다. 초기 버전에서는 관련 없는 파일 변경도 무효화한다.

서비스 dispose 때 작업을 취소하고 실행기를 종료한다. UI dispose 때 세션 리스너를 해제한다.

## V0.2: JSP 값 출처 탐색

`TraceJspValueAction` → `JspTraceService` → `JspValueAnalyzer`로 진행한다.
`JspSource`는 JSP 블록을 추출해 위치 대응표와 임시 Java 파일을 만든다.
JS 선언 바인딩은 문자열/주석을 가린 뒤 한 번 추출해 재사용한다. Ultimate 언어 API는 쓰지 않는다.
`LocalValue`가 Java PSI에서 지역 변수의 보수적인 출처를 판정한다. 재대입/루프/람다 등은 중단한다.

`RequestWriterFinder`는 속성 키의 단어 인덱스로 후보 파일을 찾고, PSI에서 문자열 키와
Servlet request 수신 타입을 확인한다. 문자열 화면 경로는 힌트로 표시하며, 불일치만으로 제외하지 않는다.
<!--4차수정: 사용 위치별 그래프 노드와 불확실한 출처의 경계 처리.-->
자바 로컬 변수 노드는 선언이 아닌 사용 위치로 식별하고 해당 위치에 스마트 포인터를 만든다.
같은 변수를 재대입한 전후의 출처가 하나의 노드에 섞이지 않는다. 람다 내부 로컬 출처는 중단한다.
`ValueTrace`의 typed edge는 출처 → 사용처 방향이며 요청 경계를 넘는 연결은 `CANDIDATE_ATTRIBUTE`다.
그래프 모델은 계속 JDK에만 의존하고, 소스 위치/스마트 포인터는 결과와 세션이 관리한다.

별도 `JspTraceService`가 취소 가능한 smart-mode 읽기 작업과 세대 번호를 관리한다.
분석 결과를 EDT에서 게시하기 전에 코드·루트·문서 버전을 검사한다.
JSP 위치는 RangeMarker, Java 위치는 스마트 PSI 포인터로 추적한다.
`JspTracePanel`은 공통 Swing 그래프를 재사용하고 후보 연결을 점선으로 표시한다.
기존 메서드 탐색으로 넘어가도 JSP 그래프는 유지한다. 두 창 모두 IDE 종료 후 저장하지 않는다.

정확한 제한과 사용 예는 [JSP 역추적](jsp-tracing.md)에 기록한다.

<!--4차수정: 독립된 변수 탐색의 구조 추가.-->
## V0.3: Java 변수 위치 탐색

`ExploreVariableRelationsAction`은 파일과 커서 위치를 `VariableSession`에 넘긴다.
취소 가능한 smart-mode 읽기 액션에서 `VariableAtCaret`이 변수 이름을 해석하고,
`VariableAnalyzer`가 `ReferencesSearch`와 해석된 선언 비교로 같은 변수의 참조를 수집한다.
초기값, 단순 대입, 복합 대입/증감, 읽기를 구분한다. 배열 요소/객체 내용 변경을 변수 재대입과 혼동하지 않는다.
검색 콜백은 결과 객체로 동기화하고 참조 200개·방문 20,000회·약 5초의 협력적 제한을 적용한다.

`VariableAnalysis`가 스마트 포인터와 집계를 소유한다. UI와 `CodeGraph`는 계속 PSI와 분리한다.
쓰기/초기값 → 변수 → 읽기 방향으로 연결하며, 값이 특정 읽기에 도달한다는 의미를 부여하지 않는다.
필드 검색은 같은 선언의 여러 객체를 포함할 수 있고 객체 동일성을 추론하지 않는다.
`VariableSession`은 한 작업씩 실행하고 세대 번호·PSI/루트/문서 버전으로 오래된 결과를 차단한다.
All/Reads/Changes는 검색 결과를 필터링하며, 다른 탐색 창의 상태는 변경하지 않는다.
서비스 종료 시 작업과 실행기를 정리한다.

## 검증과 근거

실제 JDK 8 + 기준 SDK에서 컴파일하고, 별도 headless IDE에서 실제 PSI/참조 인덱스와
서비스 수명을 검사한다. 테스트에는 SDK 내장 구성만 사용하며 제품에 테스트 코드를 넣지 않는다.

- [플랫폼별 Java 버전](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html)
- [Java 기능 의존성 분리](https://blog.jetbrains.com/platform/2019/06/java-functionality-extracted-as-a-plugin/)
- [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)
- [Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)

최신 문서만으로 구버전 호환성을 가정하지 않고 183 바이너리의 실제 API와 컴파일로 확인한다.
