# Local backend launcher regression harness

Run the complete Windows PowerShell 5.1 suite from `kaipaile-server`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/run-start-local-backend-regression.ps1
```

The harness covers:

- valid, placeholder, malformed, mismatched, and missing-project configuration preflight;
- refusal to stop an unrelated listener owner during `-Restart`;
- isolated PID artifacts for overlapping launches on different ports;
- same-port launcher mutex refusal without stopping the first process or replacing its PID;
- process and PID cleanup after a startup timeout;
- propagation to the Java child while the launcher parent environment remains clean.

It has no Pester dependency. At runtime it compiles `fixtures/FakeJava.cs` into a
temporary `java.exe`, snapshots both launcher scripts into a temporary workspace,
and uses a generated AppId/AppSecret pair. Every harness-spawned launcher or
fixture parent has inherited `WECHAT_MINIAPP_APP_ID` and
`WECHAT_MINIAPP_APP_SECRET` entries removed without reading their values. The
default secret file and the real `.sce/runtime` tree are never used.

Run selected cases with `-Case`, for example:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/run-start-local-backend-regression.ps1 -Case OwnerRefusal,TimeoutCleanup
```

Use `-KeepArtifacts` only while diagnosing a failure. The printed temporary path
contains synthetic credentials and fixture logs, never the real local secret.

The PID isolation contract is behavior-based: after both different-port backends
are healthy, the runtime directory must contain distinct PID artifacts that still
identify both live processes. The harness does not require a particular filename.
