# Path: mobile/app/proguard-rules.pro
# Purpose: Preserves app entry points that external runtimes instantiate reflectively.
# Last changed: 2026-11-23 — retained the Shizuku UserService constructor for release builds.
-keep class com.kp.filemanager.export.ShizukuShellUserService { public <init>(); }
