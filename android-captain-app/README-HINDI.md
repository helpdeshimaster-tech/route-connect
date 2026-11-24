# RouteConnect Captain – Android ऐप (स्क्रीन बंद होने पर भी लोकेशन)

## APK बनाने के 4 कदम
1. `gradle.properties` में `driverUrl=` वाली लाइन में अपनी वेबसाइट का driver.html लिंक लिखें।
2. पूरा `route-connect-main` फ़ोल्डर (वेबसाइट + `android-captain-app` + `.github`) अपनी GitHub रिपॉज़िटरी में डालें।
3. GitHub → Actions → "Build Captain APK" → Run workflow।
4. 5-7 मिनट बाद रन खोलें → नीचे Artifacts में `RouteConnect-Captain-apk` डाउनलोड करें। या `v1.0` जैसा टैग बनाएँ तो APK Releases पेज पर लगेगा, वहीं से ड्राइवर डाउनलोड करेंगे।

## ड्राइवर के फ़ोन पर
APK इंस्टॉल करें → लॉगिन → लोकेशन "हमेशा अनुमति दें" और बैटरी "Allow" → ट्रिप शुरू करें। ऊपर नोटिफ़िकेशन दिखेगा "लाइव ट्रैकिंग चालू"; स्क्रीन बंद करने पर भी चलता रहेगा।
मैन्युअल चेक-इन वही बटन हैं जो पहले थे।
