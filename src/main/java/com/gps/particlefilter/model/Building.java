package com.gps.particlefilter.model;

import java.util.List;

public class Building {
    private List<Point3D> vertices;  // Building vertices in 3D space
    private double height;

    public Building(List<Point3D> vertices, double height) {
        this.vertices = vertices;
        this.height = height;
    }

    public List<Point3D> getVertices() {
        return vertices;
    }

    public double getHeight() {
        return height;
    }

    public boolean intersectsLine(Point3D start, Point3D end) {
        // Check if line segment intersects with building
        // This is a simplified implementation - you'll need to implement proper 3D line-building intersection
        
        // 1. Check if line intersects with any of the building's faces
        // 2. Check if intersection point is within building height
        
        // For now, using a simple bounding box check
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        
        for (Point3D vertex : vertices) {
            minX = Math.min(minX, vertex.getX());
            minY = Math.min(minY, vertex.getY());
            maxX = Math.max(maxX, vertex.getX());
            maxY = Math.max(maxY, vertex.getY());
        }

        // Check if line segment intersects with bounding box
        return lineIntersectsBox(start, end, 
            new Point3D(minX, minY, 0), 
            new Point3D(maxX, maxY, height));
    }

    private boolean lineIntersectsBox(Point3D start, Point3D end, Point3D min, Point3D max) {
        // Simplified check - you should implement a proper algorithm
        double tmin = Double.NEGATIVE_INFINITY;
        double tmax = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 3; i++) {
            double d = end.getX() - start.getX();
            if (Math.abs(d) < 1e-7) {
                if (start.getX() < min.getX() || start.getX() > max.getX()) {
                    return false;
                }
            } else {
                double t1 = (min.getX() - start.getX()) / d;
                double t2 = (max.getX() - start.getX()) / d;
                if (t1 > t2) {
                    double temp = t1;
                    t1 = t2;
                    t2 = temp;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) {
                    return false;
                }
            }
        }
        return true;
    }
}
