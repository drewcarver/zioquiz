const server = Bun.serve({
    fetch(request) {
      const fileName = /^http:\/\/(.*)\/(.*)/.exec(request.url)?.at(-1)
      return new Response(Bun.file(fileName!))
    },
})

console.log(`Server is running on port: ${server.port}`)
