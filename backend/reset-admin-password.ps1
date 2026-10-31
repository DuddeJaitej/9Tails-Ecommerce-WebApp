$ErrorActionPreference = 'Stop'

$previousAdminEmail = $env:ADMIN_EMAIL
$previousAdminPassword = $env:ADMIN_PASSWORD
$previousResetFlag = $env:ADMIN_PASSWORD_RESET_ON_STARTUP
$password = $null
$passwordPointer = [IntPtr]::Zero

try {
    $emailInput = (Read-Host 'Admin email to reset').Trim().ToLowerInvariant()
    if ($emailInput -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
        throw 'Enter a valid administrator email address.'
    }

    $securePassword = Read-Host 'New password (at least 12 characters)' -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    if ($password.Length -lt 12) {
        throw 'Password must be at least 12 characters. Run this script again and choose a longer password.'
    }
    if ([System.Text.Encoding]::UTF8.GetByteCount($password) -gt 72) {
        throw 'Password must be no more than 72 UTF-8 bytes. Choose a shorter password.'
    }

    $env:ADMIN_EMAIL = $emailInput
    $env:ADMIN_PASSWORD = $password
    $env:ADMIN_PASSWORD_RESET_ON_STARTUP = 'true'

    Write-Host 'Starting the backend. The configured admin password will be reset once during this startup.'
    Write-Host 'After confirming admin sign-in, stop this backend with Ctrl+C and start it normally.'
    & mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot exited with code $LASTEXITCODE. Review the error above."
    }
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $password = $null
    $securePassword = $null

    if ($null -eq $previousAdminEmail) {
        Remove-Item Env:ADMIN_EMAIL -ErrorAction SilentlyContinue
    } else {
        $env:ADMIN_EMAIL = $previousAdminEmail
    }
    if ($null -eq $previousAdminPassword) {
        Remove-Item Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
    } else {
        $env:ADMIN_PASSWORD = $previousAdminPassword
    }
    if ($null -eq $previousResetFlag) {
        Remove-Item Env:ADMIN_PASSWORD_RESET_ON_STARTUP -ErrorAction SilentlyContinue
    } else {
        $env:ADMIN_PASSWORD_RESET_ON_STARTUP = $previousResetFlag
    }
}
