package com.gps.particlefilter.los;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.config.Configuration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Comparator;

public class LosCalculator {
    private List<Building> buildings;
    private List<Satellite> satellites;
    private boolean debugPrinted = false; // Parameter to check if we already printed the information
    private Configuration config;
    
    // Ray-Shooting optimization: Spatial indexing for buildings
    private List<Building> sortedBuildingsByDistance;
    private boolean rayShooting; // Enable Ray-Shooting optimization (from config)
    
    // Tolerance range in wall collision calculation (meters)
    private double losTolerance;
    
    // Conversion factor - approximately how many meters per degree (DEPRECATED - now using proper UTM)
    @Deprecated
    private static final double METERS_PER_DEGREE = 111300.0;
    
    // Article's C/N0 threshold for LOS/NLOS classification (from config)
    private double cnThreshold;
    
    // Classification modes
    public enum ClassificationMode {
        SIGNAL_STRENGTH_ONLY,    // Article's primary method
        GEOMETRIC_ONLY,          // Current method
        HYBRID                   // Combined (recommended)
    }
    
    private ClassificationMode classificationMode;
    
    // Misclassification error simulation
    private double misclassificationErrorPercentage = 0.0;
    private java.util.Random misclassificationRandom;

    public LosCalculator(List<Building> buildings, List<Satellite> satellites) {
        this.buildings = buildings;
        this.satellites = satellites;
        this.config = Configuration.getInstance();
        
        // Load configuration settings
        String modeString = config.getLosClassificationMode();
        try {
            this.classificationMode = ClassificationMode.valueOf(modeString);
        } catch (IllegalArgumentException e) {
            this.classificationMode = ClassificationMode.GEOMETRIC_ONLY;
        }
        this.rayShooting = config.isLosRayShootingEnabled();
        this.losTolerance = config.getLosTolerance();
        this.cnThreshold = config.getLosSignalThreshold();
        
        // Initialize misclassification random generator
        this.misclassificationRandom = new java.util.Random();
        
        // Initialize Ray-Shooting optimization
        initializeRayShooting();
        
        // Print building information only if debug is enabled
        if (config.isDebugBuildingInfoEnabled()) {
            System.out.println("\n=== DEBUG BUILDING INFO ===");
            System.out.println("Building count: " + buildings.size());
            
            for (int i = 0; i < buildings.size(); i++) {
            Building building = buildings.get(i);
            if (building == null) {
                System.out.println("Building " + i + " is null!");
                continue;
            }
            
            List<Point3D> vertices = building.getVertices();
            if (vertices == null) {
                System.out.println("Building " + i + " has no vertices!");
                continue;
            }
            
            System.out.println("\nBuilding " + i + ":");
            System.out.println("Height: " + building.getHeight() + " meters");
            System.out.println("Number of vertices: " + vertices.size());
            
                // Print vertices
                for (int j = 0; j < vertices.size(); j++) {
                    Point3D vertex = vertices.get(j);
                    System.out.printf("Vertex %d: (%.6f, %.6f)\n", 
                        j, vertex.getX(), vertex.getY());
                }
            }
            System.out.println("\n=== END BUILDING DEBUG INFO ===\n");
        }
    }

    public Map<String, Boolean> calculateLOS(Point3D pos) {
        Map<String, Boolean> result = new HashMap<>();
        
        for (Satellite satellite : satellites) {
            boolean isLos;
            
            switch (classificationMode) {
                case SIGNAL_STRENGTH_ONLY:
                    // Article's primary method: Use C/N0 threshold (configurable)
                    isLos = satellite.isLosFromSignalStrength(cnThreshold);
                    break;
                    
                case GEOMETRIC_ONLY:
                    // Original geometric method
                    isLos = isLosGeometric(pos, satellite);
                    break;
                    
                case HYBRID:
                default:
                    // Article's recommended approach: Signal strength + geometric validation
                    boolean signalLos = satellite.isLosFromSignalStrength(cnThreshold);
                    boolean geometricLos = isLosGeometric(pos, satellite);
                    
                    // If signal suggests NLOS, apply signal degradation for realism
                    if (!signalLos && geometricLos) {
                        // Geometric says LOS but signal is weak - likely multipath/attenuation
                        satellite.applyNlosSignalDegradation();
                    }
                    
                    // Combine both: if either suggests NLOS, classify as NLOS
                    isLos = signalLos && geometricLos;
                    break;
            }
            
            // Apply misclassification error if enabled
            if (misclassificationErrorPercentage > 0.0) {
                if (misclassificationRandom.nextDouble() * 100.0 < misclassificationErrorPercentage) {
                    isLos = !isLos;  // Flip the classification
                }
            }
            
            result.put(satellite.getName(), isLos);
        }
        
        return result;
    }
    
    /**
     * Geometric LOS calculation with Ray-Shooting optimization
     */
    private boolean isLosGeometric(Point3D pos, Satellite satellite) {
        if (rayShooting) {
            return isLosGeometricRayShoot(pos, satellite);
        } else {
            // Original method: check against all buildings
            for (Building building : buildings) {
                if (building == null || building.getVertices() == null || building.getVertices().size() < 3) {
                    continue;
                }
                
                LosResult losResult = computeLosDetailedWithIntersection(pos, building, satellite);
                if (!losResult.isLos()) {
                    return false; // Blocked by this building
                }
            }
            return true; // Not blocked by any building
        }
    }
    
    /**
     * Ray-Shooting optimization: O(N × k × log(B)) complexity
     * Only checks buildings that are likely to intersect the ray
     */
    private boolean isLosGeometricRayShoot(Point3D pos, Satellite satellite) {
        // Get buildings sorted by relevance to the ray direction
        List<Building> candidateBuildings = getRelevantBuildings(pos, satellite);
        
        // Check only the most relevant buildings (logarithmic portion of B)
        int maxBuildings = Math.min(candidateBuildings.size(), (int) Math.ceil(Math.log(buildings.size() + 1) * 2));
        
        for (int i = 0; i < maxBuildings; i++) {
            Building building = candidateBuildings.get(i);
            if (building == null || building.getVertices() == null || building.getVertices().size() < 3) {
                continue;
            }
            
            LosResult losResult = computeLosDetailedWithIntersection(pos, building, satellite);
            if (!losResult.isLos()) {
                return false; // Blocked by this building
            }
        }
        return true; // Not blocked by any relevant building
    }

    /**
     * Calculates if there is a direct line of sight (LOS) between user point and satellite
     * Returns an object containing all relevant information: whether there's LOS, intersection point, ray height, etc.
     */
    public LosResult computeLosDetailedWithIntersection(Point3D userPoint, Building building, Satellite satellite) {
        List<Point3D> vertices = building.getVertices();
        
        // Iterate through all walls of the building
        for (int i = 0; i < vertices.size() - 1; i++) {
            Point3D p1 = vertices.get(i);
            Point3D p2 = vertices.get(i + 1);
            
            // Calculate direction vector to satellite
            double azimuthRad = Math.toRadians(satellite.getAzimuth());
            double elevationRad = Math.toRadians(satellite.getElevation());
            
            // Unit vector in direction of satellite
            double dx = Math.sin(azimuthRad) * Math.cos(elevationRad);
            double dy = Math.cos(azimuthRad) * Math.cos(elevationRad);
            double dz = Math.sin(elevationRad);
            
            // Calculate intersection with vertical plane of the wall
            double wallDx = p2.getX() - p1.getX();
            double wallDy = p2.getY() - p1.getY();
            
            // Calculate cross product of wall vector with direction vector
            double normalX = -wallDy;  // Vector perpendicular to wall
            double normalY = wallDx;
            
            // Scalar product of direction vector with normal
            double dot = dx * normalX + dy * normalY;
            
            if (Math.abs(dot) > 1e-10) {  // If ray is not parallel to wall
                // Calculate parameter t of intersection point
                double t = ((p1.getX() - userPoint.getX()) * normalX + 
                          (p1.getY() - userPoint.getY()) * normalY) / dot;
                
                if (t > 0) {  // Intersection in positive direction
                    // Intersection point
                    double intersectX = userPoint.getX() + t * dx;
                    double intersectY = userPoint.getY() + t * dy;
                    
                    // Check if intersection point is on the wall
                    double wallLen = Math.sqrt(wallDx * wallDx + wallDy * wallDy);
                    double s = ((intersectX - p1.getX()) * wallDx + 
                              (intersectY - p1.getY()) * wallDy) / (wallLen * wallLen);
                    
                    if (s >= 0 && s <= 1) {  // Intersection on the wall
                        // Calculate ray height at intersection point (now using proper UTM coordinates in meters)
                        double horizontalDistance = t * Math.sqrt(dx * dx + dy * dy);
                        double heightGain = horizontalDistance * Math.tan(elevationRad);
                        double rayHeightAtIntersection = userPoint.getZ() + heightGain;
                        
                        // Check if ray is blocked by the building
                        if (rayHeightAtIntersection < building.getHeight()) {
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
     * Set the classification mode
     */
    public void setClassificationMode(ClassificationMode mode) {
        this.classificationMode = mode;
        System.out.println("LOS/NLOS Classification mode set to: " + mode);
    }
    
    /**
     * Set the misclassification error percentage for Fig 19 analysis
     * @param percentage Error percentage (0.0-100.0)
     */
    public void setMisclassificationErrorPercentage(double percentage) {
        this.misclassificationErrorPercentage = percentage;
        if (percentage > 0.0) {
            System.out.println("LOS/NLOS Misclassification error set to: " + percentage + "%");
        } else {
            System.out.println("LOS/NLOS Misclassification error disabled");
        }
    }
    
    /**
     * Get the current misclassification error percentage
     */
    public double getMisclassificationErrorPercentage() {
        return misclassificationErrorPercentage;
    }
    
    /**
     * Get current classification mode
     */
    public ClassificationMode getClassificationMode() {
        return classificationMode;
    }
    
    /**
     * Simulate realistic signal strength degradation for urban environment
     * This creates mixed LOS/NLOS scenarios as shown in the article
     */
    public void simulateUrbanSignalDegradation() {
        System.out.println("\n=== Applying Urban Signal Degradation ===");
        int degradedCount = 0;
        
        for (Satellite satellite : satellites) {
            // Randomly degrade some satellites to create realistic NLOS scenarios
            // Article shows ~40-60% satellites can be NLOS in dense urban areas
            if (Math.random() < 0.4) { // 40% chance of degradation
                double originalCn = satellite.getCnRatio();
                satellite.applyNlosSignalDegradation();
                degradedCount++;
                System.out.printf("%s: C/N0 %.1f -> %.1f dB-Hz (NLOS)%n", 
                    satellite.getName(), originalCn, satellite.getCnRatio());
            }
        }
        
        System.out.println("Applied signal degradation to " + degradedCount + "/" + satellites.size() + " satellites");
        System.out.println("=== Urban Signal Degradation Complete ===\n");
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
     * Initialize Ray-Shooting optimization
     * Pre-computes spatial indexing for buildings
     */
    private void initializeRayShooting() {
        if (!rayShooting || buildings == null || buildings.isEmpty()) {
            return;
        }
        
        System.out.println("Initializing Ray-Shooting optimization for " + buildings.size() + " buildings...");
        
        // Create a copy of buildings for spatial indexing
        sortedBuildingsByDistance = new ArrayList<>(buildings);
        
        System.out.println("Ray-Shooting optimization initialized.");
    }
    
    /**
     * Get buildings sorted by relevance to the satellite ray
     * This implements the article's spatial indexing approach
     */
    private List<Building> getRelevantBuildings(Point3D pos, Satellite satellite) {
        if (sortedBuildingsByDistance == null) {
            return buildings;
        }
        
        // Calculate ray direction vector
        double azimuthRad = Math.toRadians(satellite.getAzimuth());
        double elevationRad = Math.toRadians(satellite.getElevation());
        
        double dx = Math.sin(azimuthRad) * Math.cos(elevationRad);
        double dy = Math.cos(azimuthRad) * Math.cos(elevationRad);
        
        // Sort buildings by their relevance to the ray
        List<Building> candidates = new ArrayList<>(sortedBuildingsByDistance);
        candidates.sort((b1, b2) -> {
            double score1 = calculateBuildingRelevanceScore(pos, b1, dx, dy);
            double score2 = calculateBuildingRelevanceScore(pos, b2, dx, dy);
            return Double.compare(score2, score1); // Higher score first
        });
        
        return candidates;
    }
    
    /**
     * Calculate relevance score for a building based on ray direction
     * Higher score = more likely to intersect the ray
     */
    private double calculateBuildingRelevanceScore(Point3D pos, Building building, double rayDx, double rayDy) {
        if (building.getVertices() == null || building.getVertices().isEmpty()) {
            return 0.0;
        }
        
        // Calculate building center
        double centerX = 0, centerY = 0;
        for (Point3D vertex : building.getVertices()) {
            centerX += vertex.getX();
            centerY += vertex.getY();
        }
        centerX /= building.getVertices().size();
        centerY /= building.getVertices().size();
        
        // Vector from position to building center
        double toBuildingX = centerX - pos.getX();
        double toBuildingY = centerY - pos.getY();
        
        // Calculate distance
        double distance = Math.sqrt(toBuildingX * toBuildingX + toBuildingY * toBuildingY);
        if (distance < 1e-10) {
            return Double.MAX_VALUE; // Very close building
        }
        
        // Normalize vector to building
        toBuildingX /= distance;
        toBuildingY /= distance;
        
        // Calculate dot product with ray direction
        double alignment = rayDx * toBuildingX + rayDy * toBuildingY;
        
        // Score combines alignment with ray direction and inverse distance
        // Buildings closer to the ray path and closer to position get higher scores
        double alignmentScore = Math.max(0, alignment); // Only forward direction
        double distanceScore = 1.0 / (1.0 + distance); // Inverse distance (distance already in meters from UTM)
        double heightScore = Math.log(1.0 + building.getHeight() / 50.0); // Taller buildings more likely to block
        
        return alignmentScore * distanceScore * heightScore;
    }
    
    /**
     * Enable/disable Ray-Shooting optimization
     */
    public void setRayShootingOptimization(boolean enabled) {
        this.rayShooting = enabled;
        if (enabled) {
            initializeRayShooting();
        }
        System.out.println("Ray-Shooting optimization " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if Ray-Shooting optimization is enabled
     */
    public boolean isRayShootingEnabled() {
        return rayShooting;
    }

    /**
     * Class representing the LOS calculation result
     */
    public static class LosResult {
        private final boolean isLos;           // Whether there is a direct line of sight
        private final double deltaH;           // Height difference in case of NLOS
        private final Point2D intersection;    // Intersection point with wall (if any)
        private final double rayHeight;        // Ray height at intersection point

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
