# Resampling Stage

## Overview
The resampling stage selects particles based on their weights to focus computational resources on the most likely positions. It uses systematic resampling to maintain particle diversity while eliminating low-weight particles.

## How It Works (0-100%)

### Step 1: Prepare New Particle List (0-5%)
Initialize storage for the new generation of particles:

```java
public void resample() {
    List<Particle> newParticles = new ArrayList<>();
    int n = particles.size();
```

**What happens:**
- Creates empty list for resampled particles
- Stores current particle count for resampling loop
- Prepares to replace current particle set

### Step 2: Create Cumulative Weights Array (5-25%)
Build cumulative probability distribution:

```java
// Create cumulative weights array
double[] cumulativeWeights = new double[n];
cumulativeWeights[0] = particles.get(0).getWeight();
for (int i = 1; i < n; i++) {
    cumulativeWeights[i] = cumulativeWeights[i-1] + particles.get(i).getWeight();
}
```

**What happens:**
- First element = weight of first particle
- Each subsequent element = sum of all previous weights
- Final element should equal 1.0 (normalized weights)
- Creates probability ranges for systematic sampling

**Example:**
If particles have weights [0.1, 0.3, 0.4, 0.2], cumulative becomes [0.1, 0.4, 0.8, 1.0]

### Step 3: Initialize Systematic Resampling (25-35%)
Set up systematic sampling parameters:

```java
// Resample using systematic resampling
double step = 1.0 / n;              // e.g., 0.01 for 100 particles
double u = random.nextUniform(0, step);  // Random start between 0 and step
int j = 0;                          // Index for current particle
```

**What happens:**
- `step` = 1/N ensures even sampling intervals
- `u` = random starting point in [0, step) ensures randomness
- `j` tracks which particle we're currently considering
- This method ensures low-variance resampling

### Step 4: Systematic Resampling Loop (35-85%)
Select particles based on systematic sampling:

```java
for (int i = 0; i < n; i++) {
    // Find particle whose cumulative weight covers current sample point
    while (j < n-1 && u > cumulativeWeights[j]) {
        j++;
    }
    
    // Create new particle as copy of selected particle
    Particle newParticle = new Particle(particles.get(j).getPosition());
    newParticle.setLosStatus(new HashMap<>(particles.get(j).getLosStatus()));
    newParticle.setWeight(particles.get(j).getWeight());
    newParticles.add(newParticle);
    
    u += step;  // Move to next sample point
}
```

**What happens for each iteration:**
1. **Find Target Particle (35-50%):** 
   - `u` represents current sample point (0.0 to 1.0)
   - Advance `j` until `cumulativeWeights[j] >= u`
   - This finds which particle covers the current sample point

2. **Clone Selected Particle (50-75%):**
   - Create new Particle with same position
   - Copy LOS status map (deep copy to avoid reference sharing)
   - Copy current weight value

3. **Add to New Generation (75-80%):**
   - Add cloned particle to new generation
   - Move sample point forward by `step` for next iteration

4. **Systematic Progression (80-85%):**
   - `u += step` ensures even spacing between sample points
   - Guarantees low-variance resampling

### Step 5: Replace Particle Population (85-100%)
Replace old particles with resampled ones:

```java
particles = newParticles;
```

**What happens:**
- Old particle list is replaced completely
- High-weight particles may appear multiple times in new population
- Low-weight particles may not appear at all
- Total particle count remains constant
- Population is now focused on promising regions

## Systematic Resampling Advantages

1. **Low Variance:** Ensures even sampling across weight distribution
2. **Deterministic Spacing:** Sample points are evenly spaced with random offset
3. **Efficient:** O(N) complexity vs O(N log N) for other methods
4. **Maintains Diversity:** Even low-weight particles have chance to be selected

## Visual Example
```
Weights:     [0.1,  0.3,  0.4,  0.2]
Cumulative:  [0.1,  0.4,  0.8,  1.0]
Sample pts:  [0.02, 0.27, 0.52, 0.77] (step=0.25, u=0.02)
Selected:    [0,    1,    2,    2  ]  (particle indices)
```

Result: Particle 0 selected once, particle 1 once, particle 2 twice, particle 3 zero times.

## Key Parameters

| Parameter | Value | Purpose |
|-----------|--------|---------|
| `step` | 1.0/N | Even spacing between sample points |
| `u` | random(0, step) | Random offset for sample points |
| `cumulativeWeights` | Running sum | Probability distribution |

## When This Happens
- Called after weight calculation in each update cycle
- Occurs before particle movement in next iteration
- Typically runs 100+ times during route tracking

## Result
After resampling:
- Particle count remains constant
- High-weight regions have more particles
- Low-weight regions have fewer particles
- Population focuses on most likely positions
- Maintains potential for exploring other areas

## File References
- Implementation: `src/main/java/com/gps/particlefilter/ParticleFilter.java:105`
- Called from: `src/main/java/com/gps/particlefilter/ParticleFilter.java:196` (update method)