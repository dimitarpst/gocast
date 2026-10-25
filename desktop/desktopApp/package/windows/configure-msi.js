/**
 * Author GoCast maintenance rules into a jpackage MSI; build-time only.
 * Usage: cscript //nologo configure-msi.js <path-to-msi> <version>
 */
var msiPath = WScript.Arguments.length > 0 ? WScript.Arguments(0) : null;
if (!msiPath) {
  WScript.Echo("Usage: cscript //nologo configure-msi.js <gocast.msi> <version>");
  WScript.Quit(1);
}

var fso = new ActiveXObject("Scripting.FileSystemObject");
if (!fso.FileExists(msiPath)) {
  WScript.Echo("MSI not found: " + msiPath);
  WScript.Quit(1);
}

var expectedVersion = WScript.Arguments.length > 1 ? WScript.Arguments(1) : null;
if (!expectedVersion) {
  WScript.Echo("Expected release version is required");
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

function scalar(sql) {
  var view = db.OpenView(sql);
  view.Execute();
  var rec = view.Fetch();
  var value = rec ? rec.StringData(1) : null;
  view.Close();
  return value;
}

try {
  var version = scalar("SELECT `Value` FROM `Property` WHERE `Property`='ProductVersion'");
  var upgradeCode = scalar("SELECT `Value` FROM `Property` WHERE `Property`='UpgradeCode'");
  if (version !== expectedVersion || String(upgradeCode).toUpperCase() !== "{7E3A9C2B-5F14-4D8A-9B6E-1C0D4A8F2E75}") {
    throw new Error("Unexpected GoCast package identity/version");
  }
  if (!tableHasRow("SELECT `Directory` FROM `Directory` WHERE `Directory`='INSTALLDIR'")) {
    throw new Error("Missing expected GoCast installation directory");
  }
  var removeFiles = Number(scalar("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action`='RemoveFiles'"));
  var initialise = Number(scalar("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action`='InstallInitialize'"));
  if (!removeFiles || !initialise || removeFiles <= initialise + 1) {
    throw new Error("Cannot locate safe deferred cleanup ordering");
  }

  // jpackage's application template permits downgrades by default. Add an
  // independent detect-only range and a native MSI error action before removal.
  var newerProperty = "GOCAST_NEWER_VERSION_FOUND";
  execSql("DELETE FROM `Upgrade` WHERE `ActionProperty`='" + newerProperty + "'");
  execSql("INSERT INTO `Upgrade` (`UpgradeCode`,`VersionMin`,`Attributes`,`ActionProperty`) " +
    "VALUES ('" + upgradeCode + "','" + version + "',2,'" + newerProperty + "')");
  var secure = scalar("SELECT `Value` FROM `Property` WHERE `Property`='SecureCustomProperties'");
  if (secure === null) {
    execSql("INSERT INTO `Property` (`Property`,`Value`) VALUES ('SecureCustomProperties','" + newerProperty + "')");
  } else if ((";" + secure + ";").indexOf(";" + newerProperty + ";") < 0) {
    execSql("UPDATE `Property` SET `Value`='" + secure + ";" + newerProperty + "' WHERE `Property`='SecureCustomProperties'");
  }
  var lastDetection = Math.max(
    Number(scalar("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action`='FindRelatedProducts'")),
    Number(scalar("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action`='JpFindRelatedProducts'"))
  );
  var cost = Number(scalar("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action`='CostInitialize'"));
  execSql("DELETE FROM `InstallExecuteSequence` WHERE `Action`='GoCastPreventDowngrade'");
  execSql("DELETE FROM `CustomAction` WHERE `Action`='GoCastPreventDowngrade'");
  var guardSequence = lastDetection + 1;
  while (tableHasRow("SELECT `Action` FROM `InstallExecuteSequence` WHERE `Sequence`=" + guardSequence)) guardSequence++;
  if (!lastDetection || !cost || guardSequence >= cost) throw new Error("Cannot schedule downgrade protection");
  execSql("INSERT INTO `CustomAction` (`Action`,`Type`,`Target`) VALUES ('GoCastPreventDowngrade',19," +
    "'A newer version of GoCast is already installed. Open that version to check for updates.')");
  execSql("INSERT INTO `InstallExecuteSequence` (`Action`,`Condition`,`Sequence`) " +
    "VALUES ('GoCastPreventDowngrade','" + newerProperty + " AND NOT Installed'," + guardSequence + ")");

  // Keep old-product removal in the MSI transaction so a failed replacement can
  // roll it back. The stock JDK 21 template removes it before CostInitialize.
  var processComponents = Number(scalar("SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action`='ProcessComponents'"));
  var removeOldSequence = initialise + 1;
  while (tableHasRow("SELECT `Action` FROM `InstallExecuteSequence` WHERE `Sequence`=" + removeOldSequence + " AND `Action`<>'RemoveExistingProducts'")) removeOldSequence++;
  if (!processComponents || removeOldSequence >= processComponents ||
      !tableHasRow("SELECT `Action` FROM `InstallExecuteSequence` WHERE `Action`='RemoveExistingProducts'")) {
    throw new Error("Cannot locate transactional upgrade removal slot");
  }
  execSql("UPDATE `InstallExecuteSequence` SET `Sequence`=" + removeOldSequence + " WHERE `Action`='RemoveExistingProducts'");
  execSql("DELETE FROM `InstallExecuteSequence` WHERE `Action`='GocastCleanup'");
  execSql("DELETE FROM `CustomAction` WHERE `Action`='GocastCleanup'");
  var cleanupSequence = removeFiles - 1;
  while (tableHasRow("SELECT `Action` FROM `InstallExecuteSequence` WHERE `Sequence`=" + cleanupSequence)) cleanupSequence--;
  if (cleanupSequence <= initialise) throw new Error("No cleanup slot before RemoveFiles");
  // Ensure CustomAction table exists (jpackage MSIs include it).
    if (!tableHasRow("SELECT `Action` FROM `CustomAction` WHERE `Action`='GocastCleanup'")) {
      // Type 34 (directory EXE) + Continue + deferred + no impersonate = 3170.
      // Use the installed directory as the working directory and the protected system cmd.exe.
      execSql(
        "INSERT INTO `CustomAction` (`Action`,`Type`,`Source`,`Target`) " +
          "VALUES ('GocastCleanup', 3170, 'INSTALLDIR', " +
          "'\"[SystemFolder]cmd.exe\" /d /s /c \"\"[INSTALLDIR]resources\\gocast-cleanup.cmd\"\"')"
      );
    }

  if (!tableHasRow(
    "SELECT `Action` FROM `InstallExecuteSequence` WHERE `Action`='GocastCleanup'"
  )) {
    // Before payload removal; explicit uninstall only, never during upgrades.
    execSql(
      "INSERT INTO `InstallExecuteSequence` (`Action`,`Condition`,`Sequence`) " +
        "VALUES ('GocastCleanup', 'Installed AND REMOVE=\"ALL\" AND NOT UPGRADINGPRODUCTCODE', " + cleanupSequence + ")"
    );
  }

  db.Commit();
  WScript.Echo("Configured GoCast cleanup, downgrade protection and transactional upgrade removal in " + msiPath);
  WScript.Quit(0);
} catch (e) {
  WScript.Echo("GoCast MSI maintenance authoring failed: " + e.message);
  WScript.Quit(2);
}
