package com.gps.particlefilter.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Particle {
    private Point3D position;
    private double weight;
    private double previousWeight; // For Bayesian weight function
    private Map<String, Boolean> losStatus;

    public Particle(Point3D position) {
        this.position = position;
        this.weight = 1.0;
        this.previousWeight = 0.0; // Initialize previous weight
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
    
    public double getPreviousWeight() {
        return previousWeight;
    }
    
    public void setPreviousWeight(double previousWeight) {
        this.previousWeight = previousWeight;
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
            
            // Check if satellite's LOS state matches between particle and real state
            if (losStatus.containsKey(satelliteId) && 
                losStatus.get(satelliteId).equals(entry.getValue())) {
                count++;
            }
        }
        
        // Return match percentage
        return count;
    }

    /**
     * Returns the percentage match between particle's LOS state and real state
     */
    public double getLosMatchPercentage(Map<String, Boolean> referenceStatus) {
        int matches = matchingLosCount(referenceStatus);
        return (double) matches / referenceStatus.size();
    }

    /**
     * Returns the count of satellites that are LOS and NLOS
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

    /**
     * Updated movement function to work with distance and angle - adapted for UTM coordinate system
     * 
     * @param distance - the distance to move in meters
     * @param azimuth - the azimuth angle in degrees (from north)
     * @param noise - noise level for distance and angle
     */
    public void move(double distance, double azimuth, double noise) {
        Random random = new Random();
        
        // Add Gaussian noise to distance and angle
        double noisyDistance = distance + random.nextGaussian() * noise * 0.5;
        double noisyAzimuth = azimuth + random.nextGaussian() * noise * 2.0;

        // Correct calculation for azimuth from north in UTM coordinates
        double azimuthRad = Math.toRadians(noisyAzimuth);
        double dx = noisyDistance * Math.sin(azimuthRad); // Easting component
        double dy = noisyDistance * Math.cos(azimuthRad); // Northing component
        
        // In UTM system, x represents easting, y represents northing
        double newX = position.getX() + dx;
        double newY = position.getY() + dy;
        double newZ = position.getZ(); // Height remains constant
        
        // Update particle position
        this.position = new Point3D(newX, newY, newZ);
    }
}
