# 헌옷일번지 Windows 관리자

.NET Framework 4.8 WPF 프로그램. 기존 관리자 Bearer 인증 API를 사용하며 Cloudflare 인증키를 배포하지 않습니다.

빌드: Windows PowerShell에서 `powershell -NoProfile -ExecutionPolicy Bypass -File desktop-admin/build.ps1`.

필요 환경: .NET Framework 4.8 reference assemblies 및 Windows .NET Framework C# compiler. 추가 NuGet 의존성 없음.

산출물: `build/HeonotDesk-Setup-1.0.2.exe`, 설치 전 직접 실행하는 `build/HeonotDesk.exe` 및 `.config`.

버전은 `version.json`과 `src/AppVersion.cs`에서 함께 올립니다. `node scripts/publish-desktop.mjs`는 버전별 설치 파일과 크기·SHA-256이 들어 있는 `public/desktop-app/latest.json`을 준비합니다. 배포하면 기존 1.0.1 이상 프로그램이 시작 시 및 매시간 확인해 자동 다운로드합니다. 사용자가 설치를 선택하면 검증된 설치 파일을 실행하고 앱을 종료합니다. 설치기는 해당 프로세스 종료를 기다린 뒤 고정 설치 폴더의 실행 파일·설정·제거 파일·아이콘만 교체하고 재실행합니다. 로그인 토큰과 업무 데이터는 건드리지 않습니다.

업데이트 단위 검증: `HeonotDesk.exe --test-updater <결과파일>`.

설치 교체 검증: `HeonotDesk-Setup-1.0.2.exe --self-test <결과파일>`. 임시 폴더에서만 실행 파일 교체와 잠긴 파일 처리 과정을 시험하며 실제 설치·바로가기·레지스트리를 바꾸지 않습니다.

시각 검증: `HeonotDesk.exe --self-test <출력폴더>`. 예시 자료로 다섯 화면을 렌더링하며 서버에 접속하거나 실제 로그인 파일을 바꾸지 않습니다.

실제 연결 검증: `HeonotDesk.exe --check-connection <결과파일>`. 단기 관리자 세션은 프로세스 환경변수 `HEONOT_DESK_TEST_TOKEN`으로만 전달합니다. 집계 및 유입 표를 읽기만 하며 고객 데이터를 내보내거나 수정하지 않습니다.

개발 미리보기: `HeonotDesk.exe --demo`. 저장 동작은 비활성화되어 있습니다.

서버 회귀 검증: `node --test tests/desktop-admin.test.mjs tests/website-stats*.test.mjs`.

사용 안내: `docs/PC관리자-설치와사용.md`.
