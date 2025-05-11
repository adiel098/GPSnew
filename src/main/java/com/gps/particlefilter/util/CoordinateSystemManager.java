package com.gps.particlefilter.util;

import com.gps.particlefilter.model.Point3D;

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
        converter = new CoordinateConverter();
        useUtm = true; // Default to using UTM coordinates
        defaultUtmZone = 36; // Default zone (can be overridden based on initial coordinates)
        isNorthernHemisphere = true; // Default to northern hemisphere
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
}
