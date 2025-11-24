interface FrameStats {
    fps: number;
    resolution: { width: number; height: number };
    processingTime: number;
    timestamp: number;
}

class EdgeDetectionViewer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private statsElement: HTMLElement;
    private imageElement: HTMLImageElement;

    constructor() {
        this.canvas = document.getElementById('canvas') as HTMLCanvasElement;
        this.ctx = this.canvas.getContext('2d')!;
        this.statsElement = document.getElementById('stats')!;
        this.imageElement = document.getElementById('processed-image') as HTMLImageElement;

        this.initialize();
    }

    private initialize(): void {
        // Load sample processed image (base64 encoded)
        this.loadSampleImage();

        // Update stats
        this.updateStats({
            fps: 15,
            resolution: { width: 640, height: 480 },
            processingTime: 45,
            timestamp: Date.now()
        });

        // Setup WebSocket mock connection
        this.setupMockConnection();
    }

    private loadSampleImage(): void {
        // This would be a base64 encoded processed frame from the Android app
        const sampleBase64 = this.generateSampleEdgeImage();
        this.imageElement.src = `data:image/png;base64,${sampleBase64}`;

        this.imageElement.onload = () => {
            this.drawToCanvas();
        };
    }

    private generateSampleEdgeImage(): string {
        // Generate a sample edge detection visualization
        const tempCanvas = document.createElement('canvas');
        tempCanvas.width = 640;
        tempCanvas.height = 480;
        const tempCtx = tempCanvas.getContext('2d')!;

        // Create gradient background
        const gradient = tempCtx.createLinearGradient(0, 0, 640, 480);
        gradient.addColorStop(0, '#1a1a1a');
        gradient.addColorStop(1, '#0a0a0a');
        tempCtx.fillStyle = gradient;
        tempCtx.fillRect(0, 0, 640, 480);

        tempCtx.strokeStyle = '#FFFFFF';
        tempCtx.lineWidth = 2;

        // Draw geometric shapes (simulating edge detection)
        // Rectangle edges
        tempCtx.strokeRect(50, 50, 200, 150);
        tempCtx.strokeRect(300, 80, 280, 200);
        
        // Circle edges
        tempCtx.beginPath();
        tempCtx.arc(150, 350, 80, 0, Math.PI * 2);
        tempCtx.stroke();
        
        tempCtx.beginPath();
        tempCtx.arc(450, 320, 100, 0, Math.PI * 2);
        tempCtx.stroke();

        // Random edge-like lines (simulating detected features)
        for (let i = 0; i < 30; i++) {
            const x1 = Math.random() * 640;
            const y1 = Math.random() * 480;
            const length = 30 + Math.random() * 60;
            const angle = Math.random() * Math.PI * 2;
            
            tempCtx.beginPath();
            tempCtx.moveTo(x1, y1);
            tempCtx.lineTo(
                x1 + Math.cos(angle) * length, 
                y1 + Math.sin(angle) * length
            );
            tempCtx.globalAlpha = 0.6 + Math.random() * 0.4;
            tempCtx.stroke();
        }
        
        tempCtx.globalAlpha = 1.0;

        // Add "DEMO" text
        tempCtx.fillStyle = 'rgba(255, 255, 255, 0.3)';
        tempCtx.font = 'bold 48px Arial';
        tempCtx.textAlign = 'center';
        tempCtx.fillText('DEMO MODE', 320, 240);
        
        tempCtx.font = '16px Arial';
        tempCtx.fillStyle = 'rgba(255, 255, 255, 0.5)';
        tempCtx.fillText('Connect Android app for live feed', 320, 270);

        return tempCanvas.toDataURL('image/png').split(',')[1];
    }

    private drawToCanvas(): void {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.ctx.drawImage(this.imageElement, 0, 0, this.canvas.width, this.canvas.height);

        // Add overlay text
        this.ctx.fillStyle = '#00FF00';
        this.ctx.font = '16px monospace';
        this.ctx.fillText('Edge Detection Output', 10, 25);
    }

    private updateStats(stats: FrameStats): void {
        const html = `
            <div class="stat-item">
                <span class="label">FPS:</span>
                <span class="value">${stats.fps}</span>
            </div>
            <div class="stat-item">
                <span class="label">Resolution:</span>
                <span class="value">${stats.resolution.width}x${stats.resolution.height}</span>
            </div>
            <div class="stat-item">
                <span class="label">Processing Time:</span>
                <span class="value">${stats.processingTime}ms</span>
            </div>
            <div class="stat-item">
                <span class="label">Timestamp:</span>
                <span class="value">${new Date(stats.timestamp).toLocaleTimeString()}</span>
            </div>
        `;
        this.statsElement.innerHTML = html;
    }

    private setupMockConnection(): void {
        // Simulate receiving new frames
        setInterval(() => {
            const mockStats: FrameStats = {
                fps: 12 + Math.floor(Math.random() * 6),
                resolution: { width: 640, height: 480 },
                processingTime: 40 + Math.floor(Math.random() * 20),
                timestamp: Date.now()
            };
            this.updateStats(mockStats);
        }, 1000);
    }

    public exportCurrentFrame(): void {
        const dataUrl = this.canvas.toDataURL('image/png');
        const link = document.createElement('a');
        link.download = `edge-frame-${Date.now()}.png`;
        link.href = dataUrl;
        link.click();
    }
}

// Initialize viewer when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    const viewer = new EdgeDetectionViewer();

    // Add export button handler
    const exportBtn = document.getElementById('export-btn');
    if (exportBtn) {
        exportBtn.addEventListener('click', () => viewer.exportCurrentFrame());
    }
});

