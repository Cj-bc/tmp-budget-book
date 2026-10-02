import { serveDir, serveFile } from "jsr:@std/http/file-server";

Deno.serve(
{ port: 3000, hostname: "127.0.0.1" },
 (req: Request) => {

  const pathname = new URL(req.url).pathname;

  return serveDir(req, {
    fsRoot: Deno.env.get("FSROOT"),
  })
})
