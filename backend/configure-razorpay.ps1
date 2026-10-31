$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path -LiteralPath $envFile) {
    $replace = Read-Host "backend/.env already exists. Replace it with rotated Test Mode credentials? (y/N)"
    if ($replace -notmatch '^(y|yes)$') {
        Write-Host "Existing credentials were left unchanged."
        exit 0
    }
}

$keyId = (Read-Host "Razorpay Test Mode Key ID").Trim()
if ($keyId -notmatch '^rzp_test_[A-Za-z0-9]+$') {
    throw "Enter a valid Razorpay Test Mode Key ID beginning with rzp_test_."
}

$secureKeySecret = Read-Host "Razorpay Test Mode Key Secret (input is hidden)" -AsSecureString
$keySecretPointer = [IntPtr]::Zero
try {
    $keySecretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKeySecret)
    $keySecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keySecretPointer)
    if ([string]::IsNullOrWhiteSpace($keySecret) -or $keySecret.Contains("`r") -or $keySecret.Contains("`n")) {
        throw "The Razorpay Test Mode Key Secret cannot be empty or contain line breaks."
    }

    $preservedLines = @()
    if (Test-Path -LiteralPath $envFile) {
        $preservedLines = @(Get-Content -LiteralPath $envFile | Where-Object {
            $_ -notmatch '^\s*RAZORPAY_KEY_(ID|SECRET)\s*='
        })
    }

    $lines = @($preservedLines) + @(
        "RAZORPAY_KEY_ID=$keyId",
        "RAZORPAY_KEY_SECRET=$keySecret"
    )
    [System.IO.File]::WriteAllLines(
        $envFile,
        $lines,
        [System.Text.Encoding]::ASCII
    )
}
finally {
    if ($keySecretPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keySecretPointer)
    }
    $keySecret = $null
    $secureKeySecret = $null
}

Write-Host "Razorpay Test Mode credentials saved to ignored backend/.env."
Write-Host "Restart the backend before testing Checkout."
