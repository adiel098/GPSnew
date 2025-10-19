package com.gps.particlefilter;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import com.gps.particlefilter.los.LosCalculator;
import org.apache.commons.math3.random.RandomDataGenerator;
import java.util.*;

public class ParticleFilter {
    private List<Particle> particles;
    private List<Point3D> originalRoute;
    private LosCalculator losCalculator;
    private RandomDataGenerator random;
    private double gridSize;
    private double movementNoise;
    private List<List<Particle>> particleHistory;
    private List<Long> timestamps;
    private Point3D previousPoint;
    private CoordinateSystemManager coordManager;
    private double velocity = 0.0; // Current velocity magnitude
    private double c = 1.0; // Error model coefficient (from article)
    private boolean useBayesianWeight = true; // Enable Bayesian weight function
    private double bayesianC = 0.5; // Ratio between history and current measurement - reduced for better tracking

    public ParticleFilter(LosCalculator losCalculator, double gridSize, double movementNoise) {
        this.losCalculator = losCalculator;
        this.gridSize = gridSize;
        this.movementNoise = movementNoise;
        this.particles = new ArrayList<>();
        this.random = new RandomDataGenerator();
        this.particleHistory = new ArrayList<>();
        this.timestamps = new ArrayList<>();
        this.previousPoint = null;
        this.coordManager = CoordinateSystemManager.getInstance();
    }

    public void initializeParticles(Point3D center, int particleCount) {
        particles.clear();
        
        // DEBUG: Check coordinate system and values
        System.out.println("=== PARTICLE INITIALIZATION DEBUG ===");
        System.out.println("Center point: X=" + center.getX() + ", Y=" + center.getY() + ", Z=" + center.getZ());
        System.out.println("Grid size: " + gridSize + " meters");
        System.out.println("Using UTM: " + coordManager.isUsingUtm());
        
        // When using UTM, gridSize is already in meters
        double minY = center.getY() - gridSize;
        double maxY = center.getY() + gridSize;
        double minX = center.getX() - gridSize;
        double maxX = center.getX() + gridSize;
        
        System.out.println("Initialization bounds: X=[" + minX + " to " + maxX + "], Y=[" + minY + " to " + maxY + "]");
        double totalRangeX = maxX - minX;
        double totalRangeY = maxY - minY;
        System.out.println("Total range: X=" + totalRangeX + "m, Y=" + totalRangeY + "m");
        // Update particle altitude to 1.8 meters above ground level
        double alt = 1.8; // Average human height

        int particlesPerRow = (int) Math.sqrt(particleCount);
        
        // Ensure we don't divide by zero and have proper step sizes
        if (particlesPerRow <= 1) {
            particlesPerRow = 2; // Minimum 2x2 grid
        }
        
        double yStep = (maxY - minY) / (particlesPerRow - 1);
        double xStep = (maxX - minX) / (particlesPerRow - 1);
        
        System.out.println("Grid setup: " + particlesPerRow + "x" + particlesPerRow + " grid");
        System.out.println("Step sizes: X=" + xStep + "m, Y=" + yStep + "m");
        
        // Ensure particles are distributed evenly and don't overlap
        for (int row = 0; row < particlesPerRow && particles.size() < particleCount; row++) {
            for (int col = 0; col < particlesPerRow && particles.size() < particleCount; col++) {
                double x = minX + (col * xStep);
                double y = minY + (row * yStep);
                
                Point3D position = new Point3D(x, y, alt);
                Particle particle = new Particle(position);
                
                particle.setLosStatus(losCalculator.calculateLOS(position));
                particles.add(particle);
            }
        }
        
        // DEBUG: Validate particle distribution
        if (particles.size() > 0) {
            Point3D firstPos = particles.get(0).getPosition();
            Point3D lastPos = particles.get(particles.size() - 1).getPosition();
            System.out.println("First particle: X=" + firstPos.getX() + ", Y=" + firstPos.getY());
            System.out.println("Last particle: X=" + lastPos.getX() + ", Y=" + lastPos.getY());
            
            // Check distances from center
            double firstDistance = firstPos.distanceTo(center);
            double lastDistance = lastPos.distanceTo(center);
            System.out.println("Distance from first particle to center: " + firstDistance + " meters");
            System.out.println("Distance from last particle to center: " + lastDistance + " meters");
            
            // Calculate center of particle cloud
            double avgX = 0, avgY = 0;
            for (Particle p : particles) {
                avgX += p.getPosition().getX();
                avgY += p.getPosition().getY();
            }
            avgX /= particles.size();
            avgY /= particles.size();
            Point3D particleCenter = new Point3D(avgX, avgY, alt);
            
            double centerOffset = particleCenter.distanceTo(center);
            System.out.println("Particle cloud center: X=" + avgX + ", Y=" + avgY);
            System.out.println("Offset from GPS point: " + centerOffset + " meters");
            
            // Check for duplicates
            int duplicates = 0;
            for (int i = 0; i < particles.size(); i++) {
                for (int j = i + 1; j < particles.size(); j++) {
                    Point3D pos1 = particles.get(i).getPosition();
                    Point3D pos2 = particles.get(j).getPosition();
                    double dist = pos1.distanceTo(pos2);
                    if (dist < 0.1) { // Less than 10cm apart
                        duplicates++;
                    }
                }
            }
            if (duplicates > 0) {
                System.out.println("WARNING: Found " + duplicates + " duplicate/overlapping particle pairs!");
            }
        }
        System.out.println("Total particles created: " + particles.size());
        System.out.println("=== END INITIALIZATION DEBUG ===\n");
    }

    public void updateWeights(Point3D originalPoint) {
        // Calculate LOS/NLOS status for the reference point
        Map<String, Boolean> referenceStatus = losCalculator.calculateLOS(originalPoint);
        
        int N = referenceStatus.size(); // Total number of satellites
        double totalWeight = 0;
        
        // First pass - calculate weights using Modified Sigmoid function from the article
        for (Particle particle : particles) {
            // Calculate LOS/NLOS status for the particle
            Map<String, Boolean> particleLosStatus = losCalculator.calculateLOS(particle.getPosition());
            particle.setLosStatus(particleLosStatus);
            
            // Count matching LOS states between particle and reference
            int n = particle.matchingLosCount(referenceStatus);

            // Modified Sigmoid weight function from article (Equation 2)
            // Weight(x) = N × 1/(1 + e^(N/4-n/2))
            // This formula works correctly for N ≤ 20 satellites (article tested with 17)
            double exponent = (N / 4.0) - (n / 2.0);
            double sigmoidWeight = N * (1.0 / (1.0 + Math.exp(exponent)));
            
            // Apply Bayesian weight if enabled (Equation 3 from article)
            double finalWeight;
            if (useBayesianWeight && particle.getPreviousWeight() > 0) {
                // Weight(x_t) = c × sigmoid + (1-c) × Weight(x_t-1)
                finalWeight = bayesianC * sigmoidWeight + (1 - bayesianC) * particle.getPreviousWeight();
            } else {
                finalWeight = sigmoidWeight;
            }
            
            // Store previous weight for next iteration
            particle.setPreviousWeight(finalWeight);
            particle.setWeight(finalWeight);
            totalWeight += finalWeight;
        }

        // Second pass - normalize weights
        for (Particle particle : particles) {
            double normalizedWeight = totalWeight > 0 ? particle.getWeight() / totalWeight : 1.0 / particles.size();
            particle.setWeight(normalizedWeight);
        }
    }

    public void resample() {
        List<Particle> newParticles = new ArrayList<>();
        int n = particles.size();
        
        // Create cumulative weights array
        double[] cumulativeWeights = new double[n];
        cumulativeWeights[0] = particles.get(0).getWeight();
        for (int i = 1; i < n; i++) {
            cumulativeWeights[i] = cumulativeWeights[i-1] + particles.get(i).getWeight();
        }

        // Resample using systematic resampling
        double step = 1.0 / n;
        double u = random.nextUniform(0, step);
        int j = 0;
        
        for (int i = 0; i < n; i++) {
            while (j < n-1 && u > cumulativeWeights[j]) {
                j++;
            }
            Particle newParticle = new Particle(particles.get(j).getPosition());
            newParticle.setLosStatus(new HashMap<>(particles.get(j).getLosStatus()));
            newParticle.setWeight(particles.get(j).getWeight());
            
            newParticles.add(newParticle);
            u += step;
        }

        particles = newParticles;
    }

    public void move(Point3D from, Point3D to) {
        // Calculate distance and azimuth between points
        double distance = from.distanceTo(to);
        double azimuth = from.azimuthTo(to);

        // Handle case where points are identical (distance = 0)
        if (distance < 0.001) {
            // No movement needed, just update LOS status
            for (Particle particle : particles) {
                particle.setLosStatus(losCalculator.calculateLOS(particle.getPosition()));
            }
            return;
        }

        // Calculate velocity magnitude (assuming 1 second between updates)
        this.velocity = distance;

        // Determine c coefficient based on velocity (from article)
        // Walking speed: ~1-2 m/s → c = 1.0-1.5
        // Driving speed: >5 m/s → c = 0.5-1.0
        // Slow/stationary: <0.5 m/s → c = 2.0
        if (velocity < 0.5) {
            c = 2.0;
        } else if (velocity < 2.0) {
            c = 1.5;
        } else if (velocity < 5.0) {
            c = 1.0;
        } else {
            c = 0.5;
        }

        System.out.println("Moving from: " + from + " to: " + to);
        System.out.println("Distance: " + distance + "m, Azimuth: " + azimuth + "°");
        System.out.println("Velocity: " + velocity + " m/s, Error coefficient c: " + c);

        // Move each particle using improved noise model
        for (Particle particle : particles) {
            // Apply Gaussian noise to distance (2-5% based on velocity)
            double distanceNoiseStd = Math.max(0.02, Math.min(0.05, 1.0 / velocity)) * distance;
            double noisyDistance = distance + random.nextGaussian(0, distanceNoiseStd);

            // Apply small Gaussian noise to azimuth (±5-10 degrees based on velocity)
            double azimuthNoiseStd = velocity < 1.0 ? 10.0 : 5.0; // More noise when slow
            double noisyAzimuth = azimuth + random.nextGaussian(0, azimuthNoiseStd);

            // Calculate movement using corrected trigonometry for UTM coordinates
            double azimuthRad = Math.toRadians(noisyAzimuth);
            double dx = noisyDistance * Math.sin(azimuthRad); // Easting component
            double dy = noisyDistance * Math.cos(azimuthRad); // Northing component

            // Update particle position
            Point3D currentPos = particle.getPosition();
            Point3D newPos = new Point3D(
                currentPos.getX() + dx,
                currentPos.getY() + dy,
                currentPos.getZ()
            );

            particle.setPosition(newPos);
            particle.setLosStatus(losCalculator.calculateLOS(particle.getPosition()));
        }
    }

    public void update(Point3D currentPoint, long timestamp) {
        if (previousPoint != null) {
            move(previousPoint, currentPoint);
        }
        updateWeights(currentPoint);
        
        // Save current state to history BEFORE resampling (preserves weights for estimation)
        particleHistory.add(new ArrayList<>(particles));
        timestamps.add(timestamp);
        
        resample();
        
        // Store the current point for next update (in current coordinate system)
        previousPoint = currentPoint;
        
        // Debug information about coordinate system
        if (coordManager.isUsingUtm()) {
            System.out.println("Using UTM coordinate system (Zone: " + coordManager.getDefaultUtmZone() + 
                    (coordManager.isNorthernHemisphere() ? " North" : " South") + ")");
        } else {
            System.out.println("Using Geographic coordinate system (Latitude/Longitude)");
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }

    public List<List<Particle>> getParticleHistory() {
        return particleHistory;
    }
    
    public List<Long> getTimestamps() {
        return timestamps;
    }
    
    // Configuration methods for article-based features
    public void setUseBayesianWeight(boolean useBayesianWeight) {
        this.useBayesianWeight = useBayesianWeight;
    }
    
    public void setBayesianC(double bayesianC) {
        this.bayesianC = Math.max(0, Math.min(1, bayesianC)); // Clamp between 0 and 1
    }
    
    public double getVelocity() {
        return velocity;
    }
    
    public double getErrorCoefficient() {
        return c;
    }
}
