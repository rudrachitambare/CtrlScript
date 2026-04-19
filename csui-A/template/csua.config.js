export default {
    name:        'My App',          // App display name
    package:     'com.yourname.myapp', // Unique ID — reverse domain style
    version:     '1.0.0',
    icon:        './icon.png',      // 512x512 PNG
    splash:      './splash.png',    // 1080x1920 PNG (optional)
    orientation: 'portrait',        // 'portrait' | 'landscape' | 'auto'
    minAndroid:  8,                 // minimum Android version (API level 26+)
    // Deep link scheme (optional)
    // scheme: 'myapp',             // enables myapp://path links
    // Update JS without new APK (optional)
    // updateUrl: 'https://yourserver.com/app.js',
}
