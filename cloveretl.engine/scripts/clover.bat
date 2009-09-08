@echo off

REM this script was inspired by practices gained from ant run scripts (http://ant.apache.org/)

REM usage 
REM clover.bat <engine_arguments> <graph_name.grf> [ - <java_arguments> ]
REM example:
REM clover.bat -noJMX myGraph.grf - -server -classpath c:\myTransformation

REM split command-line arguments to two sets - clover and jvm arguments
REM and define CLOVER_HOME variable
call "%~dp0"\commonlib.bat %*

set _JAVACMD=%JAVACMD%
set TOOLS_JAR=%JAVA_HOME%\lib\tools.jar

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto createClasspath

:noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=java.exe
set TOOLS_JAR=

:createClasspath
set TRANSFORM_PATH=%~dp0

FOR /f "tokens=*" %%G IN ('dir /b "%CLOVER_HOME%/lib"') DO (call :collectClasspath %%G)
GOTO runClover

:collectClasspath
 set ENGINE_CLASSPATH=%ENGINE_CLASSPATH%;%CLOVER_HOME%/lib/%1
GOTO :eof


:runClover
echo "%_JAVACMD%" %CLOVER_OPTS% %JAVA_CMD_LINE_ARGS% -classpath "%CLASSPATH%;%USER_CLASSPATH%;%TRANSFORM_PATH%;%TOOLS_JAR%;%ENGINE_CLASSPATH%" "-Dclover.home=%CLOVER_HOME%" org.jetel.main.runGraph -plugins "%CLOVER_HOME%\plugins" %CLOVER_CMD_LINE_ARGS%
"%_JAVACMD%" %CLOVER_OPTS% %JAVA_CMD_LINE_ARGS% -classpath "%CLASSPATH%;%USER_CLASSPATH%;%TRANSFORM_PATH%;%TOOLS_JAR%;%ENGINE_CLASSPATH%" "-Dclover.home=%CLOVER_HOME%" org.jetel.main.runGraph -plugins "%CLOVER_HOME%\plugins" %CLOVER_CMD_LINE_ARGS%
set RETURN_CODE=%ERRORLEVEL%

set CLOVER_OPTS=
set CLOVER_CMD_LINE_ARGS=
set JAVA_CMD_LINE_ARGS=
set USER_CLASSPATH=
set WAS_CLASSPATH=
set _JAVACMD=
set TOOLS_JAR=
set TRANSFORM_PATH=
set ENGINE_CLASSPATH=

exit /B %RETURN_CODE%
