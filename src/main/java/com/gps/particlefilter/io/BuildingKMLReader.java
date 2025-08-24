package com.gps.particlefilter.io;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BuildingKMLReader {
    public List<Building> readBuildings(String filename) {
        List<Building> buildings = new ArrayList<>();
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        
        try {
            Kml kml = Kml.unmarshal(new File(filename));
            Document document = (Document) kml.getFeature();
            
            for (Feature feature : document.getFeature()) {
                if (feature instanceof Placemark) {
                    Placemark placemark = (Placemark) feature;
                    if (placemark.getGeometry() instanceof MultiGeometry) {
                        MultiGeometry multiGeometry = (MultiGeometry) placemark.getGeometry();
                        for (Geometry geometry : multiGeometry.getGeometry()) {
                            if (geometry instanceof Polygon) {
                                Polygon polygon = (Polygon) geometry;
                                List<Point3D> vertices = new ArrayList<>();
                                
                                // Get the outer boundary
                                Boundary outerBoundary = polygon.getOuterBoundaryIs();
                                LinearRing ring = outerBoundary.getLinearRing();
                                List<Coordinate> coordinates = ring.getCoordinates();
                                
                                for (Coordinate coord : coordinates) {
                                    // Create geographic point first (longitude=x, latitude=y)
                                    Point3D geoPoint = new Point3D(coord.getLongitude(), 
                                                                   coord.getLatitude(), 
                                                                   coord.getAltitude());
                                    
                                    // Convert to UTM coordinates for proper meter-based calculations
                                    Point3D convertedPoint = coordManager.convertFromGeographic(geoPoint);
                                    
                                    // Check if the point is not identical to the previous one
                                    if (vertices.isEmpty() || !isPointEqual(vertices.get(vertices.size()-1), convertedPoint)) {
                                        vertices.add(convertedPoint);
                                    }
                                }
                                
                                // Ensure the polygon is closed
                                if (!vertices.isEmpty() && !isPointEqual(vertices.get(0), vertices.get(vertices.size()-1))) {
                                    vertices.add(vertices.get(0));
                                }
                                
                                // Get height from the first point's altitude
                                double height = coordinates.get(0).getAltitude();
                                
                                // Only if there are at least 3 unique points
                                if (vertices.size() >= 3) {
                                    buildings.add(new Building(vertices, height));
                                } else {
                                    System.out.println("Warning: Skipping building with less than 3 unique vertices");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buildings;
    }

    public String generateValidationReport(String filename) {
        StringBuilder report = new StringBuilder();
        report.append("Building KML Validation Report\n");
        report.append("============================\n\n");

        try {
            List<Building> buildings = readBuildings(filename);
            report.append("Total buildings found: ").append(buildings.size()).append("\n\n");

            for (int i = 0; i < buildings.size(); i++) {
                Building building = buildings.get(i);
                report.append("Building ").append(i + 1).append(":\n");
                report.append("- Number of vertices: ").append(building.getVertices().size()).append("\n");
                report.append("- Height: ").append(String.format("%.2f", building.getHeight())).append(" meters\n");
                
                // Calculate building bounds
                double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
                double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
                double minAlt = Double.MAX_VALUE, maxAlt = -Double.MAX_VALUE;

                for (Point3D vertex : building.getVertices()) {
                    minLat = Math.min(minLat, vertex.getY());
                    maxLat = Math.max(maxLat, vertex.getY());
                    minLon = Math.min(minLon, vertex.getX());
                    maxLon = Math.max(maxLon, vertex.getX());
                    minAlt = Math.min(minAlt, vertex.getZ());
                    maxAlt = Math.max(maxAlt, vertex.getZ());
                }

                report.append("- Bounds:\n");
                report.append("  * Latitude: ").append(String.format("%.6f", minLat)).append(" to ")
                      .append(String.format("%.6f", maxLat)).append("\n");
                report.append("  * Longitude: ").append(String.format("%.6f", minLon)).append(" to ")
                      .append(String.format("%.6f", maxLon)).append("\n");
                report.append("  * Altitude: ").append(String.format("%.2f", minAlt)).append(" to ")
                      .append(String.format("%.2f", maxAlt)).append(" meters\n\n");
            }
        } catch (Exception e) {
            report.append("Error reading KML file: ").append(e.getMessage());
        }

        return report.toString();
    }

    // Helper function to compare points
    private boolean isPointEqual(Point3D p1, Point3D p2) {
        final double EPSILON = 0.000001; // Accuracy of about 10 centimeters
        return Math.abs(p1.getX() - p2.getX()) < EPSILON && 
               Math.abs(p1.getY() - p2.getY()) < EPSILON;
    }
}
