#!/usr/bin/env -S deno run --allow-read=@read_path@ --config @config_path@ --allow-net=127.0.0.1 --ext=ts
import { serveDir, serveFile } from "jsr:@std/http/file-server";

Deno.serve(
{ port: 3000, hostname: "127.0.0.1" },
 (req: Request) => {

  const pathname = new URL(req.url).pathname;

  return serveDir(req, {
    fsRoot: "@read_path@",
  })
})
