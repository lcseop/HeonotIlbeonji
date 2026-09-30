# 관리자 앱 연결하기: 이 순서대로 하세요

이 앱은 홈페이지로 들어온 수거 신청을 **문자 대신 안드로이드 앱에서 확인**하는 용도입니다. 신청이 오면 휴대폰 알림이 뜨고, 앱에서 신청을 누르면 전화 걸기와 연락처 추가를 할 수 있습니다.

**지금 상태:** 앱 설치 파일(APK)은 만들어져 있습니다. 다만 홈페이지가 아직 공개 서버에 연결되지 않았고, Firebase 설정이 없는 상태로 만든 APK라서 **지금 설치해도 신청 조회와 알림은 작동하지 않습니다.** 아래 단계를 끝내고 APK를 한 번 다시 만들어야 합니다.

## 1. Cloudflare 계정을 준비하세요

1. [Cloudflare](https://dash.cloudflare.com/)에 가입하거나 로그인합니다.
2. 홈페이지에 사용할 주소를 정합니다. 처음 시험할 때는 Cloudflare가 주는 `주소.workers.dev`를 써도 됩니다. 나중에 자체 도메인을 연결할 수 있습니다.
3. 홈페이지를 **Workers**로 배포해야 합니다. 사진과 HTML 파일만 올리는 방식으로는 신청서 저장과 관리자 앱 로그인이 되지 않습니다. 아직 이 사이트는 배포되지 않았습니다.

이 단계가 끝나면 `https://...`로 시작하는 **홈페이지 주소**를 적어 두세요. 아래 4·5·7단계에서 같은 주소를 씁니다.

## 2. 신청서를 저장할 곳을 만드세요

Cloudflare 화면에서 **Workers & Pages → D1 SQL Database → Create database**를 눌러 데이터베이스를 하나 만듭니다. 이름은 `heonot-pickup`처럼 알아보기 쉽게 정하면 됩니다.

홈페이지 Worker를 배포한 뒤에는 해당 Worker의 **Bindings → Add binding → D1 database**에서 방금 만든 데이터베이스를 선택합니다. **Variable name은 반드시 `PICKUP_DB`**라고 입력합니다. 이름이 다르면 신청서가 저장되지 않습니다. 데이터베이스 ID를 관리자 앱에 입력할 필요는 없습니다. [Cloudflare D1 연결 안내](https://developers.cloudflare.com/d1/get-started/)

## 3. Firebase에서 앱 설정 파일을 받으세요

Firebase는 **문자 대신 휴대폰 알림을 보내는 서비스**입니다.

1. [Firebase 콘솔](https://console.firebase.google.com/)에 로그인하고 **프로젝트 만들기**를 누릅니다. 프로젝트 이름은 자유롭게 정해도 됩니다.
2. 프로젝트에서 **Android 앱 추가**를 누릅니다.
3. **Android 패키지 이름** 칸에 정확히 `com.heonotilbeonji.admin`을 입력합니다. 이 이름은 임의로 바꾸면 안 됩니다.
4. 안내에 따라 `google-services.json`을 다운로드합니다.
5. 받은 파일을 이 프로젝트의 `android-admin/app/` 폴더에 넣습니다. 최종 파일 이름이 **`google-services.json`**인지 확인하세요. `google-services (1).json`이면 이름을 고쳐야 합니다.

이 파일은 APK를 다시 만들 때 사용합니다. [Firebase Android 앱 등록 안내](https://firebase.google.com/docs/android/setup)

## 4. Firebase에서 알림 발송용 키를 받으세요

Firebase 프로젝트의 **톱니바퀴(프로젝트 설정) → 서비스 계정 → 새 비공개 키 생성**을 누르면 JSON 파일이 내려받아집니다. 이 파일은 **서버가 알림을 보내는 권한**이므로 다른 사람에게 보내거나 GitHub·채팅에 올리지 마세요.

나중에 Cloudflare의 비밀값을 입력할 때 이 JSON 파일을 메모장으로 열어 **전체 내용**을 복사하면 됩니다. 예전 안내처럼 Base64로 변환할 필요는 없습니다. [Firebase 서비스 계정 안내](https://firebase.google.com/codelabs/use-the-fcm-http-v1-api-with-oauth-2-access-tokens)

## 5. Turnstile에 홈페이지 주소를 등록하세요

Turnstile은 신청서의 자동·스팸 입력을 막는 확인 위젯입니다. [Cloudflare Turnstile](https://dash.cloudflare.com/?to=/:account/turnstile)에서 위젯을 만들거나 기존 위젯을 수정합니다.

**호스트 이름**에는 홈페이지 주소에서 `https://`와 뒤의 `/...`를 뺀 부분만 입력합니다. 예를 들어 홈페이지가 `https://heonot.example.com`이면 `heonot.example.com`을 입력합니다. 거기서 보이는 **Site Key**와 **Secret Key**를 각각 적어 둡니다.

## 6. Cloudflare 서버에 설정값을 넣으세요

Cloudflare에서 배포한 홈페이지 Worker를 열고 **Settings → Variables and Secrets → Add**로 아래 항목을 하나씩 입력합니다. 비밀번호나 비밀키는 **Secret** 유형으로 넣습니다. 모두 입력한 뒤 **Deploy**를 누릅니다. [Cloudflare 비밀값 입력 안내](https://developers.cloudflare.com/workers/configuration/secrets/#via-the-dashboard)

| 입력할 이름 | 넣을 값 | 유형 |
| --- | --- | --- |
| `PICKUP_DELIVERY_MODE` | `dashboard` | Text |
| `PICKUP_SMS_ENABLED` | `false` | Text |
| `SITE_ORIGIN` | 1단계에서 얻은 홈페이지 주소 전체. 예: `https://heonot.example.com` | Text |
| `TURNSTILE_SITE_KEY` | 5단계의 Site Key | Text |
| `TURNSTILE_SECRET_KEY` | 5단계의 Secret Key | Secret |
| `ADMIN_PASSWORD` | 프로젝트 `.env.local` 파일의 같은 이름 오른쪽 값 | Secret |
| `ADMIN_SESSION_SECRET` | 프로젝트 `.env.local` 파일의 같은 이름 오른쪽 값 | Secret |
| `FCM_SERVICE_ACCOUNT_JSON` | 4단계에서 받은 JSON 파일 내용 전체 | Secret |

`.env.local`에 관리자 비밀번호나 로그인 비밀키가 없다면 프로젝트 폴더에서 `npm run setup:admin`을 **한 번만** 실행합니다. 이미 있는 값은 유지됩니다. `SOLAPI_...` 항목과 `PICKUP_SMS_TO`는 문자 발송용이므로 이 방식에서는 입력하지 않아도 됩니다.

설정 후 휴대폰이나 PC에서 `홈페이지주소/api/pickup`을 열어 `"enabled":true`가 보이면 신청서 서버 설정이 완료된 것입니다. `false`라면 D1 연결 또는 위 설정값을 다시 확인하세요.

## 7. 알림 설정이 들어간 APK를 다시 만드세요

3단계의 `google-services.json`을 넣은 다음, Android Studio에서 `android-admin` 폴더를 열고 **Build → Build APK(s)**를 실행합니다. 이 컴퓨터의 Android SDK는 `C:\sdk`에 설치되어 있습니다. 명령으로 빌드할 때는 다음을 실행합니다.

```powershell
cd C:\Users\소금\Documents\GitHub\HeonotIlbeonji\android-admin
$env:ANDROID_HOME='C:\sdk'
.\gradlew.bat assembleDebug
```

완성된 파일은 `android-admin/app/build/outputs/apk/debug/app-debug.apk`입니다. **이전에 만든 APK가 아니라 이 새 파일**을 휴대폰에 설치해야 알림이 작동합니다.

## 8. 휴대폰에서 연결을 확인하세요

1. APK를 설치하고 앱을 엽니다.
2. **서버 주소 설정**을 눌러 1단계의 홈페이지 주소 전체를 입력합니다.
3. 6단계에 입력한 `ADMIN_PASSWORD`로 로그인합니다.
4. **알림 설정**을 누르고 휴대폰의 알림 권한을 허용합니다.
5. 홈페이지에서 시험 신청 1건을 보냅니다. 앱에 신청이 나타나고 알림이 오는지 확인합니다.
6. 신청을 눌러 **전화 앱에서 번호 열기**와 **연락처에 추가**가 열리는지 확인합니다.

알림이 오지 않아도 신청함에 새 신청이 보이면 **신청 저장은 성공했고 알림 연결만 확인하면 됩니다.** 신청함도 비어 있으면 먼저 2·6단계의 서버 설정을 확인하세요.
