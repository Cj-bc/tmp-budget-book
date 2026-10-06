import { serveDir, serveFile } from "jsr:@std/http/file-server";

// Disable caching mechanism so that server serves latest build.
// Because this project uses nix and serves files under nix store, all fiels have the same mtime,
// which results in returning 304 Not-Modified forever
function eraseCacheRequest(req: Request): Request {
  const headers = new Headers(req.headers);
  headers.delete("If-None-Match");
  headers.delete("If-Modified-Since");
  return new Request(req, { headers });
}

Deno.serve(
{ port: 3000, hostname: "127.0.0.1" },
 (req: Request) => {
  const cacheRequestStrippedReq = eraseCacheRequest(req);
  const pathname = new URL(req.url).pathname;

  return serveDir(cacheRequestStrippedReq, {
    fsRoot: "@read_path@",
    showDirListing: true,
    headers: ["Cache-Control: no-store"],
  })
})
