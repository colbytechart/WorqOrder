param(
    [Parameter(Mandatory = $true)]
    [string] $ValidBackupPath,
    [string] $OutputDirectory = (Join-Path (Get-Location) 'm54-fixtures')
)

$source = (Resolve-Path -LiteralPath $ValidBackupPath -ErrorAction Stop).Path
if ([IO.Path]::GetExtension($source) -ne '.zip') {
    throw 'ValidBackupPath must point to a .zip file created by WorqOrder.'
}

$bytes = [IO.File]::ReadAllBytes($source)
if ($bytes.Length -lt 128) {
    throw 'The selected backup is unexpectedly small; create a fresh WorqOrder backup.'
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$validOutput = Join-Path $OutputDirectory 'WorqOrder_M54_valid.zip'
$malformedOutput = Join-Path $OutputDirectory 'WorqOrder_M54_malformed_truncated.zip'
[IO.File]::Copy($source, $validOutput, $true)

$truncatedLength = [Math]::Max(1, [Math]::Floor($bytes.Length / 2))
$truncated = New-Object byte[] $truncatedLength
[Array]::Copy($bytes, $truncated, $truncatedLength)
[IO.File]::WriteAllBytes($malformedOutput, $truncated)

Get-FileHash -LiteralPath $validOutput -Algorithm SHA256
Get-FileHash -LiteralPath $malformedOutput -Algorithm SHA256
Write-Output "Valid fixture: $validOutput"
Write-Output "Malformed fixture: $malformedOutput"
