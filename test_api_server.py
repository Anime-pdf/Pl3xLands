import http.server
import socketserver
import json
import hashlib
import time
import uuid
import random
import struct

PORT = 8000

# def pack_chunk(x: int, z: int) -> int:
#     return (x & 0xffffffff) | ((z & 0xffffffff) << 32)

def pack_chunk(x: int, z: int) -> int:
    key = (x & 0xffffffff) | ((z & 0xffffffff) << 32)
    if key >= 1 << 63:
        key -= 1 << 64
    return key

def generate_large_dataset(region_count=50, chunks_per_region=100):
    print(f"Generating {region_count} regions with {chunks_per_region} chunks each...")
    
    regions = []
    for i in range(region_count):
        center_x = random.randint(-10000, 10000)
        center_z = random.randint(-10000, 10000)
        chunks = []
        for _ in range(chunks_per_region):
            cx = center_x + random.randint(-5, 5)
            cz = center_z + random.randint(-5, 5)
            chunks.append(pack_chunk(cx, cz))

        region = {
            "id": str(uuid.uuid4()),
            "name": f"Region_{i}",
            "description": f"This is a generated description for region {i} to test string serialization overhead.",
            "ownerId": str(uuid.uuid4()),
            "contact": f"user_{i}@example.com",
            "world": f"world",
            "chunks": chunks
        }
        regions.append(region)

    regions_json = regions
    
    content_str = json.dumps(regions)
    data_hash = hashlib.sha256(content_str.encode('utf-8')).hexdigest()

    manifest = {
        "hash": data_hash,
        "timestamp": int(time.time() * 1000),
        "regions": regions
    }
    
    return manifest

CURRENT_DATA = generate_large_dataset()
print(f"Data ready. Hash: {CURRENT_DATA['hash']}")

class MockApiHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        # 1. Endpoint: /status
        # Returns just the hash so the client can decide if it needs to update
        if self.path == '/status':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            response = {
                "hash": CURRENT_DATA['hash'],
                "timestamp": CURRENT_DATA['timestamp']
            }
            self.wfile.write(json.dumps(response).encode('utf-8'))
            print(f"[200] /status requested. Returned hash: {CURRENT_DATA['hash'][:8]}...")

        # 2. Endpoint: /regions
        # Returns the full heavy payload
        elif self.path == '/regions':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            # Send the massive JSON object
            self.wfile.write(json.dumps(CURRENT_DATA).encode('utf-8'))
            print(f"[200] /regions requested. Sent {len(CURRENT_DATA['regions'])} regions.")

        else:
            self.send_response(404)
            self.end_headers()

if __name__ == "__main__":
    with socketserver.TCPServer(("", PORT), MockApiHandler) as httpd:
        print(f"\nServer started at http://localhost:{PORT}")
        print("Endpoints:")
        print(f"  GET http://localhost:{PORT}/status")
        print(f"  GET http://localhost:{PORT}/regions")
        print("\nPress Ctrl+C to stop.")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass