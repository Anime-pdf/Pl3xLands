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

CHUNK_SIZE = 16

def neighbors(cx, cz):
    return [
        (cx + CHUNK_SIZE, cz),
        (cx - CHUNK_SIZE, cz),
        (cx, cz + CHUNK_SIZE),
        (cx, cz - CHUNK_SIZE),
    ]

def pack_chunk(x: int, z: int) -> int:
    key = (x << 32) | (z & 0xffffffff)
    if key >= 1 << 63:
        key -= 1 << 64
    return key

def generate_region_chunks(
        start_x,
        start_z,
        chunk_count,
        max_radius_chunks=12
):
    visited = set()
    frontier = []

    start = (start_x, start_z)
    visited.add(start)
    frontier.append(start)

    while len(visited) < chunk_count and frontier:
        cx, cz = random.choice(frontier)

        for nx, nz in neighbors(cx, cz):
            if len(visited) >= chunk_count:
                break

            if abs(nx - start_x) > max_radius_chunks * CHUNK_SIZE:
                continue
            if abs(nz - start_z) > max_radius_chunks * CHUNK_SIZE:
                continue

            if (nx, nz) not in visited and random.random() < 0.6:
                visited.add((nx, nz))
                frontier.append((nx, nz))

        if random.random() < 0.3:
            frontier.remove((cx, cz))

    return list(visited)

def carve_holes(chunks, hole_chance=0.15):
    result = []

    for cx, cz in chunks:
        if random.random() < hole_chance:
            continue
        result.append((cx, cz))

    return result

def generate_large_dataset(region_count=20, chunks_per_region=100):
    regions = []

    for i in range(region_count):
        center_x = random.randrange(-4000, 4000, CHUNK_SIZE)
        center_z = random.randrange(-4000, 4000, CHUNK_SIZE)

        chunks_xy = generate_region_chunks(
            center_x,
            center_z,
            chunks_per_region
        )

        chunks_xy = carve_holes(chunks_xy, hole_chance=0.05)

        chunks = [pack_chunk(x, z) for x, z in chunks_xy]

        region = {
            "id": str(uuid.uuid4()),
            "name": f"Region_{i}",
            "description": f"This is a generated description for region {i} to test string serialization overhead.",
            "owner": str(uuid.uuid4()),
            "contact": f"user_{i}@example.com",
            "world": random.choice(["world", "world_nether"]),
            "chunks": chunks
        }
        regions.append(region)

    content_str = json.dumps(regions, sort_keys=True)
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