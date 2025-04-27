package com.gps.particlefilter;

import com.gps.particlefilter.kml.*;
import com.gps.particlefilter.model.*;
import com.gps.particlefilter.LosCalculator.LosResult;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting GPS Particle Filter application...");
        System.out.println("Working directory: " + System.getProperty("user.dir"));
        
        try {
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
            
            // Read input files
            try {
                System.out.println("Reading building3d.kml...");
                buildings = buildingReader.readBuildings("building3d.kml");
                System.out.println("Found " + buildings.size() + " buildings.");
            } catch (Exception e) {
                System.err.println("Error reading building3d.kml: " + e.getMessage());
                success = false;
            }
            
            try {
                System.out.println("Reading satellites.kml...");
                satellites = satelliteReader.readSatellites("satellites.kml");
                System.out.println("Found " + satellites.size() + " satellites.");
            } catch (Exception e) {
                System.err.println("Error reading satellites.kml: " + e.getMessage());
                success = false;
            }
            
            try {
                System.out.println("Reading original_route.kml...");
                route = routeReader.readRoute("original_route.kml");
                timestamps = routeReader.readTimestamps("original_route.kml");
                System.out.println("Found " + route.size() + " route points.");
            } catch (Exception e) {
                System.err.println("Error reading original_route.kml: " + e.getMessage());
                success = false;
            }

            try {
                // Validate KML files and generate report
                System.out.println("Validating KML files and generating report...");
                KMLValidator.validateAndGenerateReport("building3d.kml", "satellites.kml", "original_route.kml");
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
            
            // DEBUG: Print buildings and satellites information
            System.out.println("\n=== DEBUG INFO ===");
            System.out.println("Buildings count: " + buildings.size());
            
            // הדפסת מידע על כל הבניינים
            for (int i = 0; i < buildings.size(); i++) {
                Building building = buildings.get(i);
                System.out.println("\nBuilding " + i + ":");
                System.out.println("Height: " + building.getHeight() + " meters");
                System.out.println("Number of vertices: " + building.getVertices().size());
                
                // חישוב גבולות הבניין
                double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
                double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
                
                // הדפסת כל הנקודות של הבניין
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
                
                // הדפסת גבולות הבניין
                System.out.println("Building bounds:");
                System.out.printf("  Latitude: %.6f to %.6f\n", minLat, maxLat);
                System.out.printf("  Longitude: %.6f to %.6f\n", minLon, maxLon);
            }
            
            System.out.println("Satellites count: " + satellites.size());
            if (satellites.size() > 0) {
                Satellite firstSat = satellites.get(0);
                System.out.println("First satellite: " + firstSat.getName() + 
                                  ", Elevation: " + firstSat.getElevation() + 
                                  ", Azimuth: " + firstSat.getAzimuth());
            }
            
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
            
            // Parameters for particle filter
            double gridSize = 0.0005;     // ~50 meters
            double movementNoise = 0.0001; // Increased noise parameter for better exploration
            int particleCount = 20;      // Increased number of particles
            
            // Initialize particle filter with the real LOS calculator
            ParticleFilter particleFilter = new ParticleFilter(new LosCalculator(buildings, satellites), gridSize, movementNoise);
            
            // Initialize particle filter with the first point
            Point3D startPoint = route.get(0);
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
                
                // Save current state to history before calculating estimated position
                particleFilter.getParticleHistory().add(new ArrayList<>(particleFilter.getParticles()));
                particleFilter.getTimestamps().add(timestamp);
                
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
                        i + 1, route.size(), error * 111000);
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
            System.out.println("Avg error: " + String.format("%.2f m", avgError * 111000));
            System.out.println("Max error: " + String.format("%.2f m", maxError * 111000));
            
            // Write results to KML
            System.out.println("\nWriting results to KML files...");
            KMLWriter kmlWriter = new KMLWriter();
            
            // Debug info
            System.out.println("Particle history size: " + particleFilter.getParticleHistory().size());
            System.out.println("Timestamps size: " + particleFilter.getTimestamps().size());
            
            kmlWriter.writeParticleHistoryToKML(particleFilter.getParticleHistory(), particleFilter.getTimestamps(), "particles.kml");
            
            // Write estimated route with timestamps
            System.out.println("Writing estimated route to KML...");
            kmlWriter.writeRouteToKML(estimatedRoute, timestamps, "estimated_route.kml", false);
            
            // Write actual route
            System.out.println("Writing actual route to KML...");
            kmlWriter.writeRouteToKML(route, timestamps, "actual_route.kml", true);
            
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
        // Simple Euclidean distance in coordinate space
        // (since we're working in a small area, this approximation is reasonable)
        double latDiff = truePosition.getY() - estimatedPosition.getY();
        double lonDiff = truePosition.getX() - estimatedPosition.getX();
        double altDiff = truePosition.getZ() - estimatedPosition.getZ();
        
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }
}
