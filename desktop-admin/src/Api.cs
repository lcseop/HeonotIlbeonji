using System;
using System.IO;
using System.Text;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Collections;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace HeonotDesk {
 public sealed class LoginExpired : Exception { public LoginExpired():base("로그인이 만료되었습니다. 다시 로그인해 주세요."){} }
 public static class Json {
  static JavaScriptSerializer Serializer(){return new JavaScriptSerializer{MaxJsonLength=100000000,RecursionLimit=100};}
  public static string Write(object o){return Serializer().Serialize(o);}
  public static Dictionary<string,object> Read(string s){return Serializer().Deserialize<Dictionary<string,object>>(s);}
  public static string S(IDictionary<string,object> o,string key){return o!=null&&o.ContainsKey(key)&&o[key]!=null?Convert.ToString(o[key]):"";}
  public static long N(IDictionary<string,object> o,string key){long n;return Int64.TryParse(S(o,key),out n)?n:0;}
  public static Dictionary<string,object> D(IDictionary<string,object> o,string key){return o!=null&&o.ContainsKey(key)?o[key] as Dictionary<string,object>:null;}
  public static List<Dictionary<string,object>> Rows(IDictionary<string,object> o,string key){var a=o!=null&&o.ContainsKey(key)?o[key] as IEnumerable:null;var list=new List<Dictionary<string,object>>();if(a!=null)foreach(var row in a){var d=row as Dictionary<string,object>;if(d!=null)list.Add(d);}return list;}
  public static Dictionary<string,object> Obj(params object[] values){var o=new Dictionary<string,object>();for(int i=0;i<values.Length;i+=2)o[(string)values[i]]=values[i+1];return o;}
 }
 public sealed class Api : IDisposable {
  public const string Origin="https://1beonji.com";
  readonly HttpClient http;
  public string Token="";
  public Api(){ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;http=new HttpClient(new HttpClientHandler{AllowAutoRedirect=false});http.Timeout=TimeSpan.FromSeconds(25);}
  public async Task<Dictionary<string,object>> Call(string path,string method="GET",object payload=null){
   using(var request=new HttpRequestMessage(new HttpMethod(method),Origin+path)){
    if(!String.IsNullOrEmpty(Token))request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",Token);
    request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    if(payload!=null)request.Content=new StringContent(Json.Write(payload),Encoding.UTF8,"application/json");
    using(var response=await http.SendAsync(request)){
     string text=await response.Content.ReadAsStringAsync();if(response.StatusCode==HttpStatusCode.Unauthorized&&path!="/api/admin/native-login")throw new LoginExpired();
     Dictionary<string,object> result;try{result=Json.Read(text);}catch{throw new Exception("서버 응답을 읽지 못했습니다. 새로고침해 주세요.");}
     if(!response.IsSuccessStatusCode)throw new Exception(String.IsNullOrEmpty(Json.S(result,"message"))?"서버 연결에 실패했습니다.":Json.S(result,"message"));return result;
    }
   }
  }
  public async Task<Dictionary<string,object>> Probe(){
   var clock=System.Diagnostics.Stopwatch.StartNew();
   using(var client=new HttpClient(new HttpClientHandler{AllowAutoRedirect=false}){Timeout=TimeSpan.FromSeconds(8)}){
    var checks=await Task.WhenAll(client.GetAsync(Origin+"/"),client.GetAsync(Origin+"/js/pickup.js"),client.GetAsync(Origin+"/js/website-stats.js"),client.GetAsync(Origin+"/api/pickup"));
    try{
     var response=checks[0];var html=await response.Content.ReadAsStringAsync();bool scripts=checks[1].IsSuccessStatusCode&&checks[2].IsSuccessStatusCode&&html.Contains("pickup.js");
     var config=Json.Read(await checks[3].Content.ReadAsStringAsync());bool enabled=checks[3].IsSuccessStatusCode&&Json.S(config,"enabled").Equals("true",StringComparison.OrdinalIgnoreCase);
     bool form=html.Contains("id=\"pickupForm\"");
     return Json.Obj("http",(int)response.StatusCode,"ms",clock.ElapsedMilliseconds,"form",form,"scripts",scripts,"configuration",enabled,"ok",response.IsSuccessStatusCode&&form&&scripts&&enabled);
    }finally{foreach(var response in checks)response.Dispose();}
   }
  }
  public void Dispose(){http.Dispose();}
 }
 public static class Session {
  static string Folder{get{return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"HeonotDesk");}}
  public static string PathName{get{return Path.Combine(Folder,"session.bin");}}
  public static string Load(){try{return Encoding.UTF8.GetString(ProtectedData.Unprotect(File.ReadAllBytes(PathName),null,DataProtectionScope.CurrentUser));}catch{return "";}}
  public static void Save(string token){Directory.CreateDirectory(Folder);File.WriteAllBytes(PathName,ProtectedData.Protect(Encoding.UTF8.GetBytes(token),null,DataProtectionScope.CurrentUser));}
  public static void Clear(){if(File.Exists(PathName))File.Delete(PathName);}
 }
}
