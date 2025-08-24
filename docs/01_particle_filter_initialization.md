# Particle Filter Initialization

## Overview
The particle filter initialization is the foundation of the GPS positioning system. It creates a set of particles distributed around the initial GPS position to represent possible location states.

## How It Works (0-100%)

### Step 1: Constructor Setup (0-10%)
The ParticleFilter is constructed with essential components:

```java
public ParticleFilter(LosCalculator losCalculator, double gridSize, double movementNoise) {
    this.losCalculator = losCalculator;
    this.gridSize = gridSize;
    this.movementNoise = movementNoise;
    this.particles = new ArrayList<>();
    this.random = new RandomDataGenerator();
    this.particleHistory = new ArrayList<>();
    this.timestamps = new ArrayList<>();
    this.previousPoint = null;
    this.coordManager = CoordinateSystemManager.getInstance();
}
```

**What happens:**
- LosCalculator is stored for Line-of-Sight calculations
- Grid size defines the initialization area (typically 25 meters)
- Movement noise controls particle spread during movement
- Empty collections are prepared for particles and history tracking

### Step 2: Coordinate System Setup (10-20%)
The system configures UTM coordinates for meter-based calculations:

```java
// From Main.java
CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
coordManager.setDefaultUtmZone(36, true); // Zone 36 North for Israel
coordManager.setUseUtm(true);
```

**What happens:**
- UTM Zone 36N is set for consistent meter-based calculations
- This ensures all distance calculations are in meters, not degrees
- Enables accurate particle positioning and movement

### Step 3: Calculate Initialization Grid (20-40%)
The initialization area is calculated around the starting GPS position:

```java
public void initializeParticles(Point3D center, int particleCount) {
    particles.clear();
    // When using UTM, gridSize is already in meters
    double minY = center.getY() - gridSize;  // 25 meters south
    double maxY = center.getY() + gridSize;  // 25 meters north
    double minX = center.getX() - gridSize;  // 25 meters west
    double maxX = center.getX() + gridSize;  // 25 meters east
    double alt = 1.8; // Average human height
```

**What happens:**
- Creates a square grid centered on the initial GPS position
- Grid extends 25 meters in each direction (50x50 meter total area)
- Altitude is set to 1.8 meters (average human height)
- Grid boundaries are calculated in UTM coordinates (meters)

### Step 4: Calculate Grid Distribution (40-60%)
Particles are distributed evenly across the grid:

```java
int particlesPerRow = (int) Math.sqrt(particleCount);  // e.g., 100 particles = 10x10 grid
double yStep = (maxY - minY) / (particlesPerRow - 1);  // Step size in meters
double xStep = (maxX - minX) / (particlesPerRow - 1);  // Step size in meters
```

**What happens:**
- For 100 particles: creates a 10x10 grid
- For 400 particles: creates a 20x20 grid
- Step size determines spacing between particles
- Ensures even distribution across the initialization area

### Step 5: Create and Position Particles (60-80%)
Each particle is created and positioned on the grid:

```java
for (double y = minY; y <= maxY; y += yStep) {
    for (double x = minX; x <= maxX; x += xStep) {
        if (particles.size() >= particleCount) break;
        Point3D position = new Point3D(x, y, alt);
        Particle particle = new Particle(position);
        particles.add(particle);
    }
}
```

**What happens:**
- Nested loops create particles at grid intersections
- Each particle gets exact UTM coordinates
- Particle constructor sets initial weight to 1.0
- Previous weight initialized to 0.0 for Bayesian weighting

### Step 6: Calculate Initial LOS Status (80-100%)
Each particle's Line-of-Sight status is calculated for all satellites:

```java
for (Particle particle : particles) {
    particle.setLosStatus(losCalculator.calculateLOS(position));
}
```

**What happens:**
- LosCalculator checks if each satellite is visible from particle position
- Buildings are used to determine signal blockage
- Each particle gets a Map<String, Boolean> of satellite visibility
- This initial LOS status is used for first weight calculation

## Key Parameters

| Parameter | Typical Value | Purpose |
|-----------|---------------|----------|
| `gridSize` | 25.0 meters | Size of initialization area |
| `particleCount` | 100-400 | Number of particles to create |
| `altitude` | 1.8 meters | Human height above ground |
| `particlesPerRow` | √particleCount | Grid dimensions |

## Result
After initialization:
- All particles are positioned in a grid around the initial GPS location
- Each particle has equal weight (1.0 / particleCount after normalization)
- Each particle knows its LOS/NLOS status for all satellites
- The particle filter is ready to begin tracking

## File References
- Implementation: `src/main/java/com/gps/particlefilter/ParticleFilter.java:37`
- Usage: `src/main/java/com/gps/particlefilter/Main.java:213`