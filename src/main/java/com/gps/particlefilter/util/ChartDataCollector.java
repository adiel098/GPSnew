package com.gps.particlefilter.util;

import com.gps.particlefilter.model.Particle;
import com.gps.particlefilter.model.Point3D;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;

public class ChartDataCollector {
    private List<ErrorDataPoint> errorData;
    private String outputFile;
    private int particleCount;
    private String weightType;
    private Double misclassificationPercentage;
    
    public static class ErrorDataPoint {
        public int timeStep;
        public int particleCount;
        public double averageError;
        public long timestamp;
        public String weightType;
        public Double misclassificationPercentage;
        
        public ErrorDataPoint(int timeStep, int particleCount, double averageError, long timestamp) {
            this.timeStep = timeStep;
            this.particleCount = particleCount;
            this.averageError = averageError;
            this.timestamp = timestamp;
            this.weightType = null;
            this.misclassificationPercentage = null;
        }
        
        public ErrorDataPoint(int timeStep, int particleCount, double averageError, long timestamp, String weightType) {
            this.timeStep = timeStep;
            this.particleCount = particleCount;
            this.averageError = averageError;
            this.timestamp = timestamp;
            this.weightType = weightType;
            this.misclassificationPercentage = null;
        }
        
        public ErrorDataPoint(int timeStep, int particleCount, double averageError, long timestamp, double misclassificationPercentage) {
            this.timeStep = timeStep;
            this.particleCount = particleCount;
            this.averageError = averageError;
            this.timestamp = timestamp;
            this.weightType = null;
            this.misclassificationPercentage = misclassificationPercentage;
        }
    }
    
    public ChartDataCollector(int particleCount, String outputFile) {
        this.particleCount = particleCount;
        this.outputFile = outputFile;
        this.weightType = null;
        this.misclassificationPercentage = null;
        this.errorData = new ArrayList<>();
    }
    
    public ChartDataCollector(int particleCount, String outputFile, String weightType) {
        this.particleCount = particleCount;
        this.outputFile = outputFile;
        this.weightType = weightType;
        this.misclassificationPercentage = null;
        this.errorData = new ArrayList<>();
    }
    
    public ChartDataCollector(int particleCount, String outputFile, double misclassificationPercentage) {
        this.particleCount = particleCount;
        this.outputFile = outputFile;
        this.weightType = null;
        this.misclassificationPercentage = misclassificationPercentage;
        this.errorData = new ArrayList<>();
    }
    
    public void collectErrorData(int timeStep, Point3D truePosition, Point3D estimatedPosition, long timestamp) {
        double error = calculateError(truePosition, estimatedPosition);
        if (weightType != null) {
            errorData.add(new ErrorDataPoint(timeStep, particleCount, error, timestamp, weightType));
        } else if (misclassificationPercentage != null) {
            errorData.add(new ErrorDataPoint(timeStep, particleCount, error, timestamp, misclassificationPercentage));
        } else {
            errorData.add(new ErrorDataPoint(timeStep, particleCount, error, timestamp));
        }
    }
    
    public void collectErrorData(int timeStep, Point3D truePosition, List<Particle> particles, long timestamp) {
        Point3D estimatedPosition = calculateEstimatedPosition(particles);
        double error = calculateError(truePosition, estimatedPosition);
        if (weightType != null) {
            errorData.add(new ErrorDataPoint(timeStep, particleCount, error, timestamp, weightType));
        } else if (misclassificationPercentage != null) {
            errorData.add(new ErrorDataPoint(timeStep, particleCount, error, timestamp, misclassificationPercentage));
        } else {
            errorData.add(new ErrorDataPoint(timeStep, particleCount, error, timestamp));
        }
    }
    
    private Point3D calculateEstimatedPosition(List<Particle> particles) {
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
    
    private double calculateError(Point3D truePosition, Point3D estimatedPosition) {
        double latDiff = truePosition.getY() - estimatedPosition.getY();
        double lonDiff = truePosition.getX() - estimatedPosition.getX();
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }
    
    public void exportToCSV() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
            for (ErrorDataPoint dataPoint : errorData) {
                if (dataPoint.weightType != null) {
                    writer.printf("%d,%d,%.6f,%d,%s%n", 
                        dataPoint.timeStep, 
                        dataPoint.particleCount, 
                        dataPoint.averageError, 
                        dataPoint.timestamp,
                        dataPoint.weightType);
                } else if (dataPoint.misclassificationPercentage != null) {
                    writer.printf("%d,%d,%.6f,%d,%.1f%n", 
                        dataPoint.timeStep, 
                        dataPoint.particleCount, 
                        dataPoint.averageError, 
                        dataPoint.timestamp,
                        dataPoint.misclassificationPercentage);
                } else {
                    writer.printf("%d,%d,%.6f,%d%n", 
                        dataPoint.timeStep, 
                        dataPoint.particleCount, 
                        dataPoint.averageError, 
                        dataPoint.timestamp);
                }
            }
            writer.flush();
        }
        String additionalInfo = "";
        if (weightType != null) {
            additionalInfo = " (" + weightType + ")";
        } else if (misclassificationPercentage != null) {
            additionalInfo = " (p=" + misclassificationPercentage + "%)";
        }
        System.out.println("Exported " + errorData.size() + " data points for " + particleCount + " particles" + additionalInfo + " to " + outputFile);
    }
    
    public void exportToCSVWithHeader() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, false))) {
            // Determine which columns we need
            boolean hasWeightType = errorData.stream().anyMatch(dp -> dp.weightType != null);
            boolean hasMisclassificationPercentage = errorData.stream().anyMatch(dp -> dp.misclassificationPercentage != null);
            
            if (hasWeightType) {
                writer.println("time_step,particle_count,average_error,timestamp,weight_type");
            } else if (hasMisclassificationPercentage) {
                writer.println("time_step,particle_count,average_error,timestamp,misclassification_percentage");
            } else {
                writer.println("time_step,particle_count,average_error,timestamp");
            }
            
            for (ErrorDataPoint dataPoint : errorData) {
                if (dataPoint.weightType != null) {
                    writer.printf("%d,%d,%.6f,%d,%s%n", 
                        dataPoint.timeStep, 
                        dataPoint.particleCount, 
                        dataPoint.averageError, 
                        dataPoint.timestamp,
                        dataPoint.weightType);
                } else if (dataPoint.misclassificationPercentage != null) {
                    writer.printf("%d,%d,%.6f,%d,%.1f%n", 
                        dataPoint.timeStep, 
                        dataPoint.particleCount, 
                        dataPoint.averageError, 
                        dataPoint.timestamp,
                        dataPoint.misclassificationPercentage);
                } else {
                    writer.printf("%d,%d,%.6f,%d%n", 
                        dataPoint.timeStep, 
                        dataPoint.particleCount, 
                        dataPoint.averageError, 
                        dataPoint.timestamp);
                }
            }
            writer.flush();
        }
        String additionalInfo = "";
        if (weightType != null) {
            additionalInfo = " (" + weightType + ")";
        } else if (misclassificationPercentage != null) {
            additionalInfo = " (p=" + misclassificationPercentage + "%)";
        }
        System.out.println("Exported " + errorData.size() + " data points for " + particleCount + " particles" + additionalInfo + " to " + outputFile);
    }
    
    public List<ErrorDataPoint> getErrorData() {
        return errorData;
    }
    
    public double getAverageError() {
        if (errorData.isEmpty()) return 0.0;
        
        double sum = 0.0;
        for (ErrorDataPoint dataPoint : errorData) {
            sum += dataPoint.averageError;
        }
        return sum / errorData.size();
    }
    
    public double getMaxError() {
        if (errorData.isEmpty()) return 0.0;
        
        double max = 0.0;
        for (ErrorDataPoint dataPoint : errorData) {
            max = Math.max(max, dataPoint.averageError);
        }
        return max;
    }
}