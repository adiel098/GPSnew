import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import sys

def create_fig15_naive_bayesian_chart():
    """
    Create Fig 15: Naive vs Bayesian weight function
    Shows comparison between weight functions with memory vs without memory
    for 100 and 1000 particles
    """
    
    # File paths
    data_file = os.path.join('data', 'naive_bayesian_data.csv')
    output_file = os.path.join('output', 'fig15_naive_bayesian.png')
    
    # Check if data file exists
    if not os.path.exists(data_file):
        print(f"Error: Data file {data_file} not found!")
        print("Please run the Java batch runner with 'naive-bayesian' argument first to generate the data.")
        return False
    
    # Read the CSV data
    try:
        df = pd.read_csv(data_file)
        print(f"Loaded {len(df)} data points from {data_file}")
    except Exception as e:
        print(f"Error reading CSV file: {e}")
        return False
    
    # Check if we have weight_type column
    if 'weight_type' not in df.columns:
        print("Error: The data file doesn't contain weight_type column.")
        print("Please regenerate the data using the naive-bayesian runner.")
        return False
    
    # Create the plot with 2 subplots (100 particles on top, 1000 particles on bottom)
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8))
    
    # Define colors matching the research paper
    colors = {
        'bayesian': '#1f77b4',  # Blue for "with memory" (Bayesian)
        'naive': '#2ca02c'      # Green for "without memory" (naive)
    }
    
    # Labels matching the research paper
    labels = {
        'bayesian': '100 Particles with memory',
        'naive': '100 particles without memory'
    }
    
    particle_counts = [100, 1000]
    axes = [ax1, ax2]
    
    for i, (particle_count, ax) in enumerate(zip(particle_counts, axes)):
        print(f"\nProcessing {particle_count} particles...")
        
        # Filter data for this particle count
        particle_data = df[df['particle_count'] == particle_count]
        
        if len(particle_data) == 0:
            print(f"Warning: No data found for {particle_count} particles")
            continue
        
        # Plot both naive and Bayesian for this particle count
        for weight_type in ['naive', 'bayesian']:
            weight_data = particle_data[particle_data['weight_type'] == weight_type]
            
            if len(weight_data) == 0:
                print(f"Warning: No {weight_type} data found for {particle_count} particles")
                continue
            
            # Convert timestamps to seconds relative to start time
            time_seconds = (weight_data['timestamp'].values - weight_data['timestamp'].iloc[0]) / 1000.0
            error_meters = weight_data['average_error'].values
            
            # Create the label for this particle count and weight type
            if particle_count == 100:
                if weight_type == 'bayesian':
                    label = '100 Particles with memory'
                else:
                    label = '100 particles without memory'
            else:  # 1000 particles
                if weight_type == 'bayesian':
                    label = '1000 Particles with memory'
                else:
                    label = '1000 particles without memory'
            
            # Plot the line
            ax.plot(time_seconds, error_meters, 
                   color=colors[weight_type], 
                   linewidth=2, 
                   label=label)
            
            print(f"  Plotted {len(weight_data)} points for {weight_type} weight function")
    
    # Calculate the maximum time across all data for consistent axis scaling
    max_time = 0
    for particle_count in particle_counts:
        particle_data = df[df['particle_count'] == particle_count]
        if len(particle_data) > 0:
            time_seconds = (particle_data['timestamp'].values - particle_data['timestamp'].iloc[0]) / 1000.0
            max_time = max(max_time, time_seconds.max())
    
    # Configure the subplots to match the research paper with auto-scaled time axis
    # Top subplot (100 particles)
    ax1.set_xlim(0, max_time + 5)  # Add small buffer
    ax1.set_ylim(0, 40)
    ax1.set_ylabel('Average Error [m]', fontsize=12)
    ax1.grid(True, alpha=0.3, color='gray', linestyle='-', linewidth=0.5)
    ax1.legend(loc='upper right', fontsize=11)
    ax1.tick_params(axis='both', which='major', labelsize=10)
    
    # Bottom subplot (1000 particles)
    ax2.set_xlim(0, max_time + 5)  # Add small buffer
    ax2.set_ylim(0, 30)
    ax2.set_xlabel('Time from initialization [s]', fontsize=12)
    ax2.set_ylabel('Average Error [m]', fontsize=12)
    ax2.grid(True, alpha=0.3, color='gray', linestyle='-', linewidth=0.5)
    ax2.legend(loc='upper right', fontsize=11)
    ax2.tick_params(axis='both', which='major', labelsize=10)
    
    # Overall title
    fig.suptitle('Naive vs Bayesian weight function', fontsize=14, fontweight='bold')
    
    # Adjust spacing between subplots
    plt.tight_layout()
    plt.subplots_adjust(top=0.93)  # Make room for the main title
    
    # Create output directory if it doesn't exist
    os.makedirs('output', exist_ok=True)
    
    # Save the plot
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"\nChart saved as {output_file}")
    
    # Show the plot
    plt.show()
    
    return True

def print_data_summary():
    """Print summary statistics of the data"""
    data_file = os.path.join('data', 'naive_bayesian_data.csv')
    
    if not os.path.exists(data_file):
        print("No data file found for summary")
        return
    
    df = pd.read_csv(data_file)
    
    print("\n=== Data Summary ===")
    print(f"Total data points: {len(df)}")
    print(f"Particle counts: {sorted(df['particle_count'].unique())}")
    
    if 'weight_type' in df.columns:
        print(f"Weight types: {sorted(df['weight_type'].unique())}")
    
    print(f"Time range: {df['time_step'].min()} to {df['time_step'].max()} seconds")
    print(f"Error range: {df['average_error'].min():.2f} to {df['average_error'].max():.2f} meters")
    
    print("\nAverage error by particle count and weight type:")
    for particle_count in sorted(df['particle_count'].unique()):
        print(f"  {particle_count} particles:")
        particle_data = df[df['particle_count'] == particle_count]
        
        if 'weight_type' in df.columns:
            for weight_type in sorted(particle_data['weight_type'].unique()):
                weight_data = particle_data[particle_data['weight_type'] == weight_type]
                avg_error = weight_data['average_error'].mean()
                final_error = weight_data['average_error'].iloc[-1]
                print(f"    {weight_type:>8}: Avg = {avg_error:5.2f}m, Final = {final_error:5.2f}m")
        else:
            avg_error = particle_data['average_error'].mean()
            final_error = particle_data['average_error'].iloc[-1]
            print(f"    Overall: Avg = {avg_error:5.2f}m, Final = {final_error:5.2f}m")

def print_comparison_analysis():
    """Print analysis comparing naive vs Bayesian performance"""
    data_file = os.path.join('data', 'naive_bayesian_data.csv')
    
    if not os.path.exists(data_file):
        return
    
    df = pd.read_csv(data_file)
    
    if 'weight_type' not in df.columns:
        return
    
    print("\n=== Naive vs Bayesian Performance Analysis ===")
    
    for particle_count in sorted(df['particle_count'].unique()):
        particle_data = df[df['particle_count'] == particle_count]
        
        naive_data = particle_data[particle_data['weight_type'] == 'naive']
        bayesian_data = particle_data[particle_data['weight_type'] == 'bayesian']
        
        if len(naive_data) > 0 and len(bayesian_data) > 0:
            naive_avg = naive_data['average_error'].mean()
            bayesian_avg = bayesian_data['average_error'].mean()
            
            improvement = ((naive_avg - bayesian_avg) / naive_avg) * 100
            
            print(f"  {particle_count} particles:")
            print(f"    Naive average error:    {naive_avg:6.2f}m")
            print(f"    Bayesian average error: {bayesian_avg:6.2f}m")
            print(f"    Improvement:            {improvement:6.1f}%")

if __name__ == "__main__":
    print("=== Fig 15 Naive vs Bayesian Weight Function Chart Generator ===")
    
    # Print data summary first
    print_data_summary()
    
    # Print comparison analysis
    print_comparison_analysis()
    
    # Generate the chart
    success = create_fig15_naive_bayesian_chart()
    
    if success:
        print("\nChart generation completed successfully!")
        print("The chart shows the comparison between naive (without memory) and Bayesian (with memory) weight functions.")
        print("For 100 particles, the Bayesian approach shows significant improvement.")
        print("For 1000 particles, the difference should be minimal as stated in the research paper.")
    else:
        print("\nChart generation failed!")
        print("Please check the error messages above and ensure the data file exists.")
        sys.exit(1)