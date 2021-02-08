# xlogcat
logcat增强版

Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:
```gradle
allprojects {
   repositories {
       maven { url 'https://jitpack.io' }
   }
}
```
Step 2. Add the dependency
```gradle
dependencies {
    implementation 'com.github.bingoloves:xlogcat:1.0.0'
}
```
**使用示例**
```java
 startActivity(new Intent(MainActivity.this, LogcatActivity.class));
```