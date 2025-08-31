package com.gps.particlefilter;

import com.gps.particlefilter.io.*;
import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import com.gps.particlefilter.los.LosCalculator;
import com.gps.particlefilter.los.LosCalculator.LosResult;
import com.gps.particlefilter.config.Configuration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting GPS Particle Filter application...");
        System.out.println("Working directory: " + System.getProperty("user.dir"));
        
        // Load configuration
        Configuration config = Configuration.getInstance();
        config.printConfiguration();
        
        try {
            // Initialize coordinate system manager for UTM conversions
            System.out.println("Initializing coordinate system for UTM...");
            CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
            coordManager.setDefaultUtmZone(36, true); // Zone 36 North for Israel
            coordManager.setUseUtm(true);
            System.out.println("UTM Zone 36N enabled for consistent meter-based calculations");
            
            // Initialize KML readers
            System.out.println("Initializing KML readers...");
            BuildingKMLReader buildingReader = new BuildingKMLReader();
            SatelliteKMLReader satelliteReader = new SatelliteKMLReader();
            RouteKMLReader routeReader = new RouteKMLReader();

            List<Building> buildings = new ArrayList<>();
            List<Satellite> satellites = new ArrayList<>();
            List<Point3D> route = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            
            boolean success = true;
            
            // Read input files using configuration paths
            try {
                String buildingFile = config.getInputBuildingsKml();
                System.out.println("Reading " + buildingFile + "...");
                buildings = buildingReader.readBuildings(buildingFile);
                System.out.println("Found " + buildings.size() + " buildings.");
            } catch (Exception e) {
                System.err.println("Error reading buildings file: " + e.getMessage());
                success = false;
            }
            
            try {
                String satelliteFile = config.getInputSatellitesKml();
                System.out.println("Reading " + satelliteFile + "...");
                satellites = satelliteReader.readSatellites(satelliteFile);
                System.out.println("Found " + satellites.size() + " satellites.");
            } catch (Exception e) {
                System.err.println("Error reading satellites file: " + e.getMessage());
                success = false;
            }
            
            try {
                String routeFile = config.getInputRouteKml();
                System.out.println("Reading " + routeFile + "...");
                route = routeReader.readRoute(routeFile);
                timestamps = routeReader.readTimestamps(routeFile);
                System.out.println("Found " + route.size() + " route points.");
            } catch (Exception e) {
                System.err.println("Error reading route file: " + e.getMessage());
                success = false;
            }

            try {
                // Validate KML files and generate report
                System.out.println("Validating KML files and generating report...");
                KMLValidator.validateAndGenerateReport(
                    config.getInputBuildingsKml(), 
                    config.getInputSatellitesKml(), 
                    config.getInputRouteKml()
                );
                System.out.println("Validation complete.");
            } catch (Exception e) {
                System.err.println("Error during validation: " + e.getMessage());
            }

            if (!success || buildings.isEmpty() || satellites.isEmpty() || route.isEmpty()) {
                System.out.println("Error: Failed to read input files");
                System.exit(1);
            }

            // Initialize LOS calculator
            System.out.println("Initializing LOS calculator...");
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            
            // Apply realistic urban signal degradation as per article
            losCalculator.simulateUrbanSignalDegradation();
            
            // Set to hybrid mode (signal + geometric) as per article
            losCalculator.setClassificationMode(LosCalculator.ClassificationMode.HYBRID);
            
            // Enable Ray-Shooting optimization for O(N × k × log(B)) complexity
            System.out.println("=== Enabling Ray-Shooting Optimization ===");
            losCalculator.setRayShootingOptimization(true);
            
            // DEBUG: Print buildings and satellites information
            System.out.println("\n=== DEBUG INFO ===");
            System.out.println("Buildings count: " + buildings.size());
            
            // Print information about all buildings
            for (int i = 0; i < buildings.size(); i++) {
                Building building = buildings.get(i);
                System.out.println("\nBuilding " + i + ":");
                System.out.println("Height: " + building.getHeight() + " meters");
                System.out.println("Number of vertices: " + building.getVertices().size());
                
                // Calculate building bounds
                double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
                double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
                
                // Print all vertices of the building
                List<Point3D> vertices = building.getVertices();
                for (int j = 0; j < vertices.size(); j++) {
                    Point3D vertex = vertices.get(j);
                    System.out.printf("Vertex %d: (%.6f, %.6f)\n", 
                        j, vertex.getX(), vertex.getY());
                        
                    minLat = Math.min(minLat, vertex.getY());
                    maxLat = Math.max(maxLat, vertex.getY());
                    minLon = Math.min(minLon, vertex.getX());
                    maxLon = Math.max(maxLon, vertex.getX());
                }
                
                // Print building bounds
                System.out.println("Building bounds:");
                System.out.printf("  Latitude: %.6f to %.6f\n", minLat, maxLat);
                System.out.printf("  Longitude: %.6f to %.6f\n", minLon, maxLon);
            }
            
            System.out.println("\n=== Satellite Analysis ===");
            System.out.println("Satellites count: " + satellites.size());
            
            // Show first few satellites with C/N0 values
            int totalLosCount = 0, totalNlosCount = 0;
            for (int i = 0; i < Math.min(5, satellites.size()); i++) {
                Satellite sat = satellites.get(i);
                boolean isLos = sat.isLosFromSignalStrength();
                if (isLos) totalLosCount++; else totalNlosCount++;
                
                System.out.printf("Satellite %d: %s, Elev=%.1f°, Az=%.1f°, C/N0=%.1f dB-Hz (%s)%n",
                    i+1, sat.getName(), sat.getElevation(), sat.getAzimuth(), 
                    sat.getCnRatio(), isLos ? "LOS" : "NLOS");
            }
            
            // Count all satellites
            for (int i = 5; i < satellites.size(); i++) {
                if (satellites.get(i).isLosFromSignalStrength()) totalLosCount++; else totalNlosCount++;
            }
            
            System.out.printf("Overall signal classification: LOS=%d, NLOS=%d (%.1f%% NLOS)%n", 
                totalLosCount, totalNlosCount, (100.0 * totalNlosCount) / (totalLosCount + totalNlosCount));
            
            if (route.size() > 0) {
                Point3D firstPoint = route.get(0);
                System.out.println("First route point: X=" + firstPoint.getX() + 
                                  ", Y=" + firstPoint.getY() + 
                                  ", Z=" + firstPoint.getZ());
                
                // Test LOS calculation for first point
                Map<String, Boolean> losStatus = losCalculator.calculateLOS(firstPoint);
                int losCount = 0, nlosCount = 0;
                for (Boolean isLos : losStatus.values()) {
                    if (isLos) losCount++; else nlosCount++;
                }
                System.out.println("First point LOS calc: LOS=" + losCount + ", NLOS=" + nlosCount);
                
                // Manually check some satellites
                if (satellites.size() > 0) {
                    Satellite testSat = satellites.get(0);
                    System.out.println("Testing satellite " + testSat.getName());
                    for (Building b : buildings) {
                        LosResult result = losCalculator.computeLosDetailedWithIntersection(firstPoint, b, testSat);
                        if (!result.isLos()) {
                            System.out.println("NLOS: " + result.getDeltaH() + " meters");
                        } else {
                            System.out.println("LOS");
                        }
                    }
                    System.out.println("Point " + (1+1) + "/" + route.size() + " - LOS count: " + losCount + ", NLOS count: " + nlosCount);
                }
            }
            
            // =========== PARTICLE FILTER SIMULATION ===========
            System.out.println("\n=== Starting Particle Filter Simulation ===");
            
            // Parameters for particle filter (now properly in UTM meters!)
            double gridSize = 15.0;       // 15 meters grid size for better convergence
            double movementNoise = config.getParticleMeasurementNoise();  // From configuration
            int particleCount = config.getParticleCount();  // From configuration
            
            System.out.println("Particle filter parameters:");
            System.out.println("  Particle count: " + particleCount);
            System.out.println("  Movement noise: " + movementNoise + " meters");
            System.out.println("  Grid size: " + gridSize + " meters");
            
            // Initialize particle filter with the real LOS calculator
            ParticleFilter particleFilter = new ParticleFilter(new LosCalculator(buildings, satellites), gridSize, movementNoise);
            
            // Initialize particle filter with the first point
            Point3D startPoint = route.get(0);
            System.out.println("=== MAIN DEBUG: START POINT ===");
            System.out.println("Start point from route: X=" + startPoint.getX() + ", Y=" + startPoint.getY() + ", Z=" + startPoint.getZ());
            System.out.println("Route size: " + route.size() + " points");
            System.out.println("=== END MAIN DEBUG ===\n");
            
            particleFilter.initializeParticles(startPoint, particleCount);
            
            // Add initial state to history with first timestamp
            particleFilter.getParticleHistory().add(new ArrayList<>(particleFilter.getParticles()));
            particleFilter.getTimestamps().add(timestamps.get(0));
            
            // Add initial estimate
            List<Point3D> estimatedRoute = new ArrayList<>();
            estimatedRoute.add(calculateEstimatedPosition(particleFilter.getParticles()));
            
            // Store for error stats
            double totalError = 0;
            double maxError = 0;
            
            // Process each point in the route
            for (int i = 1; i < route.size(); i++) {
                Point3D currentPoint = route.get(i);
                long timestamp = timestamps.get(i);
                
                // Update particle filter with current point
                particleFilter.update(currentPoint, timestamp);
                
                // Calculate estimated position
                Point3D estimatedPosition = calculateEstimatedPosition(particleFilter.getParticles());
                estimatedRoute.add(estimatedPosition);
                
                // Calculate error for this point
                double error = calculateError(currentPoint, estimatedPosition);
                totalError += error;
                maxError = Math.max(maxError, error);
                
                // Print progress
                if ((i + 1) % 10 == 0) {
                    System.out.printf("Processed %d/%d points. Current error: %.2f meters%n", 
                        i + 1, route.size(), error);
                }
                
                // Calculate LOS/NLOS counts for reference point
                Map<String, Boolean> referenceLosStatus = losCalculator.calculateLOS(currentPoint);
                int losCount = 0;
                int nlosCount = 0;
                for (Boolean isLos : referenceLosStatus.values()) {
                    if (isLos) losCount++;
                    else nlosCount++;
                }

                // Print reference point LOS/NLOS status
                System.out.println("\nPoint " + (i+1) + "/" + route.size() + " - Reference point LOS/NLOS: LOS: " + losCount + ", NLOS: " + nlosCount);

                // Print particle information
                for (int j = 0; j < Math.min(10, particleFilter.getParticles().size()); j++) {
                    Particle p = particleFilter.getParticles().get(j);
                    Map<String, Boolean> particleLosStatus = p.getLosStatus();
                    int particleLosCount = 0;
                    int particleNlosCount = 0;
                    for (Boolean isLos : particleLosStatus.values()) {
                        if (isLos) particleLosCount++;
                        else particleNlosCount++;
                    }
                    System.out.printf("Point %d/%d - Particle %d - LOS: %d, NLOS: %d, Matches: %d, Weight: %.4f%n",
                        i+1, route.size(), j, particleLosCount, particleNlosCount,
                        p.matchingLosCount(referenceLosStatus), p.getWeight());
                }
            }
            
            // Calculate overall error statistics
            double avgError = totalError / (route.size() - 1);
            
            System.out.println("\n=== Simulation Summary ===");
            System.out.println("Total points: " + route.size());
            System.out.println("Avg error: " + String.format("%.2f m", avgError));
            System.out.println("Max error: " + String.format("%.2f m", maxError));
            
            // Write results to KML
            System.out.println("\nWriting results to KML files...");
            KMLWriter kmlWriter = new KMLWriter();
            
            // Debug info
            System.out.println("Particle history size: " + particleFilter.getParticleHistory().size());
            System.out.println("Timestamps size: " + particleFilter.getTimestamps().size());
            
            kmlWriter.writeParticleHistoryToKML(particleFilter.getParticleHistory(), particleFilter.getTimestamps(), config.getOutputParticlesKml());
            
            // Write estimated route with timestamps (yellow tacks only, no line)
            System.out.println("Writing estimated route to KML...");
            kmlWriter.writeRouteToKML(estimatedRoute, timestamps, config.getOutputEstimatedRouteKml(), false);
            
            // Write actual route
            System.out.println("Writing actual route to KML...");
            kmlWriter.writeRouteToKML(route, timestamps, config.getOutputActualRouteKml(), true);
            
            System.out.println("\nProcessing complete! KML files generated.");
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Calculate estimated position as weighted average of particle positions
     */
    private static Point3D calculateEstimatedPosition(List<Particle> particles) {
        double weightedX = 0;
        double weightedY = 0;
        double weightedZ = 0;
        double totalWeight = 0;
        
        for (Particle particle : particles) {
            double weight = particle.getWeight();
            Point3D position = particle.getPosition();
            
            weightedX += position.getX() * weight;
            weightedY += position.getY() * weight;
            weightedZ += position.getZ() * weight;
            totalWeight += weight;
        }
        
        // If all weights are zero, use simple average
        if (totalWeight == 0) {
            double sumX = 0, sumY = 0, sumZ = 0;
            for (Particle particle : particles) {
                Point3D position = particle.getPosition();
                sumX += position.getX();
                sumY += position.getY();
                sumZ += position.getZ();
            }
            return new Point3D(sumX / particles.size(), sumY / particles.size(), sumZ / particles.size());
        }
        
        return new Point3D(
            weightedX / totalWeight, 
            weightedY / totalWeight, 
            weightedZ / totalWeight
        );
    }
    
    /**
     * Calculate error between true position and estimated position
     * (simplified Haversine distance)
     */
    private static double calculateError(Point3D truePosition, Point3D estimatedPosition) {
        // Calculate Euclidean distance between positions
        // If coordinates are in UTM (meters), result is in meters
        // If coordinates are in geographic (degrees), result needs conversion to meters
        double xDiff = truePosition.getX() - estimatedPosition.getX();
        double yDiff = truePosition.getY() - estimatedPosition.getY();
        
        double distance = Math.sqrt(xDiff * xDiff + yDiff * yDiff);
        
        // Check if we're working with geographic coordinates (small decimal values)
        // If coordinates appear to be in degrees, convert to approximate meters
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        if (!coordManager.isUsingUtm()) {
            // Convert degrees to meters using approximate conversion
            // 1 degree lat ≈ 111,319 meters, 1 degree lon ≈ 111,319 * cos(lat) meters
            double avgLat = Math.toRadians((truePosition.getY() + estimatedPosition.getY()) / 2.0);
            double latMetersPerDegree = 111319.0;
            double lonMetersPerDegree = 111319.0 * Math.cos(avgLat);
            
            distance = Math.sqrt(
                Math.pow(xDiff * lonMetersPerDegree, 2) + 
                Math.pow(yDiff * latMetersPerDegree, 2)
            );
        }
        
        return distance;
    }
}
