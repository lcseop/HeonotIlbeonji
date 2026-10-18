$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskOut = Join-Path $taskRoot 'build'
$taskVersion = (Get-Content -LiteralPath (Join-Path $taskRoot 'version.json') -Raw | ConvertFrom-Json).version
$taskDeclaredVersion = [regex]::Match([IO.File]::ReadAllText((Join-Path $taskRoot 'src/AppVersion.cs')), 'Current="([0-9.]+)"').Groups[1].Value
if($taskVersion -ne $taskDeclaredVersion){throw 'version.json and AppVersion.cs must match'}
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
$taskFramework = 'C:/Windows/Microsoft.NET/Framework64/v4.0.30319'
$taskRefs = 'C:/Program Files (x86)/Reference Assemblies/Microsoft/Framework/.NETFramework/v4.8'
$taskCompiler = Join-Path $taskFramework 'csc.exe'
$taskLogo = Join-Path $taskRoot '../public/images/heonot-logo-v3-navy.png'
Add-Type -AssemblyName System.Drawing
$taskBitmap = [System.Drawing.Bitmap]::FromFile($taskLogo)
$taskIconPath = Join-Path $taskOut 'app.ico'
$taskFrames = @()
foreach($taskSize in @(16,24,32,48,64,128,256)) {
  $taskSmall = New-Object System.Drawing.Bitmap($taskSize,$taskSize)
  $taskGraphics = [System.Drawing.Graphics]::FromImage($taskSmall)
  $taskGraphics.Clear([System.Drawing.Color]::White)
  $taskGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $taskScale = ($taskSize * 0.88) / [Math]::Max($taskBitmap.Width,$taskBitmap.Height)
  $taskWidth = [int]($taskBitmap.Width * $taskScale)
  $taskHeight = [int]($taskBitmap.Height * $taskScale)
  $taskGraphics.DrawImage($taskBitmap,[int](($taskSize-$taskWidth)/2),[int](($taskSize-$taskHeight)/2),$taskWidth,$taskHeight)
  $taskFrameStream = New-Object IO.MemoryStream
  $taskSmall.Save($taskFrameStream,[System.Drawing.Imaging.ImageFormat]::Png)
  $taskFrames += [PSCustomObject]@{ Size=$taskSize; Bytes=$taskFrameStream.ToArray() }
  $taskFrameStream.Dispose(); $taskGraphics.Dispose(); $taskSmall.Dispose()
}
$taskBitmap.Dispose()
$taskWriter = New-Object IO.BinaryWriter([IO.File]::Create($taskIconPath))
try {
  $taskWriter.Write([UInt16]0); $taskWriter.Write([UInt16]1); $taskWriter.Write([UInt16]$taskFrames.Count)
  $taskOffset = 6 + 16 * $taskFrames.Count
  foreach($taskFrame in $taskFrames) {
    $taskDimension = if($taskFrame.Size -eq 256){0}else{$taskFrame.Size}
    $taskWriter.Write([Byte]$taskDimension); $taskWriter.Write([Byte]$taskDimension); $taskWriter.Write([Byte]0); $taskWriter.Write([Byte]0)
    $taskWriter.Write([UInt16]1); $taskWriter.Write([UInt16]32); $taskWriter.Write([UInt32]$taskFrame.Bytes.Length); $taskWriter.Write([UInt32]$taskOffset)
    $taskOffset += $taskFrame.Bytes.Length
  }
  foreach($taskFrame in $taskFrames){$taskWriter.Write([Byte[]]$taskFrame.Bytes)}
} finally { $taskWriter.Dispose() }
$taskArgs = @('/nologo','/noconfig','/nostdlib+','/codepage:65001','/utf8output','/target:winexe','/optimize+','/platform:anycpu',('/win32icon:' + $taskIconPath),('/out:'+(Join-Path $taskOut 'HeonotDesk.exe')))
foreach($taskRef in @('mscorlib','System','System.Core','System.Data','System.Xml','System.Net.Http','System.Web.Extensions','System.Security','System.Xaml','WindowsBase','PresentationCore','PresentationFramework')) { $taskArgs += '/reference:' + (Join-Path $taskRefs ($taskRef+'.dll')) }
$taskArgs += '/resource:'+(Join-Path $taskRoot 'src/Shell.xaml')+',Shell.xaml'
$taskArgs += '/resource:'+$taskLogo+',Logo.png'
$taskArgs += '/resource:'+$taskIconPath+',App.ico'
$taskArgs += (Join-Path $taskRoot 'src/Api.cs'),(Join-Path $taskRoot 'src/Main.cs'),(Join-Path $taskRoot 'src/ConnectionTest.cs'),(Join-Path $taskRoot 'src/AppVersion.cs'),(Join-Path $taskRoot 'src/DesktopUpdater.cs'),(Join-Path $taskRoot 'src/UpdateTests.cs')
& $taskCompiler @taskArgs
if($LASTEXITCODE -ne 0){throw '관리자 프로그램 빌드 실패'}
Copy-Item -LiteralPath (Join-Path $taskRoot 'src/App.config') -Destination (Join-Path $taskOut 'HeonotDesk.exe.config') -Force
& $taskCompiler /nologo /codepage:65001 /utf8output /target:winexe /optimize+ ('/win32icon:'+$taskIconPath) ("/out:"+(Join-Path $taskOut 'Uninstall.exe')) /reference:System.Windows.Forms.dll (Join-Path $taskRoot 'src/Uninstall.cs') (Join-Path $taskRoot 'src/AppVersion.cs')
if($LASTEXITCODE -ne 0){throw '제거 프로그램 빌드 실패'}
$taskInstallerName = 'HeonotDesk-Setup-'+$taskVersion+'.exe'
$taskSetup=@('/nologo','/codepage:65001','/utf8output','/target:winexe','/optimize+',('/win32icon:'+$taskIconPath),('/out:'+(Join-Path $taskOut $taskInstallerName)),'/reference:System.Windows.Forms.dll','/reference:System.Drawing.dll','/reference:Microsoft.CSharp.dll',('/resource:'+(Join-Path $taskOut 'HeonotDesk.exe')+',App.exe'),('/resource:'+(Join-Path $taskOut 'HeonotDesk.exe.config')+',App.config'),('/resource:'+(Join-Path $taskOut 'Uninstall.exe')+',Uninstall.exe'),('/resource:'+$taskIconPath+',App.ico'),(Join-Path $taskRoot 'src/Setup.cs'),(Join-Path $taskRoot 'src/AppVersion.cs'))
& $taskCompiler @taskSetup
if($LASTEXITCODE -ne 0){throw '설치 프로그램 빌드 실패'}
Write-Output ('Built: '+(Join-Path $taskOut $taskInstallerName))
