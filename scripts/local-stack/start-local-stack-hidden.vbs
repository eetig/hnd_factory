' ===========================================================================
'  HND local dev stack - silent launcher (2026-10-31)
'
'  Task Scheduler -> wscript.exe //B (no window) -> this VBS
'    -> powershell -WindowStyle Hidden -File ...\start-local-stack.ps1 -Quiet
'
'  Why this layer exists:
'    PowerShell 5.1 -WindowStyle Hidden only hides windows of processes that
'    PowerShell itself creates. It cannot hide the console host window that
'    Windows creates when PowerShell is launched. Symptom: one black console
'    stays on screen after logon (reported by the user on 2026-10-31).
'    WScript.Shell.Run with windowStyle 0 asks for a windowless process at
'    creation time, which is the only clean way without extra tools (NSSM).
'
'  Why this VBS also redirects output:
'    Without a console, Start-Transcript fails inside PowerShell (known 5.1
'    behaviour), so "silent" would also mean "no logs at all". Here cmd.exe
'    appends PowerShell's stdout+stderr to start-local-stack.out.log, so we
'    still see a trace even if the script dies before its first if-statement.
'
'  KEEP THIS FILE PURE ASCII. It is read by Windows Script Host under the
'  OEM codepage; non-ASCII comments decode into garbage and break parsing.
'
'  Status:        double-click check-stack.cmd
'  Debug window:  powershell -ExecutionPolicy Bypass -File ...start-local-stack.ps1 -ShowFrontend
' ===========================================================================

Option Explicit

Dim shell, fso, ts, scriptPath, logPath, cmd

Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

scriptPath = "D:\project\hnd_factory\scripts\local-stack\start-local-stack.ps1"
logPath = "D:\project\logs\start-local-stack.out.log"

' One marker line so we can tell "never triggered" from "triggered but failed".
On Error Resume Next
Set ts = fso.OpenTextFile(logPath, 8, True)
If Err.Number = 0 Then
    ts.WriteLine "===== " & Now & " launcher start (hidden) ====="
    ts.Close
End If
Err.Clear
On Error GoTo 0

cmd = "cmd.exe /c ""powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ _
      & scriptPath & """ -Quiet >> """ & logPath & """ 2>&1"""

'  0 = hidden window, False = do not wait (the script takes minutes)
shell.Run cmd, 0, False
