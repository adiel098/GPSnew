package com.gps.particlefilter.io;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;
import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.util.List;
import java.util.Map;

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
            
            // Convert to geographic coordinates for KML (KML uses WGS84 lon/lat/alt)
            Point3D geoPosition = CoordinateSystemManager.getInstance().convertToGeographic(position);
            
            Placemark placemark = document.createAndAddPlacemark();
            placemark.setStyleUrl("#particleStyle");
            
            Point point = placemark.createAndSetPoint();
            point.addToCoordinates(geoPosition.getX(), geoPosition.getY(), geoPosition.getZ());
            
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

            // Create styles for different match counts
            // Green for 100% matches (N)
            Style greenStyle = document.createAndAddStyle();
            greenStyle.setId("fullMatchStyle");
            IconStyle greenIcon = greenStyle.createAndSetIconStyle();
            greenIcon.setScale(0.5);
            greenIcon.setColor("ff00ff00"); // Green (aabbggrr format)
            Icon greenIconHref = greenIcon.createAndSetIcon();
            greenIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            // Blue for N-1 matches
            Style blueStyle = document.createAndAddStyle();
            blueStyle.setId("nMinus1MatchStyle");
            IconStyle blueIcon = blueStyle.createAndSetIconStyle();
            blueIcon.setScale(0.5);
            blueIcon.setColor("ffff0000"); // Blue (aabbggrr format)
            Icon blueIconHref = blueIcon.createAndSetIcon();
            blueIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            // Yellow for N-2 matches
            Style yellowStyle = document.createAndAddStyle();
            yellowStyle.setId("nMinus2MatchStyle");
            IconStyle yellowIcon = yellowStyle.createAndSetIconStyle();
            yellowIcon.setScale(0.5);
            yellowIcon.setColor("ff00ffff"); // Yellow
            Icon yellowIconHref = yellowIcon.createAndSetIcon();
            yellowIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            // Red for N-3 matches and lower
            Style redStyle = document.createAndAddStyle();
            redStyle.setId("nMinus3AndLowerMatchStyle");
            IconStyle redIcon = redStyle.createAndSetIconStyle();
            redIcon.setScale(0.5);
            redIcon.setColor("ff0000ff"); // Red
            Icon redIconHref = redIcon.createAndSetIcon();
            redIconHref.setHref("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");

            // Create a folder only for specific time points (1, 75, 160)
            int[] timePointsToShow = {75, 160};
            
            for (int timeIndex : timePointsToShow) {
                // Skip if the timeIndex is out of bounds
                if (timeIndex >= particleHistory.size() || timeIndex >= timestamps.size()) {
                    System.out.println("Warning: Time index " + timeIndex + " is out of bounds. Skipping.");
                    continue;
                }
                
                List<Particle> particles = particleHistory.get(timeIndex);
                long timestamp = timestamps.get(timeIndex);
                
                // Find the reference LOS/NLOS statuses to compare against
                // Use a central particle as reference point
                Map<String, Boolean> referenceStatus = null;
                int totalSatellites = 0;
                
                // Find particle with highest weight to use as reference
                double maxWeight = -1;
                int refIndex = -1;
                
                for (int i = 0; i < particles.size(); i++) {
                    if (particles.get(i).getWeight() > maxWeight) {
                        maxWeight = particles.get(i).getWeight();
                        refIndex = i;
                    }
                }
                
                if (refIndex >= 0) {
                    referenceStatus = particles.get(refIndex).getLosStatus();
                    totalSatellites = referenceStatus.size();
                    System.out.println("Using particle " + refIndex + " with weight " + maxWeight + " as reference");
                } else if (!particles.isEmpty()) {
                    // Fallback to first particle
                    referenceStatus = particles.get(0).getLosStatus();
                    totalSatellites = referenceStatus.size();
                    System.out.println("Using first particle as reference");
                }
                
                // Create a folder for this timestep
                Folder timeFolder = document.createAndAddFolder();
                timeFolder.setName("Time " + (timestamp / 1000));
                
                // Add time information to the folder
                TimeStamp folderTime = timeFolder.createAndSetTimeStamp();
                folderTime.setWhen(String.valueOf(timestamp / 1000));
                
                // Add all particles for this timestep to the folder
                for (Particle p : particles) {
                    Placemark placemark = timeFolder.createAndAddPlacemark();
                    
                    // Get the matches for this particle against the reference particle
                    int matches = 0;
                    
                    if (referenceStatus != null) {
                        // Compare each satellite's LOS/NLOS status with the reference
                        for (Map.Entry<String, Boolean> entry : referenceStatus.entrySet()) {
                            String satelliteId = entry.getKey();
                            Boolean refStatus = entry.getValue();
                            
                            // Check if this particle has the same LOS/NLOS status for this satellite
                            if (p.getLosStatus().containsKey(satelliteId) && 
                                p.getLosStatus().get(satelliteId).equals(refStatus)) {
                                matches++;
                            }
                        }
                    }
                    
                    // Set style based on match count compared to total possible matches
                    if (matches == totalSatellites) { // 100% match (N matches)
                        placemark.setStyleUrl("#fullMatchStyle");
                    } else if (matches == totalSatellites - 1) { // N-1 matches
                        placemark.setStyleUrl("#nMinus1MatchStyle");
                    } else if (matches == totalSatellites - 2) { // N-2 matches
                        placemark.setStyleUrl("#nMinus2MatchStyle");
                    } else { // N-3 matches or lower
                        placemark.setStyleUrl("#nMinus3AndLowerMatchStyle");
                    }
                    
                    placemark.setDescription("Matches: " + matches + "/" + totalSatellites + 
                        "\nWeight: " + p.getWeight() + 
                        "\nLOS/NLOS: " + p.getLosNlosCount() + 
                        "\nMatch level: " + (matches == totalSatellites ? "100% (N)" : 
                                           matches == totalSatellites - 1 ? "N-1" : 
                                           matches == totalSatellites - 2 ? "N-2" : "N-3 or lower"));

                    // Add coordinates (convert to geographic for KML)
                    Point point = placemark.createAndSetPoint();
                    point.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
                    Point3D pos = p.getPosition();
                    Point3D geoPos = CoordinateSystemManager.getInstance().convertToGeographic(pos);
                    point.addToCoordinates(geoPos.getX(), geoPos.getY(), geoPos.getZ());
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
                Point3D geoPoint = CoordinateSystemManager.getInstance().convertToGeographic(point);
                kmlPoint.addToCoordinates(geoPoint.getX(), geoPoint.getY(), geoPoint.getZ());
            }

            // Add line if requested
            if (drawLine) {
                Placemark linePlacemark = document.createAndAddPlacemark();
                linePlacemark.setStyleUrl("#lineStyle");
                
                LineString ls = linePlacemark.createAndSetLineString();
                ls.setAltitudeMode(AltitudeMode.RELATIVE_TO_GROUND);
                ls.setExtrude(true);
                
                // Convert route points to geographic coordinates for KML
                for (Point3D point : route) {
                    Point3D geoPoint = CoordinateSystemManager.getInstance().convertToGeographic(point);
                    ls.addToCoordinates(geoPoint.getX(), geoPoint.getY(), geoPoint.getZ());
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
