package com.gps.particlefilter.util;

import com.gps.particlefilter.model.Point3D;
import com.gps.particlefilter.config.Configuration;

/**
 * Manager class for handling coordinate system conversions throughout the application.
 * Provides a centralized way to convert between geographic (lat/lon) and UTM coordinates.
 */
public class CoordinateSystemManager {
    private static CoordinateSystemManager instance;
    private final CoordinateConverter converter;
    private boolean useUtm;
    private int defaultUtmZone;
    private boolean isNorthernHemisphere;
    
    private CoordinateSystemManager() {
        // Load configuration for coordinate system settings
        Configuration config = Configuration.getInstance();
        
        // Initialize with configuration or defaults
        useUtm = true; // Always use UTM for particle filter calculations
        defaultUtmZone = parseUtmZoneFromEpsg(config.getCoordinatesTargetEpsg());
        isNorthernHemisphere = parseHemisphereFromEpsg(config.getCoordinatesTargetEpsg());
        
        // Initialize converter with configured UTM zone
        converter = new CoordinateConverter(defaultUtmZone, isNorthernHemisphere);
        
        System.out.println("Coordinate System initialized: " + converter.getCoordinateSystemInfo());
    }
    
    /**
     * Get the singleton instance of the coordinate system manager
     * 
     * @return The coordinate system manager instance
     */
    public static synchronized CoordinateSystemManager getInstance() {
        if (instance == null) {
            instance = new CoordinateSystemManager();
        }
        return instance;
    }
    
    /**
     * Set whether to use UTM coordinates or geographic coordinates
     * 
     * @param useUtm true to use UTM coordinates, false to use geographic coordinates
     */
    public void setUseUtm(boolean useUtm) {
        this.useUtm = useUtm;
    }
    
    /**
     * Check if the system is using UTM coordinates
     * 
     * @return true if using UTM coordinates, false if using geographic coordinates
     */
    public boolean isUsingUtm() {
        return useUtm;
    }
    
    /**
     * Set the default UTM zone and hemisphere
     * 
     * @param zone UTM zone (1-60)
     * @param isNorthern true if in northern hemisphere, false for southern
     */
    public void setDefaultUtmZone(int zone, boolean isNorthern) {
        this.defaultUtmZone = zone;
        this.isNorthernHemisphere = isNorthern;
    }
    
    /**
     * Get the default UTM zone
     * 
     * @return The default UTM zone
     */
    public int getDefaultUtmZone() {
        return defaultUtmZone;
    }
    
    /**
     * Check if the default hemisphere is northern
     * 
     * @return true if northern hemisphere, false if southern
     */
    public boolean isNorthernHemisphere() {
        return isNorthernHemisphere;
    }
    
    /**
     * Convert a geographic point to the current coordinate system
     * 
     * @param geoPoint Point3D with longitude (x), latitude (y), and altitude (z)
     * @return Point3D in the current coordinate system
     */
    public Point3D convertFromGeographic(Point3D geoPoint) {
        if (useUtm) {
            return converter.convertToUtm(geoPoint);
        }
        return geoPoint;
    }
    
    /**
     * Convert a UTM point to the current coordinate system
     * 
     * @param utmPoint Point3D with UTM easting (x), northing (y), and altitude (z)
     * @return Point3D in the current coordinate system
     */
    public Point3D convertFromUtm(Point3D utmPoint) {
        if (useUtm) {
            return utmPoint;
        }
        return converter.convertToLatLon(utmPoint, defaultUtmZone, isNorthernHemisphere);
    }
    
    /**
     * Convert a point from the current coordinate system to geographic coordinates
     * 
     * @param point Point3D in the current coordinate system
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D convertToGeographic(Point3D point) {
        if (useUtm) {
            return converter.convertToLatLon(point, defaultUtmZone, isNorthernHemisphere);
        }
        return point;
    }
    
    /**
     * Convert a point from the current coordinate system to UTM coordinates
     * 
     * @param point Point3D in the current coordinate system
     * @return Point3D with UTM easting (x), northing (y), and altitude (z)
     */
    public Point3D convertToUtm(Point3D point) {
        if (useUtm) {
            return point;
        }
        return converter.convertToUtm(point);
    }
    
    /**
     * Get the underlying coordinate converter
     * 
     * @return The coordinate converter
     */
    public CoordinateConverter getConverter() {
        return converter;
    }
    
    /**
     * Parse UTM zone from EPSG code
     * @param epsgCode EPSG code (e.g., 32636 for UTM 36N)
     * @return UTM zone number (1-60)
     */
    private int parseUtmZoneFromEpsg(int epsgCode) {
        if (epsgCode >= 32601 && epsgCode <= 32660) {
            // Northern hemisphere UTM zones (326XX)
            return epsgCode - 32600;
        } else if (epsgCode >= 32701 && epsgCode <= 32760) {
            // Southern hemisphere UTM zones (327XX)
            return epsgCode - 32700;
        } else {
            // Default to Zone 36 for Israel if not UTM code
            System.out.println("Warning: EPSG code " + epsgCode + " is not a UTM code. Using UTM Zone 36N as default.");
            return 36;
        }
    }
    
    /**
     * Parse hemisphere from EPSG code
     * @param epsgCode EPSG code
     * @return true if northern hemisphere, false if southern
     */
    private boolean parseHemisphereFromEpsg(int epsgCode) {
        if (epsgCode >= 32601 && epsgCode <= 32660) {
            return true; // Northern hemisphere
        } else if (epsgCode >= 32701 && epsgCode <= 32760) {
            return false; // Southern hemisphere
        } else {
            // Default to northern hemisphere
            return true;
        }
    }
    
    /**
     * Get the current EPSG code being used for UTM
     * @return EPSG code as integer
     */
    public int getCurrentUtmEpsgCode() {
        return converter.getCurrentZone() + (converter.isNorthern() ? 32600 : 32700);
    }
    
    /**
     * Get coordinate system information
     * @return Description of current coordinate system setup
     */
    public String getCoordinateSystemInfo() {
        return converter.getCoordinateSystemInfo();
    }
}
