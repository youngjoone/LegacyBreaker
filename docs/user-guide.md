<!--4차수정: 신규 문서 전체 — 처음 사용하는 사람을 위한 설치·실행·결과 확인 안내.-->
# LegacyBreaker 기본 사용 설명서

대상 버전: **0.3.0 / Java 8 / IntelliJ IDEA 2018.3(빌드 183)**

프로젝트 소스를 읽어 메서드 호출 관계, 변수의 사용·변경 위치, JSP 값의 출처 후보를 보여주는
IntelliJ 플러그인이다. 외부 AI나 인터넷 연결 없이 사용한다.

## 1. 설치하기

설치 파일은 `legacybreaker-0.3.0.jar`다. IntelliJ 안에서 설치하며,
이 파일을 더블클릭하거나 `java -jar`로 실행하지 않는다.

1. IntelliJ에서 **Settings → Plugins**를 연다.
2. **Install Plugin from Disk**를 선택한다. 설정 화면에 따라 톱니바퀴 메뉴 안에 있다.
3. `build/distributions/legacybreaker-0.3.0.jar`를 선택한다.
4. 적용한 뒤 IntelliJ를 재시작한다.
5. Plugins 목록에서 **Code Relationship Explorer**가 활성화됐는지 확인한다.

### 소스로 직접 빌드하는 경우

기존 **JDK 8**과 **IntelliJ IDEA 2018.3 설치 폴더**가 필요하다.
프로젝트의 `src/main/java`, `src/main/resources`, `build.bat`을 갖춘 폴더에서
Windows 명령 프롬프트(cmd)를 열고 실행한다. 경로는 자신의 설치 위치로 바꾼다.

```bat
set "LEGACYBREAKER_JDK=C:\Program Files\Java\jdk1.8.0_202"
set "LEGACYBREAKER_IDEA=C:\Program Files\JetBrains\IntelliJ IDEA 2018.3.6"
build.bat
```

성공하면 `build/distributions/legacybreaker-0.3.0.jar`가 만들어진다.
이 파일로 위 설치 절차를 진행한다. Gradle·Maven·추가 라이브러리 다운로드는 필요 없다.

기존 소스에 변경분을 적용하는 위치는 [4차수정 통합 적용 목록](revision-4.md)에 있다.
새 파일은 전체를 추가하고, 기존 파일은 `//4차수정` 주석 위아래의 교체 범위를 확인한다.

## 2. 분석할 프로젝트 준비하기

1. IntelliJ에서 분석할 Java 프로젝트를 연다.
2. Java 소스 폴더가 **Sources Root**인지 확인한다. 필요하면 폴더 우클릭 → **Mark Directory as → Sources Root**로 지정한다.
3. 프로젝트가 원래 사용하던 JDK와 라이브러리를 연결한다.
   JSP의 request 속성 작성자를 찾으려면 기존 Servlet API 타입이 해석돼야 한다.
4. 하단의 인덱싱 작업이 끝날 때까지 기다린다. 검색을 먼저 실행하면 인덱싱 완료를 기다린다.

서버를 실행하지 않아도 소스 분석을 시작할 수 있다. 실행 중 실제 값과 실행 경로는 디버거에서 확인한다.

## 3. 어떤 메뉴를 선택하나요?

| 알고 싶은 것 | 커서를 놓는 곳 | 에디터 우클릭 메뉴 | 결과 창 |
| --- | --- | --- | --- |
| 이 변수를 어디서 읽고 바꾸는가? | Java 변수 이름 | **Explore Variable Relations** | Variable Relations |
| 이 메서드를 누가 호출하고, 무엇을 호출하는가? | Java 메서드 선언 또는 본문 | **Explore Code Relations** | Code Relations |
| JSP에 출력하는 값이 어디에서 왔는가? | JSP의 `<%= ... %>` 또는 직접 대입받는 JS 변수 선언 | **Trace JSP Value Origin** | JSP Value Origins |

세 창은 각각 결과를 유지한다. 창을 숨겨도 결과는 유지되지만 IDE를 재시작하면 복원하지 않는다.

## 4. 변수의 사용·변경 위치 보기 — 가장 먼저 써볼 기능

예를 들어 다음 코드에서 **`status` 이름 위에 커서**를 놓는다.

```java
String status = "READY";
status = loadStatus();
if (status != null) {
    show(status);
}
```

1. 우클릭 → **Explore Variable Relations**를 선택한다.
2. 가운데 변수 선언과 초기값, 읽는 위치, 변경하는 위치를 확인한다.
3. **Changes**를 누르면 초기값과 대입·재대입·증감 위치를 모아서 볼 수 있다.
4. **Reads**를 누르면 조건식, 메서드 인자 등 읽는 위치를 볼 수 있다.
5. 노드를 더블클릭하면 그 코드 위치로 이동한다. **Go To Source** 또는 Enter도 사용할 수 있다.

| 노드 표시 | 뜻 |
| --- | --- |
| VARIABLE | 선택한 변수 선언 |
| INITIALIZER | 선언에 적힌 초기값 식 |
| READ | 값을 읽는 위치 |
| WRITE | 값을 대입하는 위치 |
| READ_WRITE | `+=`, `++`, `--`처럼 읽고 다시 쓰는 위치 |

화살표는 **초기값·쓰기 위치 → 변수 → 읽기 위치**다.
**All**은 전체 결과, **Show Root**는 시작 변수를 화면에 표시한다.
노드를 끌어서 옮길 수 있고, 그래프가 크면 스크롤한다.

지역 변수·매개변수·필드를 지원한다. 이름이 같아도 다른 선언이면 구분한다.
필드는 같은 선언을 참조하는 다른 파일의 코드도 찾지만, 같은 객체의 필드인지는 확정하지 않는다.

위 예제에서 `loadStatus()`는 대입식으로 보인다. **현재 이 메뉴가 그 메서드의 반환값 출처까지 자동으로 따라가지는 않는다.**
또한 목록은 소스에 있는 위치이므로 어느 변경값이 실제로 어느 읽기에 도달했는지는 보장하지 않는다.

실습: [VariableExample.java](../samples/VariableExample.java)의 `amount`로 시작한다.
정상 예제 결과는 읽기 5곳, 쓰기 2곳, 읽기+쓰기 3곳, 초기값 1개다.

## 5. JSP 화면에서 자바 코드로 따라가기

현재 지원하는 대표적인 형태는 다음과 같다.

```jsp
<% String userStatus = (String) request.getAttribute("userStatus"); %>
<script>
var status = "<%= userStatus %>";
</script>
```

1. JS 선언의 `status` 또는 `<%= userStatus %>` 안에 커서를 둔다.
2. 우클릭 → **Trace JSP Value Origin**을 선택한다.
3. `status ← JSP 출력 ← userStatus ← request 속성 ← 자바 setAttribute 후보` 순서로 확인한다.
4. `setAttribute` 후보를 더블클릭해 해당 자바 코드로 이동한다.
5. 값으로 넘기는 Java 변수 이름에서 **Explore Variable Relations**를 실행하면 그 변수의 사용·변경 위치를 볼 수 있다.
6. 메서드 호출 관계가 필요하면 JSP 그래프의 Java 노드에서 **Explore Method Calls**를 누른다.

주황색 점선의 **candidate**는 작성자 후보다. 같은 키를 사용하는 다른 화면의 코드도 포함될 수 있다.
하단의 설명을 보고 해당 화면과 관련된 후보인지 확인한다.

`<% ... %>`는 자바 스크립틀릿이다. `${...}`나 `<c:...>` 같은 EL/JSTL, ModelAndView,
동적 키, include를 따라가는 추적은 아직 지원하지 않는다.
재대입 등으로 값의 출처를 확정하기 어려우면 그 위치에서 추적을 멈춘다.

실습: [status.jsp](../samples/jsp/status.jsp), [StatusController.java](../samples/jsp/StatusController.java).

## 6. 메서드 호출 관계 보기

1. Java 메서드 선언 또는 본문에 커서를 둔다.
2. 우클릭 → **Explore Code Relations**를 선택한다.
3. **Caller**는 이 메서드를 호출하는 쪽, **Callee**는 이 메서드가 호출하는 쪽이다.
4. 노드를 선택한 뒤 **Expand Callers / Expand Callees**로 필요한 방향을 추가 탐색한다.
5. 노드를 더블클릭해 소스로 이동한다.

화살표는 **호출하는 메서드 → 호출되는 메서드**다.
메서드 호출식 위에서 실행해도 시작점은 그 호출식을 **포함한 메서드**다.
호출 대상 메서드에서 시작하고 싶으면 먼저 그 선언으로 이동해 실행한다.

실습: [Example.java](../samples/Example.java)의 `save(int)`에서 시작한다.

## 7. 수정 후 새로고침과 안내 메시지

| 표시/상황 | 의미와 할 일 |
| --- | --- |
| snapshot / Code changed | 소스가 바뀌었다. 변수 창은 **Refresh**, 메서드 창은 **Refresh Root**, JSP 창은 **Refresh Trace**로 다시 분석한다. |
| PARTIAL / partial | 검색 제한에 걸려 일부 결과만 표시했다. 결과에 없다고 사용처가 없는 것은 아니다. |
| candidate | 가능한 작성자다. 실제 실행된 경로라는 뜻은 아니다. |
| stopped / boundary | 지원 범위나 불확실성 때문에 그 위치에서 추적을 멈췄다. 해당 소스와 설명을 확인한다. |
| 검색 중 또는 인덱싱 대기 | 완료를 기다리거나 **Cancel / Cancel Search**로 취소한다. |
| Variable/Root was removed | 시작한 선언이 삭제됐다. 다른 선언에서 다시 시작한다. |

## 8. 처음 사용할 때 막히는 경우

| 증상 | 확인할 것 |
| --- | --- |
| 설치할 수 없다는 메시지 | IDEA 빌드 번호가 183 계열인지, 설치 파일이 0.3.0인지 확인한다. |
| 우클릭 메뉴가 안 보임 | 플러그인 활성화·IDE 재시작 여부와 Java/JSP 파일 종류를 확인한다. |
| 변수를 선택하라는 안내 | 변수의 **이름**에 커서를 놓는다. 타입명, 메서드명, 연산자는 대상이 아니다. |
| 결과가 비어 있거나 후보가 없음 | Java source root, 인덱싱 완료, 기존 프로젝트 의존성 해석 여부를 확인한다. 지원하지 않는 표현식인지도 확인한다. |
| 자바 변수에서 setter·Map 변경이 안 보임 | 객체 내용 변경을 변수 자체의 재대입으로 분류하지 않는다. 해당 호출 코드로 이동해 확인한다. |
| 그래프 일부가 화면 밖에 있음 | 스크롤하거나 **Show Root**를 누른다. |
| 빌드가 JDK 버전 오류로 중단됨 | `LEGACYBREAKER_JDK`가 JRE 또는 다른 JDK가 아닌 JDK 8 경로인지 확인한다. |

## 현재 구현 범위

지금 사용할 수 있는 기능은 **메서드 호출 관계**, **변수의 초기값·읽기·변경 위치 목록**,
**지원되는 JSP 표현식에서 request 작성자 후보까지의 추적**이다.

메서드 인자→매개변수와 반환값을 연속으로 연결하는 값 추적, DTO getter/setter·Map 내용 추적,
DB 실제 값까지의 자동 추적은 다음 단계다.

상세 설명: [변수 탐색](variable-relations.md) · [JSP 역추적](jsp-tracing.md) · [빌드 및 전체 개요](../README.md).
