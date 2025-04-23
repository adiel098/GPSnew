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
    private static final double OBSERVER_HEIGHT = 86.3; // גובה המשתמש במטרים
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
            // יצירת נקודת משתמש במיקום (0,0)
            Point3D userPoint = new Point3D(0, 0, OBSERVER_HEIGHT);
            
            // חישוב נקודות הקיר במרחק WALL_DISTANCE מהמשתמש
            double wallCenterX = WALL_DISTANCE * Math.sin(Math.toRadians(azimuth));
            double wallCenterY = WALL_DISTANCE * Math.cos(Math.toRadians(azimuth));
            
            // חישוב נקודות הקצה של הקיר (ניצב לכיוון האזימוט)
            double wallHalfLength = WALL_LENGTH / 2.0;
            double wallDx = wallHalfLength * Math.cos(Math.toRadians(azimuth)); // ניצב לקו הראייה
            double wallDy = -wallHalfLength * Math.sin(Math.toRadians(azimuth)); // ניצב לקו הראייה
            
            Point3D wallStart = new Point3D(
                wallCenterX + wallDx,
                wallCenterY + wallDy,
                0 // בסיס הקיר
            );
            
            Point3D wallEnd = new Point3D(
                wallCenterX - wallDx,
                wallCenterY - wallDy,
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
            List<Building> buildings = new ArrayList<>();
            buildings.add(wall);
            
            // יצירת לוויין בכיוון ובזווית הנתונים
            Point3D satPosition = null; // לא צריך מיקום מדויק ללוויין בשביל הסימולציה
            Satellite satellite = new Satellite("TEST", satPosition, azimuth, elevation);
            List<Satellite> satellites = new ArrayList<>();
            satellites.add(satellite);
            
            // חישוב LOS / NLOS
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            Map<String, Boolean> losResult = losCalculator.calculateLOS(userPoint);
            boolean isLos = losResult.get(satellite.getName());
            
            // חישוב נקודת החיתוך לצורך KML
            LosCalculator.LosResult detailedResult = losCalculator.computeLosDetailedWithIntersection(userPoint, wall, satellite);
            
            // יצירת קובץ KML אם נדרש
            if (generateKmlFile) {
                String filename = "los_simulation.kml";
                generateKml(userPoint, wall, satellite, isLos, 
                          detailedResult.intersectionPoint, detailedResult.rayHeightAtIntersection, filename);
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
            // המרת נקודות לקואורדינטות גיאוגרפיות
            double baseLat = 31.771959; // ירושלים
            double baseLon = 35.217018;
            
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
            
            // סגנון עבור קו ראייה - ירוק ל-LOS, אדום ל-NLOS
            writer.write("<Style id=\"losStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff00ff00</color>\n");
            writer.write("    <width>1</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");
            
            writer.write("<Style id=\"nlosStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff0000ff</color>\n");
            writer.write("    <width>1</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");
            
            writer.write("<Style id=\"blockedStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>4f0000ff</color>\n");
            writer.write("    <width>2</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");
            
            // המרת נקודות המשתמש והקיר לקואורדינטות גיאוגרפיות
            double userLat = baseLat + userPoint.getY() / METERS_PER_DEGREE;
            double userLon = baseLon + userPoint.getX() / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
            
            // הוספת נקודת המשתמש
            writer.write("<Placemark>\n");
            writer.write("  <name>Observer (" + String.format("%.2f", userPoint.getZ()) + " m)</name>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + userLon + "," + userLat + "," + userPoint.getZ() + "</coordinates>\n");
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
            
            // המרת כל נקודות הקיר לקואורדינטות גיאוגרפיות וכתיבתן
            for (Point3D vertex : wall.getVertices()) {
                double lat = baseLat + vertex.getY() / METERS_PER_DEGREE;
                double lon = baseLon + vertex.getX() / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
                writer.write("          " + lon + "," + lat + "," + vertex.getZ() + "\n");
            }
            
            writer.write("        </coordinates>\n");
            writer.write("      </LinearRing>\n");
            writer.write("    </outerBoundaryIs>\n");
            writer.write("  </Polygon>\n");
            writer.write("</Placemark>\n");

            // חישוב נקודת הקצה של קו הראייה ומיקום הלוויין
            double rayLength = 500.0; // אורך קו הראייה במטרים
            double satelliteDistance = 500.0; // מרחק הלוויין במטרים (רחוק יותר מקו הראייה)
            
            // חישוב נקודת הסיום של קו הראייה (או נקודת החיתוך אם קיימת)
            double endLat, endLon, endZ;
            if (intersectionPoint != null) {
                endLat = baseLat + intersectionPoint.getY() / METERS_PER_DEGREE;
                endLon = baseLon + intersectionPoint.getX() / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
                endZ = zAtIntersection;
            } else {
                double dx = rayLength * Math.sin(Math.toRadians(satellite.getAzimuth()));
                double dy = rayLength * Math.cos(Math.toRadians(satellite.getAzimuth()));
                double dz = rayLength * Math.tan(Math.toRadians(satellite.getElevation()));
                endLat = baseLat + (userPoint.getY() + dy) / METERS_PER_DEGREE;
                endLon = baseLon + (userPoint.getX() + dx) / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
                endZ = userPoint.getZ() + dz;
            }

            // חישוב מיקום הלוויין (תמיד במרחק קבוע וגדול)
            double satDx = satelliteDistance * Math.sin(Math.toRadians(satellite.getAzimuth()));
            double satDy = satelliteDistance * Math.cos(Math.toRadians(satellite.getAzimuth()));
            double satDz = satelliteDistance * Math.tan(Math.toRadians(satellite.getElevation()));
            double satLat = baseLat + (userPoint.getY() + satDy) / METERS_PER_DEGREE;
            double satLon = baseLon + (userPoint.getX() + satDx) / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
            double satZ = userPoint.getZ() + satDz;

            // הוספת קו הראייה - ירוק ל-LOS, אדום ל-NLOS
            writer.write("<Placemark>\n");
            writer.write("  <name>" + (isLos ? "Line of Sight (LOS)" : "Line of Sight (NLOS)") + "</name>\n");
            writer.write("  <styleUrl>" + (isLos ? "#losStyle" : "#nlosStyle") + "</styleUrl>\n");
            writer.write("  <LineString>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>\n");
            writer.write("      " + userLon + "," + userLat + "," + userPoint.getZ() + "\n");
            writer.write("      " + satLon + "," + satLat + "," + satZ + "\n");
            writer.write("    </coordinates>\n");
            writer.write("  </LineString>\n");
            writer.write("</Placemark>\n");

            // הוספת הלוויין (תמיד במיקום הקבוע)
            writer.write("<Placemark>\n");
            writer.write("  <name>" + satellite.getName() + "</name>\n");
            writer.write("  <Style>\n");
            writer.write("    <IconStyle>\n");
            writer.write("      <scale>1.0</scale>\n");
            writer.write("      <Icon>\n");
            writer.write("        <href>http://maps.google.com/mapfiles/kml/shapes/star.png</href>\n");
            writer.write("      </Icon>\n");
            writer.write("    </IconStyle>\n");
            writer.write("  </Style>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + satLon + "," + satLat + "," + satZ + "</coordinates>\n");
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
