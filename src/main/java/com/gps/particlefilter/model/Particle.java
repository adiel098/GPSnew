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

    public void move(Point3D direction, double noise) {
        // Add random noise to movement
        double noiseX = (Math.random() - 0.5) * noise;
        double noiseY = (Math.random() - 0.5) * noise;
        double noiseZ = (Math.random() - 0.5) * noise;
        
        Point3D noiseVector = new Point3D(noiseX, noiseY, noiseZ);
        this.position = this.position.add(direction).add(noiseVector);
    }

    public int matchingLosCount(Map<String, Boolean> referenceStatus) {
        int count = 0;
        for (Map.Entry<String, Boolean> entry : referenceStatus.entrySet()) {
            String satelliteId = entry.getKey();
            if (losStatus.containsKey(satelliteId) && 
                losStatus.get(satelliteId).equals(entry.getValue())) {
                count++;
            }
        }
        return count;
    }
}
