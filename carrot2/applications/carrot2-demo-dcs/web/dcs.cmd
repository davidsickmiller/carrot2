@ECHO OFF

SET DEPLIB=WEB-INF/lib
SET OPTS=-Xms128m -Xmx384m -XX:NewRatio=1

java %OPTS% -jar %DEPLIB%\carrot2-launcher.jar -cp WEB-INF/classes -cpdir %DEPLIB% org.carrot2.dcs.http.DCSApp %1 %2 %3 %4 %5 %6 %7 %8 %9