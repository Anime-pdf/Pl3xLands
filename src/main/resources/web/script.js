// Pl3xLands Editor - Main Application
class Pl3xLandsEditor {
    constructor() {
        this.sessionToken = null;
        this.sessionExpiry = null;
        this.sessionTimerInterval = null;
        this.map = null;
        this.mapConfig = null;
        this.currentWorld = null;
        this.regions = [];
        this.selectedRegion = null;
        this.editMode = false;
        this.selectedChunks = new Set();
        this.chunkLayer = null;
        this.existingRegionLayers = [];

        // Selection tools
        this.currentTool = 'click'; // 'click', 'rectangle', 'fill'
        this.rectangleStart = null;
        this.rectangleLayer = null;

        this.init();
    }

    async init() {
        // Check for existing session
        this.sessionToken = localStorage.getItem('session_token');
        this.sessionExpiry = parseInt(localStorage.getItem('session_expiry'));

        const configResponse = await fetch('/api/config');
        this.mapConfig = await configResponse.json();

        if (!this.sessionToken || !this.sessionExpiry) {
            this.showAuthModal();
        } else {
            // Check if session expired
            if (Date.now() > this.sessionExpiry) {
                this.sessionToken = null;
                this.sessionExpiry = null;
                localStorage.removeItem('session_token');
                localStorage.removeItem('session_expiry');
                this.showAuthModal();
            } else {
                // Try to use existing token
                try {
                    this.startSessionTimer();
                    await this.loadApp();
                } catch (error) {
                    console.error('Failed to load with existing token:', error);
                    this.sessionToken = null;
                    this.sessionExpiry = null;
                    localStorage.removeItem('session_token');
                    localStorage.removeItem('session_expiry');
                    this.showAuthModal();
                }
            }
        }

        this.setupEventListeners();
    }

    setupEventListeners() {
        // Auth
        document.getElementById('auth-type').addEventListener('change', (e) => {
            const isPassword = e.target.value === 'password';
            document.getElementById('password-group').classList.toggle('hidden', !isPassword);
            document.getElementById('token-group').classList.toggle('hidden', isPassword);
        });

        document.getElementById('login-btn').addEventListener('click', () => this.handleLogin());

        // Toolbar
        document.getElementById('logout-btn').addEventListener('click', () => this.handleLogout());
        document.getElementById('world-select').addEventListener('change', (e) => this.switchWorld(e.target.value));
        document.getElementById('new-region-btn').addEventListener('click', () => this.startNewRegion());
        document.getElementById('edit-region-btn').addEventListener('click', () => this.startEditRegion());
        document.getElementById('save-changes-btn').addEventListener('click', () => this.saveFormChanges());
        document.getElementById('deselect-btn').addEventListener('click', () => this.deselectRegion());
        document.getElementById('save-region-btn').addEventListener('click', () => this.saveRegion());
        document.getElementById('cancel-edit-btn').addEventListener('click', () => this.cancelEdit());
        document.getElementById('delete-region-btn').addEventListener('click', () => this.deleteRegion());

        // Selection tools
        document.getElementById('tool-click').addEventListener('click', () => this.setTool('click'));
        document.getElementById('tool-rectangle').addEventListener('click', () => this.setTool('rectangle'));
        document.getElementById('tool-fill').addEventListener('click', () => this.setTool('fill'));
        document.getElementById('clear-selection-btn').addEventListener('click', () => this.clearSelection());

        // Form field change detection
        ['region-name', 'region-description', 'region-owner', 'region-contact', 'region-world'].forEach(id => {
            document.getElementById(id).addEventListener('input', () => this.handleFormChange());
        });

        // Search
        document.getElementById('search-input').addEventListener('input', (e) => this.filterRegions(e.target.value));

        // Form change detection (for editing details without going into edit mode)
        document.getElementById('region-name').addEventListener('input', () => this.handleFormChange());
        document.getElementById('region-description').addEventListener('input', () => this.handleFormChange());
        document.getElementById('region-owner').addEventListener('input', () => this.handleFormChange());
        document.getElementById('region-contact').addEventListener('input', () => this.handleFormChange());
        document.getElementById('region-world').addEventListener('change', () => this.handleFormChange());
    }

    // ==================== Authentication ====================

    showAuthModal() {
        document.getElementById('auth-modal').classList.remove('hidden');
        document.getElementById('app').classList.add('hidden');
    }

    hideAuthModal() {
        document.getElementById('auth-modal').classList.add('hidden');
        document.getElementById('app').classList.remove('hidden');
    }

    async handleLogin() {
        const authType = document.getElementById('auth-type').value;
        const credentials = {};

        if (authType === 'password') {
            credentials.username = document.getElementById('username-input').value;
            credentials.password = document.getElementById('password-input').value;
        } else {
            credentials.token = document.getElementById('token-input').value;
        }

        try {
            const response = await this.apiRequest('/api/auth', {
                method: 'POST',
                body: JSON.stringify(credentials),
                headers: {
                    'Content-Type': 'application/json'
                },
                skipAuth: true
            });

            if (response.success) {
                this.sessionToken = response.token;
                localStorage.setItem('session_token', this.sessionToken);

                // Calculate session expiry (get from config or default to 1 hour)
                const sessionTimeout = (this.mapConfig.sessionTimeout === null) ? 3600 : this.mapConfig.sessionTimeout;
                this.sessionExpiry = Date.now() + (sessionTimeout * 1000);
                localStorage.setItem('session_expiry', this.sessionExpiry);

                this.startSessionTimer();
                this.hideAuthModal();
                await this.loadApp();
            } else {
                this.showError('auth-error', response.error || 'Authentication failed');
            }
        } catch (error) {
            this.showError('auth-error', 'Connection error. Please check the server.');
            console.log(error)
        }
    }

    async handleLogout() {
        try {
            await this.apiRequest('/api/logout', { method: 'POST' });
        } catch (error) {
            console.error('Logout error:', error);
        }

        this.stopSessionTimer();
        this.sessionToken = null;
        this.sessionExpiry = null;
        localStorage.removeItem('session_token');
        localStorage.removeItem('session_expiry');
        window.location.reload();
    }

    // ==================== API Requests ====================

    async apiRequest(endpoint, options = {}) {
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json'
            }
        };

        if (!options.skipAuth && this.sessionToken) {
            defaultOptions.headers['Authorization'] = `Bearer ${this.sessionToken}`;
        }

        const mergedOptions = {
            ...defaultOptions,
            ...options,
            headers: {
                ...defaultOptions.headers,
                ...options.headers
            }
        };

        const response = await fetch(endpoint, mergedOptions);

        if (response.status === 401) {
            // Unauthorized - token expired or invalid
            this.handleLogout();
            throw new Error('Unauthorized');
        }

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || `HTTP ${response.status}`);
        }

        return data;
    }

    // ==================== App Initialization ====================

    async loadApp() {
        this.showLoading(true);

        try {
            // Load map configuration
            const configResponse = await fetch('/api/config');
            this.mapConfig = await configResponse.json();

            // Load worlds
            await this.loadWorlds();

            // Initialize map
            this.initMap();

            // Load regions
            await this.loadRegions();

            this.setStatus('Ready');
        } catch (error) {
            console.error('Failed to load app:', error);
            throw error;
        } finally {
            this.showLoading(false);
        }
    }

    async loadRegions() {
        const response = await this.apiRequest('/api/editor/regions');
        this.regions = response.regions || [];
        this.renderRegionList();
    }

    async loadWorlds() {
        const response = await this.apiRequest('/api/editor/worlds');
        const worlds = response.worlds || [];

        const worldSelect = document.getElementById('world-select');
        const regionWorldSelect = document.getElementById('region-world');

        worldSelect.innerHTML = '';
        regionWorldSelect.innerHTML = '';

        worlds.forEach(world => {
            const option1 = document.createElement('option');
            option1.value = world;
            option1.textContent = world;
            worldSelect.appendChild(option1);

            const option2 = document.createElement('option');
            option2.value = world;
            option2.textContent = world;
            regionWorldSelect.appendChild(option2);
        });

        if (worlds.length > 0) {
            this.currentWorld = worlds[0];
            worldSelect.value = this.currentWorld;
        }
    }

    // ==================== Map ====================

    initMap() {
        const worldConfig = this.mapConfig.worlds[0];
        const center = worldConfig.center;
        const zoom = worldConfig.zoom;

        const scaleFactor = 1 / Math.pow(2, zoom.maxOut);
        L.CRS.Minecraft = L.Util.extend({}, L.CRS.Simple, {
            transformation: new L.Transformation(1, 0, 1, 0),

            scale: function (zoom) {
                return Math.pow(2, zoom) * scaleFactor;
            },

            zoom: function (scale) {
                return Math.log(scale / scaleFactor) / Math.LN2;
            }
        });

        this.map = L.map('map', {
            crs: L.CRS.Minecraft,
            center: [center.x, center.z],
            zoom: zoom.default,
            minZoom: zoom.maxOut - zoom.maxIn - 1,
            maxZoom: zoom.maxOut,
            attributionControl: false
        });

        // Add tile layer
        const tileUrl = `${this.mapConfig.mapUrl}/tiles/${this.currentWorld}/{z}/${worldConfig.renderer}/{x}_{y}.${this.mapConfig.format}`;
        L.tileLayer(tileUrl, {
            tileSize: 512,
            noWrap: true,
            minZoom: zoom.maxOut - zoom.maxIn - 1,
            maxZoom: zoom.maxOut,
            zoomReverse: true
        }).addTo(this.map);

        // Add cursor coordinate tracking
        this.map.on('mousemove', (e) => this.handleCursorCoords(e));

        // Load existing regions
        this.loadExistingRegions();
    }

    handleCursorCoords(e) {
        const coords = document.getElementById('cursor-coords');
        if (coords) {
            const x = Math.floor(e.latlng.lng);
            const z = Math.floor(e.latlng.lat);
            coords.textContent = `X: ${x}, Z: ${z}`;
        }
    }

    argbToHex(argb) {
        if(argb === null) return null
        const rgb = argb & 0xFFFFFF;
        return '#' + rgb.toString(16).padStart(6, '0').toUpperCase();
    }

    async loadExistingRegions() {
        try {
            const response = await fetch('/api/regions');
            const markersData = await response.json();

            // Clear existing layers
            this.existingRegionLayers.forEach(layer => this.map.removeLayer(layer));
            this.existingRegionLayers = [];

            const worldMarkers = markersData[this.currentWorld] || [];

            worldMarkers.forEach(markerData => {
                if (!markerData.polygons || !Array.isArray(markerData.polygons))
                    return

                const multiPolygonLatLngs = markerData.polygons.map(polygon => {
                    return polygon.polylines.map(polyline => {
                        return polyline.points.map(p => [p.z, p.x]);
                    });
                });

                const leafletPolygon = L.polygon(multiPolygonLatLngs, {
                    color: this.argbToHex(markerData.options?.stroke?.color) || '#3b82f6',
                    fillColor: this.argbToHex(markerData.options?.fill?.color) || '#3b82f6',
                    fillOpacity: (markerData.options?.fill?.color ?
                        ((markerData.options.fill?.color >> 24) & 0xff) / 255 : 0.2),
                    weight: 2,
                    noClip: true,
                    fillRule: 'evenodd'
                });

                if (markerData.options?.popup?.content) {
                    leafletPolygon.bindPopup(markerData.options.popup.content);
                }

                if (markerData.options?.tooltip?.content) {
                    leafletPolygon.bindTooltip(markerData.options.tooltip.content, {
                        sticky: markerData.options.tooltip.sticky || false
                    });
                }

                leafletPolygon.addTo(this.map);
                this.existingRegionLayers.push(leafletPolygon);
            });

            console.log(`Loaded ${this.existingRegionLayers.length} region polygons for ${this.currentWorld}`);
        } catch (error) {
            console.error('Failed to load existing regions:', error);
        }
    }

    switchWorld(world) {
        this.currentWorld = world;
        // Reload map with new world
        this.map.remove();
        this.initMap();
    }

    // ==================== Region List ====================

    renderRegionList() {
        const container = document.getElementById('region-list');
        container.innerHTML = '';

        if (this.regions.length === 0) {
            container.innerHTML = '<p style="padding: 16px; text-align: center; color: #9ca3af;">No regions yet</p>';
            return;
        }

        this.regions.forEach(region => {
            const item = document.createElement('div');
            item.className = 'region-item';
            if (this.selectedRegion && this.selectedRegion.id === region.id) {
                item.classList.add('active');
            }

            item.innerHTML = `
                <div class="region-item-name">${this.escapeHtml(region.name)}</div>
                <div class="region-item-info">
                    ${this.escapeHtml(region.owner)} • ${region.chunks.length} chunks • ${this.escapeHtml(region.world)}
                </div>
            `;

            item.addEventListener('click', () => this.selectRegion(region));
            container.appendChild(item);
        });
    }

    filterRegions(query) {
        const items = document.querySelectorAll('.region-item');
        const lowerQuery = query.toLowerCase();

        items.forEach(item => {
            const name = item.querySelector('.region-item-name').textContent.toLowerCase();
            const info = item.querySelector('.region-item-info').textContent.toLowerCase();

            if (name.includes(lowerQuery) || info.includes(lowerQuery)) {
                item.style.display = '';
            } else {
                item.style.display = 'none';
            }
        });
    }

    selectRegion(region) {
        if (this.selectedRegion === region) {
            this.deselectRegion();
            return;
        }

        this.selectedRegion = region;
        this.renderRegionList();
        this.showRegionDetails(region);
    }

    deselectRegion() {
        this.selectedRegion = null;
        this.renderRegionList();

        // Hide edit panel (don't touch flex property to preserve scrolling)
        document.getElementById('region-edit-panel').classList.add('hidden');

        // Reset buttons
        document.getElementById('new-region-btn').classList.remove('hidden');
        document.getElementById('edit-region-btn').classList.add('hidden');
        document.getElementById('save-changes-btn').classList.add('hidden');
        document.getElementById('deselect-btn').classList.add('hidden');
        document.getElementById('delete-region-btn').classList.add('hidden');

        // Clear any highlighted chunks
        if (this.chunkLayer) {
            this.chunkLayer.clearLayers();
        }

        this.setStatus('Ready');
    }

    showRegionDetails(region) {
        document.getElementById('region-edit-panel').classList.remove('hidden');
        // Don't override flex on region-list-panel to keep scrolling

        // Populate form (leave fields enabled so they can be edited)
        document.getElementById('region-id').value = region.id;
        document.getElementById('region-id').disabled = true; // Can't change ID (ever)
        document.getElementById('region-name').value = region.name;
        document.getElementById('region-name').disabled = false;
        document.getElementById('region-description').value = region.description;
        document.getElementById('region-description').disabled = false;
        document.getElementById('region-owner').value = region.owner;
        document.getElementById('region-owner').disabled = false;
        document.getElementById('region-contact').value = region.contact;
        document.getElementById('region-contact').disabled = false;
        document.getElementById('region-world').value = region.world;
        document.getElementById('region-world').disabled = false;

        // Show edit buttons
        document.getElementById('new-region-btn').classList.add('hidden');
        document.getElementById('edit-region-btn').classList.remove('hidden');
        document.getElementById('deselect-btn').classList.remove('hidden');
        document.getElementById('delete-region-btn').classList.remove('hidden');

        // Hide edit mode buttons
        document.getElementById('save-region-btn').classList.add('hidden');
        document.getElementById('cancel-edit-btn').classList.add('hidden');
        document.getElementById('selection-tools').classList.add('hidden');

        // Highlight region on map
        this.highlightRegionChunks(region.chunks);

        // Zoom to region
        this.zoomToRegion(region.chunks);
    }

    // ==================== Edit Mode ====================

    startNewRegion() {
        this.editMode = true;
        this.selectedRegion = null;
        this.selectedChunks.clear();

        // Clear form
        document.getElementById('region-id').value = '';
        document.getElementById('region-id').disabled = false;
        document.getElementById('region-name').value = '';
        document.getElementById('region-description').value = '';
        document.getElementById('region-owner').value = '';
        document.getElementById('region-contact').value = '';
        document.getElementById('region-world').value = this.currentWorld;

        // Show form
        document.getElementById('region-edit-panel').classList.remove('hidden');

        // Update buttons
        document.getElementById('new-region-btn').classList.add('hidden');
        document.getElementById('save-region-btn').classList.remove('hidden');
        document.getElementById('cancel-edit-btn').classList.remove('hidden');
        document.getElementById('delete-region-btn').classList.add('hidden');

        // Show instructions
        document.getElementById('edit-instructions').classList.remove('hidden');

        // Enable chunk selection
        this.enableChunkSelection();

        this.setStatus('Edit mode: Click chunks to select');
    }

    startEditRegion() {
        if (!this.selectedRegion) return;

        this.editMode = true;

        // Load existing chunks into selection
        this.selectedChunks.clear();
        this.selectedRegion.chunks.forEach(chunk => {
            this.selectedChunks.add(BigInt(chunk));
        });

        // Enable form editing
        document.getElementById('region-name').disabled = false;
        document.getElementById('region-description').disabled = false;
        document.getElementById('region-owner').disabled = false;
        document.getElementById('region-contact').disabled = false;
        document.getElementById('region-world').disabled = false;

        // Update buttons
        document.getElementById('edit-region-btn').classList.add('hidden');
        document.getElementById('delete-region-btn').classList.add('hidden');
        document.getElementById('save-region-btn').classList.remove('hidden');
        document.getElementById('cancel-edit-btn').classList.remove('hidden');
        document.getElementById('selection-tools').classList.remove('hidden');

        // Show instructions
        document.getElementById('edit-instructions').classList.remove('hidden');

        // Enable chunk selection
        this.enableChunkSelection();

        // Update display to show existing chunks
        this.updateChunkDisplay();

        this.setStatus('Editing region: Modify chunks and details');
    }

    // ==================== Selection Tools ====================

    setTool(tool) {
        this.currentTool = tool;

        // Update button states
        document.querySelectorAll('.btn-tool').forEach(btn => {
            btn.classList.remove('active');
        });
        document.getElementById(`tool-${tool}`).classList.add('active');

        // Clear rectangle if switching away from rectangle tool
        if (tool !== 'rectangle' && this.rectangleLayer) {
            this.map.removeLayer(this.rectangleLayer);
            this.rectangleLayer = null;
            this.rectangleStart = null;
        }

        // Update cursor style
        const mapContainer = document.getElementById('map');
        mapContainer.style.cursor = tool === 'fill' ? 'crosshair' : 'default';

        // Update status
        const toolNames = {
            'click': 'Click to select/deselect chunks',
            'rectangle': 'Click and drag to select rectangle',
            'fill': 'Click inside an enclosed area to fill'
        };
        this.setStatus(`Tool: ${toolNames[tool]}`);
    }

    clearSelection() {
        if (!confirm('Clear all selected chunks?')) return;

        this.selectedChunks.clear();
        this.updateChunkDisplay();
        this.setStatus('Selection cleared');
    }

    enableChunkSelection() {
        // Remove old handlers
        this.map.off('click');
        this.map.off('mousedown');
        this.map.off('mousemove');
        this.map.off('mouseup');

        // Add click handler (works for all tools)
        this.map.on('click', (e) => this.handleMapClick(e));

        // Add rectangle tool handlers
        this.map.on('mousedown', (e) => this.handleRectangleStart(e));
        this.map.on('mousemove', (e) => { this.handleRectangleMove(e); this.handleCursorCoords(e) });
        this.map.on('mouseup', (e) => this.handleRectangleEnd(e));

        // Visual feedback layer
        if (this.chunkLayer) {
            this.map.removeLayer(this.chunkLayer);
        }
        this.chunkLayer = L.layerGroup().addTo(this.map);

        this.updateChunkDisplay();
    }

    disableChunkSelection() {
        this.map.off('click');
        this.map.off('mousedown');
        this.map.off('mousemove');
        this.map.off('mouseup');

        this.map.on('mousemove', (e) => this.handleCursorCoords(e));

        if (this.chunkLayer) {
            this.map.removeLayer(this.chunkLayer);
            this.chunkLayer = null;
        }

        if (this.rectangleLayer) {
            this.map.removeLayer(this.rectangleLayer);
            this.rectangleLayer = null;
        }

        document.getElementById('edit-instructions').classList.add('hidden');
    }

    handleMapClick(e) {
        if (!this.editMode) return;

        // Don't process click if it was part of a rectangle drag
        if (this.rectangleStart && this.currentTool === 'rectangle') {
            return;
        }

        const latlng = e.latlng;
        const chunkX = Math.floor(latlng.lng / 16) * 16;
        const chunkZ = Math.floor(latlng.lat / 16) * 16;

        if (this.currentTool === 'click') {
            // Click tool: toggle single chunk
            const packed = this.packChunk(chunkX, chunkZ);

            if (this.selectedChunks.has(packed)) {
                this.selectedChunks.delete(packed);
            } else {
                this.selectedChunks.add(packed);
            }

            this.updateChunkDisplay();
        } else if (this.currentTool === 'fill') {
            // Fill tool: flood fill from clicked chunk
            this.floodFill(chunkX, chunkZ);
        }
        // Rectangle tool is handled by mouse down/move/up
    }

    updateChunkDisplay() {
        if (!this.chunkLayer) return;

        this.chunkLayer.clearLayers();

        this.selectedChunks.forEach(packed => {
            const [x, z] = this.unpackChunk(packed);

            // Leaflet bounds are [lat, lng] which is [z, x] in Minecraft
            const bounds = [
                [z, x],
                [z + 16, x + 16]
            ];

            const rect = L.rectangle(bounds, {
                color: '#3b82f6',
                fillColor: '#3b82f6',
                fillOpacity: 0.3,
                weight: 2
            });

            this.chunkLayer.addLayer(rect);
        });

        document.getElementById('chunk-count').textContent = this.selectedChunks.size;
    }


    handleRectangleStart(e) {
        if (!this.editMode || this.currentTool !== 'rectangle') return;

        // Prevent map dragging during rectangle selection
        this.map.dragging.disable();

        const latlng = e.latlng;
        this.rectangleStart = {
            x: Math.floor(latlng.lng / 16) * 16,
            z: Math.floor(latlng.lat / 16) * 16
        };
    }

    handleRectangleMove(e) {
        if (!this.editMode || this.currentTool !== 'rectangle' || !this.rectangleStart) return;

        const latlng = e.latlng;
        const endX = Math.floor(latlng.lng / 16) * 16;
        const endZ = Math.floor(latlng.lat / 16) * 16;

        // Remove old rectangle preview
        if (this.rectangleLayer) {
            this.map.removeLayer(this.rectangleLayer);
        }

        // Draw rectangle preview
        const minX = Math.min(this.rectangleStart.x, endX);
        const maxX = Math.max(this.rectangleStart.x, endX) + 16;
        const minZ = Math.min(this.rectangleStart.z, endZ);
        const maxZ = Math.max(this.rectangleStart.z, endZ) + 16;

        this.rectangleLayer = L.rectangle([
            [minZ, minX],
            [maxZ, maxX]
        ], {
            color: '#f59e0b',
            fillColor: '#f59e0b',
            fillOpacity: 0.2,
            weight: 2,
            dashArray: '5, 5'
        }).addTo(this.map);
    }

    handleRectangleEnd(e) {
        if (!this.editMode || this.currentTool !== 'rectangle' || !this.rectangleStart) return;

        // Re-enable map dragging
        this.map.dragging.enable();

        const latlng = e.latlng;
        const endX = Math.floor(latlng.lng / 16) * 16;
        const endZ = Math.floor(latlng.lat / 16) * 16;

        // Select all chunks in rectangle
        const minX = Math.min(this.rectangleStart.x, endX);
        const maxX = Math.max(this.rectangleStart.x, endX);
        const minZ = Math.min(this.rectangleStart.z, endZ);
        const maxZ = Math.max(this.rectangleStart.z, endZ);

        for (let x = minX; x <= maxX; x += 16) {
            for (let z = minZ; z <= maxZ; z += 16) {
                const packed = this.packChunk(x, z);
                this.selectedChunks.add(packed);
            }
        }

        // Clean up
        if (this.rectangleLayer) {
            this.map.removeLayer(this.rectangleLayer);
            this.rectangleLayer = null;
        }
        this.rectangleStart = null;

        this.updateChunkDisplay();
    }

    floodFill(startX, startZ) {
        const startPacked = this.packChunk(startX, startZ);

        // If clicking on an already selected chunk, do nothing
        if (this.selectedChunks.has(startPacked)) {
            return;
        }

        // BFS flood fill
        const queue = [[startX, startZ]];
        const visited = new Set();
        visited.add(startPacked);

        const maxChunks = 1000; // Safety limit
        let filled = 0;

        while (queue.length > 0 && filled < maxChunks) {
            const [x, z] = queue.shift();
            const packed = this.packChunk(x, z);

            // Add this chunk
            this.selectedChunks.add(packed);
            filled++;

            // Check 4 neighbors
            const neighbors = [
                [x + 16, z],
                [x - 16, z],
                [x, z + 16],
                [x, z - 16]
            ];

            for (const [nx, nz] of neighbors) {
                const nPacked = this.packChunk(nx, nz);

                // Skip if already visited or selected
                if (visited.has(nPacked) || this.selectedChunks.has(nPacked)) {
                    continue;
                }

                // Check if this neighbor would be enclosed
                // (Simple version: only fill if it's not already part of a selected region)
                visited.add(nPacked);
                queue.push([nx, nz]);
            }
        }

        if (filled >= maxChunks) {
            alert(`Fill stopped at ${maxChunks} chunks for safety. Use rectangle tool for large areas.`);
        }

        this.updateChunkDisplay();
    }

    highlightRegionChunks(chunks) {
        // Clear existing highlight
        if (this.chunkLayer) {
            this.chunkLayer.clearLayers();
        } else {
            this.chunkLayer = L.layerGroup().addTo(this.map);
        }

        if (chunks.length === 0) return;

        chunks.forEach(packed => {
            const [x, z] = this.unpackChunk(packed);

            const bounds = [
                [z, x],
                [z + 16, x + 16]
            ];

            const rect = L.rectangle(bounds, {
                color: '#10b981',
                fillColor: '#10b981',
                fillOpacity: 0.2,
                weight: 2
            });

            this.chunkLayer.addLayer(rect);
        });
    }

    handleFormChange() {
        // Show save changes button when form is modified (only if not in edit mode)
        if (!this.editMode && this.selectedRegion) {
            document.getElementById('save-changes-btn').classList.remove('hidden');
        }
    }

    async saveFormChanges() {
        if (!this.selectedRegion) return;

        const name = document.getElementById('region-name').value.trim();
        const description = document.getElementById('region-description').value.trim();
        const owner = document.getElementById('region-owner').value.trim();
        const contact = document.getElementById('region-contact').value.trim();
        const world = document.getElementById('region-world').value;

        if (!name || !owner) {
            alert('Please fill in all required fields');
            return;
        }

        const region = {
            id: this.selectedRegion.id,
            name,
            description,
            owner,
            contact,
            world,
            chunks: this.selectedRegion.chunks // Keep existing chunks
        };

        this.showLoading(true);

        try {
            await this.apiRequest(`/api/editor/regions/${region.id}`, {
                method: 'PUT',
                body: JSON.stringify(region, (_, v) => typeof v === 'bigint' ? v.toString() : v)
            });

            this.setStatus('Region details updated successfully');

            // Update local copy
            this.selectedRegion.name = name;
            this.selectedRegion.description = description;
            this.selectedRegion.owner = owner;
            this.selectedRegion.contact = contact;
            this.selectedRegion.world = world;

            await this.loadRegions();
            await this.loadExistingRegions();

            // Hide save changes button
            document.getElementById('save-changes-btn').classList.add('hidden');

        } catch (error) {
            alert(`Failed to save changes: ${error.message}`);
        } finally {
            this.showLoading(false);
        }
    }

    zoomToRegion(chunks) {
        if (!chunks || chunks.length === 0) return;

        let minX = Infinity, maxX = -Infinity;
        let minZ = Infinity, maxZ = -Infinity;

        chunks.forEach(packed => {
            const [x, z] = this.unpackChunk(packed);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x + 16);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z + 16);
        });

        const bounds = L.latLngBounds(
            [minZ, minX],
            [maxZ, maxX]
        );

        this.map.fitBounds(bounds, {
            padding: [50, 50],
            maxZoom: 3,
            animate: true,
            duration: 0.5
        });
    }

    cancelEdit() {
        this.editMode = false;
        this.selectedChunks.clear();
        this.disableChunkSelection();

        // Reset tool
        this.setTool('click');

        document.getElementById('region-edit-panel').classList.add('hidden');
        document.getElementById('new-region-btn').classList.remove('hidden');
        document.getElementById('edit-region-btn').classList.add('hidden');
        document.getElementById('deselect-btn').classList.add('hidden');
        document.getElementById('save-region-btn').classList.add('hidden');
        document.getElementById('cancel-edit-btn').classList.add('hidden');
        document.getElementById('selection-tools').classList.add('hidden');

        this.setStatus('Ready');
    }

    async saveRegion() {
        const id = document.getElementById('region-id').value.trim();
        const name = document.getElementById('region-name').value.trim();
        const description = document.getElementById('region-description').value.trim();
        const owner = document.getElementById('region-owner').value.trim();
        const contact = document.getElementById('region-contact').value.trim();
        const world = document.getElementById('region-world').value;

        if (!id || !name || !owner) {
            alert('Please fill in all required fields');
            return;
        }

        if (this.selectedChunks.size === 0) {
            alert('Please select at least one chunk');
            return;
        }

        const region = {
            id,
            name,
            description,
            owner,
            contact,
            world,
            chunks: Array.from(this.selectedChunks)
        };

        this.showLoading(true);

        try {
            if (this.selectedRegion) {
                // Update existing
                await this.apiRequest(`/api/editor/regions/${id}`, {
                    method: 'PUT',
                    body: JSON.stringify(region, (_, v) => typeof v === 'bigint' ? v.toString() : v)
                });
                this.setStatus('Region updated successfully');
            } else {
                // Create new
                await this.apiRequest('/api/editor/regions', {
                    method: 'POST',
                    body: JSON.stringify(region, (_, v) => typeof v === 'bigint' ? v.toString() : v)
                });
                this.setStatus('Region created successfully');
            }

            await this.loadRegions();
            await this.loadExistingRegions();
            this.cancelEdit();

        } catch (error) {
            alert(`Failed to save region: ${error.message}`);
        } finally {
            this.showLoading(false);
        }
    }

    async deleteRegion() {
        if (!this.selectedRegion) return;

        if (!confirm(`Are you sure you want to delete region "${this.selectedRegion.name}"?`)) {
            return;
        }

        this.showLoading(true);

        try {
            await this.apiRequest(`/api/editor/regions/${this.selectedRegion.id}`, {
                method: 'DELETE'
            });

            this.setStatus('Region deleted successfully');
            await this.loadRegions();
            await this.loadExistingRegions();
            this.cancelEdit();

        } catch (error) {
            alert(`Failed to delete region: ${error.message}`);
        } finally {
            this.showLoading(false);
        }
    }

    // ==================== Session Timer ====================

    startSessionTimer() {
        this.stopSessionTimer(); // Clear any existing timer

        this.sessionTimerInterval = setInterval(() => {
            if (!this.sessionExpiry) {
                this.stopSessionTimer();
                return;
            }

            const remaining = this.sessionExpiry - Date.now();

            if (remaining <= 0) {
                // Session expired
                this.handleLogout();
                return;
            }

            // Update display
            const minutes = Math.floor(remaining / 60000);
            const seconds = Math.floor((remaining % 60000) / 1000);

            const timerElement = document.getElementById('session-timer');
            if (timerElement) {
                timerElement.textContent = `Session: ${minutes}:${seconds.toString().padStart(2, '0')}`;

                // Warning color when < 5 minutes
                if (minutes < 5) {
                    timerElement.style.color = '#ef4444';
                } else if (minutes < 10) {
                    timerElement.style.color = '#f59e0b';
                } else {
                    timerElement.style.color = '#9ca3af';
                }
            }
        }, 1000);
    }

    stopSessionTimer() {
        if (this.sessionTimerInterval) {
            clearInterval(this.sessionTimerInterval);
            this.sessionTimerInterval = null;
        }
    }

    // ==================== Utilities ====================

    packChunk(x, z) {
        return (BigInt(x) << 32n) | (BigInt(z) & 0xFFFFFFFFn);
    }

    unpackChunk(packed) {
        const packedBig = BigInt(packed);
        const x = Number(packedBig >> 32n);
        const z = BigInt(packedBig) & 0xFFFFFFFFn;
        const zNorm = Number(z >= 0x80000000n ? z - 0x100000000n : z);
        return [x, zNorm];
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showLoading(show) {
        document.getElementById('loading-overlay').classList.toggle('hidden', !show);
    }

    setStatus(message) {
        document.getElementById('status-message').textContent = message;
    }

    showError(elementId, message) {
        const element = document.getElementById(elementId);
        element.textContent = message;
        element.classList.remove('hidden');

        setTimeout(() => {
            element.classList.add('hidden');
        }, 5000);
    }
}

// Initialize the application
window.addEventListener('DOMContentLoaded', () => {
    new Pl3xLandsEditor();
});