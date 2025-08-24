package com.gps.particlefilter;

import com.gps.particlefilter.io.*;
import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import com.gps.particlefilter.util.ChartDataCollector;
import com.gps.particlefilter.los.LosCalculator;
import com.gps.particlefilter.config.Configuration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;

public class ParticleFilterBatchRunner {
    
    public static void main(String[] args) {
        if (args.length > 0 && "convergence".equals(args[0])) {
            runConvergenceAnalysis();
        } else if (args.length > 0 && "naive-bayesian".equals(args[0])) {
            runNaiveBayesianComparison();
        } else if (args.length > 0 && "los-nlos".equals(args[0])) {
            runLosNlosAnalysis();
        } else {
            System.out.println("Usage: java ParticleFilterBatchRunner [convergence|naive-bayesian|los-nlos]");
            System.out.println("  convergence - Run particle filter with different particle counts for Fig 14");
            System.out.println("  naive-bayesian - Compare naive vs Bayesian weight functions for Fig 15");
            System.out.println("  los-nlos - Analyze impact of LOS/NLOS misclassification errors for Fig 19");
        }
    }
    
    public static void runConvergenceAnalysis() {
        System.out.println("=== Running Convergence Analysis for Fig 14 ===");
        
        int[] particleCounts = {100, 500, 1000, 2500};
        String outputDir = "charts" + File.separator + "data";
        String outputFile = outputDir + File.separator + "convergence_data.csv";
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        // Load configuration
        Configuration config = Configuration.getInstance();
        
        try {
            // Initialize coordinate system manager for UTM conversions
            CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
            coordManager.setDefaultUtmZone(36, true);
            coordManager.setUseUtm(true);
            
            // Initialize KML readers
            BuildingKMLReader buildingReader = new BuildingKMLReader();
            SatelliteKMLReader satelliteReader = new SatelliteKMLReader();
            RouteKMLReader routeReader = new RouteKMLReader();

            List<Building> buildings = new ArrayList<>();
            List<Satellite> satellites = new ArrayList<>();
            List<Point3D> route = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            
            // Read input files
            buildings = buildingReader.readBuildings(config.getInputBuildingsKml());
            satellites = satelliteReader.readSatellites(config.getInputSatellitesKml());
            route = routeReader.readRoute(config.getInputRouteKml());
            timestamps = routeReader.readTimestamps(config.getInputRouteKml());
            
            System.out.println("Loaded: " + buildings.size() + " buildings, " + 
                              satellites.size() + " satellites, " + 
                              route.size() + " route points");
            
            // Initialize LOS calculator
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            losCalculator.simulateUrbanSignalDegradation();
            losCalculator.setClassificationMode(LosCalculator.ClassificationMode.HYBRID);
            losCalculator.setRayShootingOptimization(true);
            
            // Initialize first data collector for header
            boolean firstRun = true;
            
            // Run simulation for each particle count
            for (int particleCount : particleCounts) {
                System.out.println("\n=== Running simulation with " + particleCount + " particles ===");
                
                // Initialize data collector
                ChartDataCollector dataCollector = new ChartDataCollector(particleCount, outputFile);
                
                // Initialize particle filter
                double gridSize = 25.0;
                double movementNoise = config.getParticleMeasurementNoise();
                
                ParticleFilter particleFilter = new ParticleFilter(
                    new LosCalculator(buildings, satellites), gridSize, movementNoise);
                
                // Initialize particles with first point
                Point3D startPoint = route.get(0);
                particleFilter.initializeParticles(startPoint, particleCount);
                
                // Collect initial error data
                dataCollector.collectErrorData(0, startPoint, particleFilter.getParticles(), timestamps.get(0));
                
                // Process each point in the route
                for (int i = 1; i < route.size(); i++) {
                    Point3D currentPoint = route.get(i);
                    long timestamp = timestamps.get(i);
                    
                    // Update particle filter
                    particleFilter.update(currentPoint, timestamp);
                    
                    // Collect error data
                    dataCollector.collectErrorData(i, currentPoint, particleFilter.getParticles(), timestamp);
                    
                    // Print progress every 50 points
                    if (i % 50 == 0) {
                        System.out.printf("  Processed %d/%d points (%.1f%%)%n", 
                            i, route.size(), (100.0 * i) / route.size());
                    }
                }
                
                // Export data to CSV
                if (firstRun) {
                    dataCollector.exportToCSVWithHeader();
                    firstRun = false;
                } else {
                    dataCollector.exportToCSV();
                }
                
                // Print summary statistics
                System.out.printf("Completed %d particles: Avg Error = %.2f m, Max Error = %.2f m%n",
                    particleCount, dataCollector.getAverageError(), dataCollector.getMaxError());
            }
            
            System.out.println("\n=== Convergence Analysis Complete ===");
            System.out.println("Data exported to: " + outputFile);
            System.out.println("Ready to generate Fig 14 chart!");
            
        } catch (Exception e) {
            System.err.println("Error during convergence analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void runNaiveBayesianComparison() {
        System.out.println("=== Running Naive vs Bayesian Weight Function Comparison for Fig 15 ===");
        
        int[] particleCounts = {100, 1000};
        boolean[] bayesianSettings = {false, true}; // false = naive, true = Bayesian
        String[] weightTypes = {"naive", "bayesian"};
        
        String outputDir = "charts" + File.separator + "data";
        String outputFile = outputDir + File.separator + "naive_bayesian_data.csv";
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        // Load configuration
        Configuration config = Configuration.getInstance();
        
        try {
            // Initialize coordinate system manager for UTM conversions
            CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
            coordManager.setDefaultUtmZone(36, true);
            coordManager.setUseUtm(true);
            
            // Initialize KML readers
            BuildingKMLReader buildingReader = new BuildingKMLReader();
            SatelliteKMLReader satelliteReader = new SatelliteKMLReader();
            RouteKMLReader routeReader = new RouteKMLReader();

            List<Building> buildings = new ArrayList<>();
            List<Satellite> satellites = new ArrayList<>();
            List<Point3D> route = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            
            // Read input files
            buildings = buildingReader.readBuildings(config.getInputBuildingsKml());
            satellites = satelliteReader.readSatellites(config.getInputSatellitesKml());
            route = routeReader.readRoute(config.getInputRouteKml());
            timestamps = routeReader.readTimestamps(config.getInputRouteKml());
            
            System.out.println("Loaded: " + buildings.size() + " buildings, " + 
                              satellites.size() + " satellites, " + 
                              route.size() + " route points");
            
            // Initialize LOS calculator
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            losCalculator.simulateUrbanSignalDegradation();
            losCalculator.setClassificationMode(LosCalculator.ClassificationMode.HYBRID);
            losCalculator.setRayShootingOptimization(true);
            
            boolean firstRun = true;
            
            // Run simulation for each particle count and weight function combination
            for (int particleCount : particleCounts) {
                for (int i = 0; i < bayesianSettings.length; i++) {
                    boolean useBayesian = bayesianSettings[i];
                    String weightType = weightTypes[i];
                    
                    System.out.println("\n=== Running simulation with " + particleCount + 
                                     " particles (" + weightType + " weight function) ===");
                    
                    // Initialize data collector with weight type
                    ChartDataCollector dataCollector = new ChartDataCollector(
                        particleCount, outputFile, weightType);
                    
                    // Initialize particle filter
                    double gridSize = 25.0;
                    double movementNoise = config.getParticleMeasurementNoise();
                    
                    ParticleFilter particleFilter = new ParticleFilter(
                        new LosCalculator(buildings, satellites), gridSize, movementNoise);
                    
                    // Set the weight function type
                    particleFilter.setUseBayesianWeight(useBayesian);
                    
                    // Initialize particles with first point
                    Point3D startPoint = route.get(0);
                    particleFilter.initializeParticles(startPoint, particleCount);
                    
                    // Collect initial error data
                    dataCollector.collectErrorData(0, startPoint, particleFilter.getParticles(), timestamps.get(0));
                    
                    // Process each point in the route
                    for (int j = 1; j < route.size(); j++) {
                        Point3D currentPoint = route.get(j);
                        long timestamp = timestamps.get(j);
                        
                        // Update particle filter
                        particleFilter.update(currentPoint, timestamp);
                        
                        // Collect error data
                        dataCollector.collectErrorData(j, currentPoint, particleFilter.getParticles(), timestamp);
                        
                        // Print progress every 50 points
                        if (j % 50 == 0) {
                            System.out.printf("  Processed %d/%d points (%.1f%%)%n", 
                                j, route.size(), (100.0 * j) / route.size());
                        }
                    }
                    
                    // Export data to CSV
                    if (firstRun) {
                        dataCollector.exportToCSVWithHeader();
                        firstRun = false;
                    } else {
                        dataCollector.exportToCSV();
                    }
                    
                    // Print summary statistics
                    System.out.printf("Completed %d particles (%s): Avg Error = %.2f m, Max Error = %.2f m%n",
                        particleCount, weightType, dataCollector.getAverageError(), dataCollector.getMaxError());
                }
            }
            
            System.out.println("\n=== Naive vs Bayesian Comparison Complete ===");
            System.out.println("Data exported to: " + outputFile);
            System.out.println("Ready to generate Fig 15 chart!");
            
        } catch (Exception e) {
            System.err.println("Error during naive vs Bayesian comparison: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void runLosNlosAnalysis() {
        System.out.println("=== Running LOS/NLOS Misclassification Analysis for Fig 19 ===");
        
        int particleCount = 2500;  // As shown in Fig 19
        double[] misclassificationPercentages = {0.0, 10.0, 20.0, 45.0}; // p values from Fig 19
        
        String outputDir = "charts" + File.separator + "data";
        String outputFile = outputDir + File.separator + "los_nlos_misclassification_data.csv";
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        // Load configuration
        Configuration config = Configuration.getInstance();
        
        try {
            // Initialize coordinate system manager for UTM conversions
            CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
            coordManager.setDefaultUtmZone(36, true);
            coordManager.setUseUtm(true);
            
            // Initialize KML readers
            BuildingKMLReader buildingReader = new BuildingKMLReader();
            SatelliteKMLReader satelliteReader = new SatelliteKMLReader();
            RouteKMLReader routeReader = new RouteKMLReader();

            List<Building> buildings = new ArrayList<>();
            List<Satellite> satellites = new ArrayList<>();
            List<Point3D> route = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            
            // Read input files
            buildings = buildingReader.readBuildings(config.getInputBuildingsKml());
            satellites = satelliteReader.readSatellites(config.getInputSatellitesKml());
            route = routeReader.readRoute(config.getInputRouteKml());
            timestamps = routeReader.readTimestamps(config.getInputRouteKml());
            
            System.out.println("Loaded: " + buildings.size() + " buildings, " + 
                              satellites.size() + " satellites, " + 
                              route.size() + " route points");
            
            boolean firstRun = true;
            
            // Run simulation for each misclassification percentage
            for (double misclassificationPercentage : misclassificationPercentages) {
                System.out.println("\n=== Running simulation with " + particleCount + 
                                 " particles (p=" + misclassificationPercentage + "% misclassification) ===");
                
                // Initialize data collector with misclassification percentage
                ChartDataCollector dataCollector = new ChartDataCollector(
                    particleCount, outputFile, misclassificationPercentage);
                
                // Initialize LOS calculator with misclassification error
                LosCalculator losCalculator = new LosCalculator(buildings, satellites);
                losCalculator.simulateUrbanSignalDegradation();
                losCalculator.setClassificationMode(LosCalculator.ClassificationMode.HYBRID);
                losCalculator.setRayShootingOptimization(true);
                losCalculator.setMisclassificationErrorPercentage(misclassificationPercentage);
                
                // Initialize particle filter
                double gridSize = 25.0;
                double movementNoise = config.getParticleMeasurementNoise();
                
                ParticleFilter particleFilter = new ParticleFilter(
                    losCalculator, gridSize, movementNoise);
                
                // Initialize particles with first point
                Point3D startPoint = route.get(0);
                particleFilter.initializeParticles(startPoint, particleCount);
                
                // Collect initial error data
                dataCollector.collectErrorData(0, startPoint, particleFilter.getParticles(), timestamps.get(0));
                
                // Process each point in the route
                for (int i = 1; i < route.size(); i++) {
                    Point3D currentPoint = route.get(i);
                    long timestamp = timestamps.get(i);
                    
                    // Update particle filter
                    particleFilter.update(currentPoint, timestamp);
                    
                    // Collect error data
                    dataCollector.collectErrorData(i, currentPoint, particleFilter.getParticles(), timestamp);
                    
                    // Print progress every 50 points
                    if (i % 50 == 0) {
                        System.out.printf("  Processed %d/%d points (%.1f%%)%n", 
                            i, route.size(), (100.0 * i) / route.size());
                    }
                }
                
                // Export data to CSV
                if (firstRun) {
                    dataCollector.exportToCSVWithHeader();
                    firstRun = false;
                } else {
                    dataCollector.exportToCSV();
                }
                
                // Print summary statistics
                System.out.printf("Completed p=%.1f%%: Avg Error = %.2f m, Max Error = %.2f m%n",
                    misclassificationPercentage, dataCollector.getAverageError(), dataCollector.getMaxError());
            }
            
            System.out.println("\n=== LOS/NLOS Misclassification Analysis Complete ===");
            System.out.println("Data exported to: " + outputFile);
            System.out.println("Ready to generate Fig 19 chart!");
            
        } catch (Exception e) {
            System.err.println("Error during LOS/NLOS misclassification analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
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
}