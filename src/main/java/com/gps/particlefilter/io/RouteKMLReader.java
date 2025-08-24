package com.gps.particlefilter.io;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class RouteKMLReader {
    public List<Point3D> readRoute(String filename) {
        List<Point3D> route = new ArrayList<>();
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        
        try {
            Kml kml = Kml.unmarshal(new File(filename));
            Document document = (Document) kml.getFeature();
            
            for (Feature feature : document.getFeature()) {
                if (feature instanceof Placemark) {
                    Placemark placemark = (Placemark) feature;
                    
                    // Handle LineString geometry
                    if (placemark.getGeometry() instanceof LineString) {
                        LineString lineString = (LineString) placemark.getGeometry();
                        List<Coordinate> coordinates = lineString.getCoordinates();
                        
                        for (Coordinate coord : coordinates) {
                            // Create geographic point (lon, lat, alt) with 1.8 meters added for user height
                            Point3D geoPoint = new Point3D(coord.getLongitude(), 
                                                     coord.getLatitude(), 
                                                     coord.getAltitude() + 1.8);
                            
                            // Convert to current coordinate system (UTM if enabled)
                            Point3D convertedPoint = coordManager.convertFromGeographic(geoPoint);
                            route.add(convertedPoint);
                        }
                    }
                    // Handle Point geometry
                    else if (placemark.getGeometry() instanceof Point) {
                        Point point = (Point) placemark.getGeometry();
                        Coordinate coord = point.getCoordinates().get(0);
                        
                        // Create geographic point (lon, lat, alt) with 1.8 meters added for user height
                        Point3D geoPoint = new Point3D(coord.getLongitude(), 
                                                 coord.getLatitude(), 
                                                 coord.getAltitude() + 1.8);
                        
                        // Convert to current coordinate system (UTM if enabled)
                        Point3D convertedPoint = coordManager.convertFromGeographic(geoPoint);
                        route.add(convertedPoint);
                    }
                }
            }
            
            System.out.println("Read " + route.size() + " route points from " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return route;
    }

    public String generateValidationReport(String filename) {
        StringBuilder report = new StringBuilder();
        report.append("Route KML Validation Report\n");
        report.append("=========================\n\n");
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();

        try {
            List<Point3D> route = readRoute(filename);
            report.append("Total route points: ").append(route.size()).append("\n\n");

            if (!route.isEmpty()) {
                // Calculate route bounds and statistics
                double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                double minAlt = Double.MAX_VALUE, maxAlt = -Double.MAX_VALUE;
                double totalDistance = 0.0;

                Point3D previousPoint = null;
                for (Point3D point : route) {
                    minY = Math.min(minY, point.getY());
                    maxY = Math.max(maxY, point.getY());
                    minX = Math.min(minX, point.getX());
                    maxX = Math.max(maxX, point.getX());
                    minAlt = Math.min(minAlt, point.getZ());
                    maxAlt = Math.max(maxAlt, point.getZ());

                    if (previousPoint != null) {
                        totalDistance += calculateDistance(previousPoint, point);
                    }
                    previousPoint = point;
                }
                
                // Report coordinates in current coordinate system
                if (coordManager.isUsingUtm()) {
                    report.append("Route bounds (UTM):\n");
                    report.append("- Northing (Y): ").append(String.format("%.2f", minY)).append(" to ")
                          .append(String.format("%.2f", maxY)).append(" meters\n");
                    report.append("- Easting (X): ").append(String.format("%.2f", minX)).append(" to ")
                          .append(String.format("%.2f", maxX)).append(" meters\n");
                    report.append("- UTM Zone: ").append(coordManager.getDefaultUtmZone())
                          .append(coordManager.isNorthernHemisphere() ? " North" : " South").append("\n");
                } else {
                    report.append("Route bounds (Geographic):\n");
                    report.append("- Latitude (Y): ").append(String.format("%.6f", minY)).append(" to ")
                          .append(String.format("%.6f", maxY)).append("\n");
                    report.append("- Longitude (X): ").append(String.format("%.6f", minX)).append(" to ")
                          .append(String.format("%.6f", maxX)).append("\n");
                }
                
                report.append("- Altitude: ").append(String.format("%.2f", minAlt)).append(" to ")
                      .append(String.format("%.2f", maxAlt)).append(" meters\n\n");

                report.append("Route statistics:\n");
                report.append("- Total distance: ").append(String.format("%.2f", totalDistance)).append(" meters\n");
                report.append("- Average altitude: ")
                      .append(String.format("%.2f", route.stream().mapToDouble(Point3D::getZ).average().orElse(0)))
                      .append(" meters\n\n");

                report.append("Sample points:\n");
                int maxSamplePoints = Math.min(5, route.size());
                for (int i = 0; i < maxSamplePoints; i++) {
                    Point3D point = route.get(i);
                    if (coordManager.isUsingUtm()) {
                        report.append(String.format("Point %d (UTM): (Easting: %.2f, Northing: %.2f, Alt: %.2f)\n", 
                                  i + 1, point.getX(), point.getY(), point.getZ()));
                    } else {
                        report.append(String.format("Point %d (Geo): (Lon: %.6f, Lat: %.6f, Alt: %.2f)\n", 
                                  i + 1, point.getX(), point.getY(), point.getZ()));
                    }
                }
            }
        } catch (Exception e) {
            report.append("Error reading KML file: ").append(e.getMessage());
        }

        return report.toString();
    }

    public List<Long> readTimestamps(String filename) throws Exception {
        List<Long> timestamps = new ArrayList<>();
        Kml kml = Kml.unmarshal(new File(filename));
        Document document = (Document) kml.getFeature();
        
        // Find all timestamps from Placemarks
        for (Feature feature : document.getFeature()) {
            if (feature instanceof Placemark) {
                Placemark placemark = (Placemark) feature;
                if (placemark.getTimePrimitive() instanceof TimeStamp) {
                    TimeStamp timeStamp = (TimeStamp) placemark.getTimePrimitive();
                    String whenStr = timeStamp.getWhen();
                    try {
                        long timestamp = Long.parseLong(whenStr);
                        timestamps.add(timestamp * 1000L); // Convert to milliseconds
                    } catch (NumberFormatException e) {
                        // If it's not a number, try to parse as ISO 8601
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        long timestamp = sdf.parse(whenStr).getTime();
                        timestamps.add(timestamp);
                    }
                }
            }
        }
        
        // If no timestamps found, create artificial ones
        if (timestamps.isEmpty()) {
            long startTime = 0;
            List<Point3D> route = readRoute(filename);
            for (int i = 0; i < route.size(); i++) {
                timestamps.add(startTime + i * 1000L); // Add 1 second per point
            }
        }
        
        return timestamps;
    }

    private double calculateDistance(Point3D p1, Point3D p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double dz = p2.getZ() - p1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
