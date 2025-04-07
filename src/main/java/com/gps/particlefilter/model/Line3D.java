package com.gps.particlefilter.model;

public class Line3D {
    private Point3D p1;
    private double azimuth;
    private double elevation;
    private double length;

    public Line3D(Point3D p1, double azimuth, double elevation, double length) {
        this.p1 = p1;
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.length = length;
    }

    public Point3D getP1() {
        return p1;
    }

    public Point3D getEndPoint() {
        // Calculate end point using spherical coordinates
        double horizontalDistance = length * Math.cos(Math.toRadians(elevation));
        double x = p1.getX() + horizontalDistance * Math.sin(Math.toRadians(azimuth));
        double y = p1.getY() + horizontalDistance * Math.cos(Math.toRadians(azimuth));
        double z = p1.getZ() + length * Math.sin(Math.toRadians(elevation));
        return new Point3D(x, y, z);
    }

    public Point2D getIntersectionPoint(Line2D wall) {
        // Project to 2D for intersection calculation
        Point2D p1_2d = new Point2D(p1.getX(), p1.getY());
        Point3D endPoint = getEndPoint();
        Point2D p2_2d = new Point2D(endPoint.getX(), endPoint.getY());
        
        return Line2D.getIntersectionPoint(p1_2d, p2_2d, wall.getP1(), wall.getP2());
    }
}
