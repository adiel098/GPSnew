# GPS Particle Filter - Folder Structure

## Overview
The project has been organized with a clear separation between input data and output files.

## Folder Structure

```
GPSnew/
├── config.properties          # Main configuration file
├── data/                      # Input KML files (static data)
│   ├── building3d.kml        # 3D building models
│   ├── original_route.kml    # Original GPS route data
│   └── satellites.kml        # Satellite constellation data
├── kml_output/               # Generated output files
│   ├── actual_route.kml      # Actual route visualization
│   ├── estimated_route.kml   # Particle filter estimated route
│   ├── particles.kml         # Particle history visualization
│   └── los_simulation.kml    # LOS/NLOS test visualization
├── src/                      # Source code
└── lib/                      # JAR dependencies
```

## Configuration

All file paths are configurable via `config.properties`:

### Input Files (data/ folder)
- `input.buildings.kml=data/building3d.kml`
- `input.route.kml=data/original_route.kml`
- `input.satellites.kml=data/satellites.kml`

### Output Files (kml_output/ folder)
- `output.particles.kml=kml_output/particles.kml`
- `output.estimated.route.kml=kml_output/estimated_route.kml`
- `output.actual.route.kml=kml_output/actual_route.kml`
- `output.los.simulation.kml=kml_output/los_simulation.kml`

## Usage

1. **Place your input data** in the `data/` folder
2. **Modify settings** in `config.properties` as needed
3. **Run the application** - output files will be created in `kml_output/`
4. **View results** using Google Earth or any KML viewer

## Key Configuration Settings

### Particle Filter Settings
- `particle.count=500` - Number of particles to use
- `particle.measurement.noise=10.0` - Measurement noise in meters

### LOS/NLOS Classification
- `los.classification.mode=HYBRID` - GEOMETRIC_ONLY, SIGNAL_STRENGTH_ONLY, or HYBRID
- `los.signal.threshold=30.0` - Signal strength threshold (dB-Hz)

### Simulation Parameters
- `simulation.observer.height=85.5` - Observer height in meters
- `simulation.wall.height=100.0` - Wall height for testing

All settings are automatically loaded when the application starts.