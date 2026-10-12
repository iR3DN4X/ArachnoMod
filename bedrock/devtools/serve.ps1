param([int]$Port = 8765, [string]$Root)
# Minimal static file server so the browser pane can import the Bedrock engine's ES modules.
$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Start()
Write-Host "Serving $Root on http://localhost:$Port/"
while ($listener.IsListening) {
  $ctx = $listener.GetContext()
  try {
    $rel = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath.TrimStart('/'))
    if ($rel -eq '') {
      $bytes = [Text.Encoding]::UTF8.GetBytes('<!doctype html><meta charset="utf-8"><title>Engine sim</title><body>engine sim host</body>')
      $ctx.Response.ContentType = 'text/html'
    } else {
      $file = if ($rel.StartsWith('test/')) { Join-Path $PSScriptRoot ('simtest/' + $rel.Substring(5)) }
              elseif ($rel.StartsWith('rp/')) { Join-Path (Join-Path (Split-Path (Split-Path $Root)) 'resource_pack') $rel.Substring(3) }
              elseif ($rel.StartsWith('bp/')) { Join-Path (Split-Path $Root) $rel.Substring(3) }
              else { Join-Path $Root $rel }
      if (-not (Test-Path $file -PathType Leaf)) { $ctx.Response.StatusCode = 404; $ctx.Response.Close(); continue }
      $bytes = [IO.File]::ReadAllBytes($file)
      $ctx.Response.ContentType = switch ([IO.Path]::GetExtension($file)) { '.js' { 'text/javascript' } '.html' { 'text/html' } '.json' { 'application/json' } default { 'application/octet-stream' } }
    }
    $ctx.Response.Headers.Add('Cache-Control', 'no-store')
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
  } catch { }
  $ctx.Response.Close()
}
