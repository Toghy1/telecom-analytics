# 离线演示包的本地静态服务器（Windows 自带 PowerShell，无需安装 Node / Python）。
#
# 为什么需要它：构建产物是 ES Module，双击 dist/index.html 用 file:// 打开会被
# 浏览器的 CORS 策略拦掉；路由是 history 模式，也需要「找不到路径就回 index.html」。
# 这个脚本就干这两件事。
#
# 用法：双击同目录的「启动.bat」，或手动执行
#     powershell -NoProfile -ExecutionPolicy Bypass -File serve.ps1
#
# 只监听 localhost（不对外网开放），端口从 8088 起，被占用就顺延到 8098。

$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot 'dist'
if (-not (Test-Path (Join-Path $root 'index.html'))) {
    Write-Host ''
    Write-Host '找不到 dist/index.html。' -ForegroundColor Red
    Write-Host '请确认压缩包完整解压，且 dist 与本脚本在同一层目录。'
    Write-Host ''
    Read-Host '按回车退出'
    exit 1
}

$rootFull = [System.IO.Path]::GetFullPath($root)

$listener = New-Object System.Net.HttpListener
$port = 0
foreach ($candidate in 8088..8098) {
    $listener.Prefixes.Clear()
    $listener.Prefixes.Add("http://localhost:$candidate/")
    try {
        $listener.Start()
        $port = $candidate
        break
    } catch {
        continue
    }
}

if ($port -eq 0) {
    Write-Host '8088-8098 都被占用，无法启动。请关掉占用端口的程序后重试。' -ForegroundColor Red
    Read-Host '按回车退出'
    exit 1
}

$url = "http://localhost:$port/"
Write-Host ''
Write-Host '  电信用户行为分析系统 —— 离线演示版' -ForegroundColor Cyan
Write-Host "  地址：$url"
Write-Host '  演示账号：13800001234 / analyst / admin，口令均为 123456'
Write-Host ''
Write-Host '  浏览器没有自动打开的话，把上面的地址复制到浏览器里。'
Write-Host '  ⚠️ 这个窗口必须保持打开，关掉它网页就断了。按 Ctrl+C 停止。'
Write-Host ''

Start-Process $url

$mime = @{
    '.html' = 'text/html; charset=utf-8'
    '.js'   = 'text/javascript; charset=utf-8'
    '.mjs'  = 'text/javascript; charset=utf-8'
    '.css'  = 'text/css; charset=utf-8'
    '.json' = 'application/json; charset=utf-8'
    '.svg'  = 'image/svg+xml'
    '.png'  = 'image/png'
    '.jpg'  = 'image/jpeg'
    '.jpeg' = 'image/jpeg'
    '.gif'  = 'image/gif'
    '.ico'  = 'image/x-icon'
    '.woff' = 'font/woff'
    '.woff2' = 'font/woff2'
    '.ttf'  = 'font/ttf'
    '.map'  = 'application/json; charset=utf-8'
}

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response

        try {
            $rel = [System.Uri]::UnescapeDataString($req.Url.AbsolutePath).TrimStart('/')
            if ([string]::IsNullOrWhiteSpace($rel)) { $rel = 'index.html' }

            $file = [System.IO.Path]::GetFullPath((Join-Path $rootFull $rel))

            # 防止 ../ 跳出 dist
            if (-not $file.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
                $res.StatusCode = 403
                $res.Close()
                continue
            }

            # 目录或不存在 → 回落 index.html（history 路由刷新）
            if ((Test-Path $file -PathType Container) -or -not (Test-Path $file -PathType Leaf)) {
                $file = Join-Path $rootFull 'index.html'
            }

            $ext = [System.IO.Path]::GetExtension($file).ToLowerInvariant()
            $res.ContentType = if ($mime.ContainsKey($ext)) { $mime[$ext] } else { 'application/octet-stream' }
            $bytes = [System.IO.File]::ReadAllBytes($file)
            $res.ContentLength64 = $bytes.Length
            # 演示包内容固定，允许缓存静态资源；index.html 不缓存，避免换包后看到旧页面
            if ($file -like '*index.html') {
                $res.Headers.Add('Cache-Control', 'no-cache')
            }
            $res.OutputStream.Write($bytes, 0, $bytes.Length)
        } catch {
            try { $res.StatusCode = 500 } catch { }
        } finally {
            try { $res.OutputStream.Close() } catch { }
        }
    }
} finally {
    if ($listener.IsListening) { $listener.Stop() }
    $listener.Close()
}
