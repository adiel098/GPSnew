package com.gps.particlefilter;

import com.gps.particlefilter.model.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class LosCalculator {
    private List<Building> buildings;
    private List<Satellite> satellites;
    private boolean debugPrinted = false; // פרמטר לבדיקה האם כבר הדפסנו את המידע
    
    // מרווח סבלנות בחישוב התנגשות עם קיר (מטרים) - מבוטל לטובת ערכים מוחלטים
    private static final double LOS_TOLERANCE = 0.0;
    
    // פקטור המרה - כמה מטרים הם מעלה אחת בערך
    private static final double METERS_PER_DEGREE = 111300.0;

    public LosCalculator(List<Building> buildings, List<Satellite> satellites) {
        this.buildings = buildings;
        this.satellites = satellites;
        
        // הדפסת מידע על הבניינים
        System.out.println("\n=== מידע על הבניינים ===");
        System.out.println("מספר בניינים: " + buildings.size());
        for (int i = 0; i < buildings.size(); i++) {
            Building building = buildings.get(i);
            if (building == null) {
                System.out.println("בניין " + i + " הוא null!");
                continue;
            }
            
            List<Point3D> vertices = building.getVertices();
            if (vertices == null) {
                System.out.println("בניין " + i + " אין לו נקודות!");
                continue;
            }
            
            System.out.println("\nבניין " + i + ":");
            System.out.println("גובה: " + building.getHeight() + " מטרים");
            System.out.println("מספר קירות: " + vertices.size());
            
            // הדפסת הקירות
            for (int j = 0; j < vertices.size(); j++) {
                Point3D vertex = vertices.get(j);
                System.out.printf("נקודה %d: (%.6f, %.6f)\n", 
                    j, vertex.getX(), vertex.getY());
            }
        }
        System.out.println("\n=== סיום מידע על הבניינים ===\n");
    }

    public Map<String, Boolean> calculateLOS(Point3D pos) {
        Map<String, Boolean> result = new HashMap<>();
        
        for (Satellite satellite : satellites) {
            boolean isLos = true;
            double requiredHeightAddition = 0;
            
            // Skip invalid buildings
            for (Building building : buildings) {
                if (building == null || building.getVertices() == null || building.getVertices().size() < 3) {
                    continue;
                }
                
                LosResult losResult = computeLosDetailedWithIntersection(pos, building, satellite);
                if (!losResult.isLos()) {
                    isLos = false;
                    requiredHeightAddition = losResult.getDeltaH();
                    break;
                }
            }
            
            result.put(satellite.getName(), isLos);
        }
        
        return result;
    }

    /**
     * מחשב האם יש קו ראייה ישיר (LOS) בין נקודת המשתמש ללוויין
     * מחזיר אובייקט המכיל את כל המידע הרלוונטי: האם יש LOS, נקודת החיתוך, גובה הקרן וכו'
     */
    public LosResult computeLosDetailedWithIntersection(Point3D userPoint, Building building, Satellite satellite) {
        List<Point3D> vertices = building.getVertices();
        
        // נעבור על כל הקירות של הבניין
        for (int i = 0; i < vertices.size() - 1; i++) {
            Point3D p1 = vertices.get(i);
            Point3D p2 = vertices.get(i + 1);
            
            // חישוב וקטור הכיוון ללוויין
            double azimuthRad = Math.toRadians(satellite.getAzimuth());
            double elevationRad = Math.toRadians(satellite.getElevation());
            
            // וקטור יחידה בכיוון הלוויין
            double dx = Math.sin(azimuthRad) * Math.cos(elevationRad);
            double dy = Math.cos(azimuthRad) * Math.cos(elevationRad);
            double dz = Math.sin(elevationRad);
            
            // חישוב נקודת החיתוך עם המישור האנכי של הקיר
            double wallDx = p2.getX() - p1.getX();
            double wallDy = p2.getY() - p1.getY();
            
            // חישוב המכפלה הווקטורית של וקטור הקיר עם וקטור הכיוון
            double normalX = -wallDy;  // וקטור ניצב לקיר
            double normalY = wallDx;
            
            // מכפלה סקלרית של וקטור הכיוון עם הנורמל
            double dot = dx * normalX + dy * normalY;
            
            if (Math.abs(dot) > 1e-10) {  // אם הקרן לא מקבילה לקיר
                // חישוב פרמטר t של נקודת החיתוך
                double t = ((p1.getX() - userPoint.getX()) * normalX + 
                          (p1.getY() - userPoint.getY()) * normalY) / dot;
                
                if (t > 0) {  // החיתוך בכיוון החיובי
                    // נקודת החיתוך
                    double intersectX = userPoint.getX() + t * dx;
                    double intersectY = userPoint.getY() + t * dy;
                    
                    // בדיקה האם נקודת החיתוך על הקיר
                    double wallLen = Math.sqrt(wallDx * wallDx + wallDy * wallDy);
                    double s = ((intersectX - p1.getX()) * wallDx + 
                              (intersectY - p1.getY()) * wallDy) / (wallLen * wallLen);
                    
                    if (s >= 0 && s <= 1) {  // החיתוך על הקיר
                        // חישוב גובה הקרן בנקודת החיתוך
                        double horizontalDistance = t * Math.sqrt(dx * dx + dy * dy) * METERS_PER_DEGREE;
                        double heightGain = horizontalDistance * Math.tan(elevationRad);
                        double rayHeightAtIntersection = userPoint.getZ() + heightGain;
                        
                        // בדיקה האם הקרן נחסמת על ידי הבניין
                        if (rayHeightAtIntersection <= building.getHeight()) {
                            double deltaH = building.getHeight() - rayHeightAtIntersection;
                            return new LosResult(false, deltaH, 
                                new Point2D(intersectX, intersectY), rayHeightAtIntersection);
                        }
                    }
                }
            }
        }
        
        return new LosResult(true, 0, null, 0);
    }

    /**
     * Returns counts of LOS and NLOS satellites for a position
     * @return int[2] array where [0] = LOS count, [1] = NLOS count
     */
    public int[] getLosNlosCount(Point3D position) {
        Map<String, Boolean> status = calculateLOS(position);
        int losCount = 0;
        int nlosCount = 0;
        
        for (Boolean isLos : status.values()) {
            if (isLos) {
                losCount++;
            } else {
                nlosCount++;
            }
        }
        
        return new int[] {losCount, nlosCount};
    }
    
    /**
     * Return a compact representation of LOS/NLOS status for a point 
     */
    public String getLosStatusString(Point3D position) {
        Map<String, Boolean> losStatus = calculateLOS(position);
        int losCount = 0;
        int nlosCount = 0;
        
        for (Boolean isLos : losStatus.values()) {
            if (isLos) {
                losCount++;
            } else {
                nlosCount++;
            }
        }
        
        return String.format("Satellites - LOS: %d, NLOS: %d", losCount, nlosCount);
    }

    /**
     * מחלקה המייצגת את תוצאת חישוב ה-LOS
     */
    public static class LosResult {
        private final boolean isLos;           // האם יש קו ראייה ישיר
        private final double deltaH;           // ההפרש בגובה במקרה של NLOS
        private final Point2D intersection;    // נקודת החיתוך עם הקיר (אם יש)
        private final double rayHeight;        // גובה הקרן בנקודת החיתוך

        public LosResult(boolean isLos, double deltaH, Point2D intersection, double rayHeight) {
            this.isLos = isLos;
            this.deltaH = deltaH;
            this.intersection = intersection;
            this.rayHeight = rayHeight;
        }

        public boolean isLos() { return isLos; }
        public double getDeltaH() { return deltaH; }
        public Point2D getIntersection() { return intersection; }
        public double getRayHeight() { return rayHeight; }
    }
}
