// Path: mobile/app/src/main/aidl/com/kp/filemanager/export/IShizukuShellService.aidl
// Purpose: Defines the privileged command boundary hosted by Shizuku UserService.
// Last changed: 2026-11-23 — replaced the removed Shizuku newProcess API.
package com.kp.filemanager.export;

import android.os.Bundle;

interface IShizukuShellService {
    void destroy() = 16777114;
    Bundle execute(String commandId, String command) = 1;
    void cancel(String commandId) = 2;
}
