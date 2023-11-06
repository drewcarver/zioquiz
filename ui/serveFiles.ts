const server = Bun.serve((req, res) => {
  const rootDirectory = './ui/';
  const filePath = Bun.file(rootDirectory.join(req.url));

  fs.access(filePath, fs.constants.F_OK, (err) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/html' });
      res.end('<h1>404 Not Found</h1>');
    } else {
      fs.readFile(filePath, (error, content) => {
        if (error) {
          res.writeHead(500, { 'Content-Type': 'text/html' });
          res.end('<h1>500 Internal Server Error</h1>');
        } else {
          const extname = path.extname(filePath);
          const contentType = getContentType(extname);
          res.writeHead(200, { 'Content-Type': contentType });
          res.end(content, 'utf-8');
        }
      });
    }
  });
});

function getContentType(extname) {
  switch (extname) {
    case '.html':
      return 'text/html';
    case '.css':
      return 'text/css';
    case '.js':
      return 'text/javascript';
    case '.jpg':
      return 'image/jpeg';
    case '.png':
      return 'image/png';
    default:
      return 'text/plain';
  }
}

const port = 3000; 
server.listen(port, () => {
  console.log(`Server is running on http://localhost:${port}`);
});
