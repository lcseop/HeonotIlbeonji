using System;
using System.IO;
using System.Threading.Tasks;
namespace HeonotDesk {
 public static class ConnectionTest {
  public static int Run(string output){
   try{using(var api=new Api()){
    api.Token=Environment.GetEnvironmentVariable("HEONOT_DESK_TEST_TOKEN");
    if(String.IsNullOrEmpty(api.Token))throw new Exception("Verification credential is missing.");
    var result=api.Call("/api/admin/desktop?month="+DateTime.UtcNow.AddHours(9).ToString("yyyy-MM")).GetAwaiter().GetResult();
    if(!result.ContainsKey("todaySummary")||!result.ContainsKey("sources"))throw new Exception("Overview response is incomplete.");
    var table=api.Call("/api/admin/desktop?mode=data&table=sources&page=0").GetAwaiter().GetResult();
    if(!table.ContainsKey("columns")||!table.ContainsKey("rows"))throw new Exception("Database view response is incomplete.");
    var health=api.Probe().GetAwaiter().GetResult();
    if(Json.S(health,"ok")!="True")throw new Exception("Website check failed.");
    File.WriteAllText(output,"Windows native client: authenticated overview, database pagination, HTTPS website check passed. No customer records exported or modified.");return 0;
   }}catch(Exception ex){File.WriteAllText(output,ex.GetType().Name+": "+ex.Message);return 1;}
  }
 }
}
