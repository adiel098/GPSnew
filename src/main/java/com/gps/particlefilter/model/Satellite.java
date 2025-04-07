package com.gps.particlefilter.model;

public class Satellite {
    private String name;
    private Point3D position;
    private double azimuth;
    private double elevation;

    public Satellite(String name, Point3D position, double azimuth, double elevation) {
        this.name = name;
        this.position = position;
        this.azimuth = azimuth;
        this.elevation = elevation;
    }

    public String getName() {
        return name;
    }

    public Point3D getPosition() {
        return position;
    }

    public void setPosition(Point3D position) {
        this.position = position;
    }

    public double getAzimuth() {
        return azimuth;
    }

    public double getElevation() {
        return elevation;
    }
}
