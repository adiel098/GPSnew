package com.gps.particlefilter.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
// No imports needed for basic types

/**
 * Configuration manager for GPS Particle Filter
 * Loads settings from config.properties file
 */
public class Configuration {
    private static Configuration instance;
    private Properties properties;
    private String configFile;

    // Default configuration file name
    private static final String DEFAULT_CONFIG_FILE = "config.properties";

    private Configuration(String configFile) {
        this.configFile = configFile;
        loadConfiguration();
    }

    /**
     * Get the singleton configuration instance with default config file
     */
    public static Configuration getInstance() {
        if (instance == null) {
            instance = new Configuration(DEFAULT_CONFIG_FILE);
        }
        return instance;
    }

    /**
     * Get the singleton configuration instance with custom config file
     */
    public static Configuration getInstance(String configFile) {
        if (instance == null) {
            instance = new Configuration(configFile);
        }
        return instance;
    }

    /**
     * Reload configuration from file
     */
    public void reload() {
        loadConfiguration();
    }

    /**
     * Load configuration from properties file
     */
    private void loadConfiguration() {
        properties = new Properties();
        try (FileInputStream input = new FileInputStream(configFile)) {
            properties.load(input);
            System.out.println("Configuration loaded from: " + configFile);
        } catch (IOException e) {
            System.err.println("Warning: Could not load configuration file " + configFile + 
                             ". Using default values. Error: " + e.getMessage());
            // Load default properties
            loadDefaultProperties();
        }
    }

    /**
     * Load default configuration values if config file is not found
     */
    private void loadDefaultProperties() {
        properties = new Properties();
        // Particle Filter Settings
        properties.setProperty("particle.count", "1000");
        properties.setProperty("particle.initial.noise.x", "50.0");
        properties.setProperty("particle.initial.noise.y", "50.0");
        properties.setProperty("particle.initial.noise.z", "10.0");
        properties.setProperty("particle.process.noise.x", "5.0");
        properties.setProperty("particle.process.noise.y", "5.0");
        properties.setProperty("particle.process.noise.z", "2.0");
        properties.setProperty("particle.measurement.noise", "10.0");
        properties.setProperty("particle.resampling.threshold", "0.5");
        
        // LOS/NLOS Classification Settings
        properties.setProperty("los.classification.mode", "GEOMETRIC_ONLY");
        properties.setProperty("los.signal.threshold", "37.0");
        properties.setProperty("los.ray.shooting.enabled", "true");
        properties.setProperty("los.tolerance", "0.0");
        
        // Simulation Settings
        properties.setProperty("simulation.observer.height", "85.5");
        properties.setProperty("simulation.wall.height", "100.0");
        properties.setProperty("simulation.satellite.elevation", "45.0");
        properties.setProperty("simulation.satellite.azimuth", "45.0");
        
        // Debug Settings
        properties.setProperty("debug.enabled", "false");
        properties.setProperty("debug.building.info", "true");
        properties.setProperty("debug.particle.info", "false");
        properties.setProperty("debug.los.calculation", "false");
    }

    // Getter methods for different configuration sections

    // Particle Filter Settings
    public int getParticleCount() {
        return getInt("particle.count", 1000);
    }

    public double getParticleInitialNoiseX() {
        return getDouble("particle.initial.noise.x", 50.0);
    }

    public double getParticleInitialNoiseY() {
        return getDouble("particle.initial.noise.y", 50.0);
    }

    public double getParticleInitialNoiseZ() {
        return getDouble("particle.initial.noise.z", 10.0);
    }

    public double getParticleProcessNoiseX() {
        return getDouble("particle.process.noise.x", 5.0);
    }

    public double getParticleProcessNoiseY() {
        return getDouble("particle.process.noise.y", 5.0);
    }

    public double getParticleProcessNoiseZ() {
        return getDouble("particle.process.noise.z", 2.0);
    }

    public double getParticleMeasurementNoise() {
        return getDouble("particle.measurement.noise", 10.0);
    }

    public double getParticleResamplingThreshold() {
        return getDouble("particle.resampling.threshold", 0.5);
    }

    // LOS/NLOS Classification Settings
    public String getLosClassificationMode() {
        String mode = getString("los.classification.mode", "GEOMETRIC_ONLY");
        // Validate the mode
        if (!mode.equals("GEOMETRIC_ONLY") && !mode.equals("SIGNAL_STRENGTH_ONLY") && !mode.equals("HYBRID")) {
            System.err.println("Warning: Invalid classification mode '" + mode + 
                             "'. Using GEOMETRIC_ONLY.");
            return "GEOMETRIC_ONLY";
        }
        return mode;
    }

    public double getLosSignalThreshold() {
        return getDouble("los.signal.threshold", 37.0);
    }

    public boolean isLosRayShootingEnabled() {
        return getBoolean("los.ray.shooting.enabled", true);
    }

    public double getLosTolerance() {
        return getDouble("los.tolerance", 0.0);
    }

    // Simulation Settings
    public double getSimulationObserverHeight() {
        return getDouble("simulation.observer.height", 85.5);
    }

    public double getSimulationWallHeight() {
        return getDouble("simulation.wall.height", 100.0);
    }

    public double getSimulationSatelliteElevation() {
        return getDouble("simulation.satellite.elevation", 45.0);
    }

    public double getSimulationSatelliteAzimuth() {
        return getDouble("simulation.satellite.azimuth", 45.0);
    }

    // File I/O Settings
    public String getInputBuildingsKml() {
        return getString("input.buildings.kml", "data/buildings.kml");
    }

    public String getInputRouteKml() {
        return getString("input.route.kml", "data/route.kml");
    }

    public String getInputSatellitesKml() {
        return getString("input.satellites.kml", "data/satellites.kml");
    }

    public String getOutputDirectory() {
        return getString("output.directory", "kml_output");
    }

    public String getOutputParticlesKml() {
        return getString("output.particles.kml", "particles.kml");
    }

    public String getOutputEstimatedRouteKml() {
        return getString("output.estimated.route.kml", "estimated_route.kml");
    }

    public String getOutputActualRouteKml() {
        return getString("output.actual.route.kml", "actual_route.kml");
    }

    public String getOutputLosSimulationKml() {
        return getString("output.los.simulation.kml", "los_simulation.kml");
    }

    // Debug Settings
    public boolean isDebugEnabled() {
        return getBoolean("debug.enabled", false);
    }

    public boolean isDebugBuildingInfoEnabled() {
        return getBoolean("debug.building.info", true);
    }

    public boolean isDebugParticleInfoEnabled() {
        return getBoolean("debug.particle.info", false);
    }

    public boolean isDebugLosCalculationEnabled() {
        return getBoolean("debug.los.calculation", false);
    }

    // Performance Settings
    public int getMaxBuildingsCheck() {
        return getInt("performance.max.buildings.check", 100);
    }

    public boolean isParallelProcessingEnabled() {
        return getBoolean("performance.parallel.enabled", true);
    }

    public int getThreadPoolSize() {
        return getInt("performance.thread.pool.size", 0);
    }

    // Visualization Settings
    public boolean isVisualizationKmlEnabled() {
        return getBoolean("visualization.enable.kml", true);
    }

    public String getVisualizationColorLos() {
        return getString("visualization.color.los", "ff00ff00");
    }

    public String getVisualizationColorNlos() {
        return getString("visualization.color.nlos", "ff0000ff");
    }

    public String getVisualizationColorWall() {
        return getString("visualization.color.wall", "ff0000ff");
    }

    public String getVisualizationColorParticle() {
        return getString("visualization.color.particle", "ffffff00");
    }

    // Coordinate System Settings
    public String getUtmZone() {
        return getString("coordinates.utm.zone", "36N");
    }

    public double getBaseLatitude() {
        return getDouble("coordinates.base.latitude", 31.771959);
    }

    public double getBaseLongitude() {
        return getDouble("coordinates.base.longitude", 35.217018);
    }

    public double getMetersPerDegree() {
        return getDouble("coordinates.meters.per.degree", 111300.0);
    }

    // Signal Simulation Settings
    public double getSignalStrengthVariation() {
        return getDouble("signal.strength.variation", 5.0);
    }

    public double getSignalStrengthBaseMin() {
        return getDouble("signal.strength.base.min", 25.0);
    }

    public double getSignalStrengthBaseMax() {
        return getDouble("signal.strength.base.max", 40.0);
    }

    public double getSignalDegradationMin() {
        return getDouble("signal.degradation.min", 10.0);
    }

    public double getSignalDegradationMax() {
        return getDouble("signal.degradation.max", 20.0);
    }

    // Coordinate System Settings (GeoTools EPSG codes)
    public int getCoordinatesSourceEpsg() {
        return getInt("coordinates.source.epsg", 4326); // WGS84
    }

    public int getCoordinatesTargetEpsg() {
        return getInt("coordinates.target.epsg", 32636); // UTM Zone 36N (Israel)
    }

    public boolean isCoordinatesAutoDetectUtm() {
        return getBoolean("coordinates.auto.detect.utm", false);
    }

    // Helper methods for type conversion
    private String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer value for " + key + 
                             ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid double value for " + key + 
                             ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Print current configuration to console
     */
    public void printConfiguration() {
        if (!isDebugEnabled()) return;
        
        System.out.println("\n=== GPS Particle Filter Configuration ===");
        System.out.println("Particle count: " + getParticleCount());
        System.out.println("LOS classification mode: " + getLosClassificationMode());
        System.out.println("LOS signal threshold: " + getLosSignalThreshold() + " dB-Hz");
        System.out.println("Ray shooting enabled: " + isLosRayShootingEnabled());
        System.out.println("Observer height: " + getSimulationObserverHeight() + " m");
        System.out.println("Wall height: " + getSimulationWallHeight() + " m");
        System.out.println("Debug enabled: " + isDebugEnabled());
        System.out.println("==========================================\n");
    }
}