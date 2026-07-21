/**
 * Post-process a jpackage MSI so uninstall runs gocast-cleanup.cmd.
 * Build-time only (cscript). Usage: cscript //nologo inject-uninstall-cleanup.js <path-to-msi>
 */
var msiPath = WScript.Arguments.length > 0 ? WScript.Arguments(0) : null;
if (!msiPath) {
  WScript.Echo("Usage: cscript //nologo inject-uninstall-cleanup.js <gocast.msi>");
  WScript.Quit(1);
}

var fso = new ActiveXObject("Scripting.FileSystemObject");
if (!fso.FileExists(msiPath)) {
  WScript.Echo("MSI not found: " + msiPath);
  WScript.Quit(1);
}

var installer = new ActiveXObject("WindowsInstaller.Installer");
// 1 = msiOpenDatabaseModeTransact
var db = installer.OpenDatabase(msiPath, 1);

function execSql(sql) {
  var view = db.OpenView(sql);
  view.Execute();
  view.Close();
}

function tableHasRow(sql) {
  var view = db.OpenView(sql);
  view.Execute();
  var rec = view.Fetch();
  view.Close();
  return rec !== null;
}

try {
  // Ensure CustomAction table exists (jpackage MSIs include it).
    if (!tableHasRow("SELECT `Action` FROM `CustomAction` WHERE `Action`='GocastCleanup'")) {
      // Type 34 (directory EXE) + Continue + deferred + no impersonate = 3170.
      // Run via SystemFolder\cmd.exe so the .cmd under INSTALLDIR\resources works.
      execSql(
        "INSERT INTO `CustomAction` (`Action`,`Type`,`Source`,`Target`) " +
          "VALUES ('GocastCleanup', 3170, 'SystemFolder', " +
          "'cmd.exe /c \"\"[INSTALLDIR]resources\\gocast-cleanup.cmd\"\"')"
      );
    }

  if (!tableHasRow(
    "SELECT `Action` FROM `InstallExecuteSequence` WHERE `Action`='GocastCleanup'"
  )) {
    // After RemoveFiles, only when uninstalling the product.
    execSql(
      "INSERT INTO `InstallExecuteSequence` (`Action`,`Condition`,`Sequence`) " +
        "VALUES ('GocastCleanup', 'REMOVE=\"ALL\"', 3700)"
    );
  }

  db.Commit();
  WScript.Echo("Injected GocastCleanup uninstall action into " + msiPath);
  WScript.Quit(0);
} catch (e) {
  WScript.Echo("inject-uninstall-cleanup failed: " + e.message);
  WScript.Quit(2);
}
