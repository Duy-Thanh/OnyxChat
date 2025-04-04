const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 8000;

const MIME_TYPES = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
};

const server = http.createServer((req, res) => {
  console.log(`${req.method} ${req.url}`);
  
  // parse URL
  const parsedUrl = url.parse(req.url);
  
  // extract the pathname from the parsed URL
  let pathname = `.${parsedUrl.pathname}`;
  
  // If the path is just '/', serve chat_client.html
  if (pathname === './') {
    pathname = './chat_client.html';
  }
  
  // Get the file extension
  const ext = path.parse(pathname).ext;
  
  // Maps file extension to MIME type
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';
  
  // Read file from file system
  fs.readFile(pathname, (err, data) => {
    if (err) {
      if (err.code === 'ENOENT') {
        // File not found
        res.writeHead(404);
        res.end(`File ${pathname} not found!`);
        return;
      }
      
      // Server error
      res.writeHead(500);
      res.end(`Error getting the file: ${err.code}`);
      return;
    }
    
    // Success - return the file
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log(`Server is running at http://localhost:${PORT}`);
}); 