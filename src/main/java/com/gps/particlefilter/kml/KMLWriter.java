package com.gps.particlefilter.kml;

import com.gps.particlefilter.model.*;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
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
            placemark.setName("Particle " + i);
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
    
    /**
     * Write a route (list of points) to a KML file with the specified color
     * 
     * @param route List of points in the route
     * @param filename Output KML filename
     * @param color Color name (red, green, blue, etc.)
     */
    public void writeRouteToKML(List<Point3D> route, String filename, String color) {
        final Kml kml = new Kml();
        Document document = kml.createAndSetDocument();
        
        // Create style for route line
        Style routeStyle = document.createAndAddStyle();
        routeStyle.setId("routeStyle");
        
        LineStyle lineStyle = routeStyle.createAndSetLineStyle();
        
        // Set color based on input
        if ("red".equalsIgnoreCase(color)) {
            lineStyle.setColor("ff0000ff"); // AABBGGRR format
        } else if ("green".equalsIgnoreCase(color)) {
            lineStyle.setColor("ff00ff00");
        } else if ("blue".equalsIgnoreCase(color)) {
            lineStyle.setColor("ffff0000");
        } else if ("yellow".equalsIgnoreCase(color)) {
            lineStyle.setColor("ff00ffff");
        } else {
            lineStyle.setColor("ffffffff"); // White by default
        }
        
        lineStyle.setWidth(4.0);
        
        // Create a placemark for the route
        Placemark routePlacemark = document.createAndAddPlacemark();
        routePlacemark.setName("Route");
        routePlacemark.setStyleUrl("#routeStyle");
        
        // Add route as LineString
        LineString lineString = routePlacemark.createAndSetLineString();
        lineString.setExtrude(true);
        lineString.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
        
        // Add each point to the coordinates
        for (Point3D point : route) {
            lineString.addToCoordinates(point.getX(), point.getY(), point.getZ());
        }
        
        // Also add individual points for better visualization
        for (int i = 0; i < route.size(); i++) {
            Point3D point = route.get(i);
            
            Placemark pointPlacemark = document.createAndAddPlacemark();
            pointPlacemark.setName("Point " + i);
            
            Style pointStyle = document.createAndAddStyle();
            pointStyle.setId("point" + i);
            
            IconStyle pointIconStyle = pointStyle.createAndSetIconStyle();
            pointIconStyle.setScale(0.5);
            pointIconStyle.createAndSetIcon().setHref("http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png");
            
            if ("red".equalsIgnoreCase(color)) {
                pointIconStyle.setColor("ff0000ff");
            } else if ("green".equalsIgnoreCase(color)) {
                pointIconStyle.setColor("ff00ff00");
            } else if ("blue".equalsIgnoreCase(color)) {
                pointIconStyle.setColor("ffff0000");
            } else if ("yellow".equalsIgnoreCase(color)) {
                pointIconStyle.setColor("ff00ffff");
            }
            
            pointPlacemark.setStyleUrl("#point" + i);
            
            Point kmlPoint = pointPlacemark.createAndSetPoint();
            kmlPoint.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
            kmlPoint.addToCoordinates(point.getX(), point.getY(), point.getZ());
        }
        
        try {
            kml.marshal(new File(filename));
            System.out.println("Route written to " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
