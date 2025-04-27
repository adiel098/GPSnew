package com.gps.particlefilter.kml;

import com.gps.particlefilter.model.*;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class KMLWriter {
    
    public void writeParticlesToKML(List<Particle> particles, String filename) {
        final Kml kml = new Kml();
        Document document = kml.createAndSetDocument();
        
        // Create style for particles
        Style particleStyle = document.createAndAddStyle();
        particleStyle.setId("particleStyle");
        
        IconStyle iconStyle = particleStyle.createAndSetIconStyle();
        iconStyle.setScale(0.5);
        iconStyle.createAndSetIcon().setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");
        
        // Add each particle as a placemark
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            Point3D position = particle.getPosition();
            
            Placemark placemark = document.createAndAddPlacemark();
            placemark.setStyleUrl("#particleStyle");
            
            Point point = placemark.createAndSetPoint();
            point.addToCoordinates(position.getX(), position.getY(), position.getZ());
            
            // Add weight information
            ExtendedData extendedData = placemark.createAndSetExtendedData();
            Data weightData = extendedData.createAndAddData("weight");
            weightData.setValue(String.valueOf(particle.getWeight()));
        }
        
        try {
            kml.marshal(new File(filename));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void writeParticleHistoryToKML(List<List<Particle>> particleHistory, List<Long> timestamps, String filename) {
        try {
            // Debug info
            System.out.println("In KMLWriter - Particle history size: " + particleHistory.size());
            System.out.println("In KMLWriter - Timestamps size: " + timestamps.size());
            
            Kml kml = KmlFactory.createKml();
            Document document = kml.createAndSetDocument();

            // Create styles for different weight ranges
            Style greenStyle = document.createAndAddStyle();
            greenStyle.setId("highWeightStyle");
            IconStyle greenIcon = greenStyle.createAndSetIconStyle();
            greenIcon.setScale(0.5);
            greenIcon.setColor("ff00ff00"); // Green (aabbggrr format)
            Icon greenIconHref = greenIcon.createAndSetIcon();
            greenIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            Style yellowStyle = document.createAndAddStyle();
            yellowStyle.setId("medWeightStyle");
            IconStyle yellowIcon = yellowStyle.createAndSetIconStyle();
            yellowIcon.setScale(0.5);
            yellowIcon.setColor("ff00ffff"); // Yellow
            Icon yellowIconHref = yellowIcon.createAndSetIcon();
            yellowIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            Style redStyle = document.createAndAddStyle();
            redStyle.setId("lowWeightStyle");
            IconStyle redIcon = redStyle.createAndSetIconStyle();
            redIcon.setScale(0.5);
            redIcon.setColor("ff0000ff"); // Red
            Icon redIconHref = redIcon.createAndSetIcon();
            redIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            // Create a folder for each timestep
            for (int t = 0; t < particleHistory.size() && t < timestamps.size(); t++) {
                List<Particle> particles = particleHistory.get(t);
                long timestamp = timestamps.get(t);
                
                // Calculate weight thresholds for this timestep
                double[] weights = particles.stream()
                    .mapToDouble(Particle::getWeight)
                    .sorted()
                    .toArray();
                double lowThreshold = weights[(int)(weights.length * 0.33)];
                double highThreshold = weights[(int)(weights.length * 0.66)];
                
                // Create a folder for this timestep
                Folder timeFolder = document.createAndAddFolder();
                timeFolder.setName("Time " + (timestamp / 1000));
                
                // Add time information to the folder
                TimeStamp folderTime = timeFolder.createAndSetTimeStamp();
                folderTime.setWhen(String.valueOf(timestamp / 1000));
                
                // Add all particles for this timestep to the folder
                for (Particle p : particles) {
                    Placemark placemark = timeFolder.createAndAddPlacemark();
                    
                    // Set style based on weight
                    double weight = p.getWeight();
                    if (weight >= highThreshold) {
                        placemark.setStyleUrl("#highWeightStyle");
                    } else if (weight >= lowThreshold) {
                        placemark.setStyleUrl("#medWeightStyle");
                    } else {
                        placemark.setStyleUrl("#lowWeightStyle");
                    }
                    
                    placemark.setDescription("Weight: " + weight + 
                        "\nRange: " + (weight >= highThreshold ? "High" : 
                                     weight >= lowThreshold ? "Medium" : "Low"));

                    // Add coordinates
                    Point point = placemark.createAndSetPoint();
                    point.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
                    Point3D pos = p.getPosition();
                    point.addToCoordinates(pos.getX(), pos.getY(), pos.getZ());
                }
            }

            kml.marshal(new File(filename));
            System.out.println("Successfully wrote particles to " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Write a route (list of points) to a KML file with the specified color
     * 
     * @param route List of points in the route
     * @param timestamps List of timestamps for each point in the route
     * @param filename Output KML filename
     * @param drawLine Whether to draw a line between the points
     */
    public void writeRouteToKML(List<Point3D> route, List<Long> timestamps, String filename, boolean drawLine) {
        try {
            Kml kml = KmlFactory.createKml();
            Document document = kml.createAndSetDocument();
            
            // Create style for points
            Style pointStyle = document.createAndAddStyle();
            pointStyle.setId("pointStyle");
            IconStyle iconStyle = pointStyle.createAndSetIconStyle();
            iconStyle.setScale(0.5);
            Icon icon = iconStyle.createAndSetIcon();
            icon.setHref("http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png");

            // Create style for line
            if (drawLine) {
                Style lineStyle = document.createAndAddStyle();
                lineStyle.setId("lineStyle");
                LineStyle kmlLineStyle = lineStyle.createAndSetLineStyle();
                kmlLineStyle.setColor("ff0000ff");
                kmlLineStyle.setWidth(2.0);
            }

            // Add points with timestamps
            for (int i = 0; i < route.size() && (timestamps == null || i < timestamps.size()); i++) {
                Placemark placemark = document.createAndAddPlacemark();
                placemark.setStyleUrl("#pointStyle");
                
                // Add time information if available
                if (timestamps != null) {
                    TimeStamp timeStamp = placemark.createAndSetTimeStamp();
                    timeStamp.setWhen(String.valueOf(timestamps.get(i) / 1000));
                }
                
                Point kmlPoint = placemark.createAndSetPoint();
                kmlPoint.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
                Point3D point = route.get(i);
                kmlPoint.addToCoordinates(point.getX(), point.getY(), point.getZ());
            }

            // Add line if requested
            if (drawLine) {
                Placemark linePlacemark = document.createAndAddPlacemark();
                linePlacemark.setStyleUrl("#lineStyle");
                
                LineString lineString = linePlacemark.createAndSetLineString();
                lineString.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
                lineString.setExtrude(true);
                
                for (Point3D point : route) {
                    lineString.addToCoordinates(point.getX(), point.getY(), point.getZ());
                }
            }

            kml.marshal(new File(filename));
            System.out.println("Route written to " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Overload for backward compatibility
    public void writeRouteToKML(List<Point3D> route, String filename, boolean drawLine) {
        writeRouteToKML(route, null, filename, drawLine);
    }
}
