# Update/Measurement Stage

## Overview
The update stage is the main orchestrator of the particle filter cycle. It coordinates movement prediction, weight calculation, and resampling while maintaining historical data for analysis.

## How It Works (0-100%)

### Step 1: Check for Previous Point (0-10%)
Determine if this is the first update or a subsequent one:

```java
public void update(Point3D currentPoint, long timestamp) {
    if (previousPoint != null) {
        move(previousPoint, currentPoint);
    }
```

**What happens:**
- **First Update:** `previousPoint` is null, so no movement occurs
- **Subsequent Updates:** Move particles from previous to current GPS position
- This prevents movement on initialization
- Establishes the movement baseline for particle prediction

### Step 2: Movement Prediction (10-40%)
Move particles based on GPS trajectory (if not first update):

```java
move(previousPoint, currentPoint);
```

**What happens when called:**
1. **Calculate Movement Vector (10-20%):**
   - Distance and azimuth between GPS points
   - Velocity calculation for error model
   
2. **Apply Motion Model (20-35%):**
   - Each particle moves in the general direction of GPS movement
   - Add velocity-dependent random noise (R = c × |v|)
   - Update particle positions in UTM coordinates

3. **Update LOS Status (35-40%):**
   - Recalculate satellite visibility for new positions
   - Prepare for weight calculation step

### Step 3: Weight Calculation (40-70%)
Calculate how well each particle matches the current GPS observation:

```java
updateWeights(currentPoint);
```

**What happens during weight calculation:**
1. **Reference LOS Calculation (40-45%):**
   - Calculate LOS/NLOS status for the true GPS position
   - This becomes the "ground truth" for comparison

2. **Particle Weight Calculation (45-65%):**
   - For each particle, compare its LOS status to reference
   - Apply Modified Sigmoid function: `N × 1/(1 + e^(N/4-n/2))`
   - Apply optional Bayesian weighting with historical information

3. **Weight Normalization (65-70%):**
   - Normalize all weights to sum to 1.0
   - Ensures proper probability distribution

### Step 4: Resampling (70-80%)
Focus particles on most likely positions:

```java
resample();
```

**What happens during resampling:**
- Systematic resampling selects particles based on weights
- High-weight particles appear multiple times in new generation
- Low-weight particles may be eliminated
- Maintains particle count while focusing on promising areas

### Step 5: Save Historical State (80-90%)
Preserve current state for analysis and future processing:

```java
// Save current state to history and timestamp
particleHistory.add(new ArrayList<>(particles));
timestamps.add(timestamp);
```

**What happens:**
- **Deep Copy Particles:** `new ArrayList<>(particles)` prevents reference sharing
- **Store Timestamp:** Links particle state to GPS time
- **Build History:** Enables trajectory visualization and analysis
- **Memory Management:** Each state is preserved independently

### Step 6: Update Previous Point (90-95%)
Prepare for next iteration:

```java
// Store the current point for next update (in current coordinate system)
previousPoint = currentPoint;
```

**What happens:**
- Current GPS point becomes the reference for next movement
- Enables continuous tracking through GPS trajectory
- Maintains state between update calls

### Step 7: Debug Information (95-100%)
Output coordinate system information:

```java
// Debug information about coordinate system
if (coordManager.isUsingUtm()) {
    System.out.println("Using UTM coordinate system (Zone: " + coordManager.getDefaultUtmZone() + 
            (coordManager.isNorthernHemisphere() ? " North" : " South") + ")");
} else {
    System.out.println("Using Geographic coordinate system (Latitude/Longitude)");
}
```

**What happens:**
- Confirms coordinate system in use (typically UTM Zone 36N)
- Helps debug coordinate transformation issues
- Provides transparency about internal calculations

## Update Cycle Flow

```
GPS Point N → [Movement] → [Weight Update] → [Resampling] → [History] → Ready for GPS Point N+1
                    ↓              ↓              ↓             ↓
               Predict particle   Compare LOS    Focus on      Save state
               positions based   status with    best areas    for analysis
               on GPS movement   true position   
```

## Key State Transitions

| Stage | Input | Processing | Output |
|-------|--------|------------|---------|
| Movement | Previous & Current GPS | Motion model with noise | New particle positions |
| Weighting | Current GPS + Particles | LOS comparison + Sigmoid | Particle weights |
| Resampling | Weighted particles | Systematic selection | Focused population |
| History | Current particles | Deep copy + timestamp | Stored trajectory |

## Data Flow Example

**Input:** GPS moves from (683825.2, 3580234.1) to (683827.5, 3580238.3)

1. **Movement:** All 100 particles move ~4.8m northeast with individual noise
2. **Weighting:** Each particle's LOS status compared to reference, weights calculated
3. **Resampling:** Best particles selected, poor particles eliminated
4. **History:** Final particle positions stored with timestamp

## Performance Considerations

- **Computational Cost:** O(N × S) where N = particles, S = satellites
- **Memory Usage:** Historical states grow with trajectory length
- **Update Frequency:** Typically called 100+ times per route
- **LOS Calculations:** Most expensive operation due to building intersections

## Usage Pattern

```java
// Initialize once
ParticleFilter pf = new ParticleFilter(losCalculator, gridSize, movementNoise);
pf.initializeParticles(startPoint, particleCount);

// Update repeatedly
for (int i = 1; i < route.size(); i++) {
    Point3D currentPoint = route.get(i);
    long timestamp = timestamps.get(i);
    pf.update(currentPoint, timestamp);  // This method orchestrates everything
}
```

## Result
After each update:
- Particles have moved and adapted to new GPS observation
- Population focuses on most likely positions based on LOS evidence
- Historical trajectory is preserved for analysis
- System is ready for next GPS point

## File References
- Implementation: `src/main/java/com/gps/particlefilter/ParticleFilter.java:191`
- Usage: `src/main/java/com/gps/particlefilter/Main.java:233`
- Historical data: `src/main/java/com/gps/particlefilter/ParticleFilter.java:218` (getParticleHistory)