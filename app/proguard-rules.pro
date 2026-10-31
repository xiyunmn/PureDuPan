# 保留 META-INF/xposed/java_init.list 引用的 libxposed 入口。
-keep class com.xiyunmn.puredupan.hook.MainHook { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
