package com.gps.particlefilter.model;

public class Satellite {
    private String name;
    private Point3D position;
    private double azimuth;
    private double elevation;
    private double cnRatio; // C/N0 signal strength in dB-Hz

    public Satellite(String name, Point3D position, double azimuth, double elevation) {
        this.name = name;
        this.position = position;
        this.azimuth = azimuth;
        this.elevation = elevation;
        // Initialize with realistic C/N0 values (20-45 dB-Hz range)
        this.cnRatio = simulateSignalStrength(elevation);
    }
    
    public Satellite(String name, Point3D position, double azimuth, double elevation, double cnRatio) {
        this.name = name;
        this.position = position;
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.cnRatio = cnRatio;
    }
    
    /**
     * Simulate realistic C/N0 signal strength based on elevation
     * Higher elevation generally means stronger signal
     */
    private double simulateSignalStrength(double elevation) {
        // Base signal strength increases with elevation
        double baseStrength = 25 + (elevation / 90.0) * 15; // 25-40 dB-Hz range
        
        // Add some random variation (±5 dB-Hz)
        double variation = (Math.random() - 0.5) * 10;
        
        // Clamp to realistic range 20-45 dB-Hz
        return Math.max(20, Math.min(45, baseStrength + variation));
    }

    public String getName() {
        return name;
    }

    public Point3D getPosition() {
        return position;
    }

    public void setPosition(Point3D position) {
        this.position = position;
    }

    public double getAzimuth() {
        return azimuth;
    }

    public double getElevation() {
        return elevation;
    }
    
    public double getCnRatio() {
        return cnRatio;
    }
    
    public void setCnRatio(double cnRatio) {
        this.cnRatio = cnRatio;
    }
    
    /**
     * Classify LOS/NLOS based on C/N0 signal strength using article's 37 dB-Hz threshold
     * @return true if LOS (C/N0 >= 37 dB-Hz), false if NLOS
     */
    public boolean isLosFromSignalStrength() {
        return cnRatio >= 37.0; // Article's threshold
    }
    
    /**
     * Classify LOS/NLOS based on C/N0 signal strength using configurable threshold
     * @param threshold Signal strength threshold in dB-Hz
     * @return true if LOS (C/N0 >= threshold), false if NLOS
     */
    public boolean isLosFromSignalStrength(double threshold) {
        return cnRatio >= threshold;
    }
    
    /**
     * Apply NLOS signal degradation (reduce C/N0 by 10-20 dB-Hz)
     */
    public void applyNlosSignalDegradation() {
        double degradation = 10 + Math.random() * 10; // 10-20 dB-Hz reduction
        this.cnRatio = Math.max(20, this.cnRatio - degradation);
    }
}
