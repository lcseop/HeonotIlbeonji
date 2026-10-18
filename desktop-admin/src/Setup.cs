using System;
using System.IO;
using System.Reflection;
using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32;
using System.Windows.Forms;
using System.Drawing;
using HeonotDesk;

class Setup {
 [DllImport("shell32.dll")]static extern void SHChangeNotify(uint eventId,uint flags,IntPtr item1,IntPtr item2);
 delegate bool WindowVisitor(IntPtr window,IntPtr parameter);
 [DllImport("user32.dll")]static extern bool EnumWindows(WindowVisitor visit,IntPtr parameter);
 [DllImport("user32.dll")]static extern bool IsWindowVisible(IntPtr window);
 [DllImport("user32.dll")]static extern uint GetWindowThreadProcessId(IntPtr window,out uint process);
 [DllImport("user32.dll")]static extern bool PostThreadMessage(uint thread,uint message,IntPtr wparam,IntPtr lparam);
 const string Product="헌옷일번지 관리자";
 static string Folder{get{return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","HeonotDesk");}}
 static string IconName{get{return "HeonotDesk-"+AppVersion.Current+".ico";}}
 static void RequireClosed(){
  foreach(var process in Process.GetProcessesByName("HeonotDesk"))using(process){
   if(process.HasExited)continue;
   if(!String.Equals(process.MainModule.FileName,Path.Combine(Folder,"HeonotDesk.exe"),StringComparison.OrdinalIgnoreCase))throw new Exception("다른 폴더에서 실행 중인 관리자 프로그램도 닫아 주세요.");
   bool visible=false;EnumWindows((window,parameter)=>{uint id;GetWindowThreadProcessId(window,out id);if(id==process.Id&&IsWindowVisible(window))visible=true;return true;},IntPtr.Zero);
   if(visible)throw new Exception("실행 중인 관리자 프로그램에서 작업을 저장하고 창을 닫아 주세요.");
   // Version 1.0.0 could leave its event loop running after its last window closed.
   // Only ask that windowless, same-installation process to quit; never force-kill it.
   if(process.Threads.Count>0)PostThreadMessage((uint)process.Threads[0].Id,0x0012,IntPtr.Zero,IntPtr.Zero);
   if(!process.WaitForExit(5000))throw new Exception("관리자 프로그램 종료를 기다리고 있습니다. 완전히 종료한 뒤 다시 설치해 주세요.");
  }
 }
 static void Extract(string resource,string path){using(var input=Assembly.GetExecutingAssembly().GetManifestResourceStream(resource))using(var output=File.Create(path))input.CopyTo(output);}
 static void Shortcut(string path,string target){Type type=Type.GetTypeFromProgID("WScript.Shell");object shell=Activator.CreateInstance(type);try{dynamic shortcut=type.InvokeMember("CreateShortcut",BindingFlags.InvokeMethod,null,shell,new object[]{path});shortcut.TargetPath=target;shortcut.WorkingDirectory=Folder;shortcut.Description=Product;shortcut.IconLocation=Path.Combine(Folder,IconName)+",0";shortcut.Save();Marshal.FinalReleaseComObject(shortcut);}finally{Marshal.FinalReleaseComObject(shell);}}
 static void InstallFiles(string folder){
  Directory.CreateDirectory(folder);
  string[] files={"HeonotDesk.exe","HeonotDesk.exe.config","Uninstall.exe",IconName};
  string[] resources={"App.exe","App.config","Uninstall.exe","App.ico"};
  var applied=new System.Collections.Generic.List<string>();var previous=new System.Collections.Generic.HashSet<string>();bool safeCleanup=false;
  try{
   for(int i=0;i<files.Length;i++)Extract(resources[i],Path.Combine(folder,files[i])+".new");
   foreach(string name in files){string target=Path.Combine(folder,name);if(File.Exists(target)){File.Copy(target,target+".previous",true);previous.Add(name);}}
   foreach(string name in files){string target=Path.Combine(folder,name);if(File.Exists(target))File.Replace(target+".new",target,null,true);else File.Move(target+".new",target);applied.Add(name);}
   safeCleanup=true;
  }catch{
   foreach(string name in applied){string target=Path.Combine(folder,name);if(previous.Contains(name))File.Copy(target+".previous",target,true);else if(File.Exists(target))File.Delete(target);}
   safeCleanup=true;
   throw;
  }finally{
   foreach(string name in files)foreach(string suffix in new[]{".new",".previous"}){string temporary=Path.Combine(folder,name)+suffix;if((suffix!=".previous"||safeCleanup)&&File.Exists(temporary))File.Delete(temporary);}
  }
 }
 static void Register(bool desktop){
  string menu=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),Product);Directory.CreateDirectory(menu);
  Shortcut(Path.Combine(menu,Product+".lnk"),Path.Combine(Folder,"HeonotDesk.exe"));Shortcut(Path.Combine(menu,"프로그램 제거.lnk"),Path.Combine(Folder,"Uninstall.exe"));
  string desktopLink=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),Product+".lnk");if(desktop||File.Exists(desktopLink))Shortcut(desktopLink,Path.Combine(Folder,"HeonotDesk.exe"));
  using(var key=Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\HeonotDesk")){
   key.SetValue("DisplayName",Product);key.SetValue("DisplayVersion",AppVersion.Current);key.SetValue("Publisher","헌옷일번지");key.SetValue("InstallLocation",Folder);key.SetValue("DisplayIcon",Path.Combine(Folder,IconName));key.SetValue("UninstallString","\""+Path.Combine(Folder,"Uninstall.exe")+"\"");key.SetValue("NoModify",1);key.SetValue("NoRepair",1);
  }
  SHChangeNotify(0x08000000,0,IntPtr.Zero,IntPtr.Zero);
 }
 static void WaitForApplication(string[] args){
  if(args.Length!=3||args[1]!="--wait-pid")throw new Exception("업데이트 실행 정보를 확인해 주세요.");
  int id;if(!Int32.TryParse(args[2],out id)||id<=0)throw new Exception("업데이트 실행 정보가 올바르지 않습니다.");
  Process process;try{process=Process.GetProcessById(id);}catch(ArgumentException){return;}
  using(process){
   if(!process.HasExited){string expected=Path.Combine(Folder,"HeonotDesk.exe");string image;try{image=process.MainModule.FileName;}catch{if(process.HasExited)return;throw;}if(!String.Equals(image,expected,StringComparison.OrdinalIgnoreCase))throw new Exception("업데이트 대상 프로그램이 다릅니다.");if(!process.WaitForExit(30000))throw new Exception("관리자 프로그램을 닫은 뒤 다시 업데이트해 주세요.");}
  }
 }
 static int Update(string[] args){
  try{WaitForApplication(args);RequireClosed();InstallFiles(Folder);Register(false);Process.Start(Path.Combine(Folder,"HeonotDesk.exe"));return 0;}
  catch(Exception error){MessageBox.Show("업데이트를 완료하지 못했습니다. 기존 데이터는 유지됩니다.\n"+error.Message,Product,MessageBoxButtons.OK,MessageBoxIcon.Warning);if(File.Exists(Path.Combine(Folder,"HeonotDesk.exe")))Process.Start(Path.Combine(Folder,"HeonotDesk.exe"));return 1;}
 }
 static int TestInstaller(string output){
  string folder=Path.Combine(Path.GetTempPath(),"HeonotDesk-test-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(folder);
  try{
   string executable=Path.Combine(folder,"HeonotDesk.exe");File.WriteAllText(executable,"old executable");InstallFiles(folder);
   if(FileVersionInfo.GetVersionInfo(executable).FileVersion!=AppVersion.Current+".0")throw new Exception("Installed version mismatch");
   string config=Path.Combine(folder,"HeonotDesk.exe.config");File.WriteAllText(config,"previous config");bool rejected=false;
   using(var locked=new FileStream(executable,FileMode.Open,FileAccess.Read,FileShare.None)){try{InstallFiles(folder);}catch(IOException){rejected=true;}}
   if(!rejected||File.ReadAllText(config)!="previous config")throw new Exception("Failed install did not preserve previous files");
   File.WriteAllText(output,"Installer resource extraction, version check and locked-file failure preservation passed. No installation, shortcuts or registry changes.");return 0;
  }catch(Exception error){File.WriteAllText(output,error.ToString());return 1;}
 }
 [STAThread]static int Main(string[] args){
  if(args.Length==2&&args[0]=="--self-test")return TestInstaller(args[1]);
  Application.EnableVisualStyles();if(args.Length>0&&args[0]=="--update")return Update(args);
  var form=new Form{Text=Product+" 설치",ClientSize=new Size(560,360),StartPosition=FormStartPosition.CenterScreen,FormBorderStyle=FormBorderStyle.FixedDialog,MaximizeBox=false,Font=new Font("맑은 고딕",10),BackColor=Color.FromArgb(243,246,250),Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath)};
  form.Controls.Add(new Label{Text="헌옷일번지 관리자 데스크",Font=new Font("맑은 고딕",19,FontStyle.Bold),Location=new Point(28,30),Size=new Size(510,44),ForeColor=Color.FromArgb(24,51,78)});
  form.Controls.Add(new Label{Text="방문 통계 · 광고 유입 · 사이트 점검 · 신청서와 데이터 관리\n\n버전 "+AppVersion.Current+" · 새 버전 자동 확인 및 다운로드\n설치 후 기존 관리자 앱의 비밀번호로 로그인합니다.\n현재 Windows 사용자에게 설치하며 관리자 권한은 필요하지 않습니다.\n\n설치 위치: "+Folder,Location=new Point(30,85),Size=new Size(500,160)});
  var desktop=new CheckBox{Text="바탕화면에 바로가기 만들기",Checked=true,Location=new Point(30,250),Size=new Size(300,30)};form.Controls.Add(desktop);
  var install=new Button{Text="설치",Location=new Point(320,298),Size=new Size(100,38),BackColor=Color.FromArgb(24,51,78),ForeColor=Color.White,FlatStyle=FlatStyle.Flat};var cancel=new Button{Text="취소",Location=new Point(430,298),Size=new Size(100,38)};form.Controls.Add(install);form.Controls.Add(cancel);cancel.Click+=(s,e)=>form.Close();
  install.Click+=(s,e)=>{install.Enabled=false;try{
   RequireClosed();InstallFiles(Folder);Register(desktop.Checked);
   if(MessageBox.Show("설치했습니다. 지금 프로그램을 실행할까요?",Product,MessageBoxButtons.YesNo,MessageBoxIcon.Information)==DialogResult.Yes)Process.Start(Path.Combine(Folder,"HeonotDesk.exe"));form.Close();
  }catch(Exception error){MessageBox.Show(error.Message,"설치 확인",MessageBoxButtons.OK,MessageBoxIcon.Warning);install.Enabled=true;}};
  Application.Run(form);return 0;
 }
}
