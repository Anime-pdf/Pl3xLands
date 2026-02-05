#!/usr/bin/env python3

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
from datetime import datetime

SAMPLE_DATA = {
    "success": True,
    "data": {
        "lands": [
            {
                "id": "land_001",
                "name": "Spawn Town",
                "description": "The main spawn area for all new players",
                "contact": "admin@example.com",
                "color": "#8000FF00",
                "chunks": [
                    {"x": 0, "z": 0, "world": "world"},
                    {"x": 1, "z": 0, "world": "world"},
                    {"x": 0, "z": 1, "world": "world"},
                    {"x": 1, "z": 1, "world": "world"},
                    {"x": 2, "z": 0, "world": "world"},
                    {"x": 2, "z": 1, "world": "world"}
                ],
                "created_at": int(datetime(2024, 1, 1).timestamp() * 1000),
                "updated_at": int(datetime.now().timestamp() * 1000)
            },
            {
                "id": "land_002",
                "name": "Player Base - Alice",
                "description": "Alice's awesome castle with automatic farms",
                "contact": "alice@example.com",
                "color": "#800000FF",
                "chunks": [
                    {"x": 10, "z": 10, "world": "world"},
                    {"x": 11, "z": 10, "world": "world"},
                    {"x": 10, "z": 11, "world": "world"},
                    {"x": 11, "z": 11, "world": "world"}
                ],
                "created_at": int(datetime(2024, 1, 15).timestamp() * 1000),
                "updated_at": int(datetime.now().timestamp() * 1000)
            },
            {
                "id": "land_003",
                "name": "Shopping District",
                "description": "Community shopping area - all are welcome!",
                "contact": "community@example.com",
                "color": "#80FFA500",
                "chunks": [
                    {"x": -5, "z": -5, "world": "world"},
                    {"x": -4, "z": -5, "world": "world"},
                    {"x": -3, "z": -5, "world": "world"},
                    {"x": -5, "z": -4, "world": "world"},
                    {"x": -4, "z": -4, "world": "world"},
                    {"x": -3, "z": -4, "world": "world"}
                ],
                "created_at": int(datetime(2024, 1, 20).timestamp() * 1000),
                "updated_at": int(datetime.now().timestamp() * 1000)
            },
            {
                "id": "land_004",
                "name": "Nether Hub",
                "description": "Fast travel network hub",
                "contact": "admin@example.com",
                "chunks": [
                    {"x": 0, "z": 0, "world": "world_nether"},
                    {"x": 1, "z": 0, "world": "world_nether"},
                    {"x": 0, "z": 1, "world": "world_nether"},
                    {"x": 1, "z": 1, "world": "world_nether"}
                ],
                "created_at": int(datetime(2024, 1, 25).timestamp() * 1000),
                "updated_at": int(datetime.now().timestamp() * 1000)
            }
        ],
        "last_updated": int(datetime.now().timestamp() * 1000)
    },
    "timestamp": int(datetime.now().timestamp() * 1000)
}

class LandsAPIHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/lands' or self.path == '/lands/claims':
            SAMPLE_DATA['timestamp'] = int(datetime.now().timestamp() * 1000)
            SAMPLE_DATA['data']['last_updated'] = int(datetime.now().timestamp() * 1000)
            for land in SAMPLE_DATA['data']['lands']:
                land['updated_at'] = int(datetime.now().timestamp() * 1000)
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            response = json.dumps(SAMPLE_DATA, indent=2)
            self.wfile.write(response.encode())
            
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Served land data: {len(SAMPLE_DATA['data']['lands'])} lands")
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.end_headers()
    
    def log_message(self, format, *args):
        pass


def run_server(port=8000):
    server_address = ('', port)
    httpd = HTTPServer(server_address, LandsAPIHandler)
    
    print("=" * 60)
    print("Pl3xLands - Test API Server")
    print("=" * 60)
    print(f"Server running on http://localhost:{port}")
    print(f"API endpoint: http://localhost:{port}/lands")
    print()
    print("Set this in your config.yml:")
    print(f"  api:")
    print(f"    url: \"http://localhost:{port}/lands\"")
    print()
    print("Press Ctrl+C to stop the server")
    print("=" * 60)
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server...")
        httpd.shutdown()


if __name__ == '__main__':
    run_server()
