package com.gps.particlefilter.model;

public class Line2D {
    private Point2D p1;
    private Point2D p2;

    public Line2D(Point2D p1, Point2D p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public Point2D getP1() { return p1; }
    public Point2D getP2() { return p2; }

    public static Point2D getIntersectionPoint(Point2D line1P1, Point2D line1P2, 
                                             Point2D line2P1, Point2D line2P2) {
        double denominator = (line1P1.getX() - line1P2.getX()) * (line2P1.getY() - line2P2.getY()) - 
                           (line1P1.getY() - line1P2.getY()) * (line2P1.getX() - line2P2.getX());
        
        if (Math.abs(denominator) < 1e-10) return null;  // Lines are parallel

        double x = ((line1P1.getX() * line1P2.getY() - line1P1.getY() * line1P2.getX()) * 
                   (line2P1.getX() - line2P2.getX()) - 
                   (line1P1.getX() - line1P2.getX()) * 
                   (line2P1.getX() * line2P2.getY() - line2P1.getY() * line2P2.getX())) / denominator;
        
        double y = ((line1P1.getX() * line1P2.getY() - line1P1.getY() * line1P2.getX()) * 
                   (line2P1.getY() - line2P2.getY()) - 
                   (line1P1.getY() - line1P2.getY()) * 
                   (line2P1.getX() * line2P2.getY() - line2P1.getY() * line2P2.getX())) / denominator;

        // Check if intersection point lies on both line segments
        if (!isPointOnLineSegment(line1P1, line1P2, new Point2D(x, y)) ||
            !isPointOnLineSegment(line2P1, line2P2, new Point2D(x, y))) {
            return null;
        }

        return new Point2D(x, y);
    }

    private static boolean isPointOnLineSegment(Point2D lineStart, Point2D lineEnd, Point2D point) {
        double d1 = distance(lineStart, point);
        double d2 = distance(point, lineEnd);
        double lineLength = distance(lineStart, lineEnd);
        double buffer = 0.1; // Allow for small numerical errors
        
        return Math.abs(d1 + d2 - lineLength) < buffer;
    }

    private static double distance(Point2D p1, Point2D p2) {
        double dx = p1.getX() - p2.getX();
        double dy = p1.getY() - p2.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
