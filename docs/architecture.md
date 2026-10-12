# Java 8 / 오프라인 개발 설계

## 확정 조건과 아직 모르는 조건

- 확정: Java 8 구현, 추가 외부 라이브러리 없음, 회사 내 빌드 시 네트워크 불필요.
- 미확정: 회사 IntelliJ 정확한 버전, 기존 Plugin DevKit과 JDK 도구 구성.
- 임시 기준: IntelliJ IDEA Community 2018.3.6, build 183.
- 회사 버전 확인 전에는 해당 환경 호환 완료라고 표현하지 않는다.
- 최신 SDK 다운로드나 Gradle Wrapper 실행을 회사 빌드의 전제조건으로 두지 않는다.

## 현재 단계 (1~4)

### 1. 책임 분리

Action은 진입과 안내만 담당하고, `MethodAtCaret`이 PSI 위치를 해석한다.
장기적인 전체 구조를 빈 클래스들로 미리 생성하지 않고 기능이 필요한 단계에 추가한다.

예정 패키지:

```text
action       사용자 요청 진입점
analysis     CallerFinder, CalleeFinder, MethodAnalyzer
model        CodeNode, CodeEdge, CodeGraph (JDK만 의존)
session      프로젝트별 탐색 상태와 분석 작업 수명
navigation   노드 ID → SmartPsiElementPointer → 실제 소스
ui           Tool Window, Swing 그래프와 입력 처리
```

### 2. 로컬 SDK 빌드

JDK 8의 `javac`와 `jar`만 사용한다. `-source 1.8 -target 1.8`과 실제 JDK 8을
함께 사용해 새 Java API가 섞이지 않게 한다. SDK JAR는 컴파일 시에만 참조한다.
빌드 도구와 SDK는 제품 런타임 의존성에 포함되지 않는다.

정확한 구버전이 확인되면 해당 SDK에서 다시 컴파일하고 실행해야 한다.
2019.2부터 Java 기능이 분리되어 그 버전을 지원할 때는 `plugins/java/lib` 클래스패스도
검토해야 한다. 구버전과 호환되는 `com.intellij.modules.java` 의존성 ID를 사용한다.

### 3. 에디터 Action

`AnAction`, `AnActionEvent`, `CommonDataKeys`, `EditorPopupMenu`를 사용한다.
현대 버전의 `getActionUpdateThread()` 등은 사용하지 않는다.
`update()`에서는 Java 파일·프로젝트·에디터 유무만 검사해 팝업 표시 비용을 제한한다.
인덱싱 중 분석을 허용하는 `DumbAware`를 선언하지 않는다.

### 4. 메서드 선택

`PsiDocumentManager.commitDocument()`로 해당 문서만 PSI와 동기화한다.
`PsiFile.findElementAt()` 이후 부모를 따라 `PsiMethod`를 찾는다.
클래스 경계를 만나면 중단하여 익명 클래스 초기화 코드를 외부 메서드에 잘못 연결하지 않는다.
커서가 호출 표현식에 있으면 호출 대상이 아니라 **그 코드를 포함한 메서드**를 선택한다.
작업은 Action 실행 시 짧은 EDT 처리로 수행하며 프로젝트 전체 검색은 하지 않는다.
현재 단계는 결과 확인용 대화상자이고 최종 그래프 UI가 아니다.

## 다음 단계 (5~10)

| 단계 | 구현 이유 및 API 후보 | 성능 및 구버전 주의점 |
| --- | --- | --- |
| 5. Caller | `ReferencesSearch`, 프로젝트 검색 범위로 직접 호출자 확인 | UI 밖에서 검색, 인덱싱 대기, 취소 확인, 결과 제한 |
| 6. Callee | 현재 본문 방문과 `PsiMethodCallExpression.resolveMethod()` | 중첩 클래스/람다 경계, 미해결 호출 처리 |
| 7. Graph | JDK 컬렉션 기반 노드/간선 병합 | 오버로드 식별, 중복·재귀·순환 처리 |
| 8. Tool Window | `ToolWindowFactory`와 Swing/Java2D | 렌더링과 PSI 분석 분리, 구버전 등록 방식 확인 |
| 9. Navigation | 스마트 포인터와 `Navigatable` | 편집/삭제된 메서드 처리, 이동해도 그래프 유지 |
| 10. Expansion | 프로젝트 서비스에서 방향별 상태 관리 | 변경 시 캐시 무효화, 작업 취소, 이전 탐색 결과 배제 |

회사 IDE 버전 확인 전에는 `ReadAction.nonBlocking()` 같은 비교적 새로운 API를
기본 전제로 삼지 않는다. 해당 SDK가 지원하는 백그라운드 작업·읽기 액션 API를 확인한다.
단순히 백그라운드로 옮겨도 긴 읽기 잠금이 편집을 막을 수 있으므로 읽기 범위와 취소를 설계한다.

그래프는 `source → target`과 확장 가능한 관계 타입을 가진다. 초기 타입은 `CALLS`다.
노드의 PSI 포인터는 그래프 데이터 외부에서 관리한다. 프로젝트 서비스가 그래프를 소유하고
뷰가 이를 표시하여 에디터 이동·Tool Window 숨김 이후에도 탐색 상태를 유지한다.

AI, SQL, Spring 전용 분석, 다른 언어, 전체 프로젝트 그래프 생성은 현재 범위에서 제외한다.

## 근거

- [플랫폼별 Java 버전](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html)
- [Java 플러그인 분리와 구버전 의존성 선언](https://blog.jetbrains.com/platform/2019/06/java-functionality-extracted-as-a-plugin/)
- [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)

현재 공식 문서의 최신 예제를 그대로 복사하지 않고 실제 기준 SDK에서 컴파일해 확인한다.
