package com.gps.particlefilter;

import com.gps.particlefilter.model.*;
import org.apache.commons.math3.random.RandomDataGenerator;
import java.util.*;

public class ParticleFilter {
    private List<Particle> particles;
    private List<Point3D> originalRoute;
    private LosCalculator losCalculator;
    private RandomDataGenerator random;
    private double gridSize;
    private double movementNoise;

    public ParticleFilter(LosCalculator losCalculator, double gridSize, double movementNoise) {
        this.losCalculator = losCalculator;
        this.gridSize = gridSize;
        this.movementNoise = movementNoise;
        this.particles = new ArrayList<>();
        this.random = new RandomDataGenerator();
    }

    public void initializeParticles(Point3D center, int particleCount) {
        double halfGrid = gridSize / 2;
        
        // Create a grid of particles around the center point
        int side = (int) Math.sqrt(particleCount);
        double step = gridSize / side;
        
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                double x = center.getX() - halfGrid + i * step;
                double y = center.getY() - halfGrid + j * step;
                Point3D position = new Point3D(x, y, center.getZ());
                
                Particle particle = new Particle(position);
                particle.setLosStatus(losCalculator.calculateLOS(position));
                particles.add(particle);
            }
        }
    }

    public void updateWeights(Point3D originalPoint) {
        Map<String, Boolean> referenceStatus = losCalculator.calculateLOS(originalPoint);
        
        // Calculate weights based on matching LOS/NLOS status
        double totalWeight = 0;
        for (Particle particle : particles) {
            int matches = particle.matchingLosCount(referenceStatus);
            double weight = Math.pow(2, matches); // Exponential weighting
            particle.setWeight(weight);
            totalWeight += weight;
        }

        // Normalize weights
        for (Particle particle : particles) {
            particle.setWeight(particle.getWeight() / totalWeight);
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
            while (u > cumulativeWeights[j]) {
                j++;
            }
            Particle newParticle = new Particle(particles.get(j).getPosition());
            newParticle.setLosStatus(particles.get(j).getLosStatus());
            newParticles.add(newParticle);
            u += step;
        }

        particles = newParticles;
    }

    public void move(Point3D from, Point3D to) {
        Point3D direction = to.subtract(from);
        
        for (Particle particle : particles) {
            particle.move(direction, movementNoise);
            particle.setLosStatus(losCalculator.calculateLOS(particle.getPosition()));
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }
}
