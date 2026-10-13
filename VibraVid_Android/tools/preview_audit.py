"""Serve packaged UI assets with a disposable native-bridge fixture on loopback only."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import json
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ASSETS), **kwargs)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == '/':
            body = (ASSETS/'index.html').read_text(encoding='utf-8').replace(
                '<script src="model.js" defer>', '<script src="/qa-fixture.js"></script><script src="model.js" defer>')
            body = body.replace('<body>', '<body><aside style="position:fixed;right:0;top:0;z-index:100;background:#222;color:white;font-size:10px"><button id="qa-hold" type="button">Ritarda salvataggio</button><button id="qa-release" type="button">Completa salvataggio</button><span id="qa-state" role="status">QA</span></aside>')
            self.reply(body, 'text/html')
        elif path == '/qa-fixture.js':
            sources = json.loads((ASSETS/'providers.json').read_text(encoding='utf-8'))
            for source in sources:
                source['automatic'] = source['id'] in ('streamingcommunity','streamingcommunity_2','animeunity','animeworld','eurostreaming','raiplay')
            script = (ROOT/'tools/ui_fixture.js').read_text(encoding='utf-8').replace('/*SOURCES*/[]', json.dumps(sources))
            self.reply(script, 'text/javascript')
        else:
            super().do_GET()

    def reply(self, content, kind):
        data = content.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', kind+'; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args): pass

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1',38910),Handler).serve_forever()
