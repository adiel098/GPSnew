package com.gps.particlefilter;

import com.gps.particlefilter.model.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * בודק את פונקציונליות LOS/NLOS על ידי יצירת סימולציה פשוטה של נקודה, קיר ולוויין
 * ובדיקה אם הלוויין נראה או חסום על ידי הקיר
 */
public class LosNlosTest {
    private static final double WALL_HEIGHT = 100.0; // גובה הקיר במטרים
    private static final double OBSERVER_HEIGHT = 71; // גובה המשתמש במטרים
    private static final double WALL_DISTANCE = 30.0; // מרחק מהמשתמש לקיר במטרים
    private static final double WALL_LENGTH = 20.0; // אורך הקיר במטרים
    
    // פרמטרים לסימולציה העיקרית שתייצר קובץ KML
    private static final double MAIN_SIM_AZIMUTH = 45.0; // אזימוט לסימולציה העיקרית
    private static final double MAIN_SIM_ELEVATION = 45.0; // זווית רומם לסימולציה העיקרית

    public static void main(String[] args) {
        try {
            System.out.println("=== LOS/NLOS TEST ===");
            System.out.println("Wall Height: " + WALL_HEIGHT + "m, Wall Distance: " + WALL_DISTANCE + "m");
            
            // בדיקת LOS/NLOS עם ערכים שונים של זווית רומם (elevation)
            System.out.println("\nTesting different elevation angles with fixed azimuth (45°):");
            System.out.println("-------------------------------------------------------");
            for (double elevation : new double[] {5, 15, 30, 45, 60, 75, 90}) {
                testLosNlos(45.0, elevation, false); // לא יוצר קובץ KML
            }

            // בדיקת LOS/NLOS עם ערכים שונים של אזימוט (azimuth)
            System.out.println("\nTesting different azimuth angles with fixed elevation (45°):");
            System.out.println("-------------------------------------------------------");
            for (double azimuth : new double[] {0, 45, 90, 135, 180, 225, 270, 315}) {
                testLosNlos(azimuth, 45.0, false); // לא יוצר קובץ KML
            }
            
            // הרצת סימולציה אחת עיקרית ליצירת קובץ KML
            System.out.println("\nRunning main simulation for KML generation:");
            System.out.println("-------------------------------------------------------");
            testLosNlos(MAIN_SIM_AZIMUTH, MAIN_SIM_ELEVATION, true); // יוצר קובץ KML


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * מבצע בדיקת LOS/NLOS עם אזימוט וזווית רומם נתונים
     * @param azimuth - זווית אזימוט בדרגות
     * @param elevation - זווית רומם בדרגות
     * @param generateKmlFile - האם לייצר קובץ KML
     */
    private static void testLosNlos(double azimuth, double elevation, boolean generateKmlFile) {
        try {
            // יצירת נקודת משתמש (31.771959, 35.217018 - אזור ירושלים)
            double baseLat = 31.771959;
            double baseLon = 35.217018;
            Point3D userPoint = new Point3D(baseLat, baseLon, OBSERVER_HEIGHT);
            
            // חישוב מיקום הקיר על בסיס האזימוט
            double wallAngle = azimuth; // הקיר צריך להיות ניצב לקו הראייה
            double dx = WALL_DISTANCE * Math.sin(Math.toRadians(wallAngle));
            double dy = WALL_DISTANCE * Math.cos(Math.toRadians(wallAngle));
            
            // חישוב נקודות הקצה של הקיר ניצב לקו הראייה
            double wallHalfLength = WALL_LENGTH / 2.0;
            double wallDx = wallHalfLength * Math.cos(Math.toRadians(wallAngle)); // ניצב לקו הראייה
            double wallDy = -wallHalfLength * Math.sin(Math.toRadians(wallAngle)); // ניצב לקו הראייה
            
            Point3D wallCenter = new Point3D(
                userPoint.getX() + dx/100000, // קונברסיה גסה למעלות (שינוי קטן במעלות)
                userPoint.getY() + dy/100000,
                0 // בסיס הקיר
            );
            
            Point3D wallStart = new Point3D(
                wallCenter.getX() + wallDx/100000,
                wallCenter.getY() + wallDy/100000,
                0 // בסיס הקיר
            );
            
            Point3D wallEnd = new Point3D(
                wallCenter.getX() - wallDx/100000,
                wallCenter.getY() - wallDy/100000,
                0 // בסיס הקיר
            );
            
            // יצירת בניין (קיר) לבדיקה
            List<Point3D> vertices = new ArrayList<>();
            vertices.add(wallStart);
            vertices.add(wallEnd);
            vertices.add(new Point3D(wallEnd.getX(), wallEnd.getY(), WALL_HEIGHT));
            vertices.add(new Point3D(wallStart.getX(), wallStart.getY(), WALL_HEIGHT));
            vertices.add(wallStart); // סגירת המצולע
            
            Building wall = new Building(vertices, WALL_HEIGHT);
            
            // יצירת לוויין לפי זווית רומם ואזימוט
            Satellite satellite = new Satellite("TestSat", null, azimuth, elevation);
            
            // יצירת רשימות לחישוב LOS
            List<Building> buildings = new ArrayList<>();
            buildings.add(wall);
            
            List<Satellite> satellites = new ArrayList<>();
            satellites.add(satellite);
            
            // חישוב ה-LOS/NLOS באמצעות ה-LosCalculator שלנו
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            Map<String, Boolean> result = losCalculator.calculateLOS(userPoint);
            boolean isLos = result.get("TestSat"); // האם יש קו ראייה ללוויין
            
            // חישוב ידני להשוואה
            double manualResult = losCalculator.computeLosDetailed(userPoint, wall, satellite);
            boolean manualIsLos = (manualResult == -1);
            
            // הדפסת תוצאות הבדיקה
            System.out.println(String.format(
                "Azimuth: %5.1f°, Elevation: %5.1f° | LOS: %5s | Manual check: %5s",
                azimuth, elevation, isLos, manualIsLos));
            
            // בדיקת עקביות ופרטים נוספים
            if (isLos != manualIsLos) {
                System.out.println("  WARNING: Inconsistent results between methods!");
                System.out.println("  calculateLOS: " + isLos + ", computeLosDetailed: " + manualIsLos);
            }
            
            if (!isLos) {
                double heightDiff = manualResult;
                System.out.println("  NLOS Details: Height difference = " + String.format("%.2f m", heightDiff));
                
                // חישוב הזווית המינימלית הדרושה להשגת LOS
                double requiredElevation = Math.toDegrees(Math.atan(WALL_HEIGHT / WALL_DISTANCE));
                System.out.println("  Min required elevation for LOS: ~" + String.format("%.1f°", requiredElevation));
                
                // חישוב גובה נוסף הדרוש למשתמש כדי להגיע ל-LOS
                // התפשטות קרן אופקית = מרחק אופקי / טנגנס של זווית הרומם
                double tanElevation = Math.tan(Math.toRadians(elevation));
                
                // חישוב כמה גובה צריך להוסיף למשתמש כדי להגיע ל-LOS
                // נשתמש במשוואה: גובה הבניין - גובה הצפייה = tanElevation * מרחק אופקי
                // כלומר: גובה הצפייה = גובה הבניין - tanElevation * מרחק אופקי
                double requiredAddHeight = 0;
                
                if (elevation > 0) {
                    // גובה נדרש = גובה הבניין - (גובה המשתמש + WALL_DISTANCE * טנגנס זווית הרומם)
                    double rayHeightAtWall = OBSERVER_HEIGHT + WALL_DISTANCE * tanElevation;
                    
                    if (rayHeightAtWall < WALL_HEIGHT) {
                        requiredAddHeight = WALL_HEIGHT - rayHeightAtWall;
                        System.out.println("  Additional height required for LOS: " + String.format("%.2f m", requiredAddHeight));
                        System.out.println("  Total required observer height: " + String.format("%.2f m", OBSERVER_HEIGHT + requiredAddHeight));
                    } else {
                        System.out.println("  ERROR in LOS calculation: Ray should clear the wall at this elevation.");
                    }
                } else {
                    System.out.println("  Cannot calculate required height for negative or zero elevation angle.");
                }
            }
            
            // יצירת קובץ KML להצגה ויזואלית (רק אם התבקש)
            if (generateKmlFile) {
                String kmlFilename = "los_simulation.kml";
                generateKml(userPoint, wall, satellite, isLos, kmlFilename);
            }
            
        } catch (Exception e) {
            System.out.println("Error testing azimuth " + azimuth + ", elevation " + elevation);
            e.printStackTrace();
        }
    }
    
    /**
     * מייצר קובץ KML להדמיה ויזואלית של מצב ה-LOS/NLOS
     */
    private static void generateKml(Point3D userPoint, Building wall, Satellite satellite, 
                                  boolean isLos, String filename) {
        try {
            // יצירת תיקייה לקבצי KML אם לא קיימת
            File kmlDir = new File("kml_output");
            if (!kmlDir.exists()) {
                kmlDir.mkdir();
            }
            
            // יצירת הקובץ בתיקייה
            FileWriter writer = new FileWriter(new File(kmlDir, filename));
            
            // כתיבת ה-KML
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
            writer.write("<Document>\n");
            
            // הגדרת סגנונות
            // סגנון עבור קיר
            writer.write("<Style id=\"wallStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff0000ff</color>\n");
            writer.write("    <width>2</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("  <PolyStyle>\n");
            writer.write("    <color>4d0000ff</color>\n");
            writer.write("    <fill>1</fill>\n");
            writer.write("    <outline>1</outline>\n");
            writer.write("  </PolyStyle>\n");
            writer.write("</Style>\n");
            
            // סגנון עבור קו ראייה
            String lineColor = isLos ? "ff00ff00" : "ff0000ff"; // ירוק ל-LOS, אדום ל-NLOS
            writer.write("<Style id=\"lineStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>" + lineColor + "</color>\n");
            writer.write("    <width>2</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");

            // סגנון עבור קו ראייה פוטנציאלי (תמיד ירוק)
            writer.write("<Style id=\"potentialLineStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff00ff00</color>\n"); // ירוק תמיד
            writer.write("    <width>2</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");

            // הוספת נקודת המשתמש
            writer.write("<Placemark>\n");
            writer.write("  <name>Observer</name>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + userPoint.getY() + "," + userPoint.getX() + "," + OBSERVER_HEIGHT + "</coordinates>\n");
            writer.write("  </Point>\n");
            writer.write("</Placemark>\n");
            
            // חישוב הגובה הנדרש למשתמש כדי להגיע ל-LOS עבור המקרה של NLOS
            if (!isLos && satellite.getElevation() > 0) {
                double tanElevation = Math.tan(Math.toRadians(satellite.getElevation()));
                double rayHeightAtWall = OBSERVER_HEIGHT + WALL_DISTANCE * tanElevation;
                double requiredAddHeight = WALL_HEIGHT - rayHeightAtWall;
                
                if (requiredAddHeight > 0) {
                    double requiredHeight = OBSERVER_HEIGHT + requiredAddHeight;
                    
                    // הוספת נקודה המייצגת את הגובה הנדרש להשגת LOS
                    writer.write("<Placemark>\n");
                    writer.write("  <name>Height Required for LOS (" + String.format("%.2f m", requiredHeight) + ")</name>\n");
                    writer.write("  <Style>\n");
                    writer.write("    <IconStyle>\n");
                    writer.write("      <color>ff00ffff</color>\n"); // צהוב
                    writer.write("      <scale>1.0</scale>\n");
                    writer.write("    </IconStyle>\n");
                    writer.write("  </Style>\n");
                    writer.write("  <Point>\n");
                    writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
                    writer.write("    <coordinates>" + userPoint.getY() + "," + userPoint.getX() + "," + requiredHeight + "</coordinates>\n");
                    writer.write("  </Point>\n");
                    writer.write("</Placemark>\n");
                    
                    // הוספת קו ראייה פוטנציאלי מנקודת הגובה החדשה
                    double endHeight = requiredHeight + 300 * Math.tan(Math.toRadians(satellite.getElevation()));
                    double dx = 300 * Math.sin(Math.toRadians(satellite.getAzimuth())) / 100000; // קונברסיה גסה למעלות
                    double dy = 300 * Math.cos(Math.toRadians(satellite.getAzimuth())) / 100000; // קונברסיה גסה למעלות
                    
                    writer.write("<Placemark>\n");
                    writer.write("  <name>Potential LOS from Required Height for LOS</name>\n");
                    writer.write("  <styleUrl>#potentialLineStyle</styleUrl>\n");
                    writer.write("  <LineString>\n");
                    writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
                    writer.write("    <coordinates>\n");
                    writer.write("      " + userPoint.getY() + "," + userPoint.getX() + "," + requiredHeight + "\n");
                    writer.write("      " + (userPoint.getY() + dy) + "," + (userPoint.getX() + dx) + "," + endHeight + "\n");
                    writer.write("    </coordinates>\n");
                    writer.write("  </LineString>\n");
                    writer.write("</Placemark>\n");
                }
            }

            // הוספת הקיר
            writer.write("<Placemark>\n");
            writer.write("  <name>Wall</name>\n");
            writer.write("  <styleUrl>#wallStyle</styleUrl>\n");
            writer.write("  <Polygon>\n");
            writer.write("    <extrude>1</extrude>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <outerBoundaryIs>\n");
            writer.write("      <LinearRing>\n");
            writer.write("        <coordinates>\n");
            for (Point3D vertex : wall.getVertices()) {
                writer.write(vertex.getY() + "," + vertex.getX() + "," + (vertex.getZ() == 0 ? 0 : WALL_HEIGHT) + "\n");
            }
            writer.write("        </coordinates>\n");
            writer.write("      </LinearRing>\n");
            writer.write("    </outerBoundaryIs>\n");
            writer.write("  </Polygon>\n");
            writer.write("</Placemark>\n");

            // הוספת קו ראייה
            // חישוב נקודת הקצה של קו הראייה
            double lineLength = 300; // אורך קו הראייה במטרים
            double endHeight = OBSERVER_HEIGHT + lineLength * Math.tan(Math.toRadians(satellite.getElevation()));
            double dx = lineLength * Math.sin(Math.toRadians(satellite.getAzimuth())) / 100000; // קונברסיה גסה למעלות
            double dy = lineLength * Math.cos(Math.toRadians(satellite.getAzimuth())) / 100000; // קונברסיה גסה למעלות
            
            writer.write("<Placemark>\n");
            writer.write("  <name>Line of Sight (" + (isLos ? "LOS" : "NLOS") + ")</name>\n");
            writer.write("  <styleUrl>#lineStyle</styleUrl>\n");
            writer.write("  <LineString>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>\n");
            writer.write("      " + userPoint.getY() + "," + userPoint.getX() + "," + OBSERVER_HEIGHT + "\n");
            writer.write("      " + (userPoint.getY() + dy) + "," + (userPoint.getX() + dx) + "," + endHeight + "\n");
            writer.write("    </coordinates>\n");
            writer.write("  </LineString>\n");
            writer.write("</Placemark>\n");

            // הוספת נקודה המדמה את מיקום הלוויין (מרוחק מאוד במציאות)
            writer.write("<Placemark>\n");
            writer.write("  <name>Satellite (Symbolic)</name>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + (userPoint.getY() + dy*3) + "," + (userPoint.getX() + dx*3) + "," + (endHeight*3) + "</coordinates>\n");
            writer.write("  </Point>\n");
            writer.write("</Placemark>\n");

            writer.write("</Document>\n");
            writer.write("</kml>");
            writer.close();
            
            System.out.println("  KML file generated: " + new File(kmlDir, filename).getAbsolutePath());
            
        } catch (IOException e) {
            System.out.println("Error generating KML file: " + e.getMessage());
        }
    }
}
