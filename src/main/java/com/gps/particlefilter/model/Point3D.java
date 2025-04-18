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

    public double distanceTo(Point3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Point3D add(Point3D other) {
        return new Point3D(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Point3D subtract(Point3D other) {
        return new Point3D(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Point3D multiply(double scalar) {
        return new Point3D(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Point3D normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Point3D(x / length, y / length, z / length);
    }
}
