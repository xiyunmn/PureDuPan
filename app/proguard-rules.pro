# Keep the libxposed entry point referenced from META-INF/xposed/java_init.list.
-keep class com.xiyunmn.puredupan.hook.MainHook { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
