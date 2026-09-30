# 수거 신청 문자 연결

이 문서는 이전 SMS 방식의 설정 안내입니다. 현재 `PICKUP_DELIVERY_MODE=dashboard`에서는 문자 없이 관리자 신청함으로 접수합니다. 새 방식은 [관리자 앱 안내](admin-app.md)를 확인하세요.

SMS 방식을 다시 사용할 때의 흐름: 신청서 → `/api/pickup` → 자동 입력 방지 확인 → SOLAPI 장문 문자(LMS) → 담당자 휴대폰.
기본 수신 번호는 `01048808259`입니다. 신청자가 수신번호나 발신번호를 바꿀 수 없으며, 고객에게 자동 문자를 보내지는 않습니다.

## 실제 발송을 시작하려면

이 프로젝트에는 비공개 설정 파일 `.env.local`을 준비해 두었습니다. SOLAPI 관리 화면에서 발급한 값을 이 파일의 `SOLAPI_API_KEY=`와 `SOLAPI_API_SECRET=` 뒤에 각각 붙여 넣으세요. 인증한 발신번호는 `SOLAPI_FROM=` 뒤에 숫자만 입력합니다. `PICKUP_SMS_TO=01048808259`는 신청 문자를 받을 번호입니다. 이 파일은 Git에서 제외됩니다. 비밀키를 채팅이나 공개 저장소에 올리지 마세요.

로컬 확인은 프로젝트 폴더에서 `npm run dev`를 실행하고 `http://localhost:4174/html/index.html`을 엽니다. VS Code Live Server의 `:5500` 주소는 정적 파일만 제공하므로 `/api/pickup`을 실행할 수 없습니다. `SITE_ORIGIN`도 이 미리보기 주소와 정확히 일치해야 합니다.
로컬 위젯을 시험하려면 Turnstile에 `localhost` 호스트 이름이 허용되어 있어야 합니다. 배포 후에는 실제 도메인을 Turnstile에 등록하고 `SITE_ORIGIN`도 실제 `https://` 주소로 변경합니다.

키만으로는 신청서가 켜지지 않습니다. 실제 사이트 주소인 `SITE_ORIGIN`, 그 주소로 만든 Turnstile의 `TURNSTILE_SITE_KEY`와 `TURNSTILE_SECRET_KEY`까지 입력해야 합니다. 서버를 재시작한 후 `/api/pickup` 응답의 `enabled`가 `true`이면 접수 버튼이 활성화됩니다. 호스팅할 때는 `.env.local` 파일을 업로드하는 대신 호스팅 서비스의 비밀 환경변수 설정에 같은 이름과 값을 등록하세요.

1. [SOLAPI](https://solapi.com)에 가입하고 발신번호를 등록·인증합니다. 고객의 전화번호를 발신번호로 쓰지 않습니다.
2. 문자 발송 권한이 있는 API Key와 API Secret을 만들고 발송 잔액을 준비합니다. 요금은 SOLAPI의 현재 LMS 요금을 확인하세요. 이 작업에서는 계정 생성·충전·실제 문자 발송을 하지 않았습니다.
3. Cloudflare Turnstile에서 실제 홈페이지 도메인을 등록한 위젯을 만들고 Site Key와 Secret Key를 준비합니다. 운영 환경에 테스트 키를 쓰지 않습니다.
4. 서버 환경변수를 아래 표대로 설정합니다. 비밀키는 채팅, HTML, 공개 저장소에 넣지 않습니다. 로컬 개발은 `.env.example`을 `.env.local`로 복사해 사용하고, 운영은 호스팅의 비밀 환경변수 설정을 사용합니다.
5. 실제 호스팅에서 `/api/pickup`이 동작하도록 서버 빌드로 배포합니다. `public` 폴더만 제공하는 정적 미리보기 서버는 문자 발송을 지원하지 않습니다.
6. SOLAPI 발송 한도·잔액 알림을 설정하고, 운영 호스팅에서 `/api/pickup` POST 요청에 IP별 속도 제한(예: 분당 3회)을 설정합니다. Turnstile은 자동 입력을 줄이고 토큰 재사용을 차단하지만 비용 상한을 대신하지 않습니다.
7. 사업자의 개인정보 보유·파기 기간과 문자 발송 위탁 안내를 확정해 수집 안내에 반영합니다. 이 코드에는 별도 신청 DB나 신청 내용 로그가 없습니다. 문자 내용은 SOLAPI 발송 내역과 수신 휴대폰에 남으므로 각 보관·파기 정책도 확인합니다.
8. `PICKUP_SMS_ENABLED=true`로 켠 뒤, 개인정보가 아닌 테스트 내용으로 신청 1건을 전송하고 SOLAPI 결과와 수신 휴대폰에서 실제 도착을 확인합니다. 화면의 성공은 문자 업체의 발송 접수를 뜻하며, 통신사 도착 확인이나 방문 예약 확정을 뜻하지 않습니다.

| 환경변수 | 값 |
| --- | --- |
| `PICKUP_SMS_ENABLED` | 준비 전 `false`, 실제 운영 시 `true` |
| `PICKUP_SMS_TO` | 수신번호. 기본 `01048808259` |
| `SOLAPI_FROM` | SOLAPI에서 인증한 발신번호 |
| `SOLAPI_API_KEY` | SOLAPI API Key |
| `SOLAPI_API_SECRET` | SOLAPI API Secret, 서버 비밀값 |
| `SITE_ORIGIN` | 실제 사이트 주소, 예: `https://your-domain.example` |
| `TURNSTILE_SITE_KEY` | 공개 가능한 위젯 Site Key |
| `TURNSTILE_SECRET_KEY` | 서버 전용 Secret Key |

## 동작 및 실패 처리

- 준비되지 않은 환경에서는 온라인 접수를 막고 전화 신청을 안내합니다.
- 필수값·길이·연락처·개인정보 동의를 서버에서도 검사합니다. 날짜는 한국 시간 기준 내일부터 90일 이내이며 일요일은 제외합니다.
- 장문 문자는 2,000바이트 안에서 전송합니다. 길면 내용을 줄이도록 안내합니다.
- 버튼을 여러 번 눌러도 진행 중에는 추가 요청하지 않습니다. 서버는 Turnstile의 일회용 토큰을 검증하고 도메인과 action까지 확인합니다.
- 발송 요청을 자동 재시도하지 않습니다. 타임아웃 등 결과가 불확실하면 내용을 지우지 않고 전화로 접수 여부를 확인하도록 안내합니다.
- 서비스 오류 원문, API 비밀키, 신청자 개인정보를 응답이나 로그에 노출하지 않습니다.
- 발송 결과가 정상 접수일 때만 신청 내용을 초기화합니다.

## 검증

`node --test tests/pickup.test.mjs`

외부 서비스는 가짜 응답으로 대체하므로 테스트에서 실제 문자나 비용이 발생하지 않습니다. 실제 수신 확인은 계정 설정과 배포 후 별도로 필요합니다.

공식 문서: [SOLAPI 발송](https://solapi.com/developers/api/messages), [인증](https://solapi.com/developers/api/authentication-api-key), [Turnstile 서버 검증](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/).
