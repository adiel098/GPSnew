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
    private List<List<Particle>> particleHistory;
    private List<Long> timestamps;
    private Point3D previousPoint;

    public ParticleFilter(LosCalculator losCalculator, double gridSize, double movementNoise) {
        this.losCalculator = losCalculator;
        this.gridSize = gridSize;
        this.movementNoise = movementNoise;
        this.particles = new ArrayList<>();
        this.random = new RandomDataGenerator();
        this.particleHistory = new ArrayList<>();
        this.timestamps = new ArrayList<>();
        this.previousPoint = null;
    }

    public void initializeParticles(Point3D center, int particleCount) {
        particles.clear();
        double minLat = center.getY() - gridSize;
        double maxLat = center.getY() + gridSize;
        double minLon = center.getX() - gridSize;
        double maxLon = center.getX() + gridSize;
        double alt = center.getZ();

        int particlesPerRow = (int) Math.sqrt(particleCount);
        double latStep = (maxLat - minLat) / (particlesPerRow - 1);
        double lonStep = (maxLon - minLon) / (particlesPerRow - 1);

        for (double lat = minLat; lat <= maxLat; lat += latStep) {
            for (double lon = minLon; lon <= maxLon; lon += lonStep) {
                if (particles.size() >= particleCount) break;
                Point3D position = new Point3D(lon, lat, alt);
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
        
        // Save current state to history
        particleHistory.add(new ArrayList<>(particles));
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
        // חישוב המרחק והזווית בין הנקודות
        double distance = from.distanceTo(to);
        double azimuth = from.azimuthTo(to);
        
        // הזזת כל החלקיקים באותו מרחק וזווית (עם רעש)
        for (Particle particle : particles) {
            particle.move(distance, azimuth, movementNoise);
            particle.setLosStatus(losCalculator.calculateLOS(particle.getPosition()));
        }
        
        // Save current state to history
        particleHistory.add(new ArrayList<>(particles));
    }

    public void update(Point3D currentPoint, long timestamp) {
        if (previousPoint != null) {
            move(previousPoint, currentPoint);
        }
        updateWeights(currentPoint);
        resample();
        
        previousPoint = currentPoint;
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
}
