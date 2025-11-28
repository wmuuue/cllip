@rem Gradle startup script for Windows
@if "%%DEBUG%%" == "" @echo off
set DIRNAME=%%~dp0
set JAVA_EXE=java.exe
set CLASSPATH=%%APP_HOME%%\gradle\wrapper\gradle-wrapper.jar
"%%JAVA_EXE%%" -classpath "%%CLASSPATH%%" org.gradle.wrapper.GradleWrapperMain %%*
