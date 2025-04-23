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

    public LosCalculator(List<Building> buildings, List<Satellite> satellites) {
        this.buildings = buildings;
        this.satellites = satellites;
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
                if (!losResult.isLos) {
                    isLos = false;
                    requiredHeightAddition = losResult.heightDifference;
                    break;
                }
            }
            
            if (isLos) {
                System.out.println("LOS");
            } else {
                System.out.println("NLOS - Required height addition: " + String.format("%.2f", requiredHeightAddition) + "m");
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
        Line3D ray = new Line3D(userPoint, satellite.getAzimuth(), satellite.getElevation(), 300);
        
        // בדיקת חיתוך עם הקיר
        for (int i = 0; i < building.getVertices().size() - 1; i++) {
            Point3D p1 = building.getVertices().get(i);
            Point3D p2 = building.getVertices().get(i + 1);
            Line2D wallLine = new Line2D(new Point2D(p1.getX(), p1.getY()), 
                                    new Point2D(p2.getX(), p2.getY()));
            
            // Get the 2D intersection point
            Point2D intersectionPoint = ray.getIntersectionPoint(wallLine);
            
            if (intersectionPoint != null) {
                // חישוב המרחק האופקי לנקודת החיתוך
                double dx = intersectionPoint.getX() - userPoint.getX();
                double dy = intersectionPoint.getY() - userPoint.getY();
                double horizontalDistance = Math.sqrt(dx*dx + dy*dy);
                
                // חישוב גובה הקרן בנקודת החיתוך
                double heightGain = horizontalDistance * Math.tan(Math.toRadians(satellite.getElevation()));
                double rayHeightAtIntersection = userPoint.getZ() + heightGain;
                
                // בדיקה האם הקרן עוברת מעל או מתחת לקיר
                if (rayHeightAtIntersection >= building.getHeight()) {
                    // LOS - הקרן עוברת מעל הקיר
                    return new LosResult(true, 0, intersectionPoint, rayHeightAtIntersection);
                } else {
                    // NLOS - הקרן פוגעת בקיר
                    double deltaH = building.getHeight() - rayHeightAtIntersection;
                    return new LosResult(false, deltaH, intersectionPoint, rayHeightAtIntersection);
                }
            }
        }
        
        // אם לא נמצאה חסימה
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
        Map<String, Boolean> status = calculateLOS(position);
        int[] counts = getLosNlosCount(position);
        
        return String.format("LOS: %d, NLOS: %d", counts[0], counts[1]);
    }

    public static class LosResult {
        public boolean isLos;
        public double heightDifference;
        public Point2D intersectionPoint;
        public double rayHeightAtIntersection;
        
        public LosResult(boolean isLos, double heightDifference, Point2D intersectionPoint, double rayHeightAtIntersection) {
            this.isLos = isLos;
            this.heightDifference = heightDifference;
            this.intersectionPoint = intersectionPoint;
            this.rayHeightAtIntersection = rayHeightAtIntersection;
        }
    }
}
