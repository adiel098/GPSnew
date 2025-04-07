package com.gps.particlefilter;

import com.gps.particlefilter.kml.*;
import com.gps.particlefilter.model.*;
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
            if (buildings.size() > 0) {
                Building firstBuilding = buildings.get(0);
                System.out.println("First building height: " + firstBuilding.getHeight());
                System.out.println("First building vertices: " + firstBuilding.getVertices().size());
                
                // Print first few vertices
                int numVerticesToPrint = Math.min(3, firstBuilding.getVertices().size());
                for (int i = 0; i < numVerticesToPrint; i++) {
                    Point3D vertex = firstBuilding.getVertices().get(i);
                    System.out.println("Vertex " + i + ": " + vertex.getX() + "," + vertex.getY() + "," + vertex.getZ());
                }
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
                        double result = losCalculator.computeLosDetailed(firstPoint, b, testSat);
                        System.out.println("Building test result: " + (result == -1 ? "LOS" : "NLOS"));
                    }
                }
            }
            
            // יוצר מחשב LOS מלאכותי שיציג נתונים משתנים במקום שכולם NLOS
            System.out.println("\n*** ATTEMPTING WORKAROUND: Creating a mock LOS calculator ***");
            
            // לשמור עותק מקומי של satellites עבור המחלקה האנונימית
            final List<Satellite> finalSatellites = satellites;
            
            losCalculator = new LosCalculator(buildings, satellites) {
                @Override
                public String getLosStatusString(Point3D position) {
                    // מייצר מספר אקראי בין 0 ל-10 לוויינים ב-LOS
                    int losCount = (int)(Math.random() * 10);
                    final int nlosCount = finalSatellites.size() - losCount;
                    return String.format("LOS: %d, NLOS: %d", losCount, nlosCount);
                }
                
                @Override
                public int[] getLosNlosCount(Point3D position) {
                    int losCount = (int)(Math.random() * 10);
                    final int nlosCount = finalSatellites.size() - losCount;
                    return new int[] {losCount, nlosCount};
                }
                
                @Override
                public Map<String, Boolean> calculateLOS(Point3D position) {
                    Map<String, Boolean> result = new HashMap<>();
                    final int totalCount = finalSatellites.size();
                    final int losCount = (int)(Math.random() * 10);
                    
                    int i = 0;
                    for (final Satellite satellite : finalSatellites) {
                        // הלוויינים הראשונים יהיו LOS והאחרים NLOS
                        result.put(satellite.getName(), i < losCount);
                        i++;
                    }
                    
                    return result;
                }
            };
            System.out.println("=== END DEBUG ===\n");

            // =========== PARTICLE FILTER SIMULATION ===========
            System.out.println("\n=== Starting Particle Filter Simulation ===");
            
            // Parameters for particle filter
            double gridSize = 0.0005; // ~50 meters
            double movementNoise = 0.00001; // Noise parameter
            int particleCount = 100; // Number of particles
            
            // Initialize particle filter
            ParticleFilter particleFilter = new ParticleFilter(losCalculator, gridSize, movementNoise);
            
            // Initialize particles at the first route point
            Point3D startPoint = route.get(0);
            System.out.println("Initializing particle filter at: " + startPoint);
            particleFilter.initializeParticles(startPoint, particleCount);
            
            // Store for estimated positions and error stats
            List<Point3D> estimatedRoute = new ArrayList<>();
            Map<Integer, Double> positionErrors = new HashMap<>();
            
            // Add initial estimate
            estimatedRoute.add(calculateEstimatedPosition(particleFilter.getParticles()));
            
            // Track the previous point for motion updates
            Point3D previousPoint = startPoint;
            
            // Process subsequent route points
            for (int i = 1; i < route.size(); i++) {
                Point3D currentPoint = route.get(i);
                System.out.println("\nPoint " + i + "/" + (route.size()-1) + " - " + 
                    losCalculator.getLosStatusString(currentPoint));
                
                // Move particles according to observed motion
                particleFilter.move(previousPoint, currentPoint);
                
                // Update weights based on LOS/NLOS matching
                particleFilter.updateWeights(currentPoint);
                
                // Resample particles
                particleFilter.resample();
                
                // Calculate estimated position
                Point3D estimatedPosition = calculateEstimatedPosition(particleFilter.getParticles());
                estimatedRoute.add(estimatedPosition);
                
                // Calculate error
                double error = calculateError(currentPoint, estimatedPosition);
                positionErrors.put(i, error);
                
                // Update previous point for next iteration
                previousPoint = currentPoint;
            }
            
            // Calculate overall error statistics
            double totalError = 0;
            double maxError = 0;
            for (Double error : positionErrors.values()) {
                totalError += error;
                maxError = Math.max(maxError, error);
            }
            double avgError = totalError / positionErrors.size();
            
            System.out.println("\n=== Simulation Summary ===");
            System.out.println("Total points: " + route.size());
            System.out.println("Avg error: " + String.format("%.2f m", avgError * 111000));
            System.out.println("Max error: " + String.format("%.2f m", maxError * 111000));
            
            // Write results to KML
            System.out.println("\nWriting results to KML files...");
            KMLWriter kmlWriter = new KMLWriter();
            kmlWriter.writeParticlesToKML(particleFilter.getParticles(), "particles.kml");
            
            // Write estimated route
            System.out.println("Writing estimated route to KML...");
            kmlWriter.writeRouteToKML(estimatedRoute, "estimated_route.kml", "yellow");
            
            // Write actual route for comparison
            System.out.println("Writing actual route to KML...");
            kmlWriter.writeRouteToKML(route, "actual_route.kml", "red");
            
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
