# Weight Calculation Stage

## Overview
The weight calculation stage is the heart of the particle filter's measurement update. It compares each particle's predicted satellite visibility (LOS/NLOS) with the actual GPS position's visibility, using advanced mathematical models from research literature to assign probability weights.

## How It Works (0-100%)

### Step 1: Calculate Reference LOS Status (0-10%)
Determine the "ground truth" satellite visibility from the true GPS position:

```java
public void updateWeights(Point3D originalPoint) {
    // Calculate LOS/NLOS status for the reference point
    Map<String, Boolean> referenceStatus = losCalculator.calculateLOS(originalPoint);
    
    int N = referenceStatus.size(); // Total number of satellites
    double totalWeight = 0;
```

**What happens:**
- `originalPoint` = true GPS position from receiver
- `referenceStatus` = Map of satellite ID → LOS/NLOS boolean for true position
- `N` = total satellite count (typically 8-12 satellites)
- This becomes the "measurement" that particles are compared against

### Step 2: Calculate Individual Particle Weights (10-80%)
For each particle, compare its LOS status to the reference and calculate weight:

```java
// First pass - calculate weights using Modified Sigmoid function from the article
for (Particle particle : particles) {
    // Calculate LOS/NLOS status for the particle
    Map<String, Boolean> particleLosStatus = losCalculator.calculateLOS(particle.getPosition());
    particle.setLosStatus(particleLosStatus);
    
    // Count matching LOS states between particle and reference
    int n = particle.matchingLosCount(referenceStatus);
```

**What happens for each particle:**

#### Step 2a: Particle LOS Calculation (10-30%)
```java
Map<String, Boolean> particleLosStatus = losCalculator.calculateLOS(particle.getPosition());
```
- Calculate which satellites are visible from particle's position
- Check building occlusions using ray-shooting algorithm
- Store result for future use and analysis
- This represents the particle's "hypothesis" about satellite visibility

#### Step 2b: Count Matching Satellites (30-40%)
```java
int n = particle.matchingLosCount(referenceStatus);
```

**Implementation in Particle.java:**
```java
public int matchingLosCount(Map<String, Boolean> referenceStatus) {
    int count = 0;
    for (Map.Entry<String, Boolean> entry : referenceStatus.entrySet()) {
        String satelliteId = entry.getKey();
        // Check if satellite's LOS state matches between particle and real state
        if (losStatus.containsKey(satelliteId) && 
            losStatus.get(satelliteId).equals(entry.getValue())) {
            count++;
        }
    }
    return count;
}
```

**What happens:**
- Compare each satellite's LOS status between particle and reference
- Count exact matches (LOS-LOS or NLOS-NLOS)
- `n` = number of satellites with matching visibility status
- Higher `n` means particle position is more likely correct

#### Step 2c: Modified Sigmoid Weight Function (40-60%)
```java
// Modified Sigmoid weight function from article (Equation 2)
// Weight(x) = N × 1/(1 + e^(N/4-n/2))
double exponent = (N / 4.0) - (n / 2.0);
double sigmoidWeight = N * (1.0 / (1.0 + Math.exp(exponent)));
```

**Mathematical Properties:**
- **Input Range:** n = 0 to N (number of matching satellites)
- **Output Range:** Approaches 0 to N (scaled by number of satellites)
- **Shape:** Sigmoid curve - smooth transition from low to high probability
- **Inflection Point:** At n = N/2 (half satellites matching)

**Example with N=8 satellites:**
- n=0 matches: Weight ≈ 0.12 (very low probability)
- n=2 matches: Weight ≈ 0.73 (low probability) 
- n=4 matches: Weight ≈ 4.0 (medium probability)
- n=6 matches: Weight ≈ 7.27 (high probability)
- n=8 matches: Weight ≈ 7.88 (very high probability)

#### Step 2d: Bayesian Weight Integration (60-75%)
```java
// Apply Bayesian weight if enabled (Equation 3 from article)
double finalWeight;
if (useBayesianWeight && particle.getPreviousWeight() > 0) {
    // Weight(x_t) = c × sigmoid + (1-c) × Weight(x_t-1)
    finalWeight = bayesianC * sigmoidWeight + (1 - bayesianC) * particle.getPreviousWeight();
} else {
    finalWeight = sigmoidWeight;
}
```

**What happens:**
- **Bayesian Integration:** Combines current measurement with historical performance
- **bayesianC** (typically 0.7): Balance between current vs. historical evidence
  - 0.7 × current measurement + 0.3 × previous weight
- **Historical Memory:** Particles with good past performance get bonus
- **Cold Start:** First iteration uses pure sigmoid weight (no history)

#### Step 2e: Store Weight Values (75-80%)
```java
// Store previous weight for next iteration
particle.setPreviousWeight(finalWeight);
particle.setWeight(finalWeight);
totalWeight += finalWeight;
```

**What happens:**
- Save current weight as "previous" for next Bayesian calculation
- Set particle's current weight for resampling
- Accumulate total weight for normalization

### Step 3: Normalize Weights (80-100%)
Convert raw weights to probability distribution:

```java
// Second pass - normalize weights
for (Particle particle : particles) {
    double normalizedWeight = totalWeight > 0 ? particle.getWeight() / totalWeight : 1.0 / particles.size();
    particle.setWeight(normalizedWeight);
}
```

**What happens:**
- **Normalization:** All weights sum to exactly 1.0
- **Probability Distribution:** Each weight represents particle's probability
- **Safety Check:** If total weight = 0, use uniform distribution
- **Final Result:** Particles ready for resampling based on probability

## Weight Function Analysis

### Sigmoid Function Behavior
The Modified Sigmoid function creates a smooth probability curve:

```
Weight = N × 1/(1 + e^((N/4-n/2)))
```

**Key characteristics:**
- **Smooth Transition:** No sharp jumps between weight values
- **Scale Invariant:** Automatically adapts to satellite count N
- **Probabilistic:** Higher match count = exponentially higher weight
- **Robust:** Small measurement errors don't cause dramatic weight changes

### Bayesian Integration Benefits
```
Final Weight = 0.7 × Current Sigmoid + 0.3 × Previous Weight
```

**Advantages:**
- **Temporal Consistency:** Smooth weight changes over time
- **Noise Reduction:** Filters out measurement anomalies
- **Memory Effect:** Good particles maintain advantage over time
- **Stability:** Reduces particle degeneracy in difficult scenarios

## Example Calculation

**Scenario:** 10 satellites, particle matches 7 satellites with reference

1. **Raw Sigmoid:**
   - N = 10, n = 7
   - Exponent = (10/4) - (7/2) = 2.5 - 3.5 = -1.0
   - Weight = 10 × 1/(1 + e^(-1.0)) = 10 × 0.731 = 7.31

2. **With Bayesian (c=0.7, previous=5.0):**
   - Final = 0.7 × 7.31 + 0.3 × 5.0 = 5.12 + 1.5 = 6.62

3. **After Normalization (assume total=800):**
   - Normalized = 6.62 / 800 = 0.0083 (0.83% probability)

## Performance Impact

| Factor | Complexity | Notes |
|---------|------------|--------|
| LOS Calculation | O(B × S) | B=buildings, S=satellites |
| Weight Function | O(1) | Simple mathematical operations |
| Total per Particle | O(B × S + S) | Dominated by LOS calculation |
| Total per Update | O(N × B × S) | N=particles, bottleneck of system |

## Configuration Parameters

```java
// Enable/disable Bayesian weighting
particleFilter.setUseBayesianWeight(true);

// Set Bayesian balance (0.0 = pure history, 1.0 = pure current)
particleFilter.setBayesianC(0.7);
```

## Result
After weight calculation:
- Each particle has a probability weight based on LOS match quality
- Weights sum to 1.0 (proper probability distribution)
- Particles with better LOS matches have exponentially higher weights
- Historical performance influences current weights (if Bayesian enabled)
- Population is ready for probability-based resampling

## File References
- Implementation: `src/main/java/com/gps/particlefilter/ParticleFilter.java:62`
- Particle matching: `src/main/java/com/gps/particlefilter/model/Particle.java:52`
- Configuration: `src/main/java/com/gps/particlefilter/ParticleFilter.java:227`
- Called from: `src/main/java/com/gps/particlefilter/ParticleFilter.java:195` (update method)