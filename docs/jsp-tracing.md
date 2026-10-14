# JSP 값 역추적 — V0.2.1

<!--4차수정: 후보 판정과 람다/사용 위치별 추적 정책 갱신.-->

JSP 화면에 넣는 값의 정적 출처를 찾는다. 서버를 실행하거나 외부 AI에 코드를 보내지 않는다.
Java 8과 기존 IDEA의 Java PSI/검색 인덱스만 사용한다. Ultimate 전용 JSP/JavaScript 플러그인은 필요 없다.

## 시작하기

1. Java 소스 루트와 프로젝트의 기존 Servlet API 의존성을 IDE에 설정한다.
2. JSP에서 `var status = "<%= userStatus %>";`의 `status` 또는 `<%= userStatus %>`에 커서를 둔다.
3. 에디터 우클릭 → **Trace JSP Value Origin**을 선택한다.
4. **JSP Value Origins** 창에서 출처를 확인한다. 화살표는 값의 출처에서 사용처로 향한다.
5. 노드를 더블클릭하거나 **Go To Source**로 해당 JSP/Java 코드에 이동한다.
6. Java 노드의 **Explore Method Calls**를 누르면 기존 **Code Relations** 창에서 메서드 관계를 계속 탐색한다.
   JSP 결과는 별도로 유지된다. 이것은 메서드 호출 관계 탐색이며 반환값의 모든 분기를 계산하는 기능은 아니다.

```java
// Controller
String userStatus = loadStatus();
request.setAttribute("userStatus", userStatus);
return "status";
```

```jsp
<% String userStatus = (String) request.getAttribute("userStatus"); %>
<script>
var status = "<%= userStatus %>";
</script>
```

위 구조의 추적 순서:

```text
JS status
  ← JSP <%= userStatus %>
  ← JSP 자바 변수 userStatus
  ← request.getAttribute("userStatus")
  ← [후보] Controller의 setAttribute("userStatus", userStatus)
  ← 자바 변수 userStatus
  ← loadStatus() 호출과 해석된 메서드 선언
```

실습 파일은 `samples/jsp/status.jsp`, `samples/jsp/StatusController.java`다.
샘플 Controller를 Java source root에 두고 프로젝트에서 사용하는 Servlet API가 해석되게 설정한다.
이 예제는 기존 코드의 연결 구조를 보여준다. JSP 문자열을 JS에 직접 삽입할 때의 인코딩은 별도 문제다.

## 후보의 의미

주황색 점선 **candidate**는 같은 문자열 키로 `request.setAttribute()`를 호출한 자바 코드다.
수신 객체 타입이 `ServletRequest`/`HttpServletRequest` 계열인지 검사한다.
같은 메서드의 문자열 반환값이나 `request.getRequestDispatcher()` 경로가 JSP 경로와 맞으면 힌트를 표시한다.
문자열 경로가 맞지 않거나 찾지 못한 메서드도 목적지 미확인 후보로 유지한다.
일반 문자열 반환값은 화면명이 아닐 수도 있으므로, 힌트 불일치만으로 후보를 제외하지 않는다.
이 때문에 다른 화면의 작성자가 함께 표시될 수 있으며 기존 30개 후보 제한은 그대로 적용한다.

같은 요청인지, 어떤 분기가 실행됐는지, 중간에 속성이 덮어써졌는지, ViewResolver 설정이 맞는지는
확정하지 않는다. **화면의 실제 값과 실행 경로는 디버거에서 확인해야 한다.**
소스가 누락되거나 Servlet 타입을 해석하지 못하면 후보를 찾지 못할 수 있다.

## 현재 지원 범위

- `.jsp`의 `<% ... %>` 스크립틀릿과 `<%= ... %>` 출력식.
- `<script>` 안의 `var`/`let`/`const` 선언에 출력식을 바로 대입한 형태.
  작은따옴표/큰따옴표 또는 따옴표 없는 단일 출력식만 연결한다.
- 로컬 변수 선언 시 초기값, 초기값 없이 같은 블록에서 한 번 직접 대입한 값.
- 괄호·형변환을 걷어낸 뒤 문자열 리터럴 키의 `request.getAttribute()`.
- 자바의 같은 키 `request.setAttribute()`와 로컬 값의 출처, 직접 호출한 메서드 선언.
  자바 변수 노드는 사용 위치별로 구분하며 소스 이동도 해당 사용 위치로 이동한다.
  재대입된 사용처는 추적을 중단하고, 이전 사용처의 초기값 경로와 합치지 않는다.
- 코드 이동, 노드 이동·스크롤, **Show Root**, **Refresh Trace**, 취소.
- 코드나 프로젝트 구성이 바뀌면 snapshot으로 표시한다. 새로고침할 때 다시 분석한다.

## 아직 지원하지 않는 경우

- JS 변수의 임의 사용 위치에서 선언 찾기, 이후 JS 재대입, 문자열 결합, 객체 프로퍼티 대입.
- JSTL `<c:...>`와 EL `${...}`. `<% ... %>`는 JSTL이 아니라 자바 스크립틀릿이다.
- `Model.addAttribute`, `ModelAndView`, `session`, `getParameter`, 동적 속성 키, 사용자 래퍼 API.
- JSP include 파일을 따라가는 분석, `<%! ... %>` 선언. include는 부분 결과로 알린다.
- 조건부 재대입, 루프, 람다 내부 로컬 변수, 필드·매개변수의 출처, 메서드 반환 분기의 데이터 흐름.
  람다 내부 작성자 자체는 후보로 표시할 수 있지만 로컬 변수의 초기값으로 이어가지 않는다.
- 서비스 → DAO → SQL → DB 실제 값의 자동 추적.

지원하지 않는 식은 경계 노드와 안내를 표시한다. 잘못된 JSP 자바 구문은 오류 안내 후 중단한다.
JSP/JS 처리는 제한된 구문 추출기이며 전체 언어 파서가 아니다.

파일은 최대 300,000자/스크립틀릿 1,000개, JSP 로컬 값은 12단계, 자바 값은 10단계까지 추적한다.
속성 작성자 검색은 인덱스에서 찾은 최대 100개 Java 파일/30개 후보, 파일당 방문 50,000회,
약 8초의 협력적 제한을 둔다. 플랫폼 인덱스 내부 작업까지 강제로 끊는 시간 상한은 아니다.
그래프 제한과 검색 제한에 걸리면 PARTIAL로 표시한다. 결과가 없다고 작성자가 없음을 뜻하지는 않는다.
