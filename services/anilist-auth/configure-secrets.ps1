# Run locally after wrangler login and the initial worker deployment. Never paste the secret in chat.
param([switch]$InitializeEncryptionKey)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $secret = Read-Host 'AniList Client Secret (entrada oculta, solo se envia a Cloudflare)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | npx.cmd --yes wrangler@4 secret put CLIENT_SECRET
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo configurar CLIENT_SECRET.' }
    } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secret.Dispose() }
    if ($InitializeEncryptionKey) {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        try {
            ($bytes | ForEach-Object { $_.ToString('x2') }) -join '' | npx.cmd --yes wrangler@4 secret put TOKEN_KEY
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo configurar TOKEN_KEY.' }
        } finally { [Array]::Clear($bytes, 0, $bytes.Length) }
    }
} finally { Pop-Location }
