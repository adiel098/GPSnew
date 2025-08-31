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

    // Calculates the distance between two points
    public double distanceTo(Point3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    // Calculates the azimuth angle between two points
    // For UTM coordinates: X is easting, Y is northing
    // Azimuth is measured clockwise from north
    public double azimuthTo(Point3D other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        // Use atan2(dx, dy) for azimuth from north
        double azimuth = Math.toDegrees(Math.atan2(dx, dy));
        // Normalize to 0-360 degrees
        if (azimuth < 0) {
            azimuth += 360;
        }
        return azimuth;
    }

    // Creates a new point at given distance and azimuth
    // For UTM coordinates: azimuth is from north, X is easting, Y is northing
    public Point3D moveByDistanceAndAzimuth(double distance, double azimuth) {
        double azimuthRad = Math.toRadians(azimuth);
        // For azimuth from north: dx = distance * sin(azimuth), dy = distance * cos(azimuth)
        double dx = distance * Math.sin(azimuthRad);
        double dy = distance * Math.cos(azimuthRad);
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
