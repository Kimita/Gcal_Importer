Gcal_Importer
=============

Android Application.

Google play - https://play.google.com/store/apps/details?id=net.crappo.android.androics


[Considerations when building]
There is a following in a library that is included with the distribution of ical4j-1.0.5 . 
 * backport-util-concurrent-3.1.jar 
 * commons-codec-1.8.jar 
 * commons-lang-2.6.jar 
 * commons-logging-1.1.3.jar 
 * groovy-all-2.1.1.jar   <<--- !! Note here !! 
 * ical4j-1.0.5.jar 

groovy-all-2.1.1.jar generates a warning in large quantities at build time in auther's development environment,  Eclipse is terminated with an out of memory by it. 
To avoid this problem, it is preferable to use groovy-2.2.2-indy.jar than groovy-all-2.1.1.jar. 
However, instead of placing the library folder(PROJECT_DIR/libs/), by the use by setting the classpath as external library, would avoid the problems of another new. 

From the above, I did not include in the git repository of this application groovy-all-2.1.1.jar of ical4j included. 
If you want to build this application, it is necessary to get the groovy-2.2.2-indy.jar on your own, use it be incorporated into the project as an external JAR. (set .classpath) 

 -- get groovy -- 
  http://groovy.codehaus.org/Download 


----------------------------------------------------------------------

【ビルド時の注意点】
ical4j-1.0.5の配布物に同梱されているライブラリ(jarファイル)には下記があります。 
 * backport-util-concurrent-3.1.jar 
 * commons-codec-1.8.jar 
 * commons-lang-2.6.jar 
 * commons-logging-1.1.3.jar 
 * groovy-all-2.1.1.jar   <-- ここに注意 
 * ical4j-1.0.5.jar 

これらのうち、作者の開発環境(Eclipse with ADT)ではical4j同梱のgroovy-allを使うとAPKビルド時にけたたましくwarningが出てビルド完了前にEclipseがメモリ不足で落ちたりと酷いことになった。
調べたところ、下記の情報源などから「indyを有効にしてるgroovyだったらどうだろう？」と考えた。 
http://uehaj.hatenablog.com/entry/20120813/1344846148

そこでgroovy-2.2.2-indy.jarを使った。 
するとまず、project直下のlibsに放り込んだ場合はgroovy-allの時と同様のwarningがけたたましく出て最終的にエラーでビルドできなかった。 
しかしこれを外部jarとしてprojectにaddした場合、問題が解消してapkのビルドが完了した。 

以上のことから、groovyに関しては ical4j同梱のjarファイルを本アプリのgitリポジトリデータには含めないようにした。 
本リポジトリからsourceを取得してビルドする場合は、別途groovy-2.2.2-indy.jarを入手して外部jarとしてprojectに取り込んで(.classpathにkind="lib"で指定して)使ってください。 

 -- groovyの配布元 --
  http://groovy.codehaus.org/Download

----------------------------------------------------------------------

作者の開発/テスト環境  (auther's development and test environment)

 * OS : Windows7 / Mac OS X Mavericks 
 * IDE: Eclipse with ADT (Eclipse 4.2 / ADT v23.0.0) 
 * Android deviceis: 
    * HTCJ ISW13HT    [Android 4.0.4] 
    * Nexus7 (2012)   [Android 4.4.4] 
    * IS03            [Android 2.2.1] 

