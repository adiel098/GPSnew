# Prediction/Motion Model Stage

## Overview
The prediction stage moves particles based on the observed movement from GPS, adding realistic motion noise to account for movement uncertainty. This implements the research paper's error model: R = c × |v|, where motion error depends on velocity.

## How It Works (0-100%)

### Step 1: Calculate Movement Parameters (0-15%)
Extract movement information from GPS trajectory:

```java
public void move(Point3D from, Point3D to) {
    // Calculate distance and azimuth between points
    double distance = from.distanceTo(to);
    double azimuth = from.azimuthTo(to);
    
    // Calculate velocity magnitude (assuming 1 second between updates)
    this.velocity = distance;
```

**What happens:**
- `distance` = Euclidean distance between GPS points (in meters)
- `azimuth` = Direction of movement in degrees (0° = East, 90° = North)
- `velocity` = Distance traveled per time unit (m/s)
- These values drive the motion model for all particles

### Step 2: Adaptive Error Coefficient (15-30%)
Calculate error coefficient based on movement speed:

```java
// Determine c coefficient based on velocity (from article)
// Walking speed: ~1-2 m/s → c = 1.0-1.5
// Driving speed: >5 m/s → c = 0.5-1.0  
// Slow/stationary: <0.5 m/s → c = 2.0
if (velocity < 0.5) {
    c = 2.0;        // High uncertainty when stationary
} else if (velocity < 2.0) {
    c = 1.5;        // Medium uncertainty when walking
} else if (velocity < 5.0) {
    c = 1.0;        // Lower uncertainty when running
} else {
    c = 0.5;        // Lowest uncertainty when driving
}
```

**What happens:**
- **Stationary (< 0.5 m/s):** c = 2.0
  - High error when not moving (GPS drift, signal multipath)
- **Walking (0.5-2.0 m/s):** c = 1.5
  - Moderate error for pedestrian movement
- **Running (2.0-5.0 m/s):** c = 1.0
  - Lower error for consistent fast movement
- **Driving (> 5.0 m/s):** c = 0.5
  - Lowest error for smooth vehicle movement

### Step 3: Calculate Error Radius (30-40%)
Apply the research paper's error model:

```java
// Article's error model: R = c × |v|
double errorRadius = c * velocity;
```

**What happens:**
- Error radius grows with velocity
- Coefficient `c` modulates the relationship
- Examples:
  - Stationary: R = 2.0 × 0.1 = 0.2m
  - Walking: R = 1.5 × 1.5 = 2.25m
  - Driving: R = 0.5 × 10 = 5.0m

### Step 4: Calculate Expected Movement (40-55%)
Compute the deterministic movement vector:

```java
// Calculate expected movement
double dx = distance * Math.cos(Math.toRadians(azimuth));
double dy = distance * Math.sin(Math.toRadians(azimuth));
```

**What happens:**
- Convert polar coordinates (distance, azimuth) to Cartesian (dx, dy)
- `dx` = eastward movement in meters
- `dy` = northward movement in meters
- This is the "perfect" movement without noise

### Step 5: Generate Random Error for Each Particle (55-85%)
Add individual noise to each particle's movement:

```java
// Move each particle with error model from article
for (Particle particle : particles) {
    // Sample random point within circle of radius R (uniform distribution)
    double randomRadius = errorRadius * Math.sqrt(random.nextUniform(0, 1));
    double randomAngle = random.nextUniform(0, 2 * Math.PI);
    
    // Add random error from circle
    double errorX = randomRadius * Math.cos(randomAngle);
    double errorY = randomRadius * Math.sin(randomAngle);
```

**What happens for each particle:**
1. **Random Radius (55-65%):**
   - `sqrt(random)` ensures uniform distribution within circle
   - Without sqrt, points would cluster near center
   - `randomRadius` ranges from 0 to `errorRadius`

2. **Random Angle (65-75%):**
   - Uniform distribution from 0 to 2π radians
   - Ensures equal probability in all directions
   - No bias toward any particular direction

3. **Convert to Cartesian Error (75-85%):**
   - `errorX` = horizontal error component
   - `errorY` = vertical error component
   - These represent random deviation from expected path

### Step 6: Update Particle Positions (85-95%)
Apply movement and error to each particle:

```java
    // Update particle position with movement + error
    Point3D currentPos = particle.getPosition();
    Point3D newPos = new Point3D(
        currentPos.getX() + dx + errorX,
        currentPos.getY() + dy + errorY,
        currentPos.getZ()
    );
    
    particle.setPosition(newPos);
```

**What happens:**
- Combine deterministic movement (dx, dy) with random error (errorX, errorY)
- X coordinate: current X + expected movement + random error
- Y coordinate: current Y + expected movement + random error
- Z coordinate: remains unchanged (altitude constant)
- Each particle gets unique position due to random error

### Step 7: Update LOS Status (95-100%)
Recalculate satellite visibility for new positions:

```java
    particle.setLosStatus(losCalculator.calculateLOS(particle.getPosition()));
}
```

**What happens:**
- Calculate Line-of-Sight for each satellite from new position
- Check building occlusions at new location
- Update particle's LOS/NLOS status map
- This new status will be used in weight calculation

## Motion Model Characteristics

### Error Distribution
- **Shape:** Circular uniform distribution
- **Radius:** R = c × |v| (velocity-dependent)
- **Center:** Expected movement endpoint
- **Properties:** Unbiased, realistic spread

### Adaptive Behavior
| Movement Type | Velocity | Coefficient | Error Example |
|---------------|----------|-------------|---------------|
| Stationary GPS drift | 0.1 m/s | c = 2.0 | 0.2m radius |
| Walking pedestrian | 1.5 m/s | c = 1.5 | 2.25m radius |
| Jogging | 3.0 m/s | c = 1.0 | 3.0m radius |
| Urban driving | 10 m/s | c = 0.5 | 5.0m radius |

### Key Advantages
1. **Velocity-Adaptive:** Higher uncertainty for erratic movement
2. **Physically Realistic:** Circular error matches real GPS behavior  
3. **Research-Based:** Implements published error model
4. **Computationally Efficient:** Simple mathematical operations

## Debug Output Example
```
Moving from: Point3D(683825.2, 3580234.1, 1.8) to: Point3D(683827.5, 3580238.3, 1.8)
Distance: 4.8m, Azimuth: 61.2°
Velocity: 4.8 m/s, Error coefficient c: 1.0
```

## Result
After prediction:
- All particles move in the general direction of GPS movement
- Each particle has slightly different final position due to noise
- Movement uncertainty reflects velocity and movement type
- Particles maintain diversity while following observed trajectory

## File References
- Implementation: `src/main/java/com/gps/particlefilter/ParticleFilter.java:135`
- Called from: `src/main/java/com/gps/particlefilter/ParticleFilter.java:193` (update method)
- Alternative implementation: `src/main/java/com/gps/particlefilter/model/Particle.java:106`