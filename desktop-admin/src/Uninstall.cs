using System;
using System.IO;
using System.Diagnostics;
using Microsoft.Win32;
using System.Windows.Forms;
class Uninstall {
 [STAThread]static void Main(){
  if(MessageBox.Show("헌옷일번지 관리자 프로그램을 제거할까요? 서버의 신청서와 데이터는 삭제되지 않습니다.","프로그램 제거",MessageBoxButtons.YesNo,MessageBoxIcon.Question,MessageBoxDefaultButton.Button2)!=DialogResult.Yes)return;
  var root=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","HeonotDesk");
  if(!String.Equals(Path.GetFullPath(AppDomain.CurrentDomain.BaseDirectory).TrimEnd(Path.DirectorySeparatorChar),Path.GetFullPath(root),StringComparison.OrdinalIgnoreCase)){MessageBox.Show("설치된 위치에서 제거 프로그램을 실행해 주세요.");return;}
  if(Process.GetProcessesByName("HeonotDesk").Length>0){MessageBox.Show("관리자 프로그램을 먼저 닫아 주세요.");return;}
  try{
   foreach(var file in new[]{"HeonotDesk.exe","HeonotDesk.exe.config"}){var path=Path.Combine(root,file);if(File.Exists(path))File.Delete(path);}
   var menu=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),"헌옷일번지 관리자");foreach(var file in new[]{"헌옷일번지 관리자.lnk","프로그램 제거.lnk"}){var path=Path.Combine(menu,file);if(File.Exists(path))File.Delete(path);}if(Directory.Exists(menu)&&Directory.GetFileSystemEntries(menu).Length==0)Directory.Delete(menu);
   var shortcut=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),"헌옷일번지 관리자.lnk");if(File.Exists(shortcut))File.Delete(shortcut);
   var session=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"HeonotDesk","session.bin");if(File.Exists(session))File.Delete(session);
   Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\HeonotDesk",false);
   MessageBox.Show("프로그램과 로그인 정보를 제거했습니다. 설치 폴더에는 이 제거 파일만 남을 수 있습니다.");
  }catch(Exception e){MessageBox.Show("제거 중 확인이 필요합니다. "+e.Message);}
 }
}
