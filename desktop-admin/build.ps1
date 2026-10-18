$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskOut = Join-Path $taskRoot 'build'
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
$taskFramework = 'C:/Windows/Microsoft.NET/Framework64/v4.0.30319'
$taskRefs = 'C:/Program Files (x86)/Reference Assemblies/Microsoft/Framework/.NETFramework/v4.8'
$taskCompiler = Join-Path $taskFramework 'csc.exe'
$taskLogo = Join-Path $taskRoot '../public/images/heonot-logo-v3-navy.png'
Add-Type -AssemblyName System.Drawing
$taskBitmap = [System.Drawing.Bitmap]::FromFile($taskLogo)
$taskSmall = New-Object System.Drawing.Bitmap($taskBitmap, 64, 64)
$taskIcon = [System.Drawing.Icon]::FromHandle($taskSmall.GetHicon())
$taskIconPath = Join-Path $taskOut 'app.ico'
$taskStream = [IO.File]::Create($taskIconPath)
try { $taskIcon.Save($taskStream) } finally { $taskStream.Dispose(); $taskSmall.Dispose(); $taskBitmap.Dispose() }
$taskArgs = @('/nologo','/noconfig','/nostdlib+','/codepage:65001','/utf8output','/target:winexe','/optimize+','/platform:anycpu',('/win32icon:' + $taskIconPath),('/out:'+(Join-Path $taskOut 'HeonotDesk.exe')))
foreach($taskRef in @('mscorlib','System','System.Core','System.Data','System.Xml','System.Net.Http','System.Web.Extensions','System.Security','System.Xaml','WindowsBase','PresentationCore','PresentationFramework')) { $taskArgs += '/reference:' + (Join-Path $taskRefs ($taskRef+'.dll')) }
$taskArgs += '/resource:'+(Join-Path $taskRoot 'src/Shell.xaml')+',Shell.xaml'
$taskArgs += '/resource:'+$taskLogo+',Logo.png'
$taskArgs += (Join-Path $taskRoot 'src/Api.cs'),(Join-Path $taskRoot 'src/Main.cs'),(Join-Path $taskRoot 'src/ConnectionTest.cs')
& $taskCompiler @taskArgs
if($LASTEXITCODE -ne 0){throw '관리자 프로그램 빌드 실패'}
Copy-Item -LiteralPath (Join-Path $taskRoot 'src/App.config') -Destination (Join-Path $taskOut 'HeonotDesk.exe.config') -Force
& $taskCompiler /nologo /codepage:65001 /utf8output /target:winexe /optimize+ ("/out:"+(Join-Path $taskOut 'Uninstall.exe')) /reference:System.Windows.Forms.dll (Join-Path $taskRoot 'src/Uninstall.cs')
if($LASTEXITCODE -ne 0){throw '제거 프로그램 빌드 실패'}
$taskSetup=@('/nologo','/codepage:65001','/utf8output','/target:winexe','/optimize+',('/win32icon:'+$taskIconPath),('/out:'+(Join-Path $taskOut 'HeonotDesk-Setup-1.0.0.exe')),'/reference:System.Windows.Forms.dll','/reference:System.Drawing.dll','/reference:Microsoft.CSharp.dll',('/resource:'+(Join-Path $taskOut 'HeonotDesk.exe')+',App.exe'),('/resource:'+(Join-Path $taskOut 'HeonotDesk.exe.config')+',App.config'),('/resource:'+(Join-Path $taskOut 'Uninstall.exe')+',Uninstall.exe'),(Join-Path $taskRoot 'src/Setup.cs'))
& $taskCompiler @taskSetup
if($LASTEXITCODE -ne 0){throw '설치 프로그램 빌드 실패'}
Write-Output ('Built: '+(Join-Path $taskOut 'HeonotDesk-Setup-1.0.0.exe'))
