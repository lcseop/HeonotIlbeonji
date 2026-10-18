using System;
using System.IO;
using System.Reflection;
using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32;
using System.Windows.Forms;
using System.Drawing;

class Setup {
 const string Product="헌옷일번지 관리자";
 static string Folder{get{return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","HeonotDesk");}}
 static void Extract(string resource,string path){using(var input=Assembly.GetExecutingAssembly().GetManifestResourceStream(resource))using(var output=File.Create(path))input.CopyTo(output);}
 static void Shortcut(string path,string target){Type type=Type.GetTypeFromProgID("WScript.Shell");object shell=Activator.CreateInstance(type);try{dynamic shortcut=type.InvokeMember("CreateShortcut",BindingFlags.InvokeMethod,null,shell,new object[]{path});shortcut.TargetPath=target;shortcut.WorkingDirectory=Folder;shortcut.Description=Product;shortcut.Save();Marshal.FinalReleaseComObject(shortcut);}finally{Marshal.FinalReleaseComObject(shell);}}
 [STAThread]static int Main(){Application.EnableVisualStyles();var form=new Form{Text=Product+" 설치",ClientSize=new Size(560,360),StartPosition=FormStartPosition.CenterScreen,FormBorderStyle=FormBorderStyle.FixedDialog,MaximizeBox=false,Font=new Font("맑은 고딕",10),BackColor=Color.FromArgb(243,246,250)};
  var title=new Label{Text="헌옷일번지 관리자 데스크",Font=new Font("맑은 고딕",19,FontStyle.Bold),Location=new Point(28,30),Size=new Size(510,44),ForeColor=Color.FromArgb(24,51,78)};form.Controls.Add(title);
  form.Controls.Add(new Label{Text="방문 통계 · 광고 유입 · 사이트 점검 · 신청서와 데이터 관리\n\n기존 관리자 앱의 비밀번호로 로그인합니다.\n현재 Windows 사용자에게 설치하며 관리자 권한은 필요하지 않습니다.\n\n설치 위치: "+Folder,Location=new Point(30,90),Size=new Size(500,150)});
  var desktop=new CheckBox{Text="바탕화면에 바로가기 만들기",Checked=true,Location=new Point(30,250),Size=new Size(300,30)};form.Controls.Add(desktop);
  var install=new Button{Text="설치",Location=new Point(320,298),Size=new Size(100,38),BackColor=Color.FromArgb(24,51,78),ForeColor=Color.White,FlatStyle=FlatStyle.Flat};var cancel=new Button{Text="취소",Location=new Point(430,298),Size=new Size(100,38)};form.Controls.Add(install);form.Controls.Add(cancel);cancel.Click+=(s,e)=>form.Close();
  install.Click+=(s,e)=>{install.Enabled=false;try{
    if(Process.GetProcessesByName("HeonotDesk").Length>0)throw new Exception("실행 중인 헌옷일번지 관리자 프로그램을 닫고 다시 설치해 주세요.");
    Directory.CreateDirectory(Folder);Extract("App.exe",Path.Combine(Folder,"HeonotDesk.exe"));Extract("App.config",Path.Combine(Folder,"HeonotDesk.exe.config"));Extract("Uninstall.exe",Path.Combine(Folder,"Uninstall.exe"));
    var menu=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),Product);Directory.CreateDirectory(menu);Shortcut(Path.Combine(menu,Product+".lnk"),Path.Combine(Folder,"HeonotDesk.exe"));Shortcut(Path.Combine(menu,"프로그램 제거.lnk"),Path.Combine(Folder,"Uninstall.exe"));if(desktop.Checked)Shortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),Product+".lnk"),Path.Combine(Folder,"HeonotDesk.exe"));
    using(var key=Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\HeonotDesk")){key.SetValue("DisplayName",Product);key.SetValue("DisplayVersion","1.0.0");key.SetValue("Publisher","헌옷일번지");key.SetValue("InstallLocation",Folder);key.SetValue("DisplayIcon",Path.Combine(Folder,"HeonotDesk.exe"));key.SetValue("UninstallString","\""+Path.Combine(Folder,"Uninstall.exe")+"\"");key.SetValue("NoModify",1);key.SetValue("NoRepair",1);}
    if(MessageBox.Show("설치했습니다. 지금 프로그램을 실행할까요?",Product,MessageBoxButtons.YesNo,MessageBoxIcon.Information)==DialogResult.Yes)Process.Start(Path.Combine(Folder,"HeonotDesk.exe"));form.Close();
   }catch(Exception error){MessageBox.Show(error.Message,"설치 확인",MessageBoxButtons.OK,MessageBoxIcon.Warning);install.Enabled=true;}};
  Application.Run(form);return 0;
 }
}
