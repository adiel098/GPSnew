# Chart Generation System

This folder contains the chart generation system for your GPS particle filter research.

## Fig 14: Particle Convergence Chart

Generates "Convergence error for different number of particles" showing how accuracy improves with more particles over time.

### Quick Start

1. **Run the complete system:**
   ```bash
   # From the main project directory
   run.bat
   # Then select option 5: Generate Fig 14 Convergence Chart
   ```

2. **Manual steps (if needed):**
   ```bash
   # Install Python dependencies
   cd charts
   install_requirements.bat
   
   # Compile Java classes (if needed)
   javac -cp "../lib/*:../classes" -d ../classes ../src/main/java/com/gps/particlefilter/util/ChartDataCollector.java
   javac -cp "../lib/*:../classes" -d ../classes ../src/main/java/com/gps/particlefilter/ParticleFilterBatchRunner.java
   
   # Run simulations for all particle counts
   java -cp "../lib/*:../classes" com.gps.particlefilter.ParticleFilterBatchRunner convergence
   
   # Generate the chart
   python fig14_convergence.py
   ```

### Files Structure

```
charts/
├── README.md                    # This file
├── requirements.txt             # Python dependencies
├── install_requirements.bat     # Install Python packages
├── fig14_convergence.py         # Chart generation script
├── data/                        # Generated simulation data
│   └── convergence_data.csv     # CSV with error data for all particle counts
└── output/                      # Generated charts
    └── fig14_convergence.png    # Final chart image
```

### What It Does

1. **Data Collection**: Runs your particle filter simulation 4 times with different particle counts (100, 500, 1000, 2500)
2. **Error Tracking**: Collects positioning error at each time step for all simulations
3. **Chart Generation**: Creates a publication-ready chart showing convergence patterns
4. **Automatic Processing**: Handles compilation, data collection, and visualization automatically

### Requirements

- **Java**: JDK installed and in PATH (for running particle filter)
- **Python**: Python 3.7+ installed and in PATH
- **Python Packages**: matplotlib, pandas, numpy, seaborn (auto-installed)
- **Data Files**: Your existing KML files (buildings, satellites, route)

### Output

The generated chart (`output/fig14_convergence.png`) shows:
- X-axis: Time from initialization [s] (0-160)
- Y-axis: Average Error [m] (0-35)
- 4 lines showing error over time for different particle counts
- Colors match your research paper figure

### Troubleshooting

**Java compilation fails:**
- Check that all JAR files are in `lib/` directory
- Ensure Java JDK is installed (not just JRE)

**Python errors:**
- Run `charts/install_requirements.bat` to install packages
- Check Python version: `python --version` (needs 3.7+)

**No data generated:**
- Check that your KML files exist in the `data/` directory
- Verify particle filter runs successfully with `Main.java`

**Chart looks different:**
- Data depends on your actual GPS route and building data
- Pattern should show convergence but exact values will vary