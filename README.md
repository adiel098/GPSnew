# GPS Particle Filter Project

## מה הפרויקט מכיל

### מבנה הפרויקט
הפרויקט מכיל את הרכיבים הבאים:
- **Model**: מחלקות מודל נתונים (Point3D, Building, Satellite, וכו')
- **KML Readers**: קוראי קבצי KML לטיפים שונים (בניינים, לוויינים, מסלולים)
- **KML Validator**: כלי לבדיקת תקינות קבצי KML ויצירת דוחות מפורטים
- **LOS Calculator**: מחשב ראות קו (Line of Sight) בין נקודות מסלול ללוויינים

### קבצי KML
הפרויקט עובד עם הקבצים הבאים:
- `building3d.kml`: מכיל מידע על מבנים תלת-ממדיים
- `satellites.kml`: מכיל מידע על מיקומי לוויינים
- `original_route.kml`: מכיל נקודות מסלול

## איך להריץ את הפרויקט

### הרצת כל הפרויקט
```
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.xml/jdk.xml.internal=ALL-UNNAMED -cp "classes;lib\*" com.gps.particlefilter.Main
```

### הרצת KML Validator בלבד
```
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.xml/jdk.xml.internal=ALL-UNNAMED -cp "classes;lib\*" com.gps.particlefilter.kml.KMLValidator
```

ניתן להעביר שמות קבצים כפרמטרים:
```
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.xml/jdk.xml.internal=ALL-UNNAMED -cp "classes;lib\*" com.gps.particlefilter.kml.KMLValidator [buildingsFile] [satellitesFile] [routeFile]
```

### קמפול הפרויקט
```
javac -cp "lib\*" -d classes src\main\java\com\gps\particlefilter\model\*.java src\main\java\com\gps\particlefilter\kml\*.java src\main\java\com\gps\particlefilter\*.java
```

## שינויים אחרונים
**2025-04-01**:
- הוספת יכולת להפעיל את `KMLValidator` ישירות דרך מתודת `main` ייעודית
- תיקון קריאת קבצי KML לתמיכה בפורמטים שונים של נתיבים (LineString ו-Point)
- הוספת תמיכה בקובץ `original_route.kml` עם 184 נקודות מסלול
- תיקון פורמט XML בקובץ `original_route.kml`
- הוספת דוח וולידציה מפורט שנשמר בקובץ `kml_validation_report.txt`

## דרישות מערכת
- Java 19 או גרסה חדשה יותר
- הספריות הבאות (נמצאות בתיקיית lib):
  - JavaAPIforKml (גרסה 2.2.1)
  - JAXB API (גרסה 2.3.1)
  - Commons Math (גרסה 3.6.1)

## פלט ודוחות
הפרויקט מייצר קובץ דוח וולידציה מפורט בשם `kml_validation_report.txt` שמכיל מידע על:
- מבנים (מספר קודקודים, גובה, גבולות)
- לוויינים (מיקום, זווית עלייה, אזימוט)
- נקודות מסלול (גבולות, מרחק, גובה ממוצע)

---

**הערה**: הפרויקט דורש פרמטרים מיוחדים לעבודה עם Java 19 בגלל מגבלות מודול. אם אתה משתמש בגרסה ישנה יותר, ייתכן שתוכל להסיר את הפרמטרים `--add-opens`.
