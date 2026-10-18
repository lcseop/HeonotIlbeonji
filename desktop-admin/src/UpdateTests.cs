using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

namespace HeonotDesk {
 public static class UpdateTests {
  sealed class Feed : HttpMessageHandler {
   public byte[] Binary;public string Manifest;public bool Redirect;public int Downloads;
   protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken token){
    if(request.RequestUri.Host!="1beonji.com"||request.RequestUri.Scheme!="https"||request.Headers.Authorization!=null)throw new Exception("Untrusted update request");
    if(request.RequestUri.AbsolutePath.EndsWith("latest.json"))return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK){Content=new StringContent(Manifest)});
    Downloads++;return Task.FromResult(new HttpResponseMessage(Redirect?HttpStatusCode.Redirect:HttpStatusCode.OK){Content=new ByteArrayContent(Binary)});
   }
  }
  static void Assert(bool condition,string message){if(!condition)throw new Exception(message);}
  public static int Run(string report){
   try{RunAsync().GetAwaiter().GetResult();File.WriteAllText(report,"Update tests passed: version ordering, path/size/hash validation, verified download, cache reuse, corrupt-file rejection and redirect rejection. No installer executed.");return 0;}
   catch(Exception error){File.WriteAllText(report,error.ToString());return 1;}
  }
  static async Task RunAsync(){
   string folder=Path.Combine(Path.GetTempPath(),"HeonotDesk-update-test-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(folder);
   byte[] bytes=new byte[8192];bytes[0]=77;bytes[1]=90;File.WriteAllBytes(Path.Combine(folder,"fixture"),bytes);string hash=DesktopRelease.Digest(Path.Combine(folder,"fixture"));
   var current=new Version(AppVersion.Current);string next=new Version(current.Major,current.Minor,current.Build+1).ToString();
   var manifest=Json.Obj("version",next,"filename","HeonotDesk-Setup-"+next+".exe","path","/desktop-app/HeonotDesk-Setup-"+next+".exe","size",bytes.Length,"sha256",hash);
   var parsed=DesktopRelease.Parse(manifest);Assert(parsed.NewerThan(AppVersion.Current),"New version not detected");Assert(!parsed.NewerThan(next),"Same version offered");Assert(new DesktopRelease{Version="1.0.10"}.NewerThan("1.0.2"),"Lexical version comparison");
   foreach(string path in new[]{"https://other.example/update.exe","/desktop-app/../evil.exe","/desktop-app/HeonotDesk-Setup-1.0.2.exe?redirect=evil"}){var bad=Json.Read(Json.Write(manifest));bad["path"]=path;bool rejected=false;try{DesktopRelease.Parse(bad);}catch{rejected=true;}Assert(rejected,"Unsafe update path accepted");}
   foreach(object size in new object[]{0,-1,60*1024*1024}){var bad=Json.Read(Json.Write(manifest));bad["size"]=size;bool rejected=false;try{DesktopRelease.Parse(bad);}catch{rejected=true;}Assert(rejected,"Invalid update size accepted");}
   var feed=new Feed{Binary=bytes,Manifest=Json.Write(manifest)};
   using(var updater=new DesktopUpdater((text,percent,ready)=>{},feed,folder)){await updater.Check(true);Assert(updater.Ready,"Verified update was not ready: "+updater.LastError);await updater.Check(true);Assert(feed.Downloads==1,"Verified cached download was not reused");}
   var corrupted=new byte[8192];corrupted[0]=1;var broken=new Feed{Binary=corrupted,Manifest=Json.Write(manifest)};
   using(var updater=new DesktopUpdater((text,percent,ready)=>{},broken,Path.Combine(folder,"bad-hash"))){await updater.Check(true);Assert(!updater.Ready,"Corrupt download accepted");Assert(Directory.GetFiles(Path.Combine(folder,"bad-hash")).Length==0,"Incomplete download left behind");}
   var redirect=new Feed{Binary=bytes,Manifest=Json.Write(manifest),Redirect=true};
   using(var updater=new DesktopUpdater((text,percent,ready)=>{},redirect,Path.Combine(folder,"redirect"))){await updater.Check(true);Assert(!updater.Ready,"Redirect response accepted as an executable");}
  }
 }
}
