package com.gps.particlefilter.io;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SatelliteKMLReader {
    public List<Satellite> readSatellites(String filename) {
        List<Satellite> satellites = new ArrayList<>();
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        
        try {
            Kml kml = Kml.unmarshal(new File(filename));
            Document document = (Document) kml.getFeature();
            
            Pattern pattern = Pattern.compile("Azimuth: ([-\\d.]+)°, Elevation: ([-\\d.]+)°, Height: ([-\\d.]+) km");
            
            for (Feature feature : document.getFeature()) {
                if (feature instanceof Placemark) {
                    Placemark placemark = (Placemark) feature;
                    if (placemark.getGeometry() instanceof Point) {
                        Point point = (Point) placemark.getGeometry();
                        List<Coordinate> coordinates = point.getCoordinates();
                        if (!coordinates.isEmpty()) {
                            Coordinate coord = coordinates.get(0);
                            
                            // Extract azimuth and elevation from description
                            double azimuth = 0;
                            double elevation = 0;
                            String description = placemark.getDescription();
                            if (description != null) {
                                Matcher matcher = pattern.matcher(description);
                                if (matcher.find()) {
                                    azimuth = Double.parseDouble(matcher.group(1));
                                    elevation = Double.parseDouble(matcher.group(2));
                                }
                            }
                            
                            // Create geographic point first (longitude=x, latitude=y)
                            Point3D geoPoint = new Point3D(coord.getLongitude(), 
                                                          coord.getLatitude(), 
                                                          coord.getAltitude());
                            
                            // Convert to UTM coordinates for consistent meter-based calculations
                            Point3D convertedPoint = coordManager.convertFromGeographic(geoPoint);
                            
                            satellites.add(new Satellite(
                                placemark.getName(),
                                convertedPoint,
                                azimuth,
                                elevation
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return satellites;
    }

    public String generateValidationReport(String filename) {
        StringBuilder report = new StringBuilder();
        report.append("Satellite KML Validation Report\n");
        report.append("=============================\n\n");

        try {
            List<Satellite> satellites = readSatellites(filename);
            report.append("Total satellites found: ").append(satellites.size()).append("\n\n");

            for (Satellite satellite : satellites) {
                report.append("Satellite: ").append(satellite.getName()).append("\n");
                report.append("- Position:\n");
                report.append("  * Longitude: ").append(String.format("%.6f", satellite.getPosition().getX())).append("\n");
                report.append("  * Latitude: ").append(String.format("%.6f", satellite.getPosition().getY())).append("\n");
                report.append("  * Altitude: ").append(String.format("%.2f", satellite.getPosition().getZ())).append(" meters\n");
                report.append("- Azimuth: ").append(String.format("%.2f", satellite.getAzimuth())).append("°\n");
                report.append("- Elevation: ").append(String.format("%.2f", satellite.getElevation())).append("°\n\n");
            }
        } catch (Exception e) {
            report.append("Error reading KML file: ").append(e.getMessage());
        }

        return report.toString();
    }
}
