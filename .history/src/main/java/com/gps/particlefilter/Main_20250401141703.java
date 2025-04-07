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
                System.out.println("\nProcessing point " + i + ": " + currentPoint);
                
                // Move particles according to observed motion
                System.out.println("Moving particles...");
                particleFilter.move(previousPoint, currentPoint);
                
                // Update weights based on LOS/NLOS matching
                System.out.println("Updating particle weights...");
                particleFilter.updateWeights(currentPoint);
                
                // Resample particles
                System.out.println("Resampling particles...");
                particleFilter.resample();
                
                // Calculate estimated position
                Point3D estimatedPosition = calculateEstimatedPosition(particleFilter.getParticles());
                estimatedRoute.add(estimatedPosition);
                
                // Calculate error
                double error = calculateError(currentPoint, estimatedPosition);
                positionErrors.put(i, error);
                System.out.println("Estimated position: " + estimatedPosition);
                System.out.println("True position: " + currentPoint);
                System.out.println("Error: " + String.format("%.2f meters", error * 111000)); // Rough conversion to meters
                
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
            
            System.out.println("\n=== Particle Filter Simulation Summary ===");
            System.out.println("Total points processed: " + route.size());
            System.out.println("Average error: " + String.format("%.2f meters", avgError * 111000)); // Rough conversion to meters
            System.out.println("Maximum error: " + String.format("%.2f meters", maxError * 111000)); // Rough conversion to meters
            
            // Write results to KML
            System.out.println("\nWriting results to KML...");
            KMLWriter kmlWriter = new KMLWriter();
            kmlWriter.writeParticlesToKML(particleFilter.getParticles(), "particles.kml");
            
            // Write estimated route
            System.out.println("Writing estimated route to KML...");
            kmlWriter.writeRouteToKML(estimatedRoute, "estimated_route.kml", "blue");
            
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
