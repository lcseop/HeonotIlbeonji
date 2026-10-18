using System;
using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;

namespace HeonotDesk {
 public sealed class DesktopRelease {
  public string Version,Path,Sha; public long Size;
  public static DesktopRelease Parse(Dictionary<string,object> json){
   string version=Json.S(json,"version"),path=Json.S(json,"path"),sha=Json.S(json,"sha256");long size=Json.N(json,"size");
   if(!Regex.IsMatch(version,@"^\d{1,5}\.\d{1,5}\.\d{1,5}$")||!Regex.IsMatch(sha,@"^[a-fA-F0-9]{64}$")||size<4096||size>50*1024*1024)throw new Exception("업데이트 정보 형식이 올바르지 않습니다.");
   string filename="HeonotDesk-Setup-"+version+".exe";
   if(path!="/desktop-app/"+filename||Json.S(json,"filename")!=filename)throw new Exception("업데이트 설치 파일 경로를 확인할 수 없습니다.");
   return new DesktopRelease{Version=version,Path=path,Sha=sha,Size=size};
  }
  public bool NewerThan(string installed){return new Version(Version)>new Version(installed);}
  public static string Digest(string path){using(var file=File.OpenRead(path))using(var hash=SHA256.Create())return BitConverter.ToString(hash.ComputeHash(file)).Replace("-","").ToLowerInvariant();}
  public bool ValidFile(string path){try{return new FileInfo(path).Length==Size&&String.Equals(Digest(path),Sha,StringComparison.OrdinalIgnoreCase);}catch{return false;}}
 }
 public sealed class DesktopUpdater : IDisposable {
  readonly HttpClient http;
  readonly string cacheDirectory;
  readonly CancellationTokenSource cancel=new CancellationTokenSource();
  readonly Action<string,int,bool> notify;
  bool checking,disposed; DesktopRelease release;string readyPath;
  public bool Ready{get{return release!=null&&!String.IsNullOrEmpty(readyPath);}}
  public string LastError{get;private set;}
  public string LatestVersion{get{return release==null?"":release.Version;}}
  public DesktopUpdater(Action<string,int,bool> update,HttpMessageHandler handler=null,string cache=null){notify=update;http=new HttpClient(handler??new HttpClientHandler{AllowAutoRedirect=false}){Timeout=TimeSpan.FromMinutes(3)};cacheDirectory=cache;}
  public async Task Check(bool manual){
   if(checking||disposed)return;checking=true;
   try{
    using(var timeout=CancellationTokenSource.CreateLinkedTokenSource(cancel.Token)){
     timeout.CancelAfter(TimeSpan.FromSeconds(10));
     using(var result=await http.GetAsync(Api.Origin+"/desktop-app/latest.json",timeout.Token)){
      result.EnsureSuccessStatusCode();var next=DesktopRelease.Parse(Json.Read(await result.Content.ReadAsStringAsync()));
      if(!next.NewerThan(AppVersion.Current)){if(manual)notify("최신 버전입니다. ("+AppVersion.Current+")",100,false);return;}
      release=next;
     }
    }
    notify("새 버전 "+release.Version+"을 자동으로 내려받고 있습니다…",0,false);
    string folder=cacheDirectory??System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"HeonotDesk","Updates");Directory.CreateDirectory(folder);
    string target=System.IO.Path.Combine(folder,"HeonotDesk-Setup-"+release.Version+".exe");
    if(!release.ValidFile(target)){
     string temporary=target+"."+Guid.NewGuid().ToString("N")+".part";
     using(var deadline=CancellationTokenSource.CreateLinkedTokenSource(cancel.Token)){
     deadline.CancelAfter(TimeSpan.FromMinutes(3));
     try{
      using(var result=await http.GetAsync(Api.Origin+release.Path,HttpCompletionOption.ResponseHeadersRead,deadline.Token)){
       result.EnsureSuccessStatusCode();if(result.Content.Headers.ContentLength.HasValue&&result.Content.Headers.ContentLength.Value!=release.Size)throw new Exception("업데이트 파일 크기가 다릅니다.");
       using(var input=await result.Content.ReadAsStreamAsync())using(var output=new FileStream(temporary,FileMode.CreateNew,FileAccess.Write,FileShare.None,81920,true)){
        byte[] buffer=new byte[81920];long total=0;int count;
        while((count=await input.ReadAsync(buffer,0,buffer.Length,deadline.Token))>0){total+=count;if(total>release.Size)throw new Exception("업데이트 파일 크기가 다릅니다.");await output.WriteAsync(buffer,0,count,deadline.Token);notify("새 버전 "+release.Version+" 다운로드 중",(int)(100*total/release.Size),false);}
       }
      }
      if(!release.ValidFile(temporary))throw new Exception("업데이트 파일 검증에 실패했습니다. 다시 확인해 주세요.");
      if(File.Exists(target))File.Delete(target);File.Move(temporary,target);
     }finally{if(File.Exists(temporary))File.Delete(temporary);}
     }
    }
    readyPath=target;notify("새 버전 "+release.Version+" 준비 완료 · 작업을 마친 뒤 업데이트해 주세요.",100,true);
   }catch(OperationCanceledException){if(!disposed&&manual)notify("업데이트 확인 시간이 초과되었습니다. 잠시 후 다시 확인해 주세요.",0,false);}
   catch(Exception error){LastError=error.GetType().Name+": "+error.Message;if(!disposed)notify("업데이트 연결을 확인하지 못했습니다. 왼쪽 ‘업데이트 확인’으로 다시 시도해 주세요.",0,false);}
   finally{checking=false;}
  }
  public void Install(){
   if(!Ready||!release.ValidFile(readyPath))throw new Exception("검증된 업데이트 파일이 없습니다. 업데이트를 다시 확인해 주세요.");
   string expected=System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","HeonotDesk");
   if(!String.Equals(System.IO.Path.GetFullPath(AppDomain.CurrentDomain.BaseDirectory).TrimEnd(System.IO.Path.DirectorySeparatorChar),expected,StringComparison.OrdinalIgnoreCase))throw new Exception("이 프로그램은 설치 폴더 밖에서 실행 중입니다. 새 설치 파일을 실행해 설치해 주세요.");
   var start=new ProcessStartInfo(readyPath,"--update --wait-pid "+Process.GetCurrentProcess().Id){UseShellExecute=true,WorkingDirectory=System.IO.Path.GetDirectoryName(readyPath)};
   if(Process.Start(start)==null)throw new Exception("업데이트 설치 프로그램을 실행하지 못했습니다.");
  }
  public void Dispose(){disposed=true;cancel.Cancel();http.Dispose();cancel.Dispose();}
 }
}
