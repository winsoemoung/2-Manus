#!/usr/bin/env python3
import http.server
import socketserver
import os
import sys

PORT = 3000
DIRECTORY = "/app/applet"

class CustomHTTPHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        # Enable CORS and appropriate download headers
        self.send_header('Access-Control-Allow-Origin', '*')
        if self.path.endswith('.apk'):
            self.send_header('Content-Type', 'application/vnd.android.package-archive')
            filename = os.path.basename(self.path.split('?')[0])
            self.send_header('Content-Disposition', f'attachment; filename="{filename}"')
        super().end_headers()

def run():
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), CustomHTTPHandler) as httpd:
        print(f"Serving APKs on port {PORT}...", flush=True)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            httpd.server_close()

if __name__ == "__main__":
    run()
