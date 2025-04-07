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

    public Map<String, Boolean> calculateLOS(Point3D position) {
        Map<String, Boolean> result = new HashMap<>();
        
        for (Satellite satellite : satellites) {
            // אם הלוויין מתחת ל-10 מעלות, נחשיב אותו כ-NLOS באופן אוטומטי
            if (satellite.getElevation() < 10.0) {
                result.put(satellite.getName(), false);
                continue;
            }
            
            boolean isLos = true;
            // בדוק את כל הבניינים, אם יש חסימה אפילו על ידי בניין אחד, סמן NLOS
            for (Building building : buildings) {
                double losResult = computeLosDetailed(position, building, satellite);
                if (losResult != -1) {
                    // נמצאה חסימה
                    isLos = false;
                    break;
                }
            }
            result.put(satellite.getName(), isLos);
        }
        
        return result;
    }

    /**
     * Computes LOS/NLOS status and returns the height difference if NLOS
     * or -1 if LOS
     */
    public double computeLosDetailed(Point3D pos, Building building, Satellite satellite) {
        // Skip invalid buildings
        if (building == null || building.getVertices() == null || building.getVertices().size() < 3) {
            return -1; // התעלם מבניינים לא תקינים
        }
        
        // Create line of sight ray
        Line3D ray = new Line3D(pos, satellite.getAzimuth(), satellite.getElevation(), 300);
        
        // Check intersection with each wall of the building
        double minHeightDiff = Double.POSITIVE_INFINITY;
        
        for (int i = 0; i < building.getVertices().size() - 1; i++) {
            Point3D p1 = building.getVertices().get(i);
            Point3D p2 = building.getVertices().get(i + 1);
            Line2D wall = new Line2D(new Point2D(p1.getX(), p1.getY()), 
                                   new Point2D(p2.getX(), p2.getY()));
            
            // Get the 2D intersection point
            Point2D intersectionPoint = ray.getIntersectionPoint(wall);
            
            // הדפסת מידע דיבאג רק בפעם הראשונה
            if (!debugPrinted && intersectionPoint != null) {
                System.out.println("\n=== DEBUG: FOUND INTERSECTION ===");
                System.out.println("Satellite: " + satellite.getName());
                System.out.println("Satellite Elevation: " + satellite.getElevation());
                System.out.println("Satellite Azimuth: " + satellite.getAzimuth());
                System.out.println("Wall from: " + p1.getX() + "," + p1.getY() + " to " + p2.getX() + "," + p2.getY());
                System.out.println("Intersection at: " + intersectionPoint.getX() + "," + intersectionPoint.getY());
                
                // Calculate height at intersection point
                double dx = intersectionPoint.getX() - pos.getX();
                double dy = intersectionPoint.getY() - pos.getY();
                double horizontalDistance = Math.sqrt(dx*dx + dy*dy);
                
                // המרת מרחק אופקי ממעלות למטרים
                double horizontalDistanceInMeters = horizontalDistance * 111300 * Math.cos(Math.toRadians(pos.getX()));
                
                double heightGain = horizontalDistanceInMeters * Math.tan(Math.toRadians(satellite.getElevation()));
                double zAtIntersection = pos.getZ() + heightGain;
                
                System.out.println("Height at intersection: " + zAtIntersection);
                System.out.println("Building height: " + building.getHeight());
                System.out.println("Observer height: " + pos.getZ());
                System.out.println("Horizontal distance: " + horizontalDistance);
                System.out.println("Horizontal distance in meters: " + horizontalDistanceInMeters);
                
                debugPrinted = true;
            }
            
            if (intersectionPoint == null) continue;
            
            // Calculate horizontal distance to intersection
            double dx = intersectionPoint.getX() - pos.getX();
            double dy = intersectionPoint.getY() - pos.getY();
            double horizontalDistance = Math.sqrt(dx*dx + dy*dy);
            
            // המרת מרחק אופקי ממעלות למטרים
            double horizontalDistanceInMeters = horizontalDistance * 111300 * Math.cos(Math.toRadians(pos.getX()));
            
            // Calculate height at intersection point
            double heightGain = horizontalDistanceInMeters * Math.tan(Math.toRadians(satellite.getElevation()));
            double zAtIntersection = pos.getZ() + heightGain;
            
            // Check if ray passes below wall top
            double wallTotalHeight = building.getHeight();
            
            // Debug information always for now to help diagnose the issue
            System.out.println("\n=== DEBUG: Intersection Calculation ===");
            System.out.println("Satellite: " + satellite.getName());
            System.out.println("Satellite Elevation: " + satellite.getElevation() + "°");
            System.out.println("Observer height: " + pos.getZ() + " m");
            System.out.println("Wall height: " + wallTotalHeight + " m");
            System.out.println("Horizontal distance to wall: " + horizontalDistanceInMeters + " m");
            System.out.println("Height gain to intersection: " + heightGain + " m");
            System.out.println("Z at intersection: " + zAtIntersection + " m");
            System.out.println("Is LOS check: " + (zAtIntersection >= wallTotalHeight));
            
            // בדיקה מוחלטת - אם הגובה בנקודת החיתוך קטן מגובה הקיר, זה NLOS
            if (zAtIntersection < wallTotalHeight) {
                double heightDiff = wallTotalHeight - zAtIntersection;
                minHeightDiff = Math.min(minHeightDiff, heightDiff);
                System.out.println("NLOS: Ray hits the wall. Height difference: " + heightDiff + " m");
            } else {
                System.out.println("LOS: Ray passes above the wall");
            }
        }
        
        if (minHeightDiff != Double.POSITIVE_INFINITY) {
            return minHeightDiff;
        }
        
        return -1;
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
}
