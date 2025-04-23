package com.gps.particlefilter.model;

import java.util.HashMap;
import java.util.Map;

public class Particle {
    private Point3D position;
    private double weight;
    private Map<String, Boolean> losStatus;

    public Particle(Point3D position) {
        this.position = position;
        this.weight = 1.0;
        this.losStatus = new HashMap<>();
    }

    public Point3D getPosition() {
        return position;
    }

    public void setPosition(Point3D position) {
        this.position = position;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public Map<String, Boolean> getLosStatus() {
        return losStatus;
    }

    public void setLosStatus(Map<String, Boolean> losStatus) {
        this.losStatus = losStatus;
    }

    public int matchingLosCount(Map<String, Boolean> referenceStatus) {
        int count = 0;
        int totalSatellites = 0;
        
        for (Map.Entry<String, Boolean> entry : referenceStatus.entrySet()) {
            String satelliteId = entry.getKey();
            totalSatellites++;
            
            // בדיקה האם מצב ה-LOS של הלוויין זהה בחלקיק ובמצב האמיתי
            if (losStatus.containsKey(satelliteId) && 
                losStatus.get(satelliteId).equals(entry.getValue())) {
                count++;
            }
        }
        
        // החזרת אחוז ההתאמה
        return count;
    }

    /**
     * מחזיר את אחוז ההתאמה בין מצב ה-LOS של החלקיק למצב האמיתי
     */
    public double getLosMatchPercentage(Map<String, Boolean> referenceStatus) {
        int matches = matchingLosCount(referenceStatus);
        return (double) matches / referenceStatus.size();
    }

    /**
     * מחזיר את מספר הלוויינים שהם LOS ו-NLOS
     */
    public String getLosNlosCount() {
        if (losStatus == null) return "No LOS status";
        
        int losCount = 0;
        int nlosCount = 0;
        
        for (Boolean isLos : losStatus.values()) {
            if (isLos) {
                losCount++;
            } else {
                nlosCount++;
            }
        }
        
        return String.format("LOS: %d, NLOS: %d", losCount, nlosCount);
    }

    // עדכון פונקציית התנועה לעבוד עם מרחק וזווית
    public void move(double distance, double azimuth, double noise) {
        // הוספת רעש למרחק ולזווית
        double noisyDistance = distance + (Math.random() - 0.5) * noise;
        double noisyAzimuth = azimuth + (Math.random() - 0.5) * noise;

        // הזזת החלקיק למיקום החדש
        this.position = this.position.moveByDistanceAndAzimuth(noisyDistance, noisyAzimuth);
    }
}
