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
    private double bayesianC = 0.7; // Ratio between history and current measurement

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
        // When using UTM, gridSize is already in meters
        double minY = center.getY() - gridSize;
        double maxY = center.getY() + gridSize;
        double minX = center.getX() - gridSize;
        double maxX = center.getX() + gridSize;
        // Update particle altitude to 1.8 meters above ground level
        double alt = 1.8; // Average human height

        int particlesPerRow = (int) Math.sqrt(particleCount);
        double yStep = (maxY - minY) / (particlesPerRow - 1);
        double xStep = (maxX - minX) / (particlesPerRow - 1);

        for (double y = minY; y <= maxY; y += yStep) {
            for (double x = minX; x <= maxX; x += xStep) {
                if (particles.size() >= particleCount) break;
                Point3D position = new Point3D(x, y, alt);
                Particle particle = new Particle(position);
                particle.setLosStatus(losCalculator.calculateLOS(position));
                particles.add(particle);
            }
        }
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
        
        // Article's error model: R = c × |v|
        double errorRadius = c * velocity;
        
        // Move each particle with error model from article
        for (Particle particle : particles) {
            // Sample random point within circle of radius R (uniform distribution)
            double randomRadius = errorRadius * Math.sqrt(random.nextUniform(0, 1));
            double randomAngle = random.nextUniform(0, 2 * Math.PI);
            
            // Calculate expected movement
            double dx = distance * Math.cos(Math.toRadians(azimuth));
            double dy = distance * Math.sin(Math.toRadians(azimuth));
            
            // Add random error from circle
            double errorX = randomRadius * Math.cos(randomAngle);
            double errorY = randomRadius * Math.sin(randomAngle);
            
            // Update particle position with movement + error
            Point3D currentPos = particle.getPosition();
            Point3D newPos = new Point3D(
                currentPos.getX() + dx + errorX,
                currentPos.getY() + dy + errorY,
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
        resample();
        
        // Save current state to history and timestamp
        particleHistory.add(new ArrayList<>(particles));
        timestamps.add(timestamp);
        
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
