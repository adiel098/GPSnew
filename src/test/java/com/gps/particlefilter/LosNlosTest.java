package com.gps.particlefilter;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.los.LosCalculator;
import com.gps.particlefilter.config.Configuration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests LOS/NLOS functionality by creating a simple simulation of a point, wall and satellite
 * and checking if the satellite is visible or blocked by the wall
 */
public class LosNlosTest {
    private static final double WALL_DISTANCE = 15.0; // Distance from observer to wall in meters
    private static final double WALL_LENGTH = 20.0; // Wall length in meters
    
    // Conversion factor - approximately how many meters per degree
    private static final double METERS_PER_DEGREE = 111300.0;

    public static void main(String[] args) {
        try {
            Configuration config = Configuration.getInstance();
            double azimuth = config.getSimulationSatelliteAzimuth();
            double elevation = config.getSimulationSatelliteElevation();
            testLosNlos(azimuth, elevation, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Performs LOS/NLOS test with given azimuth and elevation angles
     * @param azimuth - Azimuth angle in degrees
     * @param elevation - Elevation angle in degrees
     * @param generateKmlFile - Whether to generate KML file
     */
    private static void testLosNlos(double azimuth, double elevation, boolean generateKmlFile) {
        try {
            Configuration config = Configuration.getInstance();
            double observerHeight = config.getSimulationObserverHeight();
            double wallHeight = config.getSimulationWallHeight();
            
            // Create observer point at location (0,0)
            Point3D userPoint = new Point3D(0, 0, observerHeight);
            
            // Calculate wall points at WALL_DISTANCE from observer
            double wallCenterX = WALL_DISTANCE * Math.sin(Math.toRadians(azimuth));
            double wallCenterY = WALL_DISTANCE * Math.cos(Math.toRadians(azimuth));
            
            // Calculate wall endpoints (perpendicular to azimuth direction)
            double wallHalfLength = WALL_LENGTH / 2.0;
            double wallDx = wallHalfLength * Math.cos(Math.toRadians(azimuth)); // Perpendicular to line of sight
            double wallDy = -wallHalfLength * Math.sin(Math.toRadians(azimuth)); // Perpendicular to line of sight
            
            Point3D wallStart = new Point3D(
                wallCenterX + wallDx,
                wallCenterY + wallDy,
                0 // Wall base
            );
            
            Point3D wallEnd = new Point3D(
                wallCenterX - wallDx,
                wallCenterY - wallDy,
                0 // Wall base
            );
            
            // Create building (wall) for testing
            List<Point3D> vertices = new ArrayList<>();
            vertices.add(wallStart);
            vertices.add(wallEnd);
            vertices.add(new Point3D(wallEnd.getX(), wallEnd.getY(), wallHeight));
            vertices.add(new Point3D(wallStart.getX(), wallStart.getY(), wallHeight));
            vertices.add(wallStart); // Close the polygon
            Building wall = new Building(vertices, wallHeight);
            List<Building> buildings = new ArrayList<>();
            buildings.add(wall);
            
            // Create satellite in the given direction and angle
            Point3D satPosition = null; // Don't need precise satellite position for simulation
            Satellite satellite = new Satellite("TEST", satPosition, azimuth, elevation);
            List<Satellite> satellites = new ArrayList<>();
            satellites.add(satellite);
            
            // Calculate LOS / NLOS
            LosCalculator losCalculator = new LosCalculator(buildings, satellites);
            Map<String, Boolean> losResult = losCalculator.calculateLOS(userPoint);
            boolean isLos = losResult.get(satellite.getName());
            
            // Calculate intersection point for KML
            LosCalculator.LosResult detailedResult = losCalculator.computeLosDetailedWithIntersection(userPoint, wall, satellite);
            
            // Display LOS/NLOS status and required height information
            System.out.println("\n=== LOS/NLOS ANALYSIS RESULTS ===");
            System.out.println("Satellite: " + satellite.getName());
            System.out.println("Azimuth: " + String.format("%.1f°", azimuth));
            System.out.println("Elevation: " + String.format("%.1f°", elevation));
            System.out.println("Observer Height: " + String.format("%.1f m", observerHeight));
            System.out.println("Wall Height: " + String.format("%.1f m", wallHeight));
            System.out.println("Status: " + (isLos ? "LOS (Line of Sight)" : "NLOS (Non-Line of Sight)"));
            
            if (!isLos) {
                double requiredHeight = detailedResult.getDeltaH();
                if (requiredHeight >= 0) {
                    System.out.println("Additional height needed for LOS: " + String.format("%.2f m", requiredHeight));
                    System.out.println("Required observer height: " + String.format("%.2f m", observerHeight + requiredHeight));
                    if (requiredHeight == 0.0) {
                        System.out.println("Note: Ray grazes the top of the obstacle (exactly at wall height)");
                    }
                } else {
                    System.out.println("Ray passes over the obstacle (unexpected NLOS result)");
                }
            } else {
                System.out.println("Clear line of sight - no additional height needed");
            }
            
            if (detailedResult.getIntersection() != null) {
                System.out.println("Ray intersects wall at: (" + 
                    String.format("%.2f", detailedResult.getIntersection().getX()) + ", " +
                    String.format("%.2f", detailedResult.getIntersection().getY()) + ")");
                System.out.println("Ray height at intersection: " + String.format("%.2f m", detailedResult.getRayHeight()));
            }
            System.out.println("=== END ANALYSIS ===\n");
            
            // Create KML file if required
            if (generateKmlFile) {
                String filename = config.getOutputLosSimulationKml();
                generateKml(userPoint, wall, satellite, isLos, 
                          detailedResult.getIntersection(), detailedResult.getRayHeight(), filename);
                System.out.println("KML file generated successfully: " + filename);          
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Generates KML file for visual representation of LOS/NLOS situation
     */
    private static void generateKml(Point3D userPoint, Building wall, Satellite satellite, 
                                  boolean isLos, Point2D intersectionPoint, double zAtIntersection, String filename) {
        try {
            // Convert points to geographic coordinates
            double baseLat = 31.771959; // Jerusalem
            double baseLon = 35.217018;
            
            // Create file with full path (filename already includes path)
            File kmlFile = new File(filename);
            File parentDir = kmlFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            FileWriter writer = new FileWriter(kmlFile);
            
            // Write KML
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
            writer.write("<Document>\n");
            
            // Define styles
            // Style for wall
            writer.write("<Style id=\"wallStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff0000ff</color>\n");
            writer.write("    <width>2</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("  <PolyStyle>\n");
            writer.write("    <color>4d0000ff</color>\n");
            writer.write("    <fill>1</fill>\n");
            writer.write("    <outline>1</outline>\n");
            writer.write("  </PolyStyle>\n");
            writer.write("</Style>\n");
            
            // Style for line of sight - green for LOS, red for NLOS
            writer.write("<Style id=\"losStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff00ff00</color>\n");
            writer.write("    <width>1</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");
            
            writer.write("<Style id=\"nlosStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>ff0000ff</color>\n");
            writer.write("    <width>1</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");
            
            writer.write("<Style id=\"blockedStyle\">\n");
            writer.write("  <LineStyle>\n");
            writer.write("    <color>4f0000ff</color>\n");
            writer.write("    <width>2</width>\n");
            writer.write("  </LineStyle>\n");
            writer.write("</Style>\n");
            
            // Convert observer and wall points to geographic coordinates
            double userLat = baseLat + userPoint.getY() / METERS_PER_DEGREE;
            double userLon = baseLon + userPoint.getX() / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
            
            // Add observer point
            writer.write("<Placemark>\n");
            writer.write("  <name>Observer (" + String.format("%.2f", userPoint.getZ()) + " m)</name>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + userLon + "," + userLat + "," + userPoint.getZ() + "</coordinates>\n");
            writer.write("  </Point>\n");
            writer.write("</Placemark>\n");

            // Add the wall
            writer.write("<Placemark>\n");
            writer.write("  <name>Wall</name>\n");
            writer.write("  <styleUrl>#wallStyle</styleUrl>\n");
            writer.write("  <Polygon>\n");
            writer.write("    <extrude>1</extrude>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <outerBoundaryIs>\n");
            writer.write("      <LinearRing>\n");
            writer.write("        <coordinates>\n");
            
            // Convert all wall points to geographic coordinates and write them
            for (Point3D vertex : wall.getVertices()) {
                double lat = baseLat + vertex.getY() / METERS_PER_DEGREE;
                double lon = baseLon + vertex.getX() / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
                writer.write("          " + lon + "," + lat + "," + vertex.getZ() + "\n");
            }
            
            writer.write("        </coordinates>\n");
            writer.write("      </LinearRing>\n");
            writer.write("    </outerBoundaryIs>\n");
            writer.write("  </Polygon>\n");
            writer.write("</Placemark>\n");

            // Calculate line of sight endpoint and satellite position
            double rayLength = 500.0; // Line of sight length in meters
            double satelliteDistance = 500.0; // Satellite distance in meters (farther than line of sight)
            
            // Calculate line of sight endpoint (or intersection point if exists)
            double endLat, endLon, endZ;
            if (intersectionPoint != null) {
                endLat = baseLat + intersectionPoint.getY() / METERS_PER_DEGREE;
                endLon = baseLon + intersectionPoint.getX() / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
                endZ = zAtIntersection;
            } else {
                double dx = rayLength * Math.sin(Math.toRadians(satellite.getAzimuth()));
                double dy = rayLength * Math.cos(Math.toRadians(satellite.getAzimuth()));
                double dz = rayLength * Math.tan(Math.toRadians(satellite.getElevation()));
                endLat = baseLat + (userPoint.getY() + dy) / METERS_PER_DEGREE;
                endLon = baseLon + (userPoint.getX() + dx) / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
                endZ = userPoint.getZ() + dz;
            }

            // Calculate satellite position (always at fixed large distance)
            double satDx = satelliteDistance * Math.sin(Math.toRadians(satellite.getAzimuth()));
            double satDy = satelliteDistance * Math.cos(Math.toRadians(satellite.getAzimuth()));
            double satDz = satelliteDistance * Math.tan(Math.toRadians(satellite.getElevation()));
            double satLat = baseLat + (userPoint.getY() + satDy) / METERS_PER_DEGREE;
            double satLon = baseLon + (userPoint.getX() + satDx) / (METERS_PER_DEGREE * Math.cos(Math.toRadians(baseLat)));
            double satZ = userPoint.getZ() + satDz;

            // Add line of sight - green for LOS, red for NLOS
            writer.write("<Placemark>\n");
            writer.write("  <name>" + (isLos ? "Line of Sight (LOS)" : "Line of Sight (NLOS)") + "</name>\n");
            writer.write("  <styleUrl>" + (isLos ? "#losStyle" : "#nlosStyle") + "</styleUrl>\n");
            writer.write("  <LineString>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>\n");
            writer.write("      " + userLon + "," + userLat + "," + userPoint.getZ() + "\n");
            writer.write("      " + satLon + "," + satLat + "," + satZ + "\n");
            writer.write("    </coordinates>\n");
            writer.write("  </LineString>\n");
            writer.write("</Placemark>\n");

            // Add the satellite (always at fixed position)
            writer.write("<Placemark>\n");
            writer.write("  <name>" + satellite.getName() + "</name>\n");
            writer.write("  <Style>\n");
            writer.write("    <IconStyle>\n");
            writer.write("      <scale>1.0</scale>\n");
            writer.write("      <Icon>\n");
            writer.write("        <href>http://maps.google.com/mapfiles/kml/shapes/star.png</href>\n");
            writer.write("      </Icon>\n");
            writer.write("    </IconStyle>\n");
            writer.write("  </Style>\n");
            writer.write("  <Point>\n");
            writer.write("    <altitudeMode>relativeToGround</altitudeMode>\n");
            writer.write("    <coordinates>" + satLon + "," + satLat + "," + satZ + "</coordinates>\n");
            writer.write("  </Point>\n");
            writer.write("</Placemark>\n");

            writer.write("</Document>\n");
            writer.write("</kml>");
            writer.close();
            
        } catch (IOException e) {
            System.out.println("Error generating KML file: " + e.getMessage());
        }
    }
}
