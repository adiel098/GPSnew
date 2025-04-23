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
        // חישוב מצב ה-LOS/NLOS עבור הנקודה האמיתית
        Map<String, Boolean> referenceStatus = losCalculator.calculateLOS(originalPoint);
        
        // הדפסת מצב LOS/NLOS של הנקודה האמיתית
        int refLosCount = 0;
        for (Boolean isLos : referenceStatus.values()) {
            if (isLos) refLosCount++;
        }
        System.out.println("\nReference point LOS/NLOS: " + 
            String.format("LOS: %d, NLOS: %d", refLosCount, referenceStatus.size() - refLosCount));
        
        // עדכון המשקולות עבור כל החלקיקים
        double totalWeight = 0;
        int particleIndex = 0;
        for (Particle particle : particles) {
            // חישוב מצב ה-LOS/NLOS עבור החלקיק
            Map<String, Boolean> particleLosStatus = losCalculator.calculateLOS(particle.getPosition());
            particle.setLosStatus(particleLosStatus);
            
            // חישוב מספר ההתאמות בין החלקיק למצב האמיתי
            int matches = particle.matchingLosCount(referenceStatus);
            
            // חישוב המשקל החדש - ככל שיש יותר התאמות, המשקל גבוה יותר
            double weight = Math.pow(2, matches); // משקל אקספוננציאלי לפי מספר ההתאמות
            particle.setWeight(weight);
            totalWeight += weight;
            
            // הדפסת מצב LOS/NLOS של החלקיק
            if (particleIndex < 10) { // מדפיס רק את 10 החלקיקים הראשונים כדי לא להציף את המסך
                System.out.println("Particle " + particleIndex + " - " + 
                    particle.getLosNlosCount() + 
                    String.format(", Matches: %d, Weight: %.4f", matches, weight));
            }
            particleIndex++;
        }

        // נרמול המשקולות כך שסכומם יהיה 1
        if (totalWeight > 0) {
            for (Particle particle : particles) {
                particle.setWeight(particle.getWeight() / totalWeight);
            }
        } else {
            // אם אין התאמות בכלל, ניתן משקל שווה לכל החלקיקים
            double equalWeight = 1.0 / particles.size();
            for (Particle particle : particles) {
                particle.setWeight(equalWeight);
            }
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

    /**
     * מזיז את כל החלקיקים בהתאם למרחק והזווית שנמדדו בין שתי נקודות במסלול המקורי
     */
    public void move(Point3D from, Point3D to) {
        // חישוב המרחק והזווית בין הנקודות
        double distance = from.distanceTo(to);
        double azimuth = from.azimuthTo(to);
        
        // הזזת כל החלקיקים באותו מרחק וזווית (עם רעש)
        for (Particle particle : particles) {
            particle.move(distance, azimuth, movementNoise);
            particle.setLosStatus(losCalculator.calculateLOS(particle.getPosition()));
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }
}
