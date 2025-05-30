package com.gps.particlefilter.model;

public class Point3D {
    private double x;
    private double y;
    private double z;

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { 
        return x; 
    }
    public double getY() { 
        return y; 
    }
    public double getZ() { 
        return z; 
    }

    public Point3D add(Point3D other) {
        return new Point3D(
            this.x + other.x,
            this.y + other.y,
            this.z + other.z
        );
    }

    public Point3D subtract(Point3D other) {
        return new Point3D(
            this.x - other.x,
            this.y - other.y,
            this.z - other.z
        );
    }

    // מחשב את המרחק בין שתי נקודות
    public double distanceTo(Point3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    // מחשב את הזווית (אזימוט) בין שתי נקודות
    public double azimuthTo(Point3D other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double azimuth = Math.toDegrees(Math.atan2(dy, dx));
        if (azimuth < 0) {
            azimuth += 360;
        }
        return azimuth;
    }

    // יוצר נקודה חדשה במרחק ובזווית נתונים
    public Point3D moveByDistanceAndAzimuth(double distance, double azimuth) {
        double azimuthRad = Math.toRadians(azimuth);
        double dx = distance * Math.cos(azimuthRad);
        double dy = distance * Math.sin(azimuthRad);
        return new Point3D(this.x + dx, this.y + dy, this.z);
    }

    public Point3D multiply(double scalar) {
        return new Point3D(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Point3D normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Point3D(x / length, y / length, z / length);
    }

    @Override
    public String toString() {
        return String.format("(%.6f, %.6f, %.6f)", x, y, z);
    }
}
