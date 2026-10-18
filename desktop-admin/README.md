# 헌옷일번지 Windows 관리자

.NET Framework 4.8 WPF 프로그램. 기존 관리자 Bearer 인증 API를 사용하며 Cloudflare 인증키를 배포하지 않습니다.

빌드: Windows PowerShell에서 `powershell -NoProfile -ExecutionPolicy Bypass -File desktop-admin/build.ps1`.

필요 환경: .NET Framework 4.8 reference assemblies 및 Windows .NET Framework C# compiler. 추가 NuGet 의존성 없음.

산출물: `build/HeonotDesk-Setup-1.0.0.exe`, 설치 전 직접 실행하는 `build/HeonotDesk.exe` 및 `.config`.

시각 검증: `HeonotDesk.exe --self-test <출력폴더>`. 예시 자료로 다섯 화면을 렌더링하며 서버에 접속하거나 실제 로그인 파일을 바꾸지 않습니다.

실제 연결 검증: `HeonotDesk.exe --check-connection <결과파일>`. 단기 관리자 세션은 프로세스 환경변수 `HEONOT_DESK_TEST_TOKEN`으로만 전달합니다. 집계 및 유입 표를 읽기만 하며 고객 데이터를 내보내거나 수정하지 않습니다.

개발 미리보기: `HeonotDesk.exe --demo`. 저장 동작은 비활성화되어 있습니다.

서버 회귀 검증: `node --test tests/desktop-admin.test.mjs tests/website-stats*.test.mjs`.

사용 안내: `docs/PC관리자-설치와사용.md`.
