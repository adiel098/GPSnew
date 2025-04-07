package com.gps.particlefilter.kml;

import com.gps.particlefilter.model.*;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BuildingKMLReader {
    public List<Building> readBuildings(String filename) {
        List<Building> buildings = new ArrayList<>();
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
                                    vertices.add(new Point3D(coord.getLongitude(), 
                                                           coord.getLatitude(), 
                                                           coord.getAltitude()));
                                }
                                
                                // Get height from the first point's altitude
                                double height = coordinates.get(0).getAltitude();
                                buildings.add(new Building(vertices, height));
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
}
