package com.gps.particlefilter.util;

import com.gps.particlefilter.model.Point3D;
import org.geotools.referencing.CRS;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.api.referencing.FactoryException;

/**
 * Professional coordinate conversion utility using GeoTools library
 * Supports conversion between Geographic (WGS84) and UTM coordinate systems
 */
public class CoordinateConverter {
    
    // Coordinate Reference Systems
    private CoordinateReferenceSystem wgs84;
    private CoordinateReferenceSystem utmCrs;
    
    // Math transforms for conversion
    private MathTransform toUTM;
    private MathTransform toWGS84;
    
    // Default UTM zone and hemisphere (Israel: UTM Zone 36N)
    private int utmZone = 36;
    private boolean isNorthern = true;
    
    /**
     * Constructor initializes GeoTools coordinate reference systems
     */
    public CoordinateConverter() {
        try {
            initializeCoordinateSystems();
        } catch (FactoryException e) {
            throw new RuntimeException("Failed to initialize coordinate systems: " + e.getMessage(), e);
        }
    }
    
    /**
     * Constructor with custom UTM zone
     * @param utmZone UTM zone (1-60)
     * @param isNorthern true for northern hemisphere, false for southern
     */
    public CoordinateConverter(int utmZone, boolean isNorthern) {
        this.utmZone = utmZone;
        this.isNorthern = isNorthern;
        try {
            initializeCoordinateSystems();
        } catch (FactoryException e) {
            throw new RuntimeException("Failed to initialize coordinate systems: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize coordinate reference systems and transformations using GeoTools
     */
    private void initializeCoordinateSystems() throws FactoryException {
        // WGS84 Geographic coordinate system (EPSG:4326)
        wgs84 = CRS.decode("EPSG:4326");
        
        // UTM coordinate system (EPSG:326XX for northern, 327XX for southern hemisphere)
        String utmEpsg;
        if (isNorthern) {
            utmEpsg = String.format("EPSG:326%02d", utmZone);
        } else {
            utmEpsg = String.format("EPSG:327%02d", utmZone);
        }
        utmCrs = CRS.decode(utmEpsg);
        
        // Create transformation objects
        toUTM = CRS.findMathTransform(wgs84, utmCrs, false);
        toWGS84 = CRS.findMathTransform(utmCrs, wgs84, false);
    }
    
    /**
     * Convert latitude/longitude to UTM coordinates using GeoTools
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees  
     * @param alt Altitude in meters
     * @return Point3D with UTM easting (x), northing (y), and altitude (z) in meters
     */
    public Point3D latLonToUtm(double lat, double lon, double alt) {
        try {
            // Create coordinate array (note: GeoTools uses lon,lat order for geographic coordinates)
            double[] geoCoord = new double[] {lon, lat};
            double[] utmCoord = new double[2];
            
            // Transform coordinate
            toUTM.transform(geoCoord, 0, utmCoord, 0, 1);
            
            return new Point3D(utmCoord[0], utmCoord[1], alt);
            
        } catch (TransformException e) {
            throw new RuntimeException("Failed to convert lat/lon to UTM: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert UTM coordinates to latitude/longitude using GeoTools
     * @param easting Easting in meters (x)
     * @param northing Northing in meters (y) 
     * @param zone UTM zone (1-60)
     * @param isNorthern true if in northern hemisphere, false for southern
     * @param alt Altitude in meters
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D utmToLatLon(double easting, double northing, int zone, boolean isNorthern, double alt) {
        try {
            // If zone or hemisphere differs from current, reinitialize
            if (zone != this.utmZone || isNorthern != this.isNorthern) {
                this.utmZone = zone;
                this.isNorthern = isNorthern;
                initializeCoordinateSystems();
            }
            
            // Create UTM coordinate array
            double[] utmCoord = new double[] {easting, northing};
            double[] geoCoord = new double[2];
            
            // Transform coordinate
            toWGS84.transform(utmCoord, 0, geoCoord, 0, 1);
            
            // Return as Point3D with longitude (x), latitude (y), altitude (z)
            return new Point3D(geoCoord[0], geoCoord[1], alt);
            
        } catch (TransformException | FactoryException e) {
            throw new RuntimeException("Failed to convert UTM to lat/lon: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert UTM coordinates to latitude/longitude using current zone/hemisphere
     * @param easting Easting in meters (x)
     * @param northing Northing in meters (y)
     * @param alt Altitude in meters
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D utmToLatLon(double easting, double northing, double alt) {
        return utmToLatLon(easting, northing, utmZone, isNorthern, alt);
    }
    
    /**
     * Convert a Point3D from lat/lon to UTM
     * @param geoPoint Point3D with longitude (x), latitude (y), and altitude (z)
     * @return Point3D with UTM easting (x), northing (y), and altitude (z)
     */
    public Point3D convertToUtm(Point3D geoPoint) {
        return latLonToUtm(geoPoint.getY(), geoPoint.getX(), geoPoint.getZ());
    }
    
    /**
     * Convert a Point3D from UTM to lat/lon using current zone/hemisphere
     * @param utmPoint Point3D with UTM easting (x), northing (y), and altitude (z)
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D convertToLatLon(Point3D utmPoint) {
        return utmToLatLon(utmPoint.getX(), utmPoint.getY(), utmPoint.getZ());
    }
    
    /**
     * Convert a Point3D from UTM to lat/lon with specified zone and hemisphere
     * @param utmPoint Point3D with UTM easting (x), northing (y), and altitude (z)
     * @param zone UTM zone (1-60)
     * @param isNorthern true if in northern hemisphere, false for southern
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D convertToLatLon(Point3D utmPoint, int zone, boolean isNorthern) {
        return utmToLatLon(utmPoint.getX(), utmPoint.getY(), zone, isNorthern, utmPoint.getZ());
    }
    
    /**
     * Get the current UTM zone
     * @return Current UTM zone (1-60)
     */
    public int getCurrentZone() {
        return utmZone;
    }
    
    /**
     * Check if current coordinates are in northern hemisphere
     * @return true if in northern hemisphere, false for southern
     */
    public boolean isNorthern() {
        return isNorthern;
    }
    
    /**
     * Set UTM zone and hemisphere
     * @param zone UTM zone (1-60)
     * @param isNorthern true for northern hemisphere, false for southern
     */
    public void setUtmZone(int zone, boolean isNorthern) {
        if (zone != this.utmZone || isNorthern != this.isNorthern) {
            this.utmZone = zone;
            this.isNorthern = isNorthern;
            try {
                initializeCoordinateSystems();
            } catch (FactoryException e) {
                throw new RuntimeException("Failed to update UTM zone: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get the EPSG code for the current UTM coordinate system
     * @return EPSG code as string (e.g., "EPSG:32636" for UTM 36N)
     */
    public String getUtmEpsgCode() {
        if (isNorthern) {
            return String.format("EPSG:326%02d", utmZone);
        } else {
            return String.format("EPSG:327%02d", utmZone);
        }
    }
    
    /**
     * Get information about the coordinate systems being used
     * @return String description of coordinate systems
     */
    public String getCoordinateSystemInfo() {
        return String.format("WGS84 (EPSG:4326) ↔ UTM Zone %d%s (%s)", 
                           utmZone, 
                           isNorthern ? "N" : "S", 
                           getUtmEpsgCode());
    }
}