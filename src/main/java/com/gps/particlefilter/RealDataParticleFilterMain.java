package com.gps.particlefilter;

import com.gps.particlefilter.io.*;
import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import com.gps.particlefilter.los.LosCalculator;
import com.gps.particlefilter.config.Configuration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Particle Filter implementation using real GNSS data from Bursa records.
 * Uses real satellite observations with actual C/N0 measurements instead of synthetic data.
 */
public class RealDataParticleFilterMain {

    // Paths for Bursa rectangle6 route1 data
    private static final String RECORDS_BASE_PATH = "records/bursa_rectangle6_route1/";
    private static final String GNSS_LOG_FILE = RECORDS_BASE_PATH + "gnss_log_2025_10_05_12_10_31.txt";
    private static final String ROUTE_KML_FILE = RECORDS_BASE_PATH + "output/gnss_log_2025_10_05_12_10_31_kml.kml";

    // Ground truth route (the actual route walked)
    private static final String GROUND_TRUTH_ROUTE_KML = "data/original_route.kml";

    // Output paths
    private static final String OUTPUT_BASE_PATH = RECORDS_BASE_PATH + "output/";
    private static final String OUTPUT_PARTICLES_KML = OUTPUT_BASE_PATH + "real_data_particles.kml";
    private static final String OUTPUT_ESTIMATED_ROUTE_KML = OUTPUT_BASE_PATH + "real_data_estimated_route.kml";
    private static final String OUTPUT_ACTUAL_ROUTE_KML = OUTPUT_BASE_PATH + "real_data_actual_route.kml";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Real Data GPS Particle Filter");
        System.out.println("Using Bursa, Turkey GNSS Records");
        System.out.println("========================================\n");
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        // Load configuration
        Configuration config = Configuration.getInstance();
        config.printConfiguration();

        try {
            // Initialize coordinate system manager for UTM conversions
            System.out.println("\nInitializing coordinate system for UTM...");
            CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();

            // Location at Lon=34.80° is in UTM Zone 36N (same as Israel)
            // UTM Zone calculation: floor((34.80 + 180) / 6) + 1 = 36
            coordManager.setDefaultUtmZone(36, true);
            coordManager.setUseUtm(true);
            System.out.println("UTM Zone 36N enabled for location at 34.80°E, 32.08°N\n");

            // Read ground truth route (actual route walked - this is what particle filter will track)
            System.out.println("\nReading ground truth route...");
            RouteKMLReader routeReader = new RouteKMLReader();
            List<Point3D> groundTruthRoute = routeReader.readRoute(GROUND_TRUTH_ROUTE_KML);
            List<Long> groundTruthTimestamps = routeReader.readTimestamps(GROUND_TRUTH_ROUTE_KML);
            System.out.println("Found " + groundTruthRoute.size() + " ground truth points");

            if (groundTruthRoute.isEmpty()) {
                System.err.println("Error: No ground truth route found in " + GROUND_TRUTH_ROUTE_KML);
                System.exit(1);
            }

            // Read GPS recorded route (with errors - for comparison only)
            System.out.println("Reading GPS recorded route from KML...");
            List<Point3D> gpsRoute = routeReader.readRoute(ROUTE_KML_FILE);
            List<Long> gpsTimestamps = routeReader.readTimestamps(ROUTE_KML_FILE);
            System.out.println("Found " + gpsRoute.size() + " GPS recorded points");

            if (gpsRoute.isEmpty()) {
                System.err.println("Error: No route points found in " + ROUTE_KML_FILE);
                System.exit(1);
            }

            // Use ground truth route for particle filter (simulate what the article does)
            List<Point3D> route = groundTruthRoute;
            List<Long> timestamps = groundTruthTimestamps;

            // Print first route point for debugging
            Point3D firstPoint = route.get(0);
            System.out.println("First route point (UTM): X=" + firstPoint.getX() +
                              ", Y=" + firstPoint.getY() +
                              ", Z=" + firstPoint.getZ());

            // Read real satellite data from GNSS log
            System.out.println("\nReading real satellite data from GNSS log...");
            RealDataSatelliteReader satelliteReader = new RealDataSatelliteReader();
            List<Satellite> satellites = satelliteReader.readSatellites(GNSS_LOG_FILE, firstPoint);

            if (satellites.isEmpty()) {
                System.err.println("Error: No satellites found in " + GNSS_LOG_FILE);
                System.exit(1);
            }

            System.out.println("Successfully loaded " + satellites.size() + " satellites with real C/N0 values");

            // Print validation report
            System.out.println("\n" + satelliteReader.generateValidationReport(GNSS_LOG_FILE, firstPoint));

            // Load buildings for geometric LOS calculation (same location as demo)
            System.out.println("\nLoading buildings data...");
            BuildingKMLReader buildingReader = new BuildingKMLReader();
            List<Building> buildings = buildingReader.readBuildings("data/building3d.kml");
            System.out.println("Loaded " + buildings.size() + " buildings");

            // Initialize LOS calculator with buildings AND real satellites
            System.out.println("\nInitializing LOS calculator...");
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);

            // Use HYBRID mode (signal strength + geometric) like the article
            losCalculator.setClassificationMode(LosCalculator.ClassificationMode.HYBRID);
            losCalculator.setRayShootingOptimization(true);
            System.out.println("Using HYBRID mode (C/N0 + geometric building occlusion)");

            // Count LOS/NLOS satellites
            int losCount = 0;
            int nlosCount = 0;
            for (Satellite sat : satellites) {
                if (sat.isLosFromSignalStrength()) {
                    losCount++;
                } else {
                    nlosCount++;
                }
            }
            System.out.printf("Signal classification: LOS=%d (%.1f%%), NLOS=%d (%.1f%%)%n",
                losCount, 100.0 * losCount / satellites.size(),
                nlosCount, 100.0 * nlosCount / satellites.size());

            // =========== PARTICLE FILTER SIMULATION ===========
            System.out.println("\n=== Starting Real Data Particle Filter Simulation ===");

            // Parameters for particle filter
            double gridSize = 15.0;  // 15 meters grid size
            double movementNoise = config.getParticleMeasurementNoise();
            int particleCount = config.getParticleCount();

            System.out.println("\nParticle filter parameters:");
            System.out.println("  Particle count: " + particleCount);
            System.out.println("  Movement noise: " + movementNoise + " meters");
            System.out.println("  Grid size: " + gridSize + " meters");

            // Initialize particle filter
            ParticleFilter particleFilter = new ParticleFilter(losCalculator, gridSize, movementNoise);

            // Initialize particles at the first point
            Point3D startPoint = route.get(0);
            particleFilter.initializeParticles(startPoint, particleCount);

            // Add initial state to history
            particleFilter.getParticleHistory().add(new ArrayList<>(particleFilter.getParticles()));
            particleFilter.getTimestamps().add(timestamps.get(0));

            // Add initial estimate
            List<Point3D> estimatedRoute = new ArrayList<>();
            estimatedRoute.add(calculateEstimatedPosition(particleFilter.getParticles()));

            // Store for error statistics
            double totalError = 0;
            double maxError = 0;
            int pointsProcessed = 0;

            // Process each point in the route
            System.out.println("\nProcessing route points...");
            for (int i = 1; i < route.size(); i++) {
                Point3D currentPoint = route.get(i);
                long timestamp = timestamps.get(i);

                // Update particle filter with current point
                particleFilter.update(currentPoint, timestamp);

                // Calculate estimated position using particles from history
                List<Particle> particlesWithWeights = particleFilter.getParticleHistory()
                    .get(particleFilter.getParticleHistory().size() - 1);
                Point3D estimatedPosition = calculateEstimatedPosition(particlesWithWeights);
                estimatedRoute.add(estimatedPosition);

                // Calculate error for this point
                double error = calculateError(currentPoint, estimatedPosition);
                totalError += error;
                maxError = Math.max(maxError, error);
                pointsProcessed++;

                // Print progress
                if ((i + 1) % 10 == 0) {
                    System.out.printf("Processed %d/%d points. Current error: %.2f meters%n",
                        i + 1, route.size(), error);
                }

                // Calculate LOS/NLOS counts for reference point
                Map<String, Boolean> referenceLosStatus = losCalculator.calculateLOS(currentPoint);
                int refLosCount = 0;
                int refNlosCount = 0;
                for (Boolean isLos : referenceLosStatus.values()) {
                    if (isLos) refLosCount++;
                    else refNlosCount++;
                }

                // Print reference point LOS/NLOS status
                System.out.println("\nPoint " + (i+1) + "/" + route.size() + " - Reference point LOS/NLOS: LOS: " + refLosCount + ", NLOS: " + refNlosCount);

                // Print particle information (first 10 particles)
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
            double avgError = pointsProcessed > 0 ? totalError / pointsProcessed : 0;

            System.out.println("\n=== Real Data Simulation Summary ===");
            System.out.println("Total route points: " + route.size());
            System.out.println("Points processed: " + pointsProcessed);
            System.out.println("Average error: " + String.format("%.2f meters", avgError));
            System.out.println("Maximum error: " + String.format("%.2f meters", maxError));
            System.out.println("Particles used: " + particleCount);
            System.out.println("Satellites tracked: " + satellites.size());

            // =========== ACCURACY COMPARISON VS GROUND TRUTH ===========
            System.out.println("\n========================================");
            System.out.println("GPS Accuracy Comparison vs Ground Truth");
            System.out.println("========================================\n");

            // Calculate how many points to compare (use overlapping points only)
            int numPointsToCompare = Math.min(gpsRoute.size(), Math.min(groundTruthRoute.size(), estimatedRoute.size()));
            System.out.println("Ground Truth Route: " + GROUND_TRUTH_ROUTE_KML + " (" + groundTruthRoute.size() + " points)");
            System.out.println("GPS Recorded Route: " + ROUTE_KML_FILE + " (" + gpsRoute.size() + " points)");
            System.out.println("Particle Filter Estimated Route: " + estimatedRoute.size() + " points");
            System.out.println("Comparing: " + numPointsToCompare + " overlapping points\n");

            // Calculate errors for raw GPS (comparing GPS recording vs ground truth)
            double gpsErrorSum = 0;
            double gpsErrorMax = 0;
            double gpsErrorMin = Double.MAX_VALUE;
            List<Double> gpsErrors = new ArrayList<>();

            for (int i = 0; i < numPointsToCompare; i++) {
                double error = calculateError(gpsRoute.get(i), groundTruthRoute.get(i));
                gpsErrors.add(error);
                gpsErrorSum += error;
                gpsErrorMax = Math.max(gpsErrorMax, error);
                gpsErrorMin = Math.min(gpsErrorMin, error);
            }

            double gpsErrorAvg = gpsErrorSum / numPointsToCompare;
            double gpsErrorStdDev = calculateStdDev(gpsErrors, gpsErrorAvg);

            // Calculate errors for particle filter
            double pfErrorSum = 0;
            double pfErrorMax = 0;
            double pfErrorMin = Double.MAX_VALUE;
            List<Double> pfErrors = new ArrayList<>();

            for (int i = 0; i < numPointsToCompare; i++) {
                double error = calculateError(estimatedRoute.get(i), groundTruthRoute.get(i));
                pfErrors.add(error);
                pfErrorSum += error;
                pfErrorMax = Math.max(pfErrorMax, error);
                pfErrorMin = Math.min(pfErrorMin, error);
            }

            double pfErrorAvg = pfErrorSum / numPointsToCompare;
            double pfErrorStdDev = calculateStdDev(pfErrors, pfErrorAvg);

            // Display comparison
            System.out.println("Raw GPS (from GNSS log):");
            System.out.println("  Average Error:     " + String.format("%.2f meters", gpsErrorAvg));
            System.out.println("  Maximum Error:     " + String.format("%.2f meters", gpsErrorMax));
            System.out.println("  Minimum Error:     " + String.format("%.2f meters", gpsErrorMin));
            System.out.println("  Std Deviation:     " + String.format("%.2f meters", gpsErrorStdDev));

            System.out.println("\nParticle Filter (" + particleCount + " particles):");
            System.out.println("  Average Error:     " + String.format("%.2f meters", pfErrorAvg));
            System.out.println("  Maximum Error:     " + String.format("%.2f meters", pfErrorMax));
            System.out.println("  Minimum Error:     " + String.format("%.2f meters", pfErrorMin));
            System.out.println("  Std Deviation:     " + String.format("%.2f meters", pfErrorStdDev));

            // Calculate improvement
            double avgImprovement = ((gpsErrorAvg - pfErrorAvg) / gpsErrorAvg) * 100;
            double maxImprovement = ((gpsErrorMax - pfErrorMax) / gpsErrorMax) * 100;

            System.out.println("\n=== IMPROVEMENT ===");
            System.out.println("  Average Error Reduction:   " + String.format("%.1f%%", avgImprovement));
            System.out.println("  Maximum Error Reduction:   " + String.format("%.1f%%", maxImprovement));
            System.out.println("\nParticle Filter achieves " + String.format("%.1f%%", avgImprovement) + " better accuracy than raw GPS!");
            System.out.println("========================================\n");

            // Write results to KML
            System.out.println("\n=== Writing Results to KML Files ===");
            KMLWriter kmlWriter = new KMLWriter();

            System.out.println("Writing particle history to: " + OUTPUT_PARTICLES_KML);
            kmlWriter.writeParticleHistoryToKML(
                particleFilter.getParticleHistory(),
                particleFilter.getTimestamps(),
                OUTPUT_PARTICLES_KML
            );

            System.out.println("Writing estimated route to: " + OUTPUT_ESTIMATED_ROUTE_KML);
            kmlWriter.writeRouteToKML(estimatedRoute, timestamps, OUTPUT_ESTIMATED_ROUTE_KML, false);

            System.out.println("Writing actual route to: " + OUTPUT_ACTUAL_ROUTE_KML);
            kmlWriter.writeRouteToKML(route, timestamps, OUTPUT_ACTUAL_ROUTE_KML, true);

            System.out.println("\n========================================");
            System.out.println("Processing Complete!");
            System.out.println("========================================");
            System.out.println("\nOutput files:");
            System.out.println("  - " + OUTPUT_PARTICLES_KML);
            System.out.println("  - " + OUTPUT_ESTIMATED_ROUTE_KML);
            System.out.println("  - " + OUTPUT_ACTUAL_ROUTE_KML);
            System.out.println("\nOpen these files in Google Earth to visualize the results.");

        } catch (Exception e) {
            System.err.println("\nERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
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
     */
    private static double calculateError(Point3D truePosition, Point3D estimatedPosition) {
        // Calculate Euclidean distance in UTM coordinates (meters)
        double xDiff = truePosition.getX() - estimatedPosition.getX();
        double yDiff = truePosition.getY() - estimatedPosition.getY();

        return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }

    /**
     * Calculate standard deviation of errors
     */
    private static double calculateStdDev(List<Double> errors, double mean) {
        if (errors.isEmpty()) {
            return 0;
        }

        double sumSquaredDiff = 0;
        for (Double error : errors) {
            double diff = error - mean;
            sumSquaredDiff += diff * diff;
        }

        return Math.sqrt(sumSquaredDiff / errors.size());
    }
}
