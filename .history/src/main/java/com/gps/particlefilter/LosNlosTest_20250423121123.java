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
    private static final double OBSERVER_HEIGHT = 85; // גובה המשתמש במטרים
    private static final double WALL_DISTANCE = 15.0; // מרחק מהמשתמש לקיר במטרים
    private static final double WALL_LENGTH = 20.0; // אורך הקיר במטרים
    
    // פקטור המרה - כמה מטרים הם מעלה אחת בערך
    private static final double METERS_PER_DEGREE = 111300.0;
    
    // פרמטרים לסימולציה העיקרית שתייצר קובץ KML
    private static final double MAIN_SIM_AZIMUTH = 45.0; // אזימוט לסימולציה העיקרית
    private static final double MAIN_SIM_ELEVATION = 45.0; // זווית רומם לסימולציה העיקרית

    public static void main(String[] args) {
        try {
            testLosNlos(MAIN_SIM_AZIMUTH, MAIN_SIM_ELEVATION, true);
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
            
            // המרה מדויקת יותר ממטרים למעלות
            double dLat = dy / METERS_PER_DEGREE;
            double dLon = dx / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
            
            // חישוב נקודות הקצה של הקיר ניצב לקו הראייה
            double wallHalfLength = WALL_LENGTH / 2.0;
            double wallDx = wallHalfLength * Math.cos(Math.toRadians(wallAngle)); // ניצב לקו הראייה
            double wallDy = -wallHalfLength * Math.sin(Math.toRadians(wallAngle)); // ניצב לקו הראייה
            
            // המרה מדויקת יותר של אורך הקיר ממטרים למעלות
            double wallDLat = wallDy / METERS_PER_DEGREE;
            double wallDLon = wallDx / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
            
            Point3D wallCenter = new Point3D(
                userPoint.getX() + dLat, // מרחק אופקי בכיוון צפון/דרום
                userPoint.getY() + dLon, // מרחק אופקי בכיוון מזרח/מערב
                0 // בסיס הקיר
            );
            
            Point3D wallStart = new Point3D(
                wallCenter.getX() + wallDLat,
                wallCenter.getY() + wallDLon,
                0 // בסיס הקיר
            );
            
            Point3D wallEnd = new Point3D(
                wallCenter.getX() - wallDLat,
                wallCenter.getY() - wallDLon,
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
            
            // חישוב LOS / NLOS
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            Map<String, Boolean> losResult = losCalculator.calculateLOS(userPoint);
            boolean isLos = losResult.get(satellite.getName());
            
            // לצורך מיקום מדויק יותר ב-KML, מוצאים את נקודת החיתוך האמיתית
            Point2D intersectionPoint = null;
            double zAtIntersection = 0;
            double horizontalDistanceInMeters = 0;
            
            // שימוש בגישה דומה לזו שנמצאת ב-LosCalculator כדי למצוא את נקודת החיתוך
            // הלוגיקה הזו כפולה במזיד כדי לא לשנות את ה-LosCalculator ולהשתמש
            // בתוצאה שלו לצורכי ציור ה-KML בלבד
            Line3D ray = new Line3D(userPoint, satellite.getAzimuth(), satellite.getElevation(), 50);
            
            // בדיקת חיתוך עם הקיר
            for (int i = 0; i < wall.getVertices().size() - 1; i++) {
                Point3D p1 = wall.getVertices().get(i);
                Point3D p2 = wall.getVertices().get(i + 1);
                Line2D wallLine = new Line2D(new Point2D(p1.getX(), p1.getY()), 
                                        new Point2D(p2.getX(), p2.getY()));
                
                // Get the 2D intersection point
                Point2D tempPoint = ray.getIntersectionPoint(wallLine);
                
                if (tempPoint != null) {
                    intersectionPoint = tempPoint;
                    
                    // Calculate horizontal distance to intersection
                    double dx_intersect = intersectionPoint.getX() - userPoint.getX();
                    double dy_intersect = intersectionPoint.getY() - userPoint.getY();
                    double horizontalDistance = Math.sqrt(dx_intersect*dx_intersect + dy_intersect*dy_intersect);
                    
                    // המרת מרחק אופקי ממעלות למטרים
                    horizontalDistanceInMeters = horizontalDistance * 111300 * Math.cos(Math.toRadians(userPoint.getX()));
                    
                    // Calculate height at intersection point
                    double heightGain = horizontalDistanceInMeters * Math.tan(Math.toRadians(satellite.getElevation()));
                    zAtIntersection = userPoint.getZ() + heightGain;
                    
                    break; // נמצאה נקודת חיתוך
                }
            }
            
            // חישוב פרטי NLOS אם צריך
            if (!isLos) {
                double heightDiff = losCalculator.computeLosDetailed(userPoint, wall, satellite);
                double rayHeightAtWall = OBSERVER_HEIGHT + WALL_DISTANCE * Math.tan(Math.toRadians(elevation));
                double requiredAddHeight = WALL_HEIGHT - rayHeightAtWall;
                System.out.println("NLOS - Required height addition: " + String.format("%.2f", requiredAddHeight) + "m");
            } else {
                System.out.println("LOS");
            }
            
            // יצירת קובץ KML אם נדרש
            if (generateKmlFile) {
                String filename = "los_simulation.kml";
                generateKml(userPoint, wall, satellite, isLos, intersectionPoint, zAtIntersection, filename);
                System.out.println("KML file generated successfully: kml_output/" + filename);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * מייצר קובץ KML להדמיה ויזואלית של מצב ה-LOS/NLOS
     */
    private static void generateKml(Point3D userPoint, Building wall, Satellite satellite, 
                                  boolean isLos, Point2D intersectionPoint, double zAtIntersection, String filename) {
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
            
            // הוספת נקודת המשתמש
            writer.write("<Placemark>\n");
            writer.write("  <name>Observer (" + String.format("%.2f", OBSERVER_HEIGHT) + " m)</name>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + userPoint.getY() + "," + userPoint.getX() + "," + OBSERVER_HEIGHT + "</coordinates>\n");
            writer.write("  </Point>\n");
            writer.write("</Placemark>\n");

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
            // חישוב נקודת הקצה של קו הראייה - נשתמש באותו מרחק כמו הלוויין
            double lineLength = 50; // אורך קו הראייה במטרים - זהה למרחק הלוויין
            double endHeight = OBSERVER_HEIGHT + lineLength * Math.tan(Math.toRadians(satellite.getElevation()));
            
            // המרה מדויקת יותר ממטרים למעלות
            double dx = lineLength * Math.sin(Math.toRadians(satellite.getAzimuth()));
            double dy = lineLength * Math.cos(Math.toRadians(satellite.getAzimuth()));
            double dLat = dy / METERS_PER_DEGREE;
            double dLon = dx / (METERS_PER_DEGREE * Math.cos(Math.toRadians(userPoint.getX())));
            
            // נציג את הקו בצורה שונה, תלוי אם יש או אין חיתוך עם הקיר
            writer.write("<Placemark>\n");
            writer.write("  <name>" + (isLos ? "Line of Sight (LOS)" : "Line of Sight (NLOS - Blocked)") + "</name>\n");
            writer.write("  <styleUrl>#lineStyle</styleUrl>\n");
            writer.write("  <LineString>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>\n");
            writer.write("      " + userPoint.getY() + "," + userPoint.getX() + "," + OBSERVER_HEIGHT + "\n");
            writer.write("      " + (userPoint.getY() + dLon) + "," + (userPoint.getX() + dLat) + "," + endHeight + "\n");
            writer.write("    </coordinates>\n");
            writer.write("  </LineString>\n");
            writer.write("</Placemark>\n");
            
            // הוספת נקודה המדמה את מיקום הלוויין (באותו מיקום כמו סוף הקו)
            writer.write("<Placemark>\n");
            writer.write("  <name>Satellite (Symbolic)</name>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + (userPoint.getY() + dLon) + "," + (userPoint.getX() + dLat) + "," + endHeight + "</coordinates>\n");
            writer.write("  </Point>\n");
            writer.write("</Placemark>\n");
            
            writer.write("</Document>\n");
            writer.write("</kml>");
            writer.close();
            
        } catch (IOException e) {
            System.out.println("Error generating KML file: " + e.getMessage());
        }
    }
}
