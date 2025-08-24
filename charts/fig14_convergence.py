import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import sys

def create_fig14_convergence_chart():
    """
    Create Fig 14: Convergence error for different number of particles
    Matches the research paper figure exactly
    """
    
    # File paths
    data_file = os.path.join('data', 'convergence_data.csv')
    output_file = os.path.join('output', 'fig14_convergence.png')
    
    # Check if data file exists
    if not os.path.exists(data_file):
        print(f"Error: Data file {data_file} not found!")
        print("Please run the Java batch runner first to generate the data.")
        return False
    
    # Read the CSV data
    try:
        df = pd.read_csv(data_file)
        print(f"Loaded {len(df)} data points from {data_file}")
    except Exception as e:
        print(f"Error reading CSV file: {e}")
        return False
    
    # Create the plot with exact styling from the research paper
    plt.figure(figsize=(10, 7))
    
    # Define colors for each particle count (matching the research paper)
    colors = {
        2500: '#1f77b4',  # Blue for 2500 particles
        1000: '#2ca02c',  # Green for 1000 particles  
        500: '#d62728',   # Red for 500 particles
        100: '#17becf'    # Cyan for 100 particles
    }
    
    # Plot each particle count
    particle_counts = [2500, 1000, 500, 100]
    
    for particle_count in particle_counts:
        # Filter data for this particle count
        particle_data = df[df['particle_count'] == particle_count]
        
        if len(particle_data) == 0:
            print(f"Warning: No data found for {particle_count} particles")
            continue
            
        # Convert timestamps to seconds relative to start time
        time_seconds = (particle_data['timestamp'].values - particle_data['timestamp'].iloc[0]) / 1000.0
        error_meters = particle_data['average_error'].values
        
        # Plot the line
        plt.plot(time_seconds, error_meters, 
                color=colors[particle_count], 
                linewidth=2, 
                label=f'{particle_count} Particles')
        
        print(f"Plotted {len(particle_data)} points for {particle_count} particles")
    
    # Calculate the maximum time across all data for consistent axis scaling
    max_time = 0
    for particle_count in particle_counts:
        particle_data = df[df['particle_count'] == particle_count]
        if len(particle_data) > 0:
            time_seconds = (particle_data['timestamp'].values - particle_data['timestamp'].iloc[0]) / 1000.0
            max_time = max(max_time, time_seconds.max())
    
    # Set up the plot to match the research paper with auto-scaled time axis
    plt.xlim(0, max_time + 5)  # Add small buffer
    plt.ylim(0, 35)
    plt.xlabel('Time from initialization [s]', fontsize=12)
    plt.ylabel('Average Error [m]', fontsize=12)
    plt.title('Convergence error for different number of particles', fontsize=14, fontweight='bold')
    
    # Add grid (light gray)
    plt.grid(True, alpha=0.3, color='gray', linestyle='-', linewidth=0.5)
    
    # Add legend in upper right
    plt.legend(loc='upper right', fontsize=11)
    
    # Set tick parameters
    plt.tick_params(axis='both', which='major', labelsize=10)
    
    # Tight layout to prevent label cutoff
    plt.tight_layout()
    
    # Create output directory if it doesn't exist
    os.makedirs('output', exist_ok=True)
    
    # Save the plot
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Chart saved as {output_file}")
    
    # Show the plot
    plt.show()
    
    return True

def print_data_summary():
    """Print summary statistics of the data"""
    data_file = os.path.join('data', 'convergence_data.csv')
    
    if not os.path.exists(data_file):
        print("No data file found for summary")
        return
    
    df = pd.read_csv(data_file)
    
    print("\n=== Data Summary ===")
    print(f"Total data points: {len(df)}")
    print(f"Particle counts: {sorted(df['particle_count'].unique())}")
    print(f"Time range: {df['time_step'].min()} to {df['time_step'].max()} seconds")
    print(f"Error range: {df['average_error'].min():.2f} to {df['average_error'].max():.2f} meters")
    
    print("\nAverage error by particle count:")
    for particle_count in sorted(df['particle_count'].unique()):
        particle_data = df[df['particle_count'] == particle_count]
        avg_error = particle_data['average_error'].mean()
        final_error = particle_data['average_error'].iloc[-1]
        print(f"  {particle_count:4d} particles: Avg = {avg_error:5.2f}m, Final = {final_error:5.2f}m")

if __name__ == "__main__":
    print("=== Fig 14 Convergence Chart Generator ===")
    
    # Print data summary first
    print_data_summary()
    
    # Generate the chart
    success = create_fig14_convergence_chart()
    
    if success:
        print("\nChart generation completed successfully!")
        print("The chart shows how different particle counts affect convergence over time.")
        print("Lower particle counts generally show higher error and slower convergence.")
    else:
        print("\nChart generation failed!")
        print("Please check the error messages above and ensure the data file exists.")
        sys.exit(1)